import time

import numpy as np
import pandas as pd

from app_utils.preprocessor import Preprocessor
from error_metrics import ErrorMetrics
from predictor_models.arima_model import ArimaModel
from predictor_models.holt_winters_model import HoltWintersModel
from predictor_models.linear_regression_model import LinearRegressionModel
from predictor_models.lstm_model import LSTMModel
from predictor_models.random_forest_model import RandomForestModel
from predictor_models.svr_model import SVRModel


class Predictor:

    def __init__(self, prediction_settings):
        self._prediction_settings = prediction_settings
        self._num_of_predictions = {}
        self._predictor_model = Predictor.get_predictor_model(
            name=prediction_settings["predictor"]["predictor"],
            prediction_settings=prediction_settings
        )

    def _prepare_input(self, values, is_test_data):
        prediction = self._prediction_settings["prediction"]
        input_values = values
        offset = 0

        get_input_size = getattr(
            self._predictor_model, "get_input_size", None
        )
        if callable(get_input_size):
            input_size = get_input_size(is_test_data)
            if len(values) < input_size:
                raise ValueError(
                    f"Model requires {input_size} input values, "
                    f"but only {len(values)} were provided"
                )
            offset = len(values) - input_size
            input_values = values[offset:]

        dataframe, scaler = Preprocessor.process(
            data=input_values,
            smoothing=prediction.get("smoothing"),
            scale=prediction["scale"]
        )

        dataframe["timestamp"] = dataframe["timestamp"] + offset
        return dataframe, scaler

    @staticmethod
    def _serialize(dataframe, scaler=None):
        return {
            "timestamp": dataframe["timestamp"].tolist(),
            "data": Preprocessor.get_data_inverse(
                dataframe["data"], scaler
            )
        }

    def _get_prediction_status(self, is_test_data):
        getter = getattr(
            self._predictor_model, "get_prediction_status", None
        )
        if not callable(getter):
            return None
        status = getter(is_test_data)
        return dict(status) if status is not None else None

    def _predict(
            self, feature_name, dataframe, scaler,
            prediction_length, is_test_data):
        values = self._predictor_model.predict(
            feature_name=feature_name,
            dataframe=dataframe.copy(),
            prediction_length=prediction_length,
            is_test_data=is_test_data
        )
        values = np.asarray(values, dtype=float)

        if values.ndim != 1 or len(values) != prediction_length:
            raise ValueError(
                "Prediction must be a one-dimensional sequence "
                f"of length {prediction_length}"
            )

        raw_values = Preprocessor.get_data_inverse(values, scaler)
        if not np.all(np.isfinite(raw_values)):
            raise ValueError("Prediction contains non-finite values")

        start = int(dataframe["timestamp"].iloc[-1]) + 1
        return pd.DataFrame({
            "timestamp": np.arange(start, start + prediction_length),
            "data": raw_values
        })

    def compute(self, feature):
        if self._predictor_model is None:
            raise ValueError("No predictor model was found")

        start_time = time.time()
        feature_name = feature["name"]
        prediction = self._prediction_settings["prediction"]

        original_data, _ = Preprocessor.process(
            data=feature["values"],
            smoothing=None,
            scale=False
        )

        test_size = prediction["testSize"]
        prediction_length = prediction["length"]
        if (
            isinstance(prediction_length, bool)
            or not isinstance(prediction_length, (int, np.integer))
            or prediction_length < 1
        ):
            raise ValueError("Prediction length must be a positive integer")

        raw_test_input, raw_test_target = Preprocessor.create_test_data(
            original_data, test_size
        )

        future_input, future_scaler = self._prepare_input(
            original_data["data"].to_numpy(),
            is_test_data=False
        )
        test_input, test_scaler = self._prepare_input(
            raw_test_input["data"].to_numpy(),
            is_test_data=True
        )

        future_prediction = self._predict(
            feature_name,
            future_input,
            future_scaler,
            prediction_length,
            is_test_data=False
        )
        future_status = self._get_prediction_status(False)

        test_prediction = self._predict(
            feature_name,
            test_input,
            test_scaler,
            test_size,
            is_test_data=True
        )
        test_status = self._get_prediction_status(True)

        error_metrics = {
            "RMSE": ErrorMetrics.RMSE(
                raw_test_target, test_prediction
            ),
            "MSE": ErrorMetrics.MSE(
                raw_test_target, test_prediction
            ),
            "MAE": ErrorMetrics.MAE(
                raw_test_target, test_prediction
            )
        }

        prediction_number = (
            self._num_of_predictions.get(feature_name, -1) + 1
        )
        self._num_of_predictions[feature_name] = prediction_number

        return {
            "feature_name": feature_name,
            "prediction_number": prediction_number,
            "prediction_settings": self._prediction_settings,
            "original_data": self._serialize(original_data),
            "preprocessed_data": self._serialize(
                future_input, future_scaler
            ),
            "test_data_beginning": self._serialize(
                test_input, test_scaler
            ),
            "test_data_end": self._serialize(raw_test_target),
            "prediction_future": self._serialize(future_prediction),
            "prediction_test": self._serialize(test_prediction),
            "prediction_status": {
                "future": future_status,
                "test": test_status
            },
            "error_metrics": error_metrics,
            "prediction_time": (time.time() - start_time) * 1000
        }

    @staticmethod
    def get_predictor_model(name, prediction_settings):
        if name == "ARIMA":
            return ArimaModel(predictor_settings=prediction_settings)
        if name == "SVR":
            return SVRModel(predictor_settings=prediction_settings)
        if name == "RANDOM_FOREST":
            return RandomForestModel(
                predictor_settings=prediction_settings
            )
        if name == "HOLT_WINTERS":
            return HoltWintersModel(
                predictionSettings=prediction_settings
            )
        if name == "LSTM":
            return LSTMModel(predictor_settings=prediction_settings)
        if name == "LINEAR_REGRESSION":
            return LinearRegressionModel(
                predictor_settings=prediction_settings
            )
        return None