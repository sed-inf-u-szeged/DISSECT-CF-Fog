package hu.u_szeged.inf.fog.simulator.fl.cosim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hu.u_szeged.inf.fog.simulator.fl.selection.Normalizer;
import hu.u_szeged.inf.fog.simulator.fl.topology.FLTopology;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Guards the property that makes the composite selector of Eq. score a
 * <i>four-term</i> policy rather than a one-term one.
 *
 * <p>With uniform cost tables every edge carries the same latency and
 * bandwidth and every node the same load, so {@link Normalizer}'s homogeneous
 * guard sets {@code L̃ = C̃ = B̃ = 0} for every node and the composite score
 * collapses to {@code γ(t)·D̃}. A campaign run that way exercises only the
 * divergence term while the paper describes a topology- and resource-aware
 * policy, so this must be asserted, not assumed.</p>
 */
class CostHeterogeneityTest {

    private static Pass1Main.Config clustered(int n, Pass1Main.CostSource source) {
        Pass1Main.Config c = new Pass1Main.Config();
        c.topologyType = "clustered";
        c.n = n;
        c.clusterSizes = new int[] { n / 2, n - n / 2 };
        c.bridges = 2;
        c.rounds = 20;
        c.seed = 11L;
        c.costSource = source;
        return c;
    }

    @Test
    @DisplayName("HETEROGENEOUS: link costs and loads vary, so L̃/C̃/B̃ are live")
    void heterogeneousCostsAreNonDegenerate() {
        Pass1Main.Built built = Pass1Main.build(clustered(6, Pass1Main.CostSource.HETEROGENEOUS));
        FLTopology topo = built.topology;

        Set<Long> latencies = new HashSet<>();
        Set<Long> bandwidths = new HashSet<>();
        for (FLTopology.Edge e : topo.edges()) {
            latencies.add(e.latencyTicks);
            bandwidths.add(e.bandwidthBytesPerTick);
        }
        assertTrue(latencies.size() > 1, "latency must vary across edges, got " + latencies);
        assertTrue(bandwidths.size() > 1, "bandwidth must vary across edges, got " + bandwidths);

        // The term that actually matters is per-NEIGHBOURHOOD spread: the
        // normaliser works within N(i), so a graph with globally varied costs
        // but locally uniform ones would still degenerate for those nodes.
        int nodesWithLatencySpread = 0;
        for (int i = 0; i < topo.size(); i++) {
            Set<Long> local = new HashSet<>();
            for (int j : topo.neighbours(i)) {
                local.add(topo.edge(i, j).latencyTicks);
            }
            if (local.size() > 1) {
                nodesWithLatencySpread++;
            }
        }
        assertEquals(topo.size(), nodesWithLatencySpread,
                "every node must see a non-degenerate latency neighbourhood");

        // Background load must vary per node AND per round.
        double[] r0 = new double[topo.size()];
        for (int i = 0; i < topo.size(); i++) {
            r0[i] = built.loadProfile.load(i, 0);
            assertTrue(r0[i] > 0.0, "node " + i + " has zero load — C̃ would degenerate");
        }
        double[] normalised = Normalizer.normalize(r0);
        boolean anyNonZero = false;
        for (double v : normalised) {
            anyNonZero |= v > 0.0;
        }
        assertTrue(anyNonZero, "normalised load is all-zero ⇒ the C̃ term is inert");
        assertNotEquals(built.loadProfile.load(0, 0), built.loadProfile.load(0, 5), 1e-12,
                "load must vary across rounds, else the schedule is static");
    }

