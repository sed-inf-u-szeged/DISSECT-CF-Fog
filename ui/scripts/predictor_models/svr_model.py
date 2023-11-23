import numpy as np
from sklearn import svm

from .predictor_model import PredictorModel


class SVRModel(PredictorModel):
    def __init__(self, simulation_settings):
        super().__init__("SVR", simulation_settings)

    def predict(self, feature_name, dataframe, prediction_length, is_test_data):
        future_timestamps = PredictorModel.create_future_timestamp(dataframe=dataframe, prediction_length=prediction_length)

        model = svm.SVR(kernel=self._simulation_settings["predictor"]["hyperparameters"]["kernel"])
        model.fit(np.array(dataframe["timestamp"].values).reshape(-1, 1), dataframe["data"].values)
        result = model.predict(np.array(future_timestamps).reshape(-1, 1))

        return result.tolist()
