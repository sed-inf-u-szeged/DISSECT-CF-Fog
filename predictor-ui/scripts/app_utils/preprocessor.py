import pandas as pd
import numpy as np
from sklearn import preprocessing
from scipy.signal import savgol_filter


class Preprocessor:

    @staticmethod
    def process(data, smoothing, scale):
        timestamp = [i for i in range(0, len(data))]
        dataframe = pd.DataFrame({"data": data, "timestamp": timestamp})

        if smoothing is not None:
            dataframe["data"] = savgol_filter(
                dataframe["data"].values,
                smoothing["windowSize"],
                smoothing["polynomialDegree"]
            )

        scaler = None
        if scale:
            scaler = preprocessing.MinMaxScaler(feature_range=(0, 1))
            normed = scaler.fit_transform(np.array(dataframe["data"]).reshape(-1, 1))
            dataframe["data"] = normed.flatten().tolist()

        dataframe.reset_index(drop=True, inplace=True)
        return dataframe, scaler

    @staticmethod
    def create_test_data(data, test_size):
        return data.iloc[:len(data) - test_size], data.iloc[-test_size:]

    @staticmethod
    def get_data_inverse(data, scaler):  # TODO 50 - 49.99999999999999, 3147 - 3147.0000000000005
        return scaler.inverse_transform(np.array(data).reshape(-1, 1)).flatten().tolist()
