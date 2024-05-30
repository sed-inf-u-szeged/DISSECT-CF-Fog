package hu.u_szeged.inf.fog.simulator.prediction;

import java.util.ArrayList;
import java.util.List;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * The Feature class represents a measurable characteristic or property used in time series analysis.
 * It provides methods to compute, store, and retrieve the feature's values.
 */
public abstract class Feature {
    
    private String name;
    private List<Double> values;
    private List<Prediction> predictions;
    private boolean hasNewValue;

    /**
     * Constructs a Feature object with the specified name.
     *
     * @param name the name of the feature
     */
    public Feature(String name) {
        this.name = name;
        this.values = new ArrayList<>();
        this.predictions = new ArrayList<>();
        this.hasNewValue = false;
    }

    /**
     * This method should be implemented by subclasses to
     * provide specific computation logic for the feature's value.
     *
     * @return the computed feature value
     */
    public abstract double compute();

    /**
     * Computes the feature's value and adds it to the list of values.
     */
    public void computeValue() {
        values.add(compute());
        hasNewValue = true;
    }

    /**
     * Converts the feature's data to a JSON object.
     *
     * @return a JSONObject representing the feature's data
     */
    public JSONObject toJson() throws JSONException {
        return new JSONObject()
                .put("name", name)
                .put("values", Utils.listToJsonArray(values));
    }
    
    /**
     * Converts the feature's data within a specified window size to a JSON object.
     *
     * @param windowSize the size of the window
     * @return a JSONObject representing the feature's data within the window size
     */
    public JSONObject toJson(int windowSize) throws JSONException {
        return new JSONObject()
                .put("name", name)
                .put("values", Utils.listToJsonArray(getWindowValues(windowSize)));
    }

    /**
     * Retrieves a list of the feature's values within a specified window size.
     * If the number of values is less than the window size, an empty list is returned.
     *
     * @param windowSize the size of the window
     * @return a list of the feature's values within the window size
     */
    public List<Double> getWindowValues(int windowSize) {
        if (values.size() < windowSize) {
            return new ArrayList<>();
        }
        return values.subList(values.size() - windowSize, values.size());
    }

    public void setHasNewValue(boolean hasNewValue) {
        this.hasNewValue = hasNewValue;
    }

    public boolean getHasNewValue() {
        return hasNewValue;
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
        return "Feature{" 
                + "name='" + name + '\''
                + ", values=" + values.size() 
                + ", predictions=" + predictions.size() 
                + '}';
    }
}