package hu.u_szeged.inf.fog.simulator.prediction;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import java.io.IOException;
import java.util.Objects;

/**
 * Observes a simulation on a common time grid, without inserting observer events
 * into its event queue. Equal-time application events retain their original order.
 */
public final class FixedIntervalSampler {
    public static final String PHASE = "AFTER_EVENTS_AT_TIME";

    private FixedIntervalSampler() { }

    @FunctionalInterface
    public interface SampleWriter {
        void sample(long sampleIndex, long timeMillis) throws IOException;
    }

    /**
     * Samples at 0, interval, ... through sampleUntil, then continues normal task
     * draining through simulationLimit. The callback must only read simulation
     * state. It must neither schedule events nor advance the simulation clock.
     *
     * <p>A boundary is observed after ALL events at that timestamp. Timed.fire()
     * increments the engine counter afterwards, so the supplied boundary time is
     * the sample timestamp; reading Timed.getFireCount() in the callback is not.
     * No future event is executed to obtain the sample. If the event queue empties
     * early, sampling stops too; no synthetic idle tail is generated.
     *
     * @return the number of sampled boundaries (not the number of node rows)
     */
    public static long simulate(long interval, long sampleUntil, long simulationLimit,
            SampleWriter writer) throws IOException {
        Objects.requireNonNull(writer, "writer");
        if (interval <= 0 || sampleUntil < 0 || simulationLimit < sampleUntil
                || simulationLimit == Long.MAX_VALUE || Timed.getFireCount() != 0) {
            throw new IllegalArgumentException("Expected a positive interval, bounded time range and fresh simulation");
        }
        long count = 0;
        for (long time = 0; time <= sampleUntil && Timed.getNextFire() != -1;) {
            Timed.simulateUntil(time);
            // simulateUntil does not enter its loop when already at the boundary.
            if (Timed.getFireCount() == time && Timed.getNextFire() == time) {
                Timed.fire();
            }
            if (Timed.getFireCount() < time) {
                break; // The last real event occurred before this boundary.
            }
            writer.sample(count++, time);
            if (sampleUntil - time < interval) {
                break;
            }
            time += interval;
        }
        Timed.simulateUntil(simulationLimit);
        return count;
    }
}
