package hu.u_szeged.inf.fog.simulator.prediction;

import java.util.ArrayList;
import java.util.List;

import hu.u_szeged.inf.fog.simulator.prediction.parser.annotations.ToJsonParseIgnore;
import hu.u_szeged.inf.fog.simulator.prediction.settings.SimulationSettings;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The Feature class represents a measurable characteristic or property used in time series analysis.
 * It provides methods to compute, store, and retrieve the feature's values.
 */
@NoArgsConstructor
public abstract class Feature {
    
    @Getter
    private String name;

    @Getter
    private List<Double> values;

    @Getter
    @ToJsonParseIgnore
    private List<Prediction> predictions;

    @Setter
    @ToJsonParseIgnore
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
        if (values.size() + 1 > SimulationSettings.get().getPrediction().getBatchSize()) {
            values.remove(0);
        }

        values.add(compute());
        hasNewValue = true;
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

    public boolean getHasNewValue() {
        return hasNewValue;
    }

    public void addPrediction(Prediction result) {
        predictions.add(result);
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