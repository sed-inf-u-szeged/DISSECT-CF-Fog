from statsmodels.tsa.holtwinters import ExponentialSmoothing

from .predictor_model import PredictorModel


class HoltWintersModel(PredictorModel):
    def __init__(self, predictionSettings):
        super().__init__("HOLT_WINTERS", predictionSettings)

    def predict(self, feature_name, dataframe, prediction_length, is_test_data):
        parameters = self._predictior_settings["predictor"]["hyperparameters"]
        seasonal = parameters["seasonal"]
        seasonal_periods = parameters["seasonal_periods"]

        if len(dataframe) < 2:
            raise ValueError(
                "Holt-Winters requires at least 2 input values "
                f"for feature '{feature_name}'"
            )

        if seasonal is not None:
            if (
                isinstance(seasonal_periods, bool)
                or not isinstance(seasonal_periods, int)
                or seasonal_periods < 2
            ):
                raise ValueError(
                    "Holt-Winters seasonal_periods must be "
                    "an integer greater than 1"
                )

            required_values = 2 * seasonal_periods
            if len(dataframe) < required_values:
                raise ValueError(
                    f"Holt-Winters requires at least {required_values} "
                    f"input values for seasonal_periods={seasonal_periods}, "
                    f"but only {len(dataframe)} were provided "
                    f"for feature '{feature_name}'"
                )

        dataframe, shift_value = PredictorModel.make_data_positive(dataframe)

        model = ExponentialSmoothing(
            endog=dataframe["data"].values,
            trend=parameters["trend"],
            seasonal=seasonal,
            seasonal_periods=seasonal_periods
        ).fit(
            optimized=True,
            smoothing_level=parameters["alpha"],
            smoothing_trend=parameters["beta"],
            smoothing_seasonal=parameters["gamma"]
        )

        result = model.forecast(prediction_length)

        return PredictorModel.make_data_positive_inverse(
            result.tolist(), shift_value
        )