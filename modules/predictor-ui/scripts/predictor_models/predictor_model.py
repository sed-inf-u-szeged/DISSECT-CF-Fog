from abc import abstractmethod, ABC

import pandas as pd

from error_metrics import ErrorMetrics
from app_utils.log import Log


class PredictorModel(ABC):
    def __init__(self, name, predictor_settings):
        self._name = name
        self._predictior_settings = predictor_settings

    def make_prediction(self, feature_name, original_data, preprocessed_data, test_data_beginning, test_data_end):
        Log.info(f"Model '{self._name}' is predicting feature '{feature_name}'...")

        prediction_future = self.predict(
            feature_name=feature_name,
            dataframe=preprocessed_data.copy(),
            prediction_length=self._predictior_settings["prediction"]["length"],
            is_test_data=False
        )

        last_future_timestamp = preprocessed_data["timestamp"].values.tolist()[-1]
        prediction_future_timestamp = [(last_future_timestamp + 1) + i for i in range(0, self._predictior_settings["prediction"]["length"])]
        prediction_future_dataframe = pd.DataFrame({"data": prediction_future, "timestamp": prediction_future_timestamp})

        prediction_test = self.predict(
            feature_name=feature_name,
            dataframe=test_data_beginning.copy(),
            prediction_length=(len(preprocessed_data) - len(test_data_beginning)),
            is_test_data=True
        )

        last_test_timestamp = test_data_beginning["timestamp"].values.tolist()[-1]
        prediction_test_timestamp = [(last_test_timestamp + 1) + i for i in range(0, (len(preprocessed_data) - len(test_data_beginning)))]
        prediction_test_dataframe = pd.DataFrame({"data": prediction_test, "timestamp": prediction_test_timestamp})

        return prediction_future_dataframe, prediction_test_dataframe, {
            "RMSE": ErrorMetrics.RMSE(test_data_end, prediction_test_dataframe),
            "MSE": ErrorMetrics.MSE(test_data_end, prediction_test_dataframe),
            "MAE": ErrorMetrics.MAE(test_data_end, prediction_test_dataframe),
        }

    @abstractmethod
    def predict(self, feature_name, dataframe, prediction_length, is_test_data):
        pass

    @staticmethod
    def create_future_timestamp(dataframe, prediction_length):
        future_timestamps = []
        last_timestamp = dataframe["timestamp"].values.tolist()[-1]

        for t in range(0, prediction_length):
            future_timestamps.append(last_timestamp + t + 1)

        return future_timestamps

    @staticmethod
    def get_min_value(data):
        min_value = float("inf")
        for d in data:
            if d < min_value:
                min_value = d
        return min_value

    @staticmethod
    def make_data_positive(dataframe):
        data = dataframe["data"].values.tolist()
        min_value = PredictorModel.get_min_value(data)
        if min_value <= 0:
            shift_value = 1 - min_value
            new_data = []
            for d in data:
                new_data.append(d + shift_value)
            df = dataframe.copy()
            df["data"] = new_data
            return df, shift_value

        return dataframe, 0

    @staticmethod
    def make_data_positive_inverse(data, shift_value):
        new_data = []
        for d in data:
            new_data.append(d - shift_value)
        return new_data
