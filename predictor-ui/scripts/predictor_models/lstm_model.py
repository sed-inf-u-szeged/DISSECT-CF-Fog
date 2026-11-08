import sys
from pathlib import Path

import numpy as np
import tensorflow as tf

from .predictor_model import PredictorModel

SCRIPTS_DIRECTORY = Path(__file__).resolve().parents[1]
if str(SCRIPTS_DIRECTORY) not in sys.path:
    sys.path.insert(0, str(SCRIPTS_DIRECTORY))

from model_registry import ModelRegistry


class LSTMModel(PredictorModel):

    def __init__(self, predictor_settings):
        super().__init__("LSTM", predictor_settings)
        self._future_model = None
        self._test_model = None
        self.create_models()

    def create_models(self):
        parameters = self._predictior_settings[
            "predictor"
        ]["hyperparameters"]

        future_path = str(
            parameters.get("future_model_location", "")
        ).strip()

        if not future_path or future_path == "<path_to_h5>":
            active_model = ModelRegistry().get_active("shared", "LSTM")
            if active_model is None:
                raise FileNotFoundError(
                    "No active LSTM model was found and "
                    "future_model_location is not configured"
                )
            future_path = active_model["artifact_path"]

        self._future_model = tf.keras.models.load_model(
            future_path, compile=False
        )

        test_path = str(
            parameters.get("test_model_location", "")
        ).strip()

        if test_path and test_path != "<path_to_h5>":
            self._test_model = tf.keras.models.load_model(
                test_path, compile=False
            )
        else:
            self._test_model = self._future_model

        self.get_input_size(False)
        self.get_input_size(True)

    def _get_model(self, is_test_data):
        model = self._test_model if is_test_data else self._future_model
        if model is None:
            raise ValueError("LSTM model has not been loaded")
        return model

    def get_input_size(self, is_test_data=False):
        model = self._get_model(is_test_data)
        shape = model.input_shape

        if (
            isinstance(shape, list)
            or len(shape) != 3
            or shape[1] is None
            or shape[2] != 1
        ):
            raise ValueError(
                "LSTM requires one input with shape "
                "(batch, fixed input length, 1)"
            )

        input_size = int(shape[1])
        if input_size < 1:
            raise ValueError("LSTM input length must be positive")
        return input_size

    def predict(
            self, feature_name, dataframe,
            prediction_length, is_test_data):
        model = self._get_model(is_test_data)
        input_size = self.get_input_size(is_test_data)

        values = np.asarray(
            dataframe["data"].values, dtype=np.float32
        )

        if len(values) < input_size:
            raise ValueError(
                f"LSTM requires {input_size} input values, "
                f"but only {len(values)} were provided"
            )

        values = values[-input_size:]
        if not np.all(np.isfinite(values)):
            raise ValueError("LSTM input contains non-finite values")

        result = np.asarray(
            model.predict(
                values.reshape(1, input_size, 1),
                verbose=0
            )
        ).reshape(-1)

        if len(result) != prediction_length:
            raise ValueError(
                f"LSTM output size is {len(result)}, "
                f"but prediction length is {prediction_length}"
            )
        if not np.all(np.isfinite(result)):
            raise ValueError("LSTM prediction contains non-finite values")

        return result.tolist()