package hu.u_szeged.inf.fog.simulator.prediction;

import java.util.List;

/**
 * Checks whether historical prediction errors use the same targets.
 */
public final class PredictionWindow {

    private PredictionWindow() {
    }

    public static boolean areComparable(Prediction first, Prediction second) {
        if (!hasValidTargets(first) || !hasValidTargets(second)
                || !first.getFeatureName().equals(second.getFeatureName())) {
            return false;
        }

        Prediction.Data firstTargets = first.getTestDataEnd();
        Prediction.Data secondTargets = second.getTestDataEnd();
        int size = firstTargets.getTimestamp().size();

        if (size != secondTargets.getTimestamp().size()) {
            return false;
        }

        for (int i = 0; i < size; i++) {
            long firstTime = (long) first.getWindowStartTimestamp()
                    + firstTargets.getTimestamp().get(i);
            long secondTime = (long) second.getWindowStartTimestamp()
                    + secondTargets.getTimestamp().get(i);

            if (firstTime != secondTime
                    || numberAt(firstTargets.getData(), i)
                            != numberAt(secondTargets.getData(), i)) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasValidTargets(Prediction prediction) {
        if (prediction == null || prediction.getFeatureName() == null
                || prediction.getFeatureName().isBlank()
                || prediction.getWindowStartTimestamp() < 0) {
            return false;
        }

        Prediction.Data targets = prediction.getTestDataEnd();
        Prediction.Data predicted = prediction.getPredictionTest();

        if (targets == null || predicted == null
                || targets.getTimestamp() == null || targets.getData() == null
                || predicted.getTimestamp() == null || predicted.getData() == null) {
            return false;
        }

        int size = targets.getTimestamp().size();
        if (size == 0 || targets.getData().size() != size
                || predicted.getTimestamp().size() != size
                || predicted.getData().size() != size) {
            return false;
        }

        int previous = -1;
        for (int i = 0; i < size; i++) {
            Integer timestamp = targets.getTimestamp().get(i);
            if (timestamp == null || timestamp < 0 || timestamp <= previous
                    || !timestamp.equals(predicted.getTimestamp().get(i))
                    || !Double.isFinite(numberAt(targets.getData(), i))
                    || !Double.isFinite(numberAt(predicted.getData(), i))) {
                return false;
            }
            previous = timestamp;
        }
        return true;
    }

    private static double numberAt(List<?> values, int index) {
        Object value = values.get(index);
        return value instanceof Number
                ? ((Number) value).doubleValue() : Double.NaN;
    }
}