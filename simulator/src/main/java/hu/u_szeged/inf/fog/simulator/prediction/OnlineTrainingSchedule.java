package hu.u_szeged.inf.fog.simulator.prediction;

import hu.u_szeged.inf.fog.simulator.prediction.settings.OnlineTrainingSettings;

/**
 * Shared, domain-neutral training eligibility and window-growth rules.
 * Counts must use one consistent unit per caller: raw points for load prediction,
 * mature labelled examples for mobility. Lifecycle ownership stays with callers.
 */
public final class OnlineTrainingSchedule {
    private OnlineTrainingSchedule() { }

    /** A failed attempt cannot retry on the same data. */
    public static boolean hasNewData(int available, int previous, OnlineTrainingSettings settings) {
        if (available < 0 || previous < -1 || settings == null) {
            throw new IllegalArgumentException("Invalid training progress");
        }
        return previous < 0 || (long) available - previous >= settings.getMinimumNewDataPoints();
    }

    /** A usable model or an unfinished evaluation blocks a replacement fit. */
    public static boolean needsTraining(int available, int lastAttempt, int lastEvaluation,
            boolean hasVersion, boolean retryRequested, boolean usable, boolean pending,
            OnlineTrainingSettings settings) {
        return !usable && !pending && (!hasVersion || retryRequested)
                && hasNewData(available, Math.max(lastAttempt, lastEvaluation), settings);
    }

    /** Rejection grows the next window; degradation alone retains its current size. */
    public static int afterRejection(int window, OnlineTrainingSettings settings) {
        if (settings == null || window < 0 || window > settings.getMaximumWindowPoints()) {
            throw new IllegalArgumentException("Invalid training window");
        }
        return (int) Math.min(settings.getMaximumWindowPoints(), (long) window + settings.getWindowGrowthPoints());
    }
}
