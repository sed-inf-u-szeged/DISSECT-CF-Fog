package hu.u_szeged.inf.fog.simulator.prediction.settings;

import hu.u_szeged.inf.fog.simulator.prediction.Utils;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.*;
import org.json.JSONException;
import org.json.JSONObject;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode
public class PredictorSettings {

    private String predictor;
    private Map<String, Object> hyperparameters;
    private Map<String, Object> options;

    @AllArgsConstructor
    public enum PredictorEnum {

        ARIMA("ARIMA"),
        SVR("SVR"),
        RANDOM_FOREST("RANDOM_FOREST"),
        HOLT_WINTERS("HOLT_WINTERS"),
        LSTM("LSTM"),
        LINEAR_REGRESSION("LINEAR_REGRESSION"),
        ONLY_SIMULATION("ONLY_SIMULATION");

        public final String value;
    }

    public static PredictorSettings getPredictorSettings(PredictorEnum predictorEnum, String... params) 
            throws Exception {
        switch (predictorEnum) {
          case ARIMA:
              return new PredictorSettings(
                        PredictorEnum.ARIMA.value,
                        Map.of(
                                "p_value", 3,
                                "d_value", 0,
                                "q_value", 0
                        ),
                        null
                );
          case RANDOM_FOREST:
              return new PredictorSettings(
                        PredictorEnum.RANDOM_FOREST.value,
                        Map.of(
                                "number_of_trees", 10,
                                "max_depth", 50,
                                "lags", 100
                        ),
                        null
                );
          case HOLT_WINTERS:
              return new PredictorSettings(
                        PredictorEnum.HOLT_WINTERS.value,
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
                        PredictorEnum.LINEAR_REGRESSION.value,
                        null,
                        null
                );
          case SVR:
              return new PredictorSettings(
                        PredictorEnum.SVR.value,
                        Map.of(
                                "kernel", "rbf"
                        ),
                        null
                );
          case LSTM:
              return new PredictorSettings(
                        PredictorEnum.LSTM.value,
                        Map.of(
                                "future_model_location", "<path_to_h5>",
                                "test_model_location", "<path_to_h5>"
                        ),
                        null
                );
          case ONLY_SIMULATION:
              return new PredictorSettings(
                        PredictorEnum.ONLY_SIMULATION.value,
                        null,
                        null
                );
          default:
        }
        throw new Exception("No predictor found!");
    }

}
