package hu.u_szeged.inf.fog.simulator.prediction.lifecycle;

import hu.u_szeged.inf.fog.simulator.prediction.Prediction;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Compares two forecasts issued at the same origin on identical targets.
 * The origin is the index of the first future sample.
 */
public class PairedShadowEvaluation {

    private final String featureName;
    private final int origin;
    private final String candidateName;
    private final String baselineName;
    private final Prediction.ModelStatus baselineStatus;
    private final Map<Integer, Double> actualValues = new HashMap<>();
    private final Map<Integer, Double> candidateValues;
    private final ShadowEvaluator evaluator = new ShadowEvaluator();

    public PairedShadowEvaluation(String featureName, int origin,
            String candidateName, Prediction candidate,
            String baselineName, Prediction baseline) {
        if (featureName == null || origin < 0
                || candidateName == null || baselineName == null
                || candidateName.equals(baselineName)) {
            throw new IllegalArgumentException("Invalid comparison identity.");
        }

        this.featureName = featureName;
        this.origin = origin;
        this.candidateName = candidateName;
        this.baselineName = baselineName;

        candidateValues = readFuture(candidate);
        Map<Integer, Double> baselineValues = readFuture(baseline);
        if (!candidateValues.keySet().equals(baselineValues.keySet())) {
            throw new IllegalArgumentException(
                    "Candidate and baseline must have identical future targets.");
        }

        baselineStatus = baseline.getPredictionStatus() == null
                ? null : baseline.getPredictionStatus().getFuture();

        for (Map.Entry<Integer, Double> entry : candidateValues.entrySet()) {
            int target = entry.getKey();
            evaluator.registerPrediction(new ShadowPrediction(
                    candidateName, featureName, candidate.getPredictionNumber(),
                    target, entry.getValue()));
            evaluator.registerPrediction(new ShadowPrediction(
                    baselineName, featureName, baseline.getPredictionNumber(),
                    target, baselineValues.get(target)));
        }
    }

    private Map<Integer, Double> readFuture(Prediction prediction) {
        if (prediction == null
                || !featureName.equals(prediction.getFeatureName())
                || prediction.getWindowStartTimestamp() < 0
                || prediction.getOriginalData() == null
                || prediction.getOriginalData().getTimestamp() == null
                || prediction.getOriginalData().getTimestamp().isEmpty()
                || prediction.getPredictionFuture() == null) {
            throw new IllegalArgumentException(
                    "Prediction identity or input window is missing.");
        }

        List<Integer> history = prediction.getOriginalData().getTimestamp();
        int start = prediction.getWindowStartTimestamp();
        if ((long) start + history.size() != origin) {
            throw new IllegalArgumentException(
                    "Prediction input window does not end at the comparison origin.");
        }
        for (int i = 0; i < history.size(); i++) {
            if (!Objects.equals(history.get(i), i)) {
                throw new IllegalArgumentException(
                        "Expected consecutive local input timestamps starting at zero.");
            }
        }

        Prediction.Data future = prediction.getPredictionFuture();
        List<Integer> times = future.getTimestamp();
        List<Double> values = future.getData();
        if (times == null || values == null || times.isEmpty()
                || times.size() != values.size()) {
            throw new IllegalArgumentException("Invalid future prediction arrays.");
        }

        Map<Integer, Double> result = new HashMap<>();
        for (int i = 0; i < times.size(); i++) {
            Integer offset = times.get(i);
            Double value = values.get(i);
            if (offset == null || value == null || !Double.isFinite(value)) {
                throw new IllegalArgumentException("Invalid future prediction value.");
            }

            long target = (long) start + offset;
            if (target < origin || target > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Invalid future target timestamp.");
            }
            if (result.putIfAbsent((int) target, value) != null) {
                throw new IllegalArgumentException("Duplicate future target timestamp.");
            }
        }
        return result;
    }

    /**
     * Resolves both models using the same actual value.
     * Repeated identical observations are ignored.
     */
    public boolean resolveActualValue(String feature, int target, double value) {
        if (!featureName.equals(feature) || !candidateValues.containsKey(target)) {
            return false;
        }
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Actual value must be finite.");
        }

        Double previous = actualValues.get(target);
        if (previous != null) {
            if (Double.compare(previous, value) != 0) {
                throw new IllegalArgumentException(
                        "Conflicting actual values for the same target.");
            }
            return false;
        }

        actualValues.put(target, value);
        evaluator.resolveActualValue(featureName, target, value);
        return true;
    }

    public EvaluationMetrics getCandidateMetrics(
            double relativeTolerance, double absoluteTolerance) {
        return evaluator.calculateMetrics(candidateName, featureName,
                relativeTolerance, absoluteTolerance, Integer.MAX_VALUE);
    }

    public EvaluationMetrics getBaselineMetrics(
            double relativeTolerance, double absoluteTolerance) {
        return evaluator.calculateMetrics(baselineName, featureName,
                relativeTolerance, absoluteTolerance, Integer.MAX_VALUE);
    }

    public boolean isComplete() {
        return actualValues.size() == candidateValues.size();
    }

    public int getMatchedSamples() {
        return actualValues.size();
    }

    public int getTargetCount() {
        return candidateValues.size();
    }

    public int getOrigin() {
        return origin;
    }

    public String getFeatureName() {
        return featureName;
    }

    public String getCandidateName() {
        return candidateName;
    }

    public String getBaselineName() {
        return baselineName;
    }

    public Prediction.ModelStatus getBaselineStatus() {
        return baselineStatus;
    }
}