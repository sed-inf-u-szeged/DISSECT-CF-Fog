package hu.u_szeged.inf.fog.simulator.fl.selection;

/**
 * The exploration–exploitation lever γ(t) of the composite peer-selection score
 * (§8.2 "Exploration versus exploitation"). γ multiplies the divergence term
 * {@code D̃} in Eq. score. With γ &lt; 0 divergence is <i>rewarded</i> (a node is
 * drawn to dissimilar peers → exploration, faster early mixing); with γ &gt; 0
 * divergence is <i>penalised</i> (a node prefers similar peers → exploitation,
 * stable late convergence).
 *
 * <p>The default magnitude is {@code 0.25}, matching the equal-weight composite
 * default α = β = δ = 0.25 (so |α|+|β|+|γ|+|δ| = 1 with no perturbation). The
 * switch point of the explore-then-exploit schedule is {@code t = T/2}.</p>
 */
public enum GammaSchedule {

    /** γ = −0.25 for {@code t < T/2}, +0.25 thereafter (the default, §8.2). */
    EXPLORE_THEN_EXPLOIT,
    /** γ = +0.25 throughout (always-exploit limiting behaviour). */
    ALWAYS_EXPLOIT,
    /** γ = −0.25 throughout (always-explore limiting behaviour). */
    ALWAYS_EXPLORE;

    /** Default γ magnitude, matching the equal-weight composite default. */
    public static final double DEFAULT_MAGNITUDE = 0.25;

    /**
     * The signed γ for round {@code t} of a {@code totalRounds}-round run, at the
     * given magnitude.
     *
     * @param round the current round ({@code >= 0}).
     * @param totalRounds total rounds {@code T} ({@code >= 1}); the explore→exploit
     *                    switch is at {@code t == T/2} (integer division).
     * @param magnitude the absolute γ value (typically {@link #DEFAULT_MAGNITUDE}).
     * @return signed γ.
     */
    public double gamma(int round, int totalRounds, double magnitude) {
        switch (this) {
            case ALWAYS_EXPLOIT:
                return +magnitude;
            case ALWAYS_EXPLORE:
                return -magnitude;
            case EXPLORE_THEN_EXPLOIT:
            default:
                return round < (totalRounds / 2) ? -magnitude : +magnitude;
        }
    }

    /** Convenience: γ at the {@link #DEFAULT_MAGNITUDE}. */
    public double gamma(int round, int totalRounds) {
        return gamma(round, totalRounds, DEFAULT_MAGNITUDE);
    }
}
