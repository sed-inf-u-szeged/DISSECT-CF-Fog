package hu.u_szeged.inf.fog.simulator.fl;

import hu.mta.sztaki.lpds.cloud.simulator.DeferredEvent;

/**
 * Forces aggregation when a round has exceeded its timeout window.
 * The timeout is scheduled as {@code TIMEOUT_RATIO × roundInterval} relative
 * to round start. If the round is already closed or the round id mismatches,
 * this event is a no-op.
 */
public class RoundTimeoutEvent extends DeferredEvent {

    private final FLAggregator aggregator;
    private final int          roundId;

    /**
     * @param delayInTicks timeout delay from round start (ticks)
     * @param aggregator   aggregator to notify
     * @param roundId      round index associated with this timeout
     */
    public RoundTimeoutEvent(long delayInTicks,
                             FLAggregator aggregator,
                             int roundId) {
        super(delayInTicks);
        this.aggregator = aggregator;
        this.roundId    = roundId;
    }

    /** {@inheritDoc} */
    @Override
    protected void eventAction() {
        aggregator.handleTimeout(roundId);
    }
}
