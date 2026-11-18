package hu.u_szeged.inf.fog.simulator.prediction.lifecycle;

/** Immutable domain assessment. Percentage metrics may be NaN when inapplicable. */
public final class LifecycleAssessment {
    private final EvaluationMetrics metrics;
    private final boolean satisfied;
    private final String domain;
    private final String reason;

    public LifecycleAssessment(EvaluationMetrics metrics, boolean satisfied, String domain, String reason) {
        if (metrics == null || metrics.getMatchedSamples() < 0
                || !finiteNonnegative(metrics.getMae()) || !finiteNonnegative(metrics.getMse())
                || !finiteNonnegative(metrics.getRmse()) || domain == null || domain.isBlank()
                || reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Invalid domain lifecycle assessment.");
        }
        this.metrics = metrics;
        this.satisfied = satisfied;
        this.domain = domain;
        this.reason = reason;
    }

    private static boolean finiteNonnegative(double value) {
        return Double.isFinite(value) && value >= 0;
    }

    public EvaluationMetrics getMetrics() { return metrics; }
    public boolean isSatisfied() { return satisfied; }
    public String getDomain() { return domain; }
    public String getReason() { return reason; }
}
