package hu.u_szeged.inf.fog.simulator.prediction.lifecycle;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.u_szeged.inf.fog.simulator.demo.ScenarioBase;
import hu.u_szeged.inf.fog.simulator.prediction.PredictionLogger;
import hu.u_szeged.inf.fog.simulator.util.SimLogger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Coordinates one predictor's lifecycle for one feature.
 */
public class LifecycleCoordinator {

    private final String predictorName;
    private final String featureName;
    private final LifecyclePolicy policy;
    private final ShadowEvaluator evaluator;

    // Keep the first registered forecast for each target in both phases.
    private final Set<Integer> evaluationTargets = new HashSet<>();
    private final Set<Integer> pendingShadowTargets = new HashSet<>();

    private LifecycleState state;
    private EvaluationMetrics latestMetrics;
    private boolean automaticPromotionAllowed = true;
    private boolean monitorWhileReady;
    private boolean trustedBaseline;
    private boolean shadowDecisionLogged;

    public LifecycleCoordinator(String predictorName, String featureName,
            LifecyclePolicy policy) {
        if (predictorName == null || predictorName.isBlank()) {
            throw new IllegalArgumentException("Predictor name must not be blank.");
        }
        if (featureName == null || featureName.isBlank()) {
            throw new IllegalArgumentException("Feature name must not be blank.");
        }
        if (policy == null) {
            throw new IllegalArgumentException("Lifecycle policy must not be null.");
        }
        this.predictorName = predictorName;
        this.featureName = featureName;
        this.policy = policy;
        evaluator = new ShadowEvaluator();
        state = LifecycleState.COLLECTING_DATA;
    }

    public void setAutomaticPromotionAllowed(boolean allowed) {
        automaticPromotionAllowed = allowed;
    }

    public boolean isAutomaticPromotionAllowed() {
        return automaticPromotionAllowed;
    }

    /** Keep metrics fresh for an eligible, currently unselected model family. */
    public void setMonitorWhileReady(boolean enabled) {
        monitorWhileReady = enabled;
    }

    public boolean isMonitorWhileReady() {
        return monitorWhileReady;
    }

    public boolean canCollectEvaluation() {
        return state == LifecycleState.EVALUATING || state == LifecycleState.ACTIVE
                || (monitorWhileReady && state == LifecycleState.READY);
    }

    public void startTraining() {
        requireState(LifecycleState.COLLECTING_DATA, "start training");
        state = LifecycleState.TRAINING;
    }

    public void startRetraining() {
        if (state != LifecycleState.EVALUATING
                && state != LifecycleState.READY
                && state != LifecycleState.DEGRADED
                && state != LifecycleState.FAILED) {
            throw new IllegalStateException(
                    "Cannot start retraining while lifecycle state is " + state + ".");
        }
        evaluator.clear();
        evaluationTargets.clear();
        pendingShadowTargets.clear();
        latestMetrics = null;
        shadowDecisionLogged = false;
        state = LifecycleState.RETRAINING;
    }

    public void startEvaluation() {
        if (state != LifecycleState.TRAINING
                && state != LifecycleState.RETRAINING) {
            throw new IllegalStateException(
                    "Cannot start evaluation while lifecycle state is " + state + ".");
        }
        shadowDecisionLogged = false;
        state = LifecycleState.EVALUATING;
    }

    public void registerPrediction(ShadowPrediction prediction) {
        requireEvaluationState("register a shadow prediction");
        if (prediction == null) {
            throw new IllegalArgumentException("Prediction must not be null.");
        }
        if (!predictorName.equals(prediction.getPredictorName())
                || !featureName.equals(prediction.getFeatureName())) {
            throw new IllegalArgumentException(
                    "Prediction does not belong to this coordinator.");
        }

        // A target contributes at most one sample, including after activation.
        int target = prediction.getTargetTimestamp();
        if (!evaluationTargets.add(target)) {
            return;
        }

        if (state == LifecycleState.EVALUATING && !prediction.isResolved()) {
            pendingShadowTargets.add(target);
        }

        evaluator.registerPrediction(prediction);
    }

    public int resolveActualValue(int targetTimestamp, double actualValue) {
        requireEvaluationState("resolve an actual value");
        int resolved = evaluator.resolveActualValue(
                featureName, targetTimestamp, actualValue);
        if (resolved > 0) {
            pendingShadowTargets.remove(targetTimestamp);
        }
        return resolved;
    }

    public int getPendingShadowPredictionCount() {
        return pendingShadowTargets.size();
    }

