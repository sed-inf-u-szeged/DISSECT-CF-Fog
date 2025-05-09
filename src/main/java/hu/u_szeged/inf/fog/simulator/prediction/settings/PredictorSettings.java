package hu.u_szeged.inf.fog.simulator.prediction.settings;

import hu.u_szeged.inf.fog.simulator.prediction.Utils;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONException;
import org.json.JSONObject;

public class PredictorSettings {

    private String predictor;
    private Map<String, Object> hyperparameters;
    private Map<String, Object> options;

    public enum PredictorEnum {

        ARIMA("ARIMA"),
        SVR("SVR"),
        RANDOM_FOREST("RANDOM_FOREST"),
        HOLT_WINTERS("HOLT_WINTERS"),
        LSTM("LSTM"),
        LINEAR_REGRESSION("LINEAR_REGRESSION"),
        ONLY_SIMULATION("ONLY_SIMULATION");

        public final String value;
        
        PredictorEnum(final String value) {
            this.value = value;
        }
    }

    public PredictorSettings(PredictorEnum predictor, Map<String, Object> hyperparameters, 
            Map<String, Object> options) {
        this.predictor = predictor.value;
        this.hyperparameters = hyperparameters;
        this.options = options;
    }

    public PredictorSettings(JSONObject jsonObject) throws JSONException {
        fromJsonObject(jsonObject);
    }

    private void fromJsonObject(JSONObject jsonObject) throws JSONException {
        this.predictor = jsonObject.getString("predictor");
        this.hyperparameters = jsonObjectToHashMap(jsonObject, "hyperparameters");
        this.options = jsonObjectToHashMap(jsonObject, "options");
    }

    public HashMap<String, Object> jsonObjectToHashMap(JSONObject jsonObject, String jsonKey) throws JSONException {
        HashMap<String, Object> result = new HashMap<>();
        List<String> optionKeys = Utils.getJsonObjectKeys(jsonObject.getJSONObject(jsonKey));
        for (String key : optionKeys) {
            try {
                double value = jsonObject.getJSONObject(jsonKey).getDouble(key);
                result.put(key, value);
                continue;
            } catch (Exception e) {
                // Not Double
            }

            try {
                int value = jsonObject.getJSONObject(jsonKey).getInt(key);
                result.put(key, value);
                continue;
            } catch (Exception e) {
                // Not Integer
            }

            try {
                String value = jsonObject.getJSONObject(jsonKey).getString(key);
                result.put(key, value);
            } catch (Exception e) {
                // Error
            }
        }
        return result;
    }

    public String getPredictor() {
        return predictor;
    }

    public void setPredictor(String predictor) {
        this.predictor = predictor;
    }

    public Map<String, Object> getHyperparameters() {
        return hyperparameters;
    }

    public void setHyperparameters(Map<String, Object> hyperparameters) {
        this.hyperparameters = hyperparameters;
    }

    public Map<String, Object> getOptions() {
        return options;
    }

    public void setOptions(Map<String, Object> options) {
        this.options = options;
    }

    public JSONObject toJson() throws JSONException {
        return new JSONObject()
                .put("predictor", predictor)
                .put("hyperparameters", hyperparameters == null ? new JSONObject() : new JSONObject(hyperparameters))
                .put("options", options == null ? new JSONObject() : new JSONObject(options));
    }

    public static PredictorSettings getPredictorSettings(PredictorEnum predictorEnum, String... params) 
            throws Exception {
        switch (predictorEnum) {
          case ARIMA:
              return new PredictorSettings(
                        PredictorSettings.PredictorEnum.ARIMA,
                        Map.of(
                                "p_value", 3,
                                "d_value", 0,
                                "q_value", 0
                        ),
                        null
                );
          case RANDOM_FOREST:
              return new PredictorSettings(
                        PredictorEnum.RANDOM_FOREST,
                        Map.of(
                                "number_of_trees", 10,
                                "max_depth", 50,
                                "lags", 100
                        ),
                        null
                );
          case HOLT_WINTERS:
              return new PredictorSettings(
                        PredictorEnum.HOLT_WINTERS,
                        Map.of(
                                "trend", "add",
                                "seasonal", "add",
                                "alpha", 0.1,
                                "beta", 0.1,
                                "gamma", 0.1,
                                "seasonal_periods", 60
                        ),
                        null
                );
          case LINEAR_REGRESSION:
              return new PredictorSettings(
                        PredictorEnum.LINEAR_REGRESSION,
                        null,
                        null
                );
          case SVR:
              return new PredictorSettings(
                        PredictorEnum.SVR,
                        Map.of(
                                "kernel", "rbf"
                        ),
                        null
                );
          case LSTM:
              return new PredictorSettings(
                        PredictorEnum.LSTM,
                        Map.of(
                                "future_model_location", "<path_to_h5>",
                                "test_model_location", "<path_to_h5>"
                        ),
                        null
                );
          case ONLY_SIMULATION:
              return new PredictorSettings(
                        PredictorEnum.ONLY_SIMULATION,
                        null,
                        null
                );
          default:
        }
        throw new Exception("No predictor found!");
    }

}
