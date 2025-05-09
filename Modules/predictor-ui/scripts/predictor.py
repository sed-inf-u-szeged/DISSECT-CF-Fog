import numpy as np

from predictor_models.arima_model import ArimaModel
from predictor_models.holt_winters_model import HoltWintersModel
from predictor_models.linear_regression_model import LinearRegressionModel
from predictor_models.lstm_model import LSTMModel
from predictor_models.random_forest_model import RandomForestModel
from predictor_models.svr_model import SVRModel
from app_utils.log import Log
from app_utils.preprocessor import Preprocessor

import time

class Predictor:

    def __init__(self, simulation_settings):
        self._simulation_settings = simulation_settings
        self._num_of_predictions = {}
        self._predictor_model = Predictor.get_predictor_model(
            name=self._simulation_settings["predictor"]["predictor"],
            simulation_settings=self._simulation_settings
        )

    def compute(self, feature):  # TODO scale_inverse if it was scaled
        if self._predictor_model is None:
            Log.error("No predictor model was found!")

        feature_name, values = feature["name"], feature["values"]

        if feature_name not in self._num_of_predictions:
            self._num_of_predictions[feature_name] = -1
        self._num_of_predictions[feature_name] += 1

        original_data, scaler_original = Preprocessor.process(
            data=values,
            smoothing=None,
            scale=self._simulation_settings["prediction"]["scale"],
        )

        preprocessed_data, scaler_preprocessed = Preprocessor.process(
            data=values,
            smoothing=self._simulation_settings["prediction"]["smoothing"],
            scale=self._simulation_settings["prediction"]["scale"],
        )

        test_data_beginning, test_data_end = Preprocessor.create_test_data(
            data=preprocessed_data,
            test_size=self._simulation_settings["prediction"]["testSize"]
        )

        result = {
            "feature_name": feature_name,
            "prediction_number": self._num_of_predictions[feature_name],
            "simulation_settings": self._simulation_settings,
            "original_data": {
                "timestamp": original_data["timestamp"].values.tolist(),
                "data": Preprocessor.get_data_inverse(original_data["data"], scaler_original)
            },
            "preprocessed_data": {
                "timestamp": preprocessed_data["timestamp"].values.tolist(),
                "data": Preprocessor.get_data_inverse(preprocessed_data["data"], scaler_preprocessed)
            },
            "test_data_beginning": {
                "timestamp": test_data_beginning["timestamp"].values.tolist(),
                "data": Preprocessor.get_data_inverse(test_data_beginning["data"], scaler_preprocessed)
            },
            "test_data_end": {
                "timestamp": test_data_end["timestamp"].values.tolist(),
                "data": Preprocessor.get_data_inverse(test_data_end["data"], scaler_preprocessed)
            }
        }

        start_time = None
        if self._predictor_model is not None:
            start_time = time.time()

            prediction_future, prediction_test, error_metrics = self._predictor_model.make_prediction(
                feature_name=feature_name,
                original_data=original_data,
                preprocessed_data=preprocessed_data,
                test_data_beginning=test_data_beginning,
                test_data_end=test_data_end
            )

            result["prediction_future"] = {
                "timestamp": prediction_future["timestamp"].values.tolist(),
                "data": Preprocessor.get_data_inverse(prediction_future["data"], scaler_preprocessed)
            }
            result["prediction_test"] = {
                "timestamp": prediction_test["timestamp"].values.tolist(),
                "data": Preprocessor.get_data_inverse(prediction_test["data"], scaler_preprocessed)
            }
            result["error_metrics"] = error_metrics
            result["prediction_time"] = ((time.time() - start_time) * 1000) if start_time is not None else -1

        return result

    @staticmethod
    def get_predictor_model(name, simulation_settings):
        if name == "ARIMA":
            return ArimaModel(simulation_settings=simulation_settings)

        if name == "SVR":
            return SVRModel(simulation_settings=simulation_settings)

        if name == "RANDOM_FOREST":
            return RandomForestModel(simulation_settings=simulation_settings)

        if name == "HOLT_WINTERS":
            return HoltWintersModel(simulation_settings=simulation_settings)

        if name == "LSTM":
            return LSTMModel(simulation_settings=simulation_settings)

        if name == "LINEAR_REGRESSION":
            return LinearRegressionModel(simulation_settings=simulation_settings)

        return None
