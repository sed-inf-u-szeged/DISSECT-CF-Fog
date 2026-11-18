package hu.u_szeged.inf.fog.simulator.prediction.settings;

/**
 * Configures data-driven training with contiguous sequence windows.
 * Training samples use stride one.
 */
public class OnlineTrainingSettings {

    private final int minimumTrainingSamples;
    private final int minimumNewDataPoints;
    private final int windowGrowthPoints;
    private final int maximumWindowPoints;

    public OnlineTrainingSettings(int minimumTrainingSamples,
            int minimumNewDataPoints, int windowGrowthPoints,
            int maximumWindowPoints) {
        if (minimumTrainingSamples < 1 || minimumNewDataPoints < 1
                || windowGrowthPoints < 1 || maximumWindowPoints < 1) {
            throw new IllegalArgumentException(
                    "Online training settings must all be positive.");
        }
        this.minimumTrainingSamples = minimumTrainingSamples;
        this.minimumNewDataPoints = minimumNewDataPoints;
        this.windowGrowthPoints = windowGrowthPoints;
        this.maximumWindowPoints = maximumWindowPoints;
    }

    public static OnlineTrainingSettings getDefault() {
        return new OnlineTrainingSettings(32, 64, 64, 4096);
    }

    /**
     * Calculates the required raw data count independently of model type.
     * N points produce N - inputSize - outputSize + 1 training samples.
     */
    public int getInitialWindowPoints(int inputSize, int outputSize) {
        if (inputSize < 1 || outputSize < 1) {
            throw new IllegalArgumentException(
                    "Input and output sizes must be positive.");
        }
        long required = (long) inputSize + outputSize
                + minimumTrainingSamples - 1;
        return checkedWindowSize(required);
    }

    /**
     * Preserves the existing training configuration interface.
     */
    public int getInitialWindowPoints(TrainSettings settings) {
        if (settings == null) {
            throw new IllegalArgumentException(
                    "Training settings must not be null.");
        }

        long required = getInitialWindowPoints(
                settings.getInputSize(), settings.getOutputSize());

        if (settings.getSmoothingSettings() != null) {
            required = Math.max(required,
                    settings.getSmoothingSettings().getWindowSize());
        }
        return checkedWindowSize(required);
    }

    private int checkedWindowSize(long required) {
        if (required > maximumWindowPoints) {
            throw new IllegalArgumentException(
                    "Maximum training window is smaller than the required initial window.");
        }
        return (int) required;
    }

    public int getMinimumTrainingSamples() {
        return minimumTrainingSamples;
    }

    public int getMinimumNewDataPoints() {
        return minimumNewDataPoints;
    }

    public int getWindowGrowthPoints() {
        return windowGrowthPoints;
    }

    public int getMaximumWindowPoints() {
        return maximumWindowPoints;
    }
}