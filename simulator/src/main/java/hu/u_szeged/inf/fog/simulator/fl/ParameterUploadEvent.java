package hu.u_szeged.inf.fog.simulator.fl;

import hu.mta.sztaki.lpds.cloud.simulator.DeferredEvent;
import hu.u_szeged.inf.fog.simulator.util.SimRandom;
import java.util.Random;

/**
 * Models the network transfer of a single client update to the aggregator.
 * When fired, it may drop the update due to in-transit failure; otherwise,
 * it accounts for telemetry (payload + secure overhead and delay) and delivers
 * the update to {@link FLAggregator#addModelUpdate(FLModelUpdate, int, int)}.
 */
public class ParameterUploadEvent extends DeferredEvent {
    private final FLModelUpdate update;
    private final FLAggregator  aggregator;
    private final double        runtimeFailureProbability; // in-transit loss probability
    private final boolean       secureAggregationEnabled;
    private final long          secureExtraBytes;
    private final long          commDelay;

    private final int roundId;
    private final int baseModelVersion;
    
    private final long          totalE2E;

    /**
     * @param delayInTicks             transfer delay (ticks)
     * @param update                   model update to deliver
     * @param aggregator               destination aggregator
     * @param runtimeFailureProbability probability of loss during transfer
     * @param secureAggregationEnabled whether secure aggregation is enabled
     * @param secureExtraBytes         per-client secure-agg overhead in bytes (already included in {@code delayInTicks})
     * @param commDelay                delay used for telemetry
     * @param roundId                  round index
     * @param baseModelVersion         model version used for training
     * @param totalE2E                 total client E2E ticks (DL + TRAIN + UL) for this attempt
     */
    public ParameterUploadEvent(long delayInTicks,
                                FLModelUpdate update,
                                FLAggregator aggregator,
                                double runtimeFailureProbability,
                                boolean secureAggregationEnabled,
                                long secureExtraBytes,
                                long commDelay,
                                int roundId,
                                int baseModelVersion,
                                long totalE2E) {
        super(delayInTicks);
        this.update                     = update;
        this.aggregator                 = aggregator;
        this.runtimeFailureProbability  = runtimeFailureProbability;
        this.secureAggregationEnabled   = secureAggregationEnabled;
        this.secureExtraBytes           = secureExtraBytes;
        this.commDelay                  = commDelay;
        this.roundId                    = roundId;
        this.baseModelVersion           = baseModelVersion;
        this.totalE2E                   = totalE2E;
    }

    /** {@inheritDoc} */
    @Override
    protected void eventAction() {
        Random rng = SimRandom.get();

        // Simulate lost upload (in-transit failure)
        if (rng.nextDouble() < runtimeFailureProbability) {
            System.out.println("ParameterUploadEvent: Update lost in transit "
                    + "(p=" + runtimeFailureProbability + ").");
            aggregator.noteInTransitLoss(); // telemetry
            return;
        }

        if (secureAggregationEnabled && secureExtraBytes > 0) {
            // Note: overhead was already included when commDelay was computed.
            System.out.println("ParameterUploadEvent: secure-agg overhead (+"
                    + secureExtraBytes + " B) was already included in commDelay.");
        }

        // Telemetry: Count UL bytes (payload + sec overhead), and delay
        long modelBytes = update.getUpdateSize();
        long secBytes   = secureAggregationEnabled ? secureExtraBytes : 0L;
        aggregator.noteUploadSuccess(modelBytes, secBytes, commDelay);
        
        // Deliver with E2E included; Aggregator will count E2E only if accepted for this round
        aggregator.addModelUpdate(update, roundId, baseModelVersion, totalE2E);    // server stores or accumulates depending on mode
        
    }
}
