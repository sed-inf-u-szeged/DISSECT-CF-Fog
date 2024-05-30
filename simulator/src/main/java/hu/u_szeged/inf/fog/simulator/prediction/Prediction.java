package hu.u_szeged.inf.fog.simulator.prediction;

import hu.u_szeged.inf.fog.simulator.prediction.settings.simulation.SimulationSettings;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class Prediction {
    
    public class ErrorMetrics {
        private double RMSE, MSE, MAE;

        public ErrorMetrics(JSONObject jsonObject) throws JSONException {
            this.RMSE = jsonObject.getDouble("RMSE");
            this.MSE = jsonObject.getDouble("MSE");
            this.MAE = jsonObject.getDouble("MAE");
        }

        public double getRMSE() {
            return RMSE;
        }

        public double getMSE() {
            return MSE;
        }

        public double getMAE() {
            return MAE;
        }

        public JSONObject toJSON() throws JSONException {
            return new JSONObject()
                    .put("RMSE", RMSE)
                    .put("MSE", MSE)
                    .put("MAE", MAE);
        }
    }
    public class Data {
        private List<Integer> timestamp;
        private List<Double> data;

        public Data(JSONArray timestampJSON, JSONArray dataJSON) throws JSONException {
            this.timestamp = new ArrayList<>();
            this.data = new ArrayList<>();

            for (int i = 0; i < dataJSON.length(); i++) {
                this.data.add(dataJSON.getDouble(i));
            }

            for (int i = 0; i < timestampJSON.length(); i++) {
                this.timestamp.add(timestampJSON.getInt(i));
            }
        }

        public List<Integer> getTimestamp() {
            return timestamp;
        }

        public List<Double> getData() {
            return data;
        }

        public JSONObject toJSON() throws JSONException {
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

    public Prediction(JSONObject jsonObject) throws JSONException {
        fromJSONObject(jsonObject);
    }

    private void fromJSONObject(JSONObject jsonObject) throws JSONException {
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

    public JSONObject toJSON() throws JSONException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("feature_name", featureName);
        jsonObject.put("prediction_number", predictionNumber);
        jsonObject.put("simulation_settings", simulationSettings.toJSON());
        jsonObject.put("original_data", originalData.toJSON());
        jsonObject.put("preprocessed_data", preprocessedData.toJSON());
        jsonObject.put("test_data_beginning", testDataBeginning.toJSON());
        jsonObject.put("test_data_end", testDataEnd.toJSON());

        if (predictionFuture != null) {
            jsonObject.put("prediction_future", predictionFuture.toJSON());
        }
        if (predictionTest != null) {
            jsonObject.put("prediction_test", predictionTest.toJSON());
        }
        if (errorMetrics != null) {
            jsonObject .put("error_metrics", errorMetrics.toJSON());
        }

        jsonObject.put("prediction_time", predictionTime);

        return jsonObject;
    }
}
