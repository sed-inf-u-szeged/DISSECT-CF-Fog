import numpy as np
from statsmodels.tsa.arima.model import ARIMA

from .predictor_model import PredictorModel


class ArimaModel(PredictorModel):
    def __init__(self, predictor_settings):
        super().__init__("ARIMA", predictor_settings)
        self._prediction_status = {}

    def get_prediction_status(self, is_test_data):
        phase = "test" if is_test_data else "future"
        status = self._prediction_status.get(phase)
        return dict(status) if status is not None else None

    def predict(self, feature_name, dataframe, prediction_length, is_test_data):
        phase = "test" if is_test_data else "future"
        parameters = self._predictior_settings["predictor"]["hyperparameters"]
        order = (
            parameters["p_value"],
            parameters["d_value"],
            parameters["q_value"],
        )

        status = {
            "model": "ARIMA",
            "feature": feature_name,
            "phase": phase,
            "status": "pending",
            "converged": None,
            "finite": False,
            "order": list(order),
        }
        self._prediction_status[phase] = status

        try:
            model = ARIMA(dataframe["data"].values, order=order)
            fitted = model.fit()

            fit_details = getattr(fitted, "mle_retvals", None)
            converged = (
                fit_details.get("converged")
                if isinstance(fit_details, dict)
                else None
            )
            status["converged"] = (
                bool(converged) if converged is not None else None
            )

            result = np.asarray(
                fitted.forecast(prediction_length), dtype=float
            )
            valid = (
                result.shape == (prediction_length,)
                and np.all(np.isfinite(result))
            )
            status["finite"] = bool(np.all(np.isfinite(result)))

            if not valid:
                raise ValueError(
                    "ARIMA returned invalid prediction length or values"
                )

            if status["converged"] is True:
                status["status"] = "converged"
            elif status["converged"] is False:
                status["status"] = "not_converged"
            else:
                status["status"] = "unknown"

            return result.tolist()

        except Exception as error:
            status["status"] = "failed"
            status["error_type"] = type(error).__name__
            status["error"] = str(error)
            raise