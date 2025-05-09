from .predictor_model import PredictorModel
from statsmodels.tsa.holtwinters import ExponentialSmoothing


class HoltWintersModel(PredictorModel):
    def __init__(self, simulation_settings):
        super().__init__("HOLT_WINTERS", simulation_settings)

    def predict(self, feature_name, dataframe, prediction_length, is_test_data):
        dataframe, shift_value = PredictorModel.make_data_positive(dataframe)
        model = ExponentialSmoothing(  # Holt Winter's Exponential Smoothing
            endog=dataframe["data"].values,
            trend=self._simulation_settings["predictor"]["hyperparameters"]["trend"],
            seasonal=self._simulation_settings["predictor"]["hyperparameters"]["seasonal"],
            seasonal_periods=self._simulation_settings["predictor"]["hyperparameters"]["seasonal_periods"]
        ).fit(
            optimized=True,
            smoothing_level=self._simulation_settings["predictor"]["hyperparameters"]["alpha"],
            smoothing_trend=self._simulation_settings["predictor"]["hyperparameters"]["beta"],
            smoothing_seasonal=self._simulation_settings["predictor"]["hyperparameters"]["gamma"]
        )

        result = model.forecast(prediction_length)
        return PredictorModel.make_data_positive_inverse(result.tolist(), shift_value)
