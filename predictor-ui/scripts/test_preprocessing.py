import unittest

import numpy as np
import pandas as pd

from app_utils.preprocessor import Preprocessor
from predictor import Predictor
from predictor_models.lstm_trainer import make_data


class LastValueModel:
    """Use the last input value to test the pipeline without training."""

    def __init__(self, input_size):
        self.input_size = input_size

    def get_input_size(self, is_test_data=False):
        return self.input_size

    def predict(
            self, feature_name, dataframe,
            prediction_length, is_test_data):
        return [float(dataframe["data"].iloc[-1])] * prediction_length


def create_predictor(input_size, output_size, scale=True, smoothing=None):
    # Skip loading a real model; exercise the actual Predictor pipeline.
    predictor = Predictor.__new__(Predictor)
    predictor._prediction_settings = {
        "prediction": {
            "length": output_size,
            "testSize": output_size,
            "scale": scale,
            "smoothing": smoothing
        }
    }
    predictor._num_of_predictions = {}
    predictor._predictor_model = LastValueModel(input_size)
    return predictor


class PreprocessingTest(unittest.TestCase):

    def test_test_targets_do_not_change_test_prediction(self):
        predictor = create_predictor(
            192, 64,
            smoothing={"windowSize": 48, "polynomialDegree": 5}
        )
        inputs = np.linspace(10, 30, 192)
        first = predictor.compute({
            "name": "cpu",
            "values": np.concatenate([inputs, np.full(64, 40)])
        })
        second = predictor.compute({
            "name": "cpu",
            "values": np.concatenate([inputs, np.full(64, 10000)])
        })

        np.testing.assert_allclose(
            first["prediction_test"]["data"],
            second["prediction_test"]["data"]
        )
        np.testing.assert_allclose(
            first["test_data_beginning"]["data"],
            second["test_data_beginning"]["data"]
        )
        self.assertGreater(
            second["error_metrics"]["MAE"],
            first["error_metrics"]["MAE"]
        )

    def test_errors_use_raw_units(self):
        predictor = create_predictor(4, 2)
        result = predictor.compute({
            "name": "cpu",
            "values": [10, 20, 30, 40, 50, 60]
        })

        # Prediction: [40, 40], actual: [50, 60].
        np.testing.assert_allclose(
            result["prediction_test"]["data"], [40, 40]
        )
        self.assertAlmostEqual(result["error_metrics"]["MAE"], 15)
        self.assertAlmostEqual(result["error_metrics"]["MSE"], 250)
        self.assertAlmostEqual(
            result["error_metrics"]["RMSE"], np.sqrt(250)
        )

    def test_scale_false(self):
        predictor = create_predictor(4, 2, scale=False)
        result = predictor.compute({
            "name": "cpu",
            "values": [10, 20, 30, 40, 50, 60]
        })

        self.assertEqual(
            result["original_data"]["data"],
            [10, 20, 30, 40, 50, 60]
        )
        self.assertEqual(
            result["prediction_test"]["data"], [40, 40]
        )
        self.assertEqual(
            result["prediction_future"]["data"], [60, 60]
        )

    def test_training_and_prediction_preprocessing_match(self):
        smoothing = {"windowSize": 48, "polynomialDegree": 5}
        inputs = np.linspace(10, 30, 192)
        targets = np.linspace(40, 60, 64)
        values = np.concatenate([inputs, targets])

        x, y = make_data(
            [pd.DataFrame({"data": values})],
            input_shape=192,
            output_shape=64,
            smoothing=smoothing,
            scale=True,
            shuffle_datasets=False
        )

        predictor = create_predictor(
            192, 64, smoothing=smoothing
        )
        processed, scaler = predictor._prepare_input(
            inputs, is_test_data=True
        )

        self.assertEqual(x.shape, (1, 192, 1))
        self.assertEqual(y.shape, (1, 64))
        np.testing.assert_allclose(
            x[0, :, 0], processed["data"].to_numpy(),
            rtol=1e-6, atol=1e-6
        )
        np.testing.assert_allclose(
            Preprocessor.get_data_inverse(y[0], scaler),
            targets,
            rtol=1e-6, atol=1e-5
        )

        # Changing targets must not change the training input.
        changed = np.concatenate([inputs, targets + 10000])
        changed_x, _ = make_data(
            [pd.DataFrame({"data": changed})],
            input_shape=192,
            output_shape=64,
            smoothing=smoothing,
            scale=True,
            shuffle_datasets=False
        )
        np.testing.assert_allclose(x, changed_x)

    def test_future_window_and_timestamps(self):
        predictor = create_predictor(4, 2)
        result = predictor.compute({
            "name": "cpu",
            "values": [10, 20, 30, 40, 50, 60]
        })

        self.assertEqual(
            result["original_data"]["timestamp"], [0, 1, 2, 3, 4, 5]
        )
        self.assertEqual(
            result["preprocessed_data"]["timestamp"], [2, 3, 4, 5]
        )
        self.assertEqual(
            result["prediction_test"]["timestamp"], [4, 5]
        )
        self.assertEqual(
            result["prediction_future"]["timestamp"], [6, 7]
        )

        # Future scaling must use only the final four input values.
        processed, scaler = predictor._prepare_input(
            np.array([-10000, -5000, 30, 40, 50, 60]),
            is_test_data=False
        )
        np.testing.assert_allclose(
            processed["data"], [0, 1 / 3, 2 / 3, 1]
        )
        self.assertAlmostEqual(scaler.data_min_[0], 30)
        self.assertAlmostEqual(scaler.data_max_[0], 60)


if __name__ == "__main__":
    unittest.main()