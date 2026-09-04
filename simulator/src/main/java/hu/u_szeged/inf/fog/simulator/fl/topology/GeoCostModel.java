package hu.u_szeged.inf.fog.simulator.fl.topology;

import hu.u_szeged.inf.fog.simulator.iot.mobility.GeoLocation;

/**
 * Derives default per-link costs (latency, bandwidth) from inter-node geography
 * (design decision D1, §8.1). Reuses the simulator's existing
 * {@link GeoLocation} great-circle ({@code calculateDistance}, Haversine,
 * metres) so the topology's default link properties stay consistent with the
 * deployment's physical layout.
 *
 * <p>This helper produces <i>defaults</i>; whatever costs end up on the edges
 * are what Pass-1 exports — there is a single source of truth. A scenario may
 * instead supply explicit per-edge latency/bandwidth, in which case this class
 * is not consulted.</p>
 *
 * <h2>Latency model</h2>
 * One-way latency = base + propagation, where propagation uses a fibre signal
 * speed of ≈ 2/3 c ≈ 200,000 km/s ⇒ ≈ 5 µs/km. With the simulator's default
 * tick = 1 ms this is {@code 0.005 ticks/km}, so short fog links round to the
 * base latency while continental links add a few ticks. Both the base and the
 * per-km factor are configurable.
 *
 * <p>All methods are pure and deterministic.</p>
 */
public final class GeoCostModel {

    /** Default base one-way latency (ticks ≈ ms) — queuing/processing floor. */
    public static final long DEFAULT_BASE_LATENCY_TICKS = 1L;
    /** Default propagation factor: ticks (ms) added per km of great-circle distance. */
    public static final double DEFAULT_TICKS_PER_KM = 0.005;
    /** Default link bandwidth (bytes/tick) when geography does not constrain it. */
    public static final long DEFAULT_BANDWIDTH_BYTES_PER_TICK = 40_000L;

    private final long baseLatencyTicks;
    private final double ticksPerKm;
    private final long bandwidthBytesPerTick;

    /** A model with the documented defaults. */
    public GeoCostModel() {
        this(DEFAULT_BASE_LATENCY_TICKS, DEFAULT_TICKS_PER_KM, DEFAULT_BANDWIDTH_BYTES_PER_TICK);
    }

    /**
     * Creates a model with explicit parameters.
     *
     * @param baseLatencyTicks fixed latency floor (ticks).
     * @param ticksPerKm propagation latency added per km.
     * @param bandwidthBytesPerTick default link bandwidth (bytes/tick).
     */
    public GeoCostModel(long baseLatencyTicks, double ticksPerKm, long bandwidthBytesPerTick) {
        if (baseLatencyTicks < 0 || ticksPerKm < 0 || bandwidthBytesPerTick <= 0) {
            throw new IllegalArgumentException("invalid GeoCostModel parameters");
        }
        this.baseLatencyTicks = baseLatencyTicks;
        this.ticksPerKm = ticksPerKm;
        this.bandwidthBytesPerTick = bandwidthBytesPerTick;
    }

    /**
     * One-way latency in ticks between two locations, rounded to the nearest
     * tick and clamped to at least the base latency.
     *
     * @param a first endpoint.
     * @param b second endpoint.
     * @return latency in ticks ({@code >= baseLatencyTicks}).
     */
    public long latencyTicks(GeoLocation a, GeoLocation b) {
        double distanceKm = a.calculateDistance(b) / 1000.0; // calculateDistance returns metres
        long ticks = baseLatencyTicks + Math.round(distanceKm * ticksPerKm);
        return Math.max(baseLatencyTicks, ticks);
    }

    /** The default bandwidth (bytes/tick) for a link. */
    public long bandwidthBytesPerTick() {
        return bandwidthBytesPerTick;
    }
}
