"""Learning data/scaling/metric checks; no TensorFlow dependency or training."""
import copy
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import numpy as np
from mobility_learning import (fit_scaler,transform_x,baseline_displacements,displacement_to_gps,
                               position_errors,error_metrics,load_windows)
from build_mobility_windows import FEATURES, RADIUS, sha256


class MobilityLearningTest(unittest.TestCase):
    def setUp(self):
        rng=np.random.default_rng(42)
        self.train={'x':rng.normal(size=(4,8,6)),'y':np.array([[10,20],[30,40],[-10,20],[0,10]]),
                    'records':[{'split':'train'} for _ in range(4)]}

    def test_train_only_scaler_and_no_inplace_change(self):
        before=self.train['x'].copy();scaler=fit_scaler(self.train)
        transformed=transform_x(self.train['x'],scaler)
        np.testing.assert_array_equal(self.train['x'],before)
        np.testing.assert_allclose(transformed[:,:,:3].mean(axis=(0,1)),0,atol=1e-7)
        np.testing.assert_allclose(transformed[:,:,3:],before[:,:,3:],rtol=1e-6,atol=1e-7)
        validation=copy.deepcopy(self.train);validation['records'][0]['split']='validation'
        with self.assertRaises(ValueError):fit_scaler(validation)

    def test_fixed_scaler_applies_to_new_distribution(self):
        scaler=fit_scaler(self.train);snapshot=json.dumps(scaler,sort_keys=True)
        val=np.full((3,8,6),10000.)
        actual=transform_x(val,scaler)
        self.assertGreater(abs(actual[0,0,0]),100)
        self.assertEqual(json.dumps(scaler,sort_keys=True),snapshot)
        np.testing.assert_allclose(actual[0,0,:3],(val[0,0,:3]-scaler['x_mean'])/scaler['x_std'],rtol=1e-6)

    def test_zero_variance_is_finite(self):
        self.train['x'][:,:,:3]=0;self.train['y'][:]=0
        scaler=fit_scaler(self.train)
        self.assertTrue(np.isfinite(transform_x(self.train['x'],scaler)).all())
        self.assertEqual(scaler['y_scale_m'],1)

    def test_baselines_use_last_displacement(self):
        x=np.zeros((2,8,6));x[:,-2,:2]=[[-120,60],[0,-30]]
        baseline=baseline_displacements(x)
        np.testing.assert_array_equal(baseline['HoldPosition'],[[0,0],[0,0]])
        np.testing.assert_array_equal(baseline['ConstantVelocity'],[[120,-60],[0,30]])
        x[:,:-2,:2]=999
        np.testing.assert_array_equal(baseline_displacements(x)['ConstantVelocity'],baseline['ConstantVelocity'])

    def test_position_distance_and_inverse_units(self):
        anchor=np.array([[0.,0.],[0.,0.]])
        predicted=displacement_to_gps(np.array([[100.,0.],[0.,200.]]),anchor,[0.,0.])
        np.testing.assert_allclose(position_errors(predicted,anchor),[100.,200.],atol=1e-8)
        np.testing.assert_allclose(position_errors(predicted,predicted),0)
        self.assertAlmostEqual(error_metrics([3.,4.])['rmse_m'],np.sqrt(12.5))

    def test_bad_manifest_rejected_before_reading_windows(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);(root/'windows.json').write_text(json.dumps({'status':'FAILED'}))
            with self.assertRaises(ValueError):load_windows(root)


if __name__=='__main__':
    unittest.main(verbosity=2)
