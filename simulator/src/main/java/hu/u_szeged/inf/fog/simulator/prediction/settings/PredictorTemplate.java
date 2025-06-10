package hu.u_szeged.inf.fog.simulator.prediction.settings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class PredictorTemplate {
    private String id;
    private String label;
    private List<Parameter> hyperparameters;
    private List<Parameter> options;

    public PredictorTemplate(String id, String label, List<Parameter> hyperparameters) {
        this.id = id;
        this.label = label;
        this.hyperparameters = hyperparameters;
    }

    public PredictorTemplate(String label, List<Parameter> hyperparameters, List<Parameter> options) {
        this.id = label.toLowerCase().replaceAll(" ", "_");
        this.label = label;
        this.hyperparameters = hyperparameters;
        this.options = options;
    }

    public PredictorTemplate(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public static List<PredictorTemplate> getAll() { // TODO replace json to classes
        List<PredictorTemplate> predictorTemplates = new ArrayList<>();
        predictorTemplates.add(
                new PredictorTemplate("ARIMA", "ARIMA",
                        Arrays.asList(
                                new Parameter("P value").required()
                                .setType(Parameter.ParameterType.TEXT).setDefaultValue("3"),
                                new Parameter("D value").required()
                                .setType(Parameter.ParameterType.TEXT).setDefaultValue("0"),
                                new Parameter("Q value").required()
                                .setType(Parameter.ParameterType.TEXT).setDefaultValue("0")
                        )
                )
        );
        predictorTemplates.add(
                new PredictorTemplate("RANDOM_FOREST", "Random forest",
                        Arrays.asList(
                                new Parameter("Number of trees").required()
                                .setType(Parameter.ParameterType.TEXT).setDefaultValue("10"),
                                new Parameter("Max depth").required()
                                .setType(Parameter.ParameterType.TEXT).setDefaultValue("50"),
                                new Parameter("Lags").required()
                                .setType(Parameter.ParameterType.TEXT).setDefaultValue("100")
                        )
                )
        );
        predictorTemplates.add(
                new PredictorTemplate("HOLT_WINTERS", "Holt-Winter's method",
                        Arrays.asList(
                                new Parameter("Trend").required().setType(Parameter.ParameterType.SELECT).setOptions(
                                        Arrays.asList(
                                                new Option("Add"),
                                                new Option("Mul")
                                        )
                                ),
                                new Parameter("Seasonal").required().setType(Parameter.ParameterType.SELECT).setOptions(
                                        Arrays.asList(
                                                new Option("Add"),
                                                new Option("Mul")
                                        )
                                ),
                                new Parameter("alpha", "Alpha (Level smoothing)")
                                .required().setType(Parameter.ParameterType.TEXT).setDefaultValue("0.1"),
                                new Parameter("beta", "Beta (Trend smoothing)")
                                .required().setType(Parameter.ParameterType.TEXT).setDefaultValue("0.1"),
                                new Parameter("gamma", "Gamma (Seasonality smoothing)")
                                .required().setType(Parameter.ParameterType.TEXT).setDefaultValue("0.1"),
                                new Parameter("Seasonal periods")
                                .required().setType(Parameter.ParameterType.TEXT).setDefaultValue("60")
                        )
                )
        );
        predictorTemplates.add(
                new PredictorTemplate("LINEAR_REGRESSION", "Linear regression")
        );
        predictorTemplates.add(
                new PredictorTemplate("SVR", "SVR",
                        Arrays.asList(
                                new Parameter("Kernel").required().setType(Parameter.ParameterType.SELECT).setOptions(
                                        Arrays.asList(
                                                new Option("Linear"),
                                                new Option("Poly"),
                                                new Option("RBF"),
                                                new Option("Sigmoid")
                                        )
                                ).setDefaultValue("rbf")
                        )
                )
        );
        predictorTemplates.add(
                new PredictorTemplate("LSTM", "LSTM",
                        Arrays.asList(
                                new Parameter("Future model location").required()
                                .setType(Parameter.ParameterType.OPEN_FILE).setOptions(
                                        Arrays.asList(
                                                new Option("h5", "H5 model file")
                                        )
                                ),
                                new Parameter("Test model location")
                                .setType(Parameter.ParameterType.OPEN_FILE).setOptions(
                                        Arrays.asList(
                                                new Option("h5", "H5 model file")
                                        )
                                )
                        ),
                        Arrays.asList(
                                new Parameter("Train LSTM model").setType(Parameter.ParameterType.BUTTON)
                        )
                )
        );
        predictorTemplates.add(
                new PredictorTemplate("ONLY_SIMULATION", "Only simulation")
        );
        return predictorTemplates;
    }
}
