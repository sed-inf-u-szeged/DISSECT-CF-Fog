package hu.u_szeged.inf.fog.simulator.prediction;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Selects predictions using MAE only within matching target windows.
 */
public final class PredictionSelector {

    private PredictionSelector() {
    }

    public static Map<String, Prediction> select(
            Collection<Prediction> predictions, String baselineName) {
        Map<String, List<Prediction>> grouped = new LinkedHashMap<>();
        Map<String, Prediction> selected = new LinkedHashMap<>();

        if (predictions == null) {
            return selected;
        }

        for (Prediction prediction : predictions) {
            if (isValidCandidate(prediction)) {
                grouped.computeIfAbsent(prediction.getFeatureName(),
                        key -> new ArrayList<>()).add(prediction);
            }
        }

        for (Map.Entry<String, List<Prediction>> entry : grouped.entrySet()) {
            List<Prediction> candidates = entry.getValue();
            Prediction reference = candidates.get(0);

            if (baselineName != null) {
                for (Prediction candidate : candidates) {
                    if (candidate.getPredictionSettings() != null
                            && baselineName.equals(candidate.getPredictionSettings()
                                    .getPredictorName())) {
                        reference = candidate;
                        break;
                    }
                }
            }

            Prediction best = reference;
            for (Prediction candidate : candidates) {
                if (PredictionWindow.areComparable(reference, candidate)
                        && candidate.getErrorMetrics().getMae()
                                < best.getErrorMetrics().getMae()) {
                    best = candidate;
                }
            }

            selected.put(entry.getKey(), best);
        }

        return selected;
    }

    private static boolean isValidCandidate(Prediction prediction) {
        return prediction != null
                && prediction.getErrorMetrics() != null
                && Double.isFinite(prediction.getErrorMetrics().getMae())
                && prediction.getErrorMetrics().getMae() >= 0
                && PredictionWindow.areComparable(prediction, prediction);
    }
}