"""Target-time causality and stream integration checks; no TensorFlow or Java required."""
import copy
import csv
import io
import json
import math
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import numpy as np
import mobility_shadow_worker as worker
from mobility_shadow_evaluation import DelayedEvaluation, MODELS, distance_m
from run_mobility_shadow import audit_evaluations
from check_mobility_run import rows
from test_mobility_shadow import sample, raw


def predictions(r):
    return [dict(model=model,device_id=r['device_id'],origin_ms=int(r['time_ms']),
                 target_ms=int(r['time_ms'])+60000,origin_node=r['current_node'],
                 predicted_latitude_deg=float(r['latitude_deg']),
                 predicted_longitude_deg=float(r['longitude_deg'])) for model in MODELS]


def stream_fixture(directory, end=True):
    training=directory/'model';training.mkdir()
    for name in ('training.json','preprocessing.json','best.keras'):
        (training/name).write_text('test fixture, not a trained model')
    output=directory/'output';output.mkdir()
    samples=[sample(i) for i in range(121)]
    requests=['OBS\t%s\t%s'%('true' if i<120 else 'false',raw(r)) for i,r in enumerate(samples)]
    if end:requests.append('END')
    stdout=io.StringIO();stdin=io.StringIO('\n'.join(requests)+'\n')
    scaler=dict(x_mean=[0,0,0],x_std=[1,1,1],y_scale_m=1.)
    def zero_model(x,training=False):return np.zeros((len(x),2),dtype=np.float32)
    with patch.object(worker,'frozen_model',return_value=(zero_model,scaler)), \
         patch('sys.stdin',stdin),patch('sys.stdout',stdout):
        worker.serve(training,output)
    return output,samples,stdout.getvalue()


