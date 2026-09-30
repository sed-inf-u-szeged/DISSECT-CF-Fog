package hu.u_szeged.inf.fog.simulator.prediction.lifecycle;

/**
 * Coordinates the lifecycle and shadow evaluation of one predictor
 * for one predicted feature.
 */
public class LifecycleCoordinator {

    private final String predictorName;

    private final String featureName;

    private final LifecyclePolicy policy;

    private final ShadowEvaluator evaluator;

    private LifecycleState state;

    private EvaluationMetrics latestMetrics;

    /**
     * Creates a lifecycle coordinator in the COLLECTING_DATA state.
     *
     * @param predictorName the managed predictor
     * @param featureName the managed feature
     * @param policy the lifecycle acceptance policy
     */
    public LifecycleCoordinator(
            String predictorName,
            String featureName,
            LifecyclePolicy policy
    ) {
        if (predictorName == null || predictorName.isBlank()) {
            throw new IllegalArgumentException(
                    "Predictor name must not be blank."
            );
        }

        if (featureName == null || featureName.isBlank()) {
            throw new IllegalArgumentException(
                    "Feature name must not be blank."
            );
        }

        if (policy == null) {
            throw new IllegalArgumentException(
                    "Lifecycle policy must not be null."
            );
        }

        this.predictorName = predictorName;
        this.featureName = featureName;
        this.policy = policy;
        this.evaluator = new ShadowEvaluator();
        this.state = LifecycleState.COLLECTING_DATA;
    }

    /**
     * Moves the lifecycle from data collection to training.
     */
    public void startTraining() {
        requireState(
                LifecycleState.COLLECTING_DATA,
                "start training"
        );

        state = LifecycleState.TRAINING;
    }

    /**
     * Moves the lifecycle from training to shadow evaluation.
     */
    public void startEvaluation() {
        requireState(
                LifecycleState.TRAINING,
                "start evaluation"
        );

        state = LifecycleState.EVALUATING;
    }

    /**
     * Registers a prediction during shadow evaluation.
     *
     * @param prediction the prediction to register
     */
    public void registerPrediction(ShadowPrediction prediction) {
        requireState(
                LifecycleState.EVALUATING,
                "register a shadow prediction"
        );

        if (prediction == null) {
            throw new IllegalArgumentException(
                    "Prediction must not be null."
            );
        }

        boolean belongsToCoordinator =
                predictorName.equals(prediction.getPredictorName())
                        && featureName.equals(
                                prediction.getFeatureName()
                        );

        if (!belongsToCoordinator) {
            throw new IllegalArgumentException(
                    "Prediction does not belong to this coordinator."
            );
        }

        evaluator.registerPrediction(prediction);
    }

    /**
     * Matches an actual value with stored shadow predictions.
     *
     * @param targetTimestamp the timestamp of the observed value
     * @param actualValue the actual observed value
     * @return the number of resolved predictions
     */
    public int resolveActualValue(
            int targetTimestamp,
            double actualValue
    ) {
        requireState(
                LifecycleState.EVALUATING,
                "resolve an actual value"
        );

        return evaluator.resolveActualValue(
                featureName,
                targetTimestamp,
                actualValue
        );
    }

    /**
     * Calculates the latest metrics and moves to READY only when
     * every policy condition is satisfied.
     *
     * @return the latest evaluation metrics
     */
    public EvaluationMetrics evaluate() {
        requireState(
                LifecycleState.EVALUATING,
                "evaluate the predictor"
        );

        latestMetrics = evaluator.calculateMetrics(
                predictorName,
                featureName,
                policy.getAccuracyTolerance()
        );

        if (policy.isSatisfiedBy(latestMetrics)) {
            state = LifecycleState.READY;
        }

        return latestMetrics;
    }

    /**
     * Marks the lifecycle as failed after a technical failure.
     */
    public void markFailed() {
        state = LifecycleState.FAILED;
    }

    /**
     * Checks whether an operation is legal in the current state.
     *
     * @param expectedState the required state
     * @param operation the requested operation
     */
    private void requireState(
            LifecycleState expectedState,
            String operation
    ) {
        if (state != expectedState) {
            throw new IllegalStateException(
                    "Cannot "
                            + operation
                            + " while lifecycle state is "
                            + state
                            + "."
            );
        }
    }

    public String getPredictorName() {
        return predictorName;
    }

    public String getFeatureName() {
        return featureName;
    }

    public LifecycleState getState() {
        return state;
    }

    public EvaluationMetrics getLatestMetrics() {
        return latestMetrics;
    }
}