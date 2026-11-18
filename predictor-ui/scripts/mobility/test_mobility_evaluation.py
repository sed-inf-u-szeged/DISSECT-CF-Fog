"""Test split isolation and frozen evaluation guards; no training required."""
import copy
import json
from pathlib import Path
import tempfile
import unittest
import numpy as np
from evaluate_mobility_test import child,validate_records,load_test,verify_bundle,evaluate
from mobility_learning import displacement_to_gps


class MobilityEvaluationTest(unittest.TestCase):
    def setUp(self):
        self.cases=[];self.rows=[]
        target=displacement_to_gps(np.array([[120.,0.]]),np.array([[47.73,18.27]]),[47.73,18.27])[0]
        for route in (1,2):
            for speed in (20,41,60):
                case=f'poly_{route:02d}_s{speed}';run='run-'+case
                self.cases.append(dict(case_id=case,run_id=run,split='test'))
                for i in range(8,120):
                    origin=193000+i*60000
                    self.rows.append(dict(window_id=case+':'+str(origin),case_id=case,
                        run_id=run,device_id='0',route_id=f'poly_{route:02d}',split='test',
                        origin_index=i,history_start_index=i-7,target_index=i+1,origin_ms=origin,
                        target_ms=origin+60000,history_time_ms=list(range(origin-420000,origin+1,60000)),
                        origin_node='NONE',x=[[float((j-7)*120),0.,2.,1.,0.,1.] for j in range(8)],
                        y=[120.,0.],anchor_latitude_deg=47.73,anchor_longitude_deg=18.27,
                        target_latitude_deg=float(target[0]),target_longitude_deg=float(target[1])))
        self.manifest={'cases':self.cases}

    def test_valid_disconnected_windows_retained(self):
        x,anchor,target=validate_records(self.rows,self.manifest)
        self.assertEqual(x.shape,(672,8,6));self.assertEqual(len(target),672)

    def test_train_or_validation_rows_rejected(self):
        for split in ('train','validation'):
            rows=copy.deepcopy(self.rows);rows[0]['split']=split
            with self.assertRaises(ValueError):validate_records(rows,self.manifest)

    def test_duplicate_gap_and_mixed_device_rejected(self):
        for field,value in [('device_id','1'),('target_ms',1),('run_id','other')]:
            rows=copy.deepcopy(self.rows);rows[0][field]=value
            with self.assertRaises(ValueError):validate_records(rows,self.manifest)
        rows=copy.deepcopy(self.rows);rows[1]=copy.deepcopy(rows[0])
        with self.assertRaises(ValueError):validate_records(rows,self.manifest)

    def test_bad_target_or_nonfinite_rejected(self):
        rows=copy.deepcopy(self.rows);rows[0]['y'][0]+=100
        with self.assertRaises(ValueError):validate_records(rows,self.manifest)
        rows=copy.deepcopy(self.rows);rows[0]['x'][0][0]=float('nan')
        with self.assertRaises(ValueError):validate_records(rows,self.manifest)

    def test_checksum_precedes_json_loading(self):
        with tempfile.TemporaryDirectory() as tmp:
            path=Path(tmp);(path/'test.jsonl').write_text('NOT JSON')
            with self.assertRaisesRegex(ValueError,'checksum'):
                load_test(path,{'files_sha256':{'test.jsonl':'0'*64}})

    def test_failed_training_rejected_before_test_access(self):
        with tempfile.TemporaryDirectory() as tmp:
            path=Path(tmp);(path/'training.json').write_text(json.dumps({'status':'FAILED'}))
            with self.assertRaisesRegex(ValueError,'complete v1'):
                verify_bundle(path,path/'absent-windows')

    def test_existing_output_rejected_before_reading_data(self):
        with tempfile.TemporaryDirectory() as tmp:
            path=Path(tmp)
            with self.assertRaisesRegex(ValueError,'new evaluation directory'):
                evaluate(path/'absent-training',path/'absent-windows',path)

    def test_artifact_path_escape_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)
            for name in ('../best.keras','/tmp/best.keras','C:\\best.keras','..\\best.keras'):
                with self.assertRaises(ValueError):child(root,name)


if __name__=='__main__':unittest.main(verbosity=2)