    @Test
    @DisplayName("UNIFORM: the legacy flat tables do collapse the three cost terms")
    void uniformCostsDegenerate() {
        Pass1Main.Built built = Pass1Main.build(clustered(6, Pass1Main.CostSource.UNIFORM));
        FLTopology topo = built.topology;

        Set<Long> latencies = new HashSet<>();
        for (FLTopology.Edge e : topo.edges()) {
            latencies.add(e.latencyTicks);
        }
        assertEquals(1, latencies.size(), "flat defaults are uniform by construction");

        // Documented consequence: min == max ⇒ the normaliser returns all-zero,
        // so α·L̃ contributes nothing to the score for any node.
        List<Integer> nbrs = topo.neighbours(0);
        double[] raw = new double[nbrs.size()];
        for (int idx = 0; idx < nbrs.size(); idx++) {
            raw[idx] = topo.edge(0, nbrs.get(idx)).latencyTicks;
        }
        for (double v : Normalizer.normalize(raw)) {
            assertEquals(0.0, v, 0.0, "homogeneous guard must zero the term");
        }
        for (int i = 0; i < topo.size(); i++) {
            assertEquals(0.0, built.loadProfile.load(i, 0), 0.0,
                    "UNIFORM keeps the legacy neutral (zero) load schedule");
        }
    }

    @Test
    @DisplayName("bandwidth scaling moves communication into the binding position")
    void bandwidthScalingBindsCommunication() {
        // The boundary test of §9 rests on this: in the default deployment one
        // peer exchange costs a small fraction of a round, so the B̃ selection
        // term has no lever on round duration however it ranks peers. Scaling
        // capacity down is what creates the lever; latency must NOT move with
        // it, or the experiment cannot attribute an effect to bandwidth.
        Pass1Main.Config base = clustered(6, Pass1Main.CostSource.HETEROGENEOUS);
        Pass1Main.Built fast = Pass1Main.build(base);

        Pass1Main.Config scaledCfg = clustered(6, Pass1Main.CostSource.HETEROGENEOUS);
        scaledCfg.linkBandwidthScale = 0.1;
        Pass1Main.Built slow = Pass1Main.build(scaledCfg);

        long payload = fast.payloadBytesFloat32;
        double fastTransfer = 0;
        double slowTransfer = 0;
        for (FLTopology.Edge e : fast.topology.edges()) {
            fastTransfer += (double) payload / e.bandwidthBytesPerTick;
        }
        for (FLTopology.Edge e : slow.topology.edges()) {
            slowTransfer += (double) payload / e.bandwidthBytesPerTick;
        }
        assertTrue(slowTransfer > fastTransfer * 8.0,
                "0.1 scaling must make transfers ~10x slower, got "
                        + (slowTransfer / fastTransfer) + "x");

        // Latency is a queueing/propagation quantity and is deliberately untouched.
        List<Long> fastLat = new java.util.ArrayList<>();
        List<Long> slowLat = new java.util.ArrayList<>();
        for (FLTopology.Edge e : fast.topology.edges()) {
            fastLat.add(e.latencyTicks);
        }
        for (FLTopology.Edge e : slow.topology.edges()) {
            slowLat.add(e.latencyTicks);
        }
        assertEquals(fastLat, slowLat, "scaling bandwidth must not perturb latency");

        // Costs enter the topology hash, so the two are distinct scenarios.
        assertNotEquals(fast.topology.topologyHash(), slow.topology.topologyHash());

        // A scale of 1.0 must be an exact no-op, so S1-S5 stay reproducible.
        Pass1Main.Config unity = clustered(6, Pass1Main.CostSource.HETEROGENEOUS);
        unity.linkBandwidthScale = 1.0;
        assertEquals(fast.topology.topologyHash(), Pass1Main.build(unity).topology.topologyHash());
    }

    @Test
    @DisplayName("payload is derived from the model actually trained")
    void payloadMatchesTheModel() {
        Pass1Main.Config c = clustered(6, Pass1Main.CostSource.HETEROGENEOUS);
        c.modelName = "lenet5";
        c.numClasses = 10;
        c.inChannels = 1;
        Pass1Main.Built built = Pass1Main.build(c);
        // LeNet-5, 1 channel, 10 classes = 61,706 parameters (the harness's
        // build_model reports the same count); 4 bytes each.
        assertEquals(61_706L, built.paramCount);
        assertEquals(61_706L * 4L, built.payloadBytesFloat32);
        assertEquals(built.paramCount, built.trace.model.paramCount);

        c.numClasses = 4;
        assertEquals(61_196L, Pass1Main.build(c).paramCount,
                "a 4-class head must not be metered as a 10-class one");
    }
}
