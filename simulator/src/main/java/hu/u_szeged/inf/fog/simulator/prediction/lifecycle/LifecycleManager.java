package hu.u_szeged.inf.fog.simulator.prediction.lifecycle;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages lifecycle coordinators for multiple predictors and features.
 */
public class LifecycleManager {

    private static LifecycleManager instance;

    private final Map<String, Map<String, LifecycleCoordinator>>
            coordinators;

    /**
     * Creates an empty lifecycle manager.
     */
    private LifecycleManager() {
        coordinators = new HashMap<>();
    }

    /**
     * Returns the shared lifecycle manager instance.
     *
     * @return the lifecycle manager
     */
    public static LifecycleManager getInstance() {
        if (instance == null) {
            instance = new LifecycleManager();
        }

        return instance;
    }

    /**
     * Registers a coordinator for one predictor and one feature.
     *
     * @param coordinator the coordinator to register
     */
    public void registerCoordinator(
            LifecycleCoordinator coordinator
    ) {
        if (coordinator == null) {
            throw new IllegalArgumentException(
                    "Lifecycle coordinator must not be null."
            );
        }

        Map<String, LifecycleCoordinator> featureCoordinators =
                coordinators.computeIfAbsent(
                        coordinator.getPredictorName(),
                        ignored -> new HashMap<>()
                );

        if (featureCoordinators.containsKey(
                coordinator.getFeatureName()
        )) {
            throw new IllegalArgumentException(
                    "A lifecycle coordinator is already registered "
                            + "for predictor "
                            + coordinator.getPredictorName()
                            + " and feature "
                            + coordinator.getFeatureName()
                            + "."
            );
        }

        featureCoordinators.put(
                coordinator.getFeatureName(),
                coordinator
        );
    }

    /**
     * Returns the coordinator for one predictor and one feature.
     *
     * @param predictorName the predictor name
     * @param featureName the feature name
     * @return the matching coordinator, or null when none is registered
     */
    public LifecycleCoordinator getCoordinator(
            String predictorName,
            String featureName
    ) {
        Map<String, LifecycleCoordinator> featureCoordinators =
                coordinators.get(predictorName);

        if (featureCoordinators == null) {
            return null;
        }

        return featureCoordinators.get(featureName);
    }

    /**
     * Registers a shadow prediction with its matching coordinator.
     *
     * @param prediction the shadow prediction
     */
    public void registerPrediction(
            ShadowPrediction prediction
    ) {
        if (prediction == null) {
            throw new IllegalArgumentException(
                    "Shadow prediction must not be null."
            );
        }

        LifecycleCoordinator coordinator = requireCoordinator(
                prediction.getPredictorName(),
                prediction.getFeatureName()
        );

        coordinator.registerPrediction(prediction);
    }

    /**
     * Registers a shadow prediction only when its coordinator exists
     * and is currently evaluating.
     *
     * @param prediction the shadow prediction
     * @return true when the prediction was registered;
     *         false when it was ignored
     */
    public boolean registerPredictionIfEvaluating(
            ShadowPrediction prediction
    ) {
        if (prediction == null) {
            throw new IllegalArgumentException(
                    "Shadow prediction must not be null."
            );
        }

        LifecycleCoordinator coordinator = getCoordinator(
                prediction.getPredictorName(),
                prediction.getFeatureName()
        );

        if (coordinator == null
                || coordinator.getState()
                != LifecycleState.EVALUATING) {
            return false;
        }

        coordinator.registerPrediction(prediction);
        return true;
    }

    /**
     * Resolves an actual value in every evaluating coordinator
     * registered for the feature. Each coordinator is evaluated
     * automatically after at least one prediction is resolved.
     *
     * @param featureName the observed feature
     * @param targetTimestamp the timestamp of the actual value
     * @param actualValue the actual observed value
     * @return the total number of resolved predictions
     */
    public int resolveActualValue(
            String featureName,
            int targetTimestamp,
            double actualValue
    ) {
        int resolvedCount = 0;

        for (Map<String, LifecycleCoordinator>
                featureCoordinators : coordinators.values()) {

            LifecycleCoordinator coordinator =
                    featureCoordinators.get(featureName);

            if (coordinator != null
                    && coordinator.getState()
                    == LifecycleState.EVALUATING) {
                int coordinatorResolvedCount =
                        coordinator.resolveActualValue(
                                targetTimestamp,
                                actualValue
                        );

                resolvedCount += coordinatorResolvedCount;

                if (coordinatorResolvedCount > 0) {
                    coordinator.evaluate();
                }
            }
        }

        return resolvedCount;
    }

    /**
     * Evaluates one registered predictor and feature.
     *
     * @param predictorName the predictor name
     * @param featureName the feature name
     * @return the calculated evaluation metrics
     */
    public EvaluationMetrics evaluate(
            String predictorName,
            String featureName
    ) {
        return requireCoordinator(
                predictorName,
                featureName
        ).evaluate();
    }

    /**
     * Prints a read-only summary of all registered lifecycle coordinators.
     *
     * This method is intended for simulation observability. It does not
     * change lifecycle state or trigger another evaluation.
     */
    public void printSummary() {
        System.out.println("=== Lifecycle Summary ===");

        if (coordinators.isEmpty()) {
            System.out.println("No lifecycle coordinators registered.");
            return;
        }

        for (Map<String, LifecycleCoordinator>
                featureCoordinators : coordinators.values()) {
            for (LifecycleCoordinator coordinator
                    : featureCoordinators.values()) {
                EvaluationMetrics metrics =
                        coordinator.getLatestMetrics();

                System.out.println(
                        "Predictor: "
                                + coordinator.getPredictorName()
                );
                System.out.println(
                        "Feature: "
                                + coordinator.getFeatureName()
                );
                System.out.println(
                        "State: "
                                + coordinator.getState()
                );

                if (metrics == null) {
                    System.out.println(
                            "Metrics: not available"
                    );
                } else {
                    System.out.println(
                            "Matched samples: "
                                    + metrics.getMatchedSamples()
                    );
                    System.out.println(
                            "Accuracy: "
                                    + metrics.getAccuracy()
                    );
                    System.out.println(
                            "MAE: "
                                    + metrics.getMae()
                    );
                    System.out.println(
                            "MSE: "
                                    + metrics.getMse()
                    );
                    System.out.println(
                            "RMSE: "
                                    + metrics.getRmse()
                    );
                    System.out.println(
                            "SMAPE: "
                                    + metrics.getSmape()
                    );
                }

                System.out.println("-------------------------");
            }
        }
    }

    /**
     * Removes every registered coordinator.
     * This allows a new simulation to start with clean lifecycle state.
     */
    public void reset() {
        coordinators.clear();
    }

    /**
     * Returns a required coordinator or reports a configuration error.
     */
    private LifecycleCoordinator requireCoordinator(
            String predictorName,
            String featureName
    ) {
        LifecycleCoordinator coordinator = getCoordinator(
                predictorName,
                featureName
        );

        if (coordinator == null) {
            throw new IllegalArgumentException(
                    "No lifecycle coordinator is registered "
                            + "for predictor "
                            + predictorName
                            + " and feature "
                            + featureName
                            + "."
            );
        }

        return coordinator;
    }
}