package hu.u_szeged.inf.fog.simulator.fl;

import hu.mta.sztaki.lpds.cloud.simulator.DeferredEvent;
import hu.u_szeged.inf.fog.simulator.util.SimRandom;
import java.util.Random;

/**
 * Post-aggregation evaluation event that records a synthetic test accuracy
 * into the aggregator. Intended for trend visualization only.
 *
 *  Model: accuracy increases roughly linearly with round progress
 * (from ~0.50 towards ~0.90) plus small Gaussian jitter.
 *
 *  Time unit: ticks. The default delay is typically 1 tick. 
 */
public class EvaluationEvent extends DeferredEvent {

    private final FLAggregator aggregator;
    private final int          round;
    private final int          maxRounds;

    /**
     * @param delayInTicks evaluation delay (ticks).
     * @param aggregator target aggregator to record accuracy.
     * @param round current round id.
     * @param maxRounds maximum number of rounds in the experiment.
     */
    public EvaluationEvent(long delayInTicks,
                           FLAggregator aggregator,
                           int round,
                           int maxRounds) {
        super(delayInTicks);
        this.aggregator = aggregator;
        this.round      = round;
        this.maxRounds  = maxRounds;
    }

    /** Computes and stores accuracy for {@link #round}. */
    @Override
    protected void eventAction() {
        // §8.4 learning-source switch. The SYNTHETIC branch (provider null or
        // SYNTHETIC) is byte-identical to the SIMPAT baseline; TRACE/ONLINE route
        // the cached accuracy through the bridge. The synthetic RNG draw is only
        // consumed on the synthetic path, so the shared stream is untouched in
        // both modes (no spurious draw in TRACE mode).
        hu.u_szeged.inf.fog.simulator.fl.cosim.LearningProvider lp = aggregator.getLearningProvider();
        double acc;
        if (lp == null || lp.source() == FLLearningSource.SYNTHETIC) {
            Random rng   = SimRandom.get();
            double prog  = (round + 1) / (double) maxRounds;     // 0 → 1
            double base  = 0.50 + 0.40 * prog;                   // 0.50 → 0.90
            acc          = Math.max(0.0,
                           Math.min(1.0, base + rng.nextGaussian() * 0.02));
        } else {
            // TRACE/ONLINE: replay the recorded global-model accuracy.
            acc = lp.outcome(hu.u_szeged.inf.fog.simulator.fl.cosim.LearningProvider.GLOBAL_NODE, round).acc;
        }

        aggregator.recordAccuracy(round, acc);
        
        // On the final round, trigger export + plotting hook.
        if (round + 1 == maxRounds) {
            aggregator.onFinalEvaluationComplete();
        }
        
    }
}