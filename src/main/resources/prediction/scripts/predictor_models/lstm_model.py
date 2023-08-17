import numpy as np

from .predictor_model import PredictorModel
import tensorflow as tf


class LSTMModel(PredictorModel):
    def __init__(self, simulation_settings):
        super().__init__("LSTM", simulation_settings)
        self._future_model = None
        self._test_model = None
        self.create_models()

    def create_models(self):
        self._future_model = tf.keras.models.load_model(
            self._simulation_settings["predictor"]["hyperparameters"]["future_model_location"]
        )

        if "test_model_location" in self._simulation_settings["predictor"]["hyperparameters"]:
            test_model_location = self._simulation_settings["predictor"]["hyperparameters"]["test_model_location"]
            if test_model_location != "":
                self._test_model = tf.keras.models.load_model(test_model_location)

    def predict(self, feature_name, dataframe, prediction_length, is_test_data):
        if is_test_data:
            if self._test_model is not None:
                result = self._test_model.predict(np.array([dataframe["data"].values.tolist()]))
                return result.flatten().tolist()
            else:
                return [0 for i in range(prediction_length)]
        else:
            result = self._future_model.predict(np.array([dataframe["data"].values.tolist()]))
            return result.flatten().tolist()
