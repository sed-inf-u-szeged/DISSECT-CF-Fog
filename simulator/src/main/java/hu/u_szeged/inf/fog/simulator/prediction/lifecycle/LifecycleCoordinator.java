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
        if (predictorName == null
                || predictorName.isBlank()) {
            throw new IllegalArgumentException(
                    "Predictor name must not be blank."
            );
        }

        if (featureName == null
                || featureName.isBlank()) {
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
     * Moves a previously evaluated, failed or degraded predictor into
     * retraining and clears metrics belonging to the previous model version.
     */
    public void startRetraining() {
        boolean canStartRetraining =
                state == LifecycleState.EVALUATING
                        || state == LifecycleState.READY
                        || state == LifecycleState.DEGRADED
                        || state == LifecycleState.FAILED;

        if (!canStartRetraining) {
            throw new IllegalStateException(
                    "Cannot start retraining while lifecycle state is "
                            + state
                            + "."
            );
        }

        evaluator.clear();
        latestMetrics = null;
        state = LifecycleState.RETRAINING;
    }

    /**
     * Moves the lifecycle from training or retraining to shadow evaluation.
     */
    public void startEvaluation() {
        boolean canStartEvaluation =
                state == LifecycleState.TRAINING
                        || state == LifecycleState.RETRAINING;

        if (!canStartEvaluation) {
            throw new IllegalStateException(
                    "Cannot start evaluation while lifecycle state is "
                            + state
                            + "."
            );
        }

        state = LifecycleState.EVALUATING;
    }

    /**
     * Registers a prediction for lifecycle metric evaluation.
     *
     * @param prediction the prediction to register
     */
    public void registerPrediction(
            ShadowPrediction prediction
    ) {
        requireEvaluationState(
                "register a shadow prediction"
        );

        if (prediction == null) {
            throw new IllegalArgumentException(
                    "Prediction must not be null."
            );
        }

        boolean belongsToCoordinator =
                predictorName.equals(
                        prediction.getPredictorName()
                )
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
        requireEvaluationState(
                "resolve an actual value"
        );

        return evaluator.resolveActualValue(
                featureName,
                targetTimestamp,
                actualValue
        );
    }

    /**
     * Calculates the latest rolling metrics.
     *
     * An EVALUATING candidate moves to READY only when every lifecycle
     * policy condition is satisfied.
     *
     * An ACTIVE predictor moves to DEGRADED when a complete monitoring
     * window is available and at least one policy condition is no longer
     * satisfied.
     *
     * @return the latest evaluation metrics
     */
    public EvaluationMetrics evaluate() {
        LifecycleState evaluatedState = state;

        requireEvaluationState(
                "evaluate the predictor"
        );

        latestMetrics = evaluator.calculateMetrics(
                predictorName,
                featureName,
                policy.getAccuracyTolerance(),
                policy.getMinimumMatchedSamples()
        );

        boolean completeEvaluationWindow =
                latestMetrics.getMatchedSamples()
                        >= policy.getMinimumMatchedSamples();

        boolean policySatisfied =
                policy.isSatisfiedBy(latestMetrics);

        if (evaluatedState
                == LifecycleState.EVALUATING
                && policySatisfied) {
            state = LifecycleState.READY;
        } else if (evaluatedState
                == LifecycleState.ACTIVE
                && completeEvaluationWindow
                && !policySatisfied) {
            state = LifecycleState.DEGRADED;
        }

        return latestMetrics;
    }

    /**
     * Returns whether enough real future values have been matched to make
     * a complete lifecycle decision.
     *
     * @return true when the minimum evaluation window is complete
     */
    public boolean hasCompleteEvaluationWindow() {
        return latestMetrics != null
                && latestMetrics.getMatchedSamples()
                >= policy.getMinimumMatchedSamples();
    }

    /**
     * Returns whether the latest metrics satisfy every lifecycle policy
     * condition.
     *
     * @return true only when the latest metrics satisfy the policy
     */
    public boolean isPolicySatisfied() {
        return policy.isSatisfiedBy(latestMetrics);
    }

    /**
     * Activates a predictor that has passed lifecycle evaluation.
     * A candidate predictor cannot become active directly from evaluation.
     */
    public void activate() {
        requireState(
                LifecycleState.READY,
                "activate the predictor"
        );

        state = LifecycleState.ACTIVE;
    }

    /**
     * Activates a configured trusted baseline predictor.
     *
     * A baseline is trusted before the simulation starts, so it may begin
     * serving directly from COLLECTING_DATA without shadow evaluation.
     */
    public void activateAsBaseline() {
        requireState(
                LifecycleState.COLLECTING_DATA,
                "activate the baseline predictor"
        );

        state = LifecycleState.ACTIVE;
    }

    /**
     * Removes the predictor from active service while keeping it available
     * as an already evaluated READY model.
     */
    public void deactivate() {
        requireState(
                LifecycleState.ACTIVE,
                "deactivate the predictor"
        );

        state = LifecycleState.READY;
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

    /**
     * Allows metric collection while a predictor is undergoing shadow
     * evaluation or serving as the active predictor.
     *
     * @param operation the requested evaluation operation
     */
    private void requireEvaluationState(
            String operation
    ) {
        if (state != LifecycleState.EVALUATING
                && state != LifecycleState.ACTIVE) {
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