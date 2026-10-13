from skforecast.recursive import ForecasterRecursive
from sklearn.ensemble import RandomForestRegressor

from .predictor_model import PredictorModel


class RandomForestModel(PredictorModel):
    def __init__(self, predictor_settings):
        super().__init__("RANDOM_FOREST", predictor_settings)

    def predict(self, feature_name, dataframe, prediction_length, is_test_data):
        parameters = self._predictior_settings["predictor"]["hyperparameters"]
        lags = parameters["lags"]

        if isinstance(lags, bool) or not isinstance(lags, int) or lags < 1:
            raise ValueError("Random Forest lags must be a positive integer")

        if len(dataframe) <= lags:
            raise ValueError(
                f"Random Forest requires more than {lags} input values, "
                f"but only {len(dataframe)} were provided "
                f"for feature '{feature_name}'"
            )

        model = ForecasterRecursive(
            estimator=RandomForestRegressor(
                n_estimators=parameters["number_of_trees"],
                max_depth=parameters["max_depth"],
                random_state=42
            ),
            lags=lags
        )

        model.fit(y=dataframe["data"])
        result = model.predict(steps=prediction_length)

        return result.tolist()