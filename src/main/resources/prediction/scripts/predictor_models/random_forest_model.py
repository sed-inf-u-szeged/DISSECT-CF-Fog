from skforecast.ForecasterAutoreg import ForecasterAutoreg
from sklearn.ensemble import RandomForestRegressor

from .predictor_model import PredictorModel


class RandomForestModel(PredictorModel):
    def __init__(self, simulation_settings):
        super().__init__("RANDOM_FOREST", simulation_settings)

    def predict(self, feature_name, dataframe, prediction_length, is_test_data):
        model = ForecasterAutoreg(
            regressor=RandomForestRegressor(
                n_estimators=self._simulation_settings["predictor"]["hyperparameters"]["number_of_trees"],
                max_depth=self._simulation_settings["predictor"]["hyperparameters"]["max_depth"]
            ),
            lags=self._simulation_settings["predictor"]["hyperparameters"]["lags"]
        )
        model.fit(y=dataframe["data"])
        result = model.predict(steps=prediction_length)

        return result.tolist()
