"""Adversarial audit fixtures: leakage, premature selection and fabricated tails."""
import csv
import json
from pathlib import Path
import tempfile
import unittest

from audit_node_load_models import audit
from node_load_worker import checked_request


def write_rows(path, data):
    with path.open('w', newline='') as stream:
        writer = csv.DictWriter(stream, fieldnames=list(data[0]))
        writer.writeheader(); writer.writerows(data)


class LoadAuditTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.output = Path(self.temp.name) / 'run-test'; self.output.mkdir()
        self.key = dict(run_id='run-test', node_id='west', model='LSTM', version='v0001')
        samples = [dict(run_id='run-test',node_id='west',sample_index=i,time_ms=i*60000,waiting_assignment_bytes=100)
                   for i in range(351)]
        write_rows(self.output / 'node_load_samples.csv', samples)
        self.forecasts = [dict(self.key,origin_index=at,origin_ms=at*60000,target_index=at+d,target_ms=(at+d)*60000,
                              lead_steps=d,predicted_bytes=100,persistence_bytes=100,inference_vector_ms=1)
                          for at in (286,350) for d in range(1,65)]
        write_rows(self.output / 'node_load_model_predictions.csv', self.forecasts)
        self.evaluations = [dict(self.key,origin_index=286,target_index=i,target_ms=i*60000,predicted_bytes=100,
                                 actual_bytes=100,absolute_error_bytes=0,persistence_error_bytes=0) for i in range(287,351)]
        write_rows(self.output / 'node_load_model_evaluations.csv', self.evaluations)
        self.selections = [dict(self.key,model='Persistence' if i<350 else 'LSTM',version='fixed-v1' if i<350 else 'v0001',
                               selection_index=i,selection_ms=i*60000,target_index=i+1,target_ms=(i+1)*60000,
                               forecast_origin_index=i,lead_steps=1,predicted_bytes=100) for i in range(351)]
        write_rows(self.output / 'node_load_model_selection.csv', self.selections)
        metrics = dict(matched=64,mae_bytes=0,rmse_bytes=0,smape_percent=0,within_tolerance_percent=100)
        events = [dict(self.key,domain='NODE_LOAD',event=event,sample_index=at,time_ms=at*60000,metrics=metrics if at==350 else {})
                  for event,at in [('TRAINING',286),('TRAINED',286),('QUALIFIED',350),('PROMOTED',350)]]
        (self.output / 'node_load_model_events.jsonl').write_text(''.join(json.dumps(e)+'\n' for e in events))
        self.folder = self.output / 'node_load_models/west/LSTM/v0001'
        (self.folder / 'training').mkdir(parents=True)
        (self.folder / 'training/samples.csv').write_text('west::notYetProcessedData\n'+'100\n'*287)
        (self.folder / 'node_training.json').write_text(json.dumps(dict(self.key,origin_index=286,last_training_index=286,
                                                                       first_training_index=0,epochs=40,training_seconds=1)))
        (self.folder / 'optimized_training_report.json').write_text(json.dumps(dict(training_windows=32,
            training_columns=['west::notYetProcessedData'],epochs=40,normalization_mean=100,normalization_std=1)))
        version = dict(node_id='west',model='LSTM',version='v0001',trained_at_index=286,raw_training_points=287,training_windows=32,
                       forecasts=128,matched=64,unmatched=64,first_qualification='PASSED',final_state='ACTIVE',inference_calls=2,
                       mean_vector_inference_ms=1,training_seconds=1,mae_bytes=0,rmse_bytes=0,
                       paired_persistence_mae_bytes=0,paired_persistence_rmse_bytes=0)
        summary = dict(status='COMPLETE',run_id='run-test',models=['LSTM'],profile='candidate',epochs=40,input_points=192,
                       output_points=64,forecast_stride=64,interval_ms=60000,minimum_training_windows=32,minimum_new_points=64,
                       window_growth_points=64,resource_control=False,connection_control=False,offload_control=False,
                       target='waiting_assignment_bytes',units='B',model_scope='PER_RUN_PER_NODE_PER_FAMILY',
                       quality_standard='EXPERIMENTAL_UNCALIBRATED',versions=[version],final_selected={'west':'LSTM-v0001'},
                       quality_policy=dict(matched_targets=64,minimum_within_tolerance_percent=80,relative_tolerance=.2,
                                           absolute_tolerance_bytes=.1,maximum_smape_percent=200,mae_mse_rmse_limits='UNBOUNDED'))
        (self.output / 'node_load_model_summary.json').write_text(json.dumps(summary))

    def tearDown(self): self.temp.cleanup()

    def test_causal_fixture_retains_unobserved_tail(self):
        report = audit(self.output)
        self.assertEqual(report['matched'],64)
        self.assertEqual(report['unmatched'],64)
        self.assertEqual(report['saved_model_replay'],'NOT_RUN')

    def test_rejects_a_model_selected_before_qualification(self):
        self.selections[349].update(model='LSTM',version='v0001',forecast_origin_index=286,lead_steps=64)
        write_rows(self.output / 'node_load_model_selection.csv',self.selections)
        with self.assertRaisesRegex(ValueError,'routing mismatch'):
            audit(self.output)

    def test_rejects_training_data_not_in_the_node_history(self):
        path=self.folder / 'training/samples.csv'
        path.write_text('west::notYetProcessedData\n'+'100\n'*286+'999\n')
        with self.assertRaisesRegex(ValueError,'observed past'):
            audit(self.output)

    def test_rejects_scoring_a_future_unobserved_target(self):
        self.evaluations.append(dict(self.evaluations[-1],origin_index=350,target_index=351,target_ms=351*60000))
        write_rows(self.output / 'node_load_model_evaluations.csv',self.evaluations)
        with self.assertRaisesRegex(ValueError,'future target'):
            audit(self.output)

    def test_worker_rejects_future_length_invalid_paths_and_nonfinite_inputs(self):
        base=dict(model='LSTM',node='west',version='v0001',origin_index=286,values=[100]*287)
        self.assertEqual(len(checked_request(base,287)),287)
        for changed in (dict(origin_index=285),dict(node='../west'),dict(values=[float('nan')]*287),dict(values=[True]*287)):
            with self.subTest(changed=list(changed)),self.assertRaises(ValueError):
                checked_request(dict(base,**changed),287)


if __name__ == '__main__': unittest.main()