class MobilityShadowEvaluationTest(unittest.TestCase):
    def test_no_evaluation_before_real_target_arrives(self):
        scoring=DelayedEvaluation();self.assertEqual(scoring.observe(sample(0)),[])
        scoring.issue(predictions(sample(0)))
        self.assertEqual(sum(map(len,scoring.errors.values())),0)
        self.assertEqual(len(scoring.pending),3)
        result=scoring.observe(sample(1))
        self.assertEqual(len(result),3)
        self.assertTrue(all(r['evaluated_at_sim_ms']==r['target_ms']==253000 for r in result))
        self.assertTrue(all(r['actual_longitude_deg']==float(sample(1)['longitude_deg']) for r in result))

    def test_forecasts_are_frozen_and_current_truth_changes_only_error(self):
        a,b=DelayedEvaluation(),DelayedEvaluation();issued=predictions(sample(0))
        for s in (a,b):s.observe(sample(0));s.issue(issued)
        issued[0]['predicted_longitude_deg']=0.
        future=sample(1);future['longitude_deg']='18.28'
        left=a.observe(sample(1));right=b.observe(future)
        self.assertEqual(left[0]['predicted_longitude_deg'],18.27)
        self.assertEqual(left[0]['predicted_longitude_deg'],right[0]['predicted_longitude_deg'])
        self.assertGreater(right[0]['position_error_m'],left[0]['position_error_m'])

    def test_duplicate_issue_and_duplicate_target_are_rejected(self):
        s=DelayedEvaluation();s.observe(sample(0));s.issue(predictions(sample(0)))
        with self.assertRaises(ValueError):s.issue(predictions(sample(0)))
        s.observe(sample(1))
        with self.assertRaises(ValueError):s.observe(sample(1))
        self.assertTrue(all(len(v)==1 for v in s.errors.values()))

    def test_gap_and_identity_mismatch_do_not_consume_pending(self):
        for future in (sample(2),dict(sample(1),run_id='other'),dict(sample(1),device_id='1')):
            s=DelayedEvaluation();s.observe(sample(0));s.issue(predictions(sample(0)))
            with self.assertRaises(ValueError):s.observe(future)
            self.assertEqual(len(s.pending),3)
            self.assertEqual(len(s.observe(sample(1))),3)

    def test_bad_forecast_batches_rejected_atomically(self):
        variants=[]
        for field,value in [('origin_ms',0),('target_ms',193000),('device_id','1'),
                            ('origin_node','other'),('predicted_latitude_deg',float('nan')),
                            ('predicted_longitude_deg',181.)]:
            p=predictions(sample(0));p[1][field]=value;variants.append(p)
        variants.append(predictions(sample(0))[:2])
        p=predictions(sample(0));p[1]['model']=p[0]['model'];variants.append(p)
        for p in variants:
            s=DelayedEvaluation();s.observe(sample(0))
            with self.assertRaises(ValueError):s.issue(p)
            self.assertEqual(s.pending,[]);self.assertEqual(s.issued,0)

    def test_finish_requires_actual_observation_even_when_disconnected(self):
        s=DelayedEvaluation();s.observe(sample(0));s.issue(predictions(sample(0)))
        with self.assertRaises(ValueError):s.finish()
        evaluated=s.observe(sample(1))
        self.assertTrue(all(e['origin_node']=='NONE' for e in evaluated))
        self.assertEqual({k:v['count'] for k,v in s.finish().items()},{k:1 for k in MODELS})

    def test_distance_has_known_units_and_dateline_behavior(self):
        self.assertEqual(distance_m((47,18),(47,18)),0)
        self.assertAlmostEqual(distance_m((0,0),(0,1)),6378137*math.pi/180,places=7)
        self.assertAlmostEqual(distance_m((0,179),(0,-179)),2*6378137*math.pi/180,places=7)

    def test_stream_settles_final_observation_without_new_forecast(self):
        with tempfile.TemporaryDirectory() as tmp:
            output,samples,protocol=stream_fixture(Path(tmp))
            predictions_,_=rows(output/'shadow_predictions.csv')
            inputs=[json.loads(line) for line in (output/'shadow_inputs.jsonl').read_text().splitlines()]
            result=audit_evaluations(output,samples,predictions_,inputs)
            self.assertEqual(result['evaluation_rows'],336)
            self.assertEqual(result['pending_forecasts'],0)
            self.assertEqual(protocol.splitlines()[-2:],['WAIT\t7393000','DONE\t121\t112'])
            events=[json.loads(line) for line in (output/'shadow_events.jsonl').read_text().splitlines()]
            self.assertEqual([e['event'] for e in events[-2:]],['OBSERVATION','EVALUATION'])
            self.assertEqual(events[-1]['target_ms'],7393000)
            self.assertEqual(protocol.count('PRED\t'),112)

    def test_audit_rejects_early_evaluation_changed_truth_and_error(self):
        with tempfile.TemporaryDirectory() as tmp:
            output,samples,_=stream_fixture(Path(tmp))
            predictions_,_=rows(output/'shadow_predictions.csv')
            inputs=[json.loads(line) for line in (output/'shadow_inputs.jsonl').read_text().splitlines()]
            evaluations,header=rows(output/'shadow_evaluations.csv')
            for field,value in [('evaluated_at_sim_ms',str(int(evaluations[0]['origin_ms']))),
                                ('actual_latitude_deg','47.0'),('position_error_m','0'),
                                ('predicted_longitude_deg','18.0')]:
                changed=copy.deepcopy(evaluations);changed[0][field]=value
                with (output/'shadow_evaluations.csv').open('w',newline='',encoding='utf-8') as stream:
                    writer=csv.DictWriter(stream,fieldnames=header);writer.writeheader();writer.writerows(changed)
                with self.assertRaises(ValueError):audit_evaluations(output,samples,predictions_,inputs)

    def test_audit_rejects_evaluation_before_observation_in_journal(self):
        with tempfile.TemporaryDirectory() as tmp:
            output,samples,_=stream_fixture(Path(tmp))
            predictions_,_=rows(output/'shadow_predictions.csv')
            inputs=[json.loads(line) for line in (output/'shadow_inputs.jsonl').read_text().splitlines()]
            path=output/'shadow_events.jsonl';events=[json.loads(line) for line in path.read_text().splitlines()]
            i=next(i for i,e in enumerate(events) if e['event']=='EVALUATION')
            events[i-1],events[i]=events[i],events[i-1]
            for j,e in enumerate(events):e['sequence']=j+1
            path.write_text(''.join(json.dumps(e)+'\n' for e in events))
            with self.assertRaises(ValueError):audit_evaluations(output,samples,predictions_,inputs)

    def test_aborted_stream_cannot_report_complete(self):
        with tempfile.TemporaryDirectory() as tmp:
            with self.assertRaisesRegex(ValueError,'without END'):stream_fixture(Path(tmp),end=False)
            output=Path(tmp)/'output'
            self.assertEqual(json.loads((output/'shadow-worker.json').read_text())['status'],'FAILED')
            self.assertFalse((output/'shadow_metrics.json').exists())


if __name__=='__main__':unittest.main(verbosity=2)
