import numpy as np

from .predictor_model import PredictorModel
import tensorflow as tf
from sklearn import preprocessing


class LSTMModel(PredictorModel):
    def __init__(self, predictor_settings):
        super().__init__("LSTM", predictor_settings)
        self._future_model = None
        self._test_model = None
        self.create_models()

    def create_models(self):
        self._future_model = tf.keras.models.load_model(
            self._predictior_settings["predictor"]["hyperparameters"]["future_model_location"]
        )

        if "test_model_location" in self._predictior_settings["predictor"]["hyperparameters"]:
            test_model_location = self._predictior_settings["predictor"]["hyperparameters"]["test_model_location"]
            if test_model_location != "":
                self._test_model = tf.keras.models.load_model(test_model_location)

    def predict(self, feature_name, dataframe, prediction_length, is_test_data):
        if is_test_data:
            if self._test_model is not None:
                result = self._test_model.predict(np.array([dataframe["data"].values.tolist()]), verbose=0)
                return result.flatten().tolist()
            else:
                return [0 for i in range(prediction_length)]
        else:
            result = self._future_model.predict(np.array([dataframe["data"].values.tolist()]), verbose=0)
            return result.flatten().tolist()
