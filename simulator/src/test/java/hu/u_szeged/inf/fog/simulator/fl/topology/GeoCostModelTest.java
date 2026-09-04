package hu.u_szeged.inf.fog.simulator.fl.topology;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hu.u_szeged.inf.fog.simulator.fl.topology.FLTopology.Edge;
import hu.u_szeged.inf.fog.simulator.iot.mobility.GeoLocation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * F1 / D1 (§8.1): geography-derived per-edge latency. The wiring is tested
 * against {@code GeoLocation.calculateDistance} itself (base + round(km ×
 * ticksPerKm)), so the assertion holds regardless of the Haversine details.
 */
class GeoCostModelTest {

    private static final GeoLocation BUDAPEST = new GeoLocation(47.4979, 19.0402);
    private static final GeoLocation SZEGED = new GeoLocation(46.2530, 20.1414);

    @Test
    @DisplayName("latencyTicks = base + round(distanceKm * ticksPerKm), clamped to base")
    void latencyFollowsGreatCircle() {
        GeoCostModel model = new GeoCostModel();
        double km = BUDAPEST.calculateDistance(SZEGED) / 1000.0;
        long expected = Math.max(GeoCostModel.DEFAULT_BASE_LATENCY_TICKS,
                GeoCostModel.DEFAULT_BASE_LATENCY_TICKS
                        + Math.round(km * GeoCostModel.DEFAULT_TICKS_PER_KM));
        assertEquals(expected, model.latencyTicks(BUDAPEST, SZEGED));
        assertTrue(km > 100.0, "sanity: Budapest–Szeged is >100 km");
        assertTrue(expected > GeoCostModel.DEFAULT_BASE_LATENCY_TICKS,
                "a >100 km link must add propagation ticks over the base");
        // Zero distance clamps to the base latency.
        assertEquals(GeoCostModel.DEFAULT_BASE_LATENCY_TICKS,
                model.latencyTicks(BUDAPEST, BUDAPEST));
    }

    @Test
    @DisplayName("withGeoCosts rebuilds latencies, keeps structure/bandwidth/labels, changes the hash")
    void withGeoCostsRewiresLatencyOnly() {
        FLTopology ring = TopologyFactory.ring(3);
        // Spread far enough apart that geo latency differs from the flat default (10).
        GeoLocation[] locations = {
            new GeoLocation(47.0, 19.0),
            new GeoLocation(48.5, 21.0),
            new GeoLocation(50.0, 17.0),
        };
        GeoCostModel model = new GeoCostModel();
        FLTopology geo = TopologyFactory.withGeoCosts(ring, locations, model);

        assertEquals(ring.size(), geo.size());
        assertArrayEquals(ring.clusterLabels(), geo.clusterLabels());
        assertEquals(ring.edges().size(), geo.edges().size());
        for (int e = 0; e < ring.edges().size(); e++) {
            Edge before = ring.edges().get(e);
            Edge after = geo.edges().get(e);
            assertEquals(before.u, after.u);
            assertEquals(before.v, after.v);
            assertEquals(before.bandwidthBytesPerTick, after.bandwidthBytesPerTick,
                    "bandwidth must be preserved (geo informs latency only)");
            assertEquals(model.latencyTicks(locations[after.u], locations[after.v]),
                    after.latencyTicks, "latency must come from the geo model");
            assertTrue(after.active, "rebuilt graph starts static (all active)");
        }
        // Costs are part of the topology hash, so the geo graph hashes differently…
        assertNotEquals(ring.topologyHash(), geo.topologyHash());
        // …while the flat-default base is untouched (fallback preserved).
        assertEquals(TopologyFactory.DEFAULT_LATENCY_TICKS, ring.edges().get(0).latencyTicks);
    }

    @Test
    @DisplayName("withGeoCosts validates the locations array length")
    void locationsLengthValidated() {
        FLTopology ring = TopologyFactory.ring(3);
        assertThrows(IllegalArgumentException.class,
                () -> TopologyFactory.withGeoCosts(ring, new GeoLocation[2], new GeoCostModel()));
    }
}
