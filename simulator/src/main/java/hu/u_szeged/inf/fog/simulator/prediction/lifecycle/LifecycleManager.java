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
     * Stores the active predictor name for each feature.
     */
    private final Map<String, String> activePredictors;

    /**
     * Creates an empty lifecycle manager.
     */
    private LifecycleManager() {
        coordinators = new HashMap<>();
        activePredictors = new HashMap<>();
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
     * Activates a trusted baseline predictor for a feature.
     *
     * A baseline starts from COLLECTING_DATA because it was trusted before
     * the simulation started. Only one active predictor is allowed for a
     * feature.
     *
     * @param predictorName trusted baseline predictor
     * @param featureName feature served by the baseline
     */
    public void activateBaseline(
            String predictorName,
            String featureName
    ) {
        LifecycleCoordinator baselineCoordinator =
                requireCoordinator(
                        predictorName,
                        featureName
                );

        String currentActivePredictor =
                activePredictors.get(featureName);

        if (currentActivePredictor != null) {
            throw new IllegalStateException(
                    "Feature "
                            + featureName
                            + " already has active predictor "
                            + currentActivePredictor
                            + "."
            );
        }

        if (baselineCoordinator.getState()
                != LifecycleState.COLLECTING_DATA) {
            throw new IllegalStateException(
                    "Predictor "
                            + predictorName
                            + " for feature "
                            + featureName
                            + " cannot become a baseline while its state is "
                            + baselineCoordinator.getState()
                            + "."
            );
        }

        baselineCoordinator.activateAsBaseline();

        activePredictors.put(
                featureName,
                predictorName
        );
    }

    /**
     * Promotes a READY predictor to ACTIVE for one feature.
     *
     * If another predictor is currently active for the same feature,
     * the old predictor is first moved back to READY. This guarantees
     * that a feature has at most one active predictor.
     *
     * @param predictorName the predictor to activate
     * @param featureName the feature whose active predictor changes
     */
    public void promote(
            String predictorName,
            String featureName
    ) {
        LifecycleCoordinator newActiveCoordinator =
                requireCoordinator(
                        predictorName,
                        featureName
                );

        String currentActivePredictor =
                activePredictors.get(featureName);

        if (predictorName.equals(currentActivePredictor)) {
            return;
        }

        if (newActiveCoordinator.getState()
                != LifecycleState.READY) {
            throw new IllegalStateException(
                    "Predictor "
                            + predictorName
                            + " for feature "
                            + featureName
                            + " cannot be promoted while its state is "
                            + newActiveCoordinator.getState()
                            + "."
            );
        }

        LifecycleCoordinator oldActiveCoordinator = null;

        if (currentActivePredictor != null) {
            oldActiveCoordinator = requireCoordinator(
                    currentActivePredictor,
                    featureName
            );

            if (oldActiveCoordinator.getState()
                    != LifecycleState.ACTIVE) {
                throw new IllegalStateException(
                        "The registered active predictor "
                                + currentActivePredictor
                                + " for feature "
                                + featureName
                                + " is not in ACTIVE state."
                );
            }
        }

        if (oldActiveCoordinator != null) {
            oldActiveCoordinator.deactivate();
        }

        newActiveCoordinator.activate();

        activePredictors.put(
                featureName,
                predictorName
        );
    }

    /**
     * Returns the active predictor name for a feature.
     *
     * @param featureName the feature name
     * @return the active predictor name, or null when none is active
     */
    public String getActivePredictorName(
            String featureName
    ) {
        return activePredictors.get(featureName);
    }

    /**
     * Checks whether a predictor is active for a feature.
     *
     * @param predictorName the predictor name
     * @param featureName the feature name
     * @return true when this predictor is active for the feature
     */
    public boolean isActive(
            String predictorName,
            String featureName
    ) {
        return predictorName != null
                && predictorName.equals(
                        activePredictors.get(featureName)
                );
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
     * When a candidate satisfies every lifecycle threshold and becomes
     * READY, it is automatically promoted to ACTIVE for the feature.
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

                    if (coordinator.getState()
                            == LifecycleState.READY) {
                        promote(
                                coordinator.getPredictorName(),
                                featureName
                        );
                    }
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
            System.out.println(
                    "No lifecycle coordinators registered."
            );
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
                System.out.println(
                        "Active predictor for feature: "
                                + getActivePredictorName(
                                        coordinator.getFeatureName()
                                )
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
     * Removes every registered coordinator and active predictor.
     * This allows a new simulation to start with clean lifecycle state.
     */
    public void reset() {
        coordinators.clear();
        activePredictors.clear();
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