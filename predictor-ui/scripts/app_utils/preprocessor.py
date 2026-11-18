import numpy as np
import pandas as pd
from scipy.signal import savgol_filter
from sklearn.preprocessing import MinMaxScaler


class Preprocessor:

    @staticmethod
    def _values(data):
        values = np.asarray(data, dtype=float)

        if values.ndim != 1:
            raise ValueError("Data must be a one-dimensional sequence")
        if not np.all(np.isfinite(values)):
            raise ValueError("Data must contain only finite values")

        return values

    @staticmethod
    def process(data, smoothing, scale):
        values = Preprocessor._values(data)

        if len(values) == 0:
            raise ValueError("Input data must not be empty")

        if smoothing is not None:
            window = int(smoothing["windowSize"])
            degree = int(smoothing["polynomialDegree"])

            if window < 1 or window > len(values):
                raise ValueError(
                    "Smoothing window must be between 1 and input length"
                )
            if degree < 0 or degree >= window:
                raise ValueError(
                    "Polynomial degree must be smaller than smoothing window"
                )

            values = savgol_filter(values, window, degree)

        scaler = None
        if scale:
            scaler = MinMaxScaler(feature_range=(0, 1))
            values = scaler.fit_transform(
                values.reshape(-1, 1)
            ).ravel()

        dataframe = pd.DataFrame({
            "data": values,
            "timestamp": np.arange(len(values))
        })
        return dataframe, scaler

    @staticmethod
    def transform_targets(data, scaler):
        values = Preprocessor._values(data)

        if scaler is None or len(values) == 0:
            return values.tolist()

        return scaler.transform(
            values.reshape(-1, 1)
        ).ravel().tolist()

    @staticmethod
    def create_test_data(data, test_size):
        if (
            isinstance(test_size, bool)
            or not isinstance(test_size, (int, np.integer))
            or not 0 < test_size < len(data)
        ):
            raise ValueError(
                "Test size must be a positive integer smaller than data length"
            )

        split = len(data) - test_size
        return data.iloc[:split].copy(), data.iloc[split:].copy()

    @staticmethod
    def get_data_inverse(data, scaler):
        values = Preprocessor._values(data)

        if scaler is None or len(values) == 0:
            return values.tolist()

        return scaler.inverse_transform(
            values.reshape(-1, 1)
        ).ravel().tolist()