import sys
from pathlib import Path

import numpy as np

from .predictor_model import PredictorModel
import tensorflow as tf
from sklearn import preprocessing

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
        hyperparameters = self._predictior_settings["predictor"]["hyperparameters"]
        future_model_location = str(
            hyperparameters.get("future_model_location", "")
        ).strip()

        if future_model_location and future_model_location != "<path_to_h5>":
            self._future_model = tf.keras.models.load_model(
                future_model_location, compile=False
            )
        else:
            active_model = ModelRegistry().get_active("shared", "LSTM")

            if active_model is None:
                raise FileNotFoundError(
                    "No active LSTM model was found and "
                    "future_model_location is not configured"
                )

            self._future_model = tf.keras.models.load_model(
                active_model["artifact_path"], compile=False
            )

        test_model_location = str(
            hyperparameters.get("test_model_location", "")
        ).strip()

        if test_model_location and test_model_location != "<path_to_h5>":
            self._test_model = tf.keras.models.load_model(
                test_model_location, compile=False
            )
        else:
            self._test_model = self._future_model

    def predict(self, feature_name, dataframe, prediction_length, is_test_data):
        if is_test_data:
            if self._test_model is None:
                return [0 for i in range(prediction_length)]
            model = self._test_model
        else:
            model = self._future_model

        result = model.predict(
            self._prepare_input(model, dataframe),
            verbose=0
        )
        result = result.flatten().tolist()

        if len(result) != prediction_length:
            raise ValueError(
                f"LSTM output size is {len(result)}, "
                f"but prediction length is {prediction_length}"
            )

        return result

    @staticmethod
    def _prepare_input(model, dataframe):
        input_size = int(model.input_shape[1])
        values = np.asarray(
            dataframe["data"].values,
            dtype=np.float32
        )

        if len(values) < input_size:
            raise ValueError(
                f"LSTM requires {input_size} input values, "
                f"but only {len(values)} were provided"
            )

        return values[-input_size:].reshape(
            (1, input_size, 1)
        )
