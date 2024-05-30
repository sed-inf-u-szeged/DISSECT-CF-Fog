package hu.u_szeged.inf.fog.simulator.prediction.settings;

import org.json.JSONException;
import org.json.JSONObject;

public class PredictionSettings {
    
    public static class SmoothingSettings {
        private int windowSize;
        private int polynomialDegree;

        public SmoothingSettings(int windowSize, int polynomialDegree) {
            this.windowSize = windowSize;
            this.polynomialDegree = polynomialDegree;
        }

        public SmoothingSettings(String jsonString) throws JSONException {
            fromJSONString(jsonString);
        }

        private void fromJSONString(String jsonString) throws JSONException {
            JSONObject jsonObject = new JSONObject(jsonString);
            this.windowSize = jsonObject.getInt("windowSize");
            this.polynomialDegree = jsonObject.getInt("polynomialDegree");
        }

        public int getWindowSize() {
            return windowSize;
        }

        public void setWindowSize(int windowSize) {
            this.windowSize = windowSize;
        }

        public int getPolynomialDegree() {
            return polynomialDegree;
        }

        public void setPolynomialDegree(int polynomialDegree) {
            this.polynomialDegree = polynomialDegree;
        }

        public JSONObject toJSON() throws JSONException {
            return new JSONObject()
                    .put("windowSize", windowSize)
                    .put("polynomialDegree", polynomialDegree);
        }
    }

    private int length;
    private int testSize;
    private int batchSize;
    private SmoothingSettings smoothing;
    private boolean scale;
    private int minPredictionTime;

    public PredictionSettings(int length, int testSize, int batchSize, SmoothingSettings smoothing, boolean scale, int minPredictionTime) {
        this.length = length;
        this.testSize = testSize;
        this.batchSize = batchSize;
        this.smoothing = smoothing;
        this.scale = scale;
        this.minPredictionTime = minPredictionTime;
    }

    public PredictionSettings(JSONObject jsonObject) throws JSONException {
        fromJSONObject(jsonObject);
    }

    private void fromJSONObject(JSONObject jsonObject) throws JSONException {
        this.length = jsonObject.getInt("length");
        this.testSize = jsonObject.getInt("testSize");
        this.batchSize = jsonObject.getInt("batchSize");
        this.smoothing = new SmoothingSettings(jsonObject.getString("smoothing"));
        this.scale = jsonObject.getBoolean("scale");
        this.minPredictionTime = jsonObject.getInt("minPredictionTime");
    }

    public int getLength() {
        return length;
    }

    public void setLength(int length) {
        this.length = length;
    }

    public int getTestSize() {
        return testSize;
    }

    public void setTestSize(int testSize) {
        this.testSize = testSize;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public SmoothingSettings getSmoothing() {
        return smoothing;
    }

    public void setSmoothing(SmoothingSettings smoothing) {
        this.smoothing = smoothing;
    }

    public boolean isScale() {
        return scale;
    }

    public void setScale(boolean scale) {
        this.scale = scale;
    }

    public int getMinPredictionTime() {
        return minPredictionTime;
    }

    public void setMinPredictionTime(int minPredictionTime) {
        this.minPredictionTime = minPredictionTime;
    }

    public JSONObject toJSON() throws JSONException {
        return new JSONObject()
                .put("length", length)
                .put("testSize", testSize)
                .put("batchSize", batchSize)
                .put("smoothing", smoothing.toJSON())
                .put("scale", scale)
                .put("minPredictionTime", minPredictionTime);
    }
}
