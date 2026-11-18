"""Causal stream/feature checks without TensorFlow loading or simulation."""
import copy
import csv
import io
import unittest
import numpy as np
from mobility_shadow_worker import History,HEADER,features
from build_mobility_windows import windows_for_run


def sample(i):
    return dict(zip(HEADER,['run','0',str(193000+i*60000),str(47.73),str(18.27+i*.001),
        '60.0' if i else '', '120.0' if i else '', '2.0' if i else '', '90.0' if i else '',
        'true' if i else 'false','true' if i else 'false','NONE']))


def raw(r):
    out=io.StringIO();csv.writer(out).writerow([r[k] for k in HEADER]);return out.getvalue().rstrip('\r\n')


class MobilityShadowTest(unittest.TestCase):
    def test_first_valid_window_after_nine_observations(self):
        h=History()
        for i in range(8):self.assertIsNone(h.append(raw(sample(i)))[1])
        r,x=h.append(raw(sample(8)));self.assertEqual(np.asarray(x).shape,(8,6))
        self.assertEqual([int(r['time_ms']) for r in h.rows],[193000+i*60000 for i in range(1,9)])

    def test_exact_offline_parity_for_all_112_origins(self):
        samples=[sample(i) for i in range(121)];run=dict(case_id='dev',run_id='run',route_id='dev',split='development')
        expected,_=windows_for_run(run,samples,[47.73,18.27]);h=History();actual=[]
        for i,r in enumerate(samples):
            _,x=h.append(raw(r))
            if x is not None and i<120:actual.append(x)
        self.assertEqual(len(actual),112)
        np.testing.assert_array_equal(actual,[r['x'] for r in expected])

    def test_future_observation_cannot_mutate_existing_input(self):
        h=History()
        for i in range(9):_,x=h.append(raw(sample(i)))
        before=copy.deepcopy(x);future=sample(9);future['latitude_deg']='48.1'
        h.append(raw(future));self.assertEqual(x,before)

    def test_gap_identity_and_duplicate_rejected(self):
        for field,value in [('time_ms','193000'),('time_ms','313000'),('run_id','another'),('device_id','1')]:
            h=History();h.append(raw(sample(0)));r=sample(1);r[field]=value
            with self.assertRaises(ValueError):h.append(raw(r))

    def test_stationary_mask_is_retained(self):
        h=History();h.append(raw(sample(0)))
        for i in range(1,9):
            r=sample(i);r.update(displacement_m='0',observed_speed_m_s='0',heading_deg='',heading_valid='false')
            _,x=h.append(raw(r))
        self.assertTrue(np.all(np.asarray(x)[:,3:]==0))

    def test_heading_wrap_encoding(self):
        history=[sample(i) for i in range(1,9)];history[0]['heading_deg']='359';history[1]['heading_deg']='1'
        x=features(history);self.assertLess(x[0][3],0);self.assertGreater(x[1][3],0)
        self.assertAlmostEqual(x[0][4],x[1][4])

    def test_nonfinite_position_rejected(self):
        r=sample(0);r['latitude_deg']='nan'
        with self.assertRaises(ValueError):History().append(raw(r))


if __name__=='__main__':unittest.main(verbosity=2)
