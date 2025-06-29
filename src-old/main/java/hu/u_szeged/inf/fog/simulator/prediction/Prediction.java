package hu.u_szeged.inf.fog.simulator.prediction;

import hu.u_szeged.inf.fog.simulator.prediction.settings.SimulationSettings;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Represents a prediction with its associated data, error metrics, and settings.
 */
public class Prediction {
    
    /**
     * Represents error metrics including RMSE, MSE, and MAE.
     */
    public class ErrorMetrics {
        
        private double rmse;
        private double mse;
        private double mae;

        /**
         * Constructs ErrorMetrics object from a JSONObject.
         *
         * @param jsonObject the JSON object containing the error metrics
         */
        public ErrorMetrics(JSONObject jsonObject) throws JSONException {
            this.rmse = jsonObject.getDouble("RMSE");
            this.mse = jsonObject.getDouble("MSE");
            this.mae = jsonObject.getDouble("MAE");
        }

        public double getRmse() {
            return rmse;
        }

        public double getMse() {
            return mse;
        }

        public double getMae() {
            return mae;
        }

        /**
         * Converts the ErrorMetrics to a JSONObject.
         *
         * @return the JSON representation of the error metrics
         */
        public JSONObject toJson() throws JSONException {
            return new JSONObject()
                    .put("RMSE", rmse)
                    .put("MSE", mse)
                    .put("MAE", mae);
        }
    }
    
    /**
     * Represents a dataset with timestamps and data points.
     */
    public class Data {
        
        private List<Integer> timestamp;
        private List<Double> data;

        /**
         * Constructs Data from JSONArrays.
         *
         * @param timestampJson the JSON array containing the timestamps
         * @param dataJson the JSON array containing the data points
         */
        public Data(JSONArray timestampJson, JSONArray dataJson) throws JSONException {
            this.timestamp = new ArrayList<>();
            this.data = new ArrayList<>();

            for (int i = 0; i < dataJson.length(); i++) {
                this.data.add(dataJson.getDouble(i));
            }

            for (int i = 0; i < timestampJson.length(); i++) {
                this.timestamp.add(timestampJson.getInt(i));
            }
        }

        public List<Integer> getTimestamp() {
            return timestamp;
        }

        public List<Double> getData() {
            return data;
        }

        /**
         * Converts the Data to a JSONObject.
         *
         * @return the JSON representation of the data
         */
        public JSONObject toJson() throws JSONException {
            return new JSONObject()
                    .put("data", Utils.listToJsonArray(data))
                    .put("timestamp", Utils.listToJsonArray(timestamp));
        }
    }

    private String featureName;
    private int predictionNumber;
    private SimulationSettings simulationSettings;
    private Data originalData;
    private Data preprocessedData;
    private Data testDataBeginning;
    private Data testDataEnd;
    private Data predictionFuture;
    private Data predictionTest;
    private ErrorMetrics errorMetrics;
    private double predictionTime;

    /**
     * Constructs a Prediction from a JSONObject.
     *
     * @param jsonObject the JSON object containing the prediction details
     */
    public Prediction(JSONObject jsonObject) throws JSONException {
        this.featureName = jsonObject.getString("feature_name");
        this.predictionNumber = jsonObject.getInt("prediction_number");
        this.simulationSettings = new SimulationSettings(jsonObject.getJSONObject("simulation_settings"));

        this.originalData = new Data(
                jsonObject.getJSONObject("original_data").getJSONArray("timestamp"),
                jsonObject.getJSONObject("original_data").getJSONArray("data")
        );
        this.preprocessedData = new Data(
                jsonObject.getJSONObject("preprocessed_data").getJSONArray("timestamp"),
                jsonObject.getJSONObject("preprocessed_data").getJSONArray("data")
        );
        this.testDataBeginning = new Data(
                jsonObject.getJSONObject("test_data_beginning").getJSONArray("timestamp"),
                jsonObject.getJSONObject("test_data_beginning").getJSONArray("data")
        );
        this.testDataEnd = new Data(
                jsonObject.getJSONObject("test_data_end").getJSONArray("timestamp"),
                jsonObject.getJSONObject("test_data_end").getJSONArray("data")
        );
        if (jsonObject.has("prediction_future")) {
            this.predictionFuture = new Data(
                    jsonObject.getJSONObject("prediction_future").getJSONArray("timestamp"),
                    jsonObject.getJSONObject("prediction_future").getJSONArray("data")
            );
        }
        if (jsonObject.has("prediction_test")) {
            this.predictionTest = new Data(
                    jsonObject.getJSONObject("prediction_test").getJSONArray("timestamp"),
                    jsonObject.getJSONObject("prediction_test").getJSONArray("data")
            );
        }
        if (jsonObject.has("error_metrics")) {
            this.errorMetrics = new ErrorMetrics(jsonObject.getJSONObject("error_metrics"));
        }

        this.predictionTime = jsonObject.getDouble("prediction_time");
    }

    public String getFeatureName() {
        return featureName;
    }

    public int getPredictionNumber() {
        return predictionNumber;
    }

    public SimulationSettings getSimulationSettings() {
        return simulationSettings;
    }

    public Data getOriginalData() {
        return originalData;
    }

    public Data getPreprocessedData() {
        return preprocessedData;
    }

    public Data getTestDataBeginning() {
        return testDataBeginning;
    }

    public Data getTestDataEnd() {
        return testDataEnd;
    }

    public Data getPredictionFuture() {
        return predictionFuture;
    }

    public Data getPredictionTest() {
        return predictionTest;
    }

    public double getPredictionTime() {
        return predictionTime;
    }

    public ErrorMetrics getErrorMetrics() {
        return errorMetrics;
    }

    /**
     * Converts the Prediction to a JSONObject.
     *
     * @return the JSON representation of the prediction
     */
    public JSONObject toJson() throws JSONException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("feature_name", featureName);
        jsonObject.put("prediction_number", predictionNumber);
        jsonObject.put("simulation_settings", simulationSettings.toJson());
        jsonObject.put("original_data", originalData.toJson());
        jsonObject.put("preprocessed_data", preprocessedData.toJson());
        jsonObject.put("test_data_beginning", testDataBeginning.toJson());
        jsonObject.put("test_data_end", testDataEnd.toJson());

        if (predictionFuture != null) {
            jsonObject.put("prediction_future", predictionFuture.toJson());
        }
        if (predictionTest != null) {
            jsonObject.put("prediction_test", predictionTest.toJson());
        }
        if (errorMetrics != null) {
            jsonObject.put("error_metrics", errorMetrics.toJson());
        }

        jsonObject.put("prediction_time", predictionTime);

        return jsonObject;
    }
}