    public EvaluationMetrics evaluate() {
        requireEvaluationState("evaluate the predictor");
        LifecycleState previousState = state;
        latestMetrics = evaluator.calculateMetrics(predictorName, featureName,
                policy.getAccuracyTolerance(), policy.getAbsoluteTolerance(),
                policy.getMinimumMatchedSamples());

        boolean policySatisfied = policy.isSatisfiedBy(latestMetrics);
        if (state == LifecycleState.EVALUATING && policySatisfied) {
            state = LifecycleState.READY;
        } else if ((state == LifecycleState.ACTIVE
                || (monitorWhileReady && state == LifecycleState.READY))
                && !trustedBaseline
                && hasCompleteEvaluationWindow()
                && !policySatisfied) {
            state = LifecycleState.DEGRADED;
        }

        if (previousState == LifecycleState.EVALUATING
                && hasCompleteEvaluationWindow()
                && (!shadowDecisionLogged || policySatisfied)) {
            shadowDecisionLogged = true;
            logEvaluation(policySatisfied ? "SHADOW_PASS" : "SHADOW_FAIL",
                    previousState);
        } else if (state == LifecycleState.DEGRADED
                && previousState != LifecycleState.DEGRADED) {
            logEvaluation("DEGRADED", previousState);
        }
        return latestMetrics;
    }

    public boolean hasCompleteEvaluationWindow() {
        return latestMetrics != null
                && latestMetrics.getMatchedSamples()
                >= policy.getMinimumMatchedSamples();
    }

    public boolean isPolicySatisfied() {
        return policy.isSatisfiedBy(latestMetrics);
    }

    public void activate() {
        requireState(LifecycleState.READY, "activate the predictor");
        state = LifecycleState.ACTIVE;
    }

    public void activateAsBaseline() {
        requireState(LifecycleState.COLLECTING_DATA, "activate the baseline predictor");
        trustedBaseline = true;
        state = LifecycleState.ACTIVE;
    }

    public void deactivate() {
        requireState(LifecycleState.ACTIVE, "deactivate the predictor");
        state = LifecycleState.READY;
    }

    public void markFailed() {
        state = LifecycleState.FAILED;
    }

    private void logEvaluation(String event, LifecycleState previousState) {
        String entry = "time=" + Timed.getFireCount()
                + "; event=" + event
                + "; predictor=" + predictorName
                + "; feature=" + featureName
                + "; state=" + previousState + "->" + state
                + "; matched=" + latestMetrics.getMatchedSamples()
                + "; required=" + policy.getMinimumMatchedSamples()
                + "; MAE=" + latestMetrics.getMae()
                + "; maxMAE=" + policy.getMaximumMae()
                + "; MSE=" + latestMetrics.getMse()
                + "; maxMSE=" + policy.getMaximumMse()
                + "; RMSE=" + latestMetrics.getRmse()
                + "; maxRMSE=" + policy.getMaximumRmse()
                + "; SMAPE=" + latestMetrics.getSmape()
                + "; maxSMAPE=" + policy.getMaximumSmape()
                + "; accuracy=" + latestMetrics.getAccuracy()
                + "; minAccuracy=" + policy.getMinimumAccuracy()
                + "; relativeTolerance=" + policy.getAccuracyTolerance()
                + "; absoluteTolerance=" + policy.getAbsoluteTolerance()
                + "; reason=" + evaluationReason();

        PredictionLogger.info("Lifecycle evaluation", entry);
        if (ScenarioBase.resultDirectory != null) {
            SimLogger.logRun("Lifecycle evaluation " + entry);
        }
    }

    private String evaluationReason() {
        List<String> reasons = new ArrayList<>();
        if (latestMetrics.getMatchedSamples() < policy.getMinimumMatchedSamples()) {
            reasons.add("insufficient matched samples");
        }
        if (!(latestMetrics.getMae() <= policy.getMaximumMae())) {
            reasons.add("MAE exceeds limit or is NaN");
        }
        if (!(latestMetrics.getMse() <= policy.getMaximumMse())) {
            reasons.add("MSE exceeds limit or is NaN");
        }
        if (!(latestMetrics.getRmse() <= policy.getMaximumRmse())) {
            reasons.add("RMSE exceeds limit or is NaN");
        }
        if (!(latestMetrics.getSmape() <= policy.getMaximumSmape())) {
            reasons.add("SMAPE exceeds limit or is NaN");
        }
        if (!(latestMetrics.getAccuracy() >= policy.getMinimumAccuracy())) {
            reasons.add("accuracy below limit or is NaN");
        }
        return reasons.isEmpty()
                ? "all configured thresholds satisfied"
                : String.join(", ", reasons);
    }

    private void requireState(LifecycleState expectedState, String operation) {
        if (state != expectedState) {
            throw new IllegalStateException(
                    "Cannot " + operation + " while lifecycle state is " + state + ".");
        }
    }

    private void requireEvaluationState(String operation) {
        if (!canCollectEvaluation()) {
            throw new IllegalStateException(
                    "Cannot " + operation + " while lifecycle state is " + state + ".");
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