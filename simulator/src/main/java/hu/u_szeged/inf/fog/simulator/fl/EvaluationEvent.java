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

    /** Computes and stores synthetic accuracy for {@link #round}. */
    @Override
    protected void eventAction() {
        Random rng   = SimRandom.get();
        double prog  = (round + 1) / (double) maxRounds;     // 0 → 1
        double base  = 0.50 + 0.40 * prog;                   // 0.50 → 0.90
        double acc   = Math.max(0.0,
                       Math.min(1.0, base + rng.nextGaussian() * 0.02));

        aggregator.recordAccuracy(round, acc);
        
        // On the final round, trigger export + plotting hook.
        if (round + 1 == maxRounds) {
            aggregator.onFinalEvaluationComplete();
        }
        
    }
}
