package hu.u_szeged.inf.fog.simulator.prediction;

import hu.u_szeged.inf.fog.simulator.prediction.lifecycle.LifecycleManager;
import hu.u_szeged.inf.fog.simulator.prediction.parser.annotations.ToJsonParseIgnore;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The Feature class represents a measurable characteristic or property used
 * in time series analysis.
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
     * Computes the current feature value.
     *
     * @return the computed feature value
     */
    public abstract double compute();

    /**
     * Computes and stores one actual feature value.
     *
     * The actual value first resolves shadow predictions and may change a
     * predictor lifecycle state. The latest lifecycle state is then persisted
     * to SQLite so the predictor UI can display it.
     */
    public void computeValue() {
        double value = compute();
        values.add(value);

        int timestamp = values.size() - 1;

        LifecycleManager.getInstance().resolveActualValue(
                getName(),
                timestamp,
                value
        );

        PredictionConfigurator.sqLiteManager.syncLifecycleData(
                getName()
        );

        PredictionConfigurator.sqLiteManager.addRawDataToTable(
                getName(),
                value
        );

        hasNewValue = true;
    }

    /**
     * Retrieves the most recent values for the requested window size.
     *
     * @param windowSize the requested window size
     * @return the recent values, or an empty list when insufficient data exists
     */
    public List<Double> getWindowValues(int windowSize) {
        if (values.size() < windowSize) {
            return new ArrayList<>();
        }

        return values.subList(
                values.size() - windowSize,
                values.size()
        );
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