import numpy as np
from sklearn.linear_model import LinearRegression

from .predictor_model import PredictorModel


class LinearRegressionModel(PredictorModel):
    def __init__(self, predictor_settings):
        super().__init__("LINEAR_REGRESSION", predictor_settings)

    def predict(self, feature_name, dataframe, prediction_length, is_test_data):
        model = LinearRegression()
        model.fit(np.array(dataframe["timestamp"].values).reshape(-1, 1), dataframe["data"].values)

        future_timestamps = PredictorModel.create_future_timestamp(dataframe=dataframe, prediction_length=prediction_length)
        result = model.predict(np.array(future_timestamps).reshape(-1, 1))

        return result.tolist()
