"""Migration tests exercise original implementations, not renamed Persistence."""
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import numpy as np
import pandas as pd
from node_load_worker import Worker, checked_request, validate_options
from node_load_baselines import predict_baseline
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from predictor_models.arima_model import ArimaModel
from predictor_models.linear_regression_model import LinearRegressionModel


class MigrationTest(unittest.TestCase):
    def test_models_are_explicit_and_not_position_models(self):
        self.assertEqual(validate_options(['LSTM','TCN','GRU','PatchTST'],'candidate',40),['LSTM','TCN','GRU','PatchTST'])
        for method in ['Persistence','SVR','RF','HW','HoldPosition']:
            with self.assertRaises(ValueError):validate_options([method],'candidate',40)

    def test_baselines_use_original_algorithms_at_identical_targets(self):
        values=(np.arange(192)%32*40+np.arange(192)*2).tolist(); origin=311
        frame=pd.DataFrame({'timestamp':np.arange(origin-191,origin+1),'data':values})
        settings={'predictor':{'hyperparameters':{'p_value':3,'d_value':0,'q_value':0}}}
        for name,original in [('ARIMA',ArimaModel(settings)),('LR',LinearRegressionModel(settings))]:
            expected=original.predict('queued_bytes',frame,64,False)
            result=predict_baseline(name,values,origin)
            self.assertTrue(result['usable'])
            np.testing.assert_allclose(result['raw_values'],expected)
            np.testing.assert_allclose(result['values'],np.maximum(0,expected))
            self.assertFalse(np.allclose(result['values'],values[-1]))

    def test_nonconvergence_is_not_published_or_relabelled(self):
        with patch.object(ArimaModel,'get_prediction_status',return_value={'status':'not_converged','converged':False}):
            result=predict_baseline('ARIMA',list(range(192)),191)
        self.assertFalse(result['usable']);self.assertEqual(result['values'],[])
        self.assertEqual(result['status']['status'],'not_converged')

    def test_clipping_is_explicit_and_raw_predictions_remain_visible(self):
        result=predict_baseline('LR',list(range(192,0,-1)),191)
        self.assertTrue(result['usable']);self.assertGreater(result['clipped_count'],0)
        self.assertLess(min(result['raw_values']),0);self.assertEqual(min(result['values']),0)

    def test_forecast_input_cannot_include_future_samples(self):
        request=dict(model='GRU',node='Gyorg_fog1',version='v0001',origin_index=190,values=[100]*192)
        with self.assertRaises(ValueError):checked_request(request,192)
        with self.assertRaises(ValueError):predict_baseline('ARIMA',[100]*192,190)

    def test_baseline_does_not_start_tensorflow_or_train_neural_model(self):
        with tempfile.TemporaryDirectory() as tmp:
            worker=Worker(Path(tmp),1)
            with patch.object(worker,'runtime',side_effect=AssertionError('baseline invoked TensorFlow')):
                result=worker.handle(dict(op='BASELINE',model='LR',node='west',version='online-v1',origin_index=191,values=list(range(192))))
            self.assertTrue(result['usable']);self.assertFalse(worker.models)


if __name__=='__main__':unittest.main()
