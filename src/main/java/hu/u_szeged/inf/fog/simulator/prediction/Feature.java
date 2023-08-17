package hu.u_szeged.inf.fog.simulator.prediction;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public abstract class Feature {
    private String name;
    private List<Double> values;
    private List<Prediction> predictions;
    private boolean hasNewValue;

    public Feature(String name) {
        this.name = name;
        this.values = new ArrayList<>();
        this.predictions = new ArrayList<>();
        this.hasNewValue = false;
    }

    abstract public double compute();

    public void computeValue() {
        values.add(compute());
        hasNewValue = true;
    }

    public String getName() {
        return name;
    }

    public void addPrediction(Prediction result) {
        predictions.add(result);
    }

    public List<Double> getValues() {
        return values;
    }

    public List<Prediction> getPredictions() {
        return predictions;
    }

    @Override
    public String toString() {
        return "Feature{" +
                "name='" + name + '\'' +
                ", values=" + values.size() +
                ", predictions=" + predictions.size() +
                '}';
    }

    public JSONObject toJSON() throws JSONException {
        return new JSONObject()
                .put("name", name)
                .put("values", Utils.listToJSONArray(values));
    }

    public List<Double> getWindowValues(int windowSize) {
        if (values.size() < windowSize) {
            return new ArrayList<>();
        }
        return values.subList(values.size() - windowSize, values.size());
    }

    public JSONObject toJSON(int windowSize) throws JSONException {
        return new JSONObject()
                .put("name", name)
                .put("values", Utils.listToJSONArray(getWindowValues(windowSize)));
    }

    public void setHasNewValue(boolean hasNewValue) {
        this.hasNewValue = hasNewValue;
    }

    public boolean getHasNewValue() {
        return hasNewValue;
    }
}