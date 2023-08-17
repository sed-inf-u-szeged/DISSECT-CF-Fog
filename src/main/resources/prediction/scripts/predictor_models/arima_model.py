from .predictor_model import PredictorModel
from statsmodels.tsa.arima.model import ARIMA


class ArimaModel(PredictorModel):
    def __init__(self, simulation_settings):
        super().__init__("ARIMA", simulation_settings)

    def predict(self, feature_name, dataframe, prediction_length, is_test_data):
        model = ARIMA(
            dataframe["data"].values,
            order=(
                self._simulation_settings["predictor"]["hyperparameters"]["p_value"],
                self._simulation_settings["predictor"]["hyperparameters"]["d_value"],
                self._simulation_settings["predictor"]["hyperparameters"]["q_value"],
            )
        )
        fitted = model.fit()
        result = fitted.forecast(
            prediction_length
        )

        return result.tolist()
