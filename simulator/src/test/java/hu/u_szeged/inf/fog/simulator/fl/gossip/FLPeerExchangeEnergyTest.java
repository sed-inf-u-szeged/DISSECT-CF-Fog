package hu.u_szeged.inf.fog.simulator.fl.gossip;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import at.ac.uibk.dps.cloud.simulator.test.PMRelatedFoundation;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;

import hu.u_szeged.inf.fog.simulator.fl.FLEdgeDevice;
import hu.u_szeged.inf.fog.simulator.iot.Device;
import hu.u_szeged.inf.fog.simulator.iot.mobility.GeoLocation;
import hu.u_szeged.inf.fog.simulator.iot.mobility.RandomWalkMobilityStrategy;
import hu.u_szeged.inf.fog.simulator.iot.strategy.RandomDeviceStrategy;
import hu.u_szeged.inf.fog.simulator.util.EnergyDataCollectorFL;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * P2.2 Risk-R1 gate: a peer exchange meters transfer energy at <b>both</b>
 * endpoints, with no double-counting, and the mutual-exchange de-dup holds at
 * the energy level.
 *
 * <h2>What is asserted vs. the plan's "bytes × per-byte energy"</h2>
 * DISSECT-CF meters energy as (power state × time), not as a per-byte constant,
 * and a transfer's duration is {@code bytes / bandwidth}; so the faithful
 * translation of "increments match a hand-computed expectation" is
 * <i>proportionality to payload bytes</i>: with the exchange fired at delay 0
 * the measurement window is the transfer duration (∝ bytes), so the metered
 * energy is ∝ bytes. A mis-attributed or double-counted increment would break
 * this proportionality or the single≈½·mutual relation. Concretely:
 * <ul>
 *   <li>(a) both endpoints' meters strictly increase;</li>
 *   <li>(b) the increment scales ~linearly with payload (P, 2P, 4P);</li>
 *   <li>(c) one directed i→j transfer raises src and dst once each (one native
 *       transfer, not two mirrored halves);</li>
 *   <li>(d) when i and j mutually select, exactly one pair (two directed
 *       transfers) is planned, so mutual energy ≈ 2× a single directed transfer,
 *       not 4×.</li>
 * </ul>
 */
class FLPeerExchangeEnergyTest extends PMRelatedFoundation {

    private static final long REPO_BW = 1000L;        // bytes/tick (small ⇒ measurable duration)
    private static final long SETTLE = 5_000L;        // ticks to let PM turn-on settle
    private static final long WINDOW = 20_000L;       // fixed window ≥ max transfer duration (4P/BW=4000)
    private static final long DEVICE_START = 1_000_000_000L; // device never ticks during the window
    private static final int SIG_DIM = 8;

    @AfterEach
    void cleanup() {
        EnergyDataCollectorFL.clearAll();
        Device.allDevices.clear();
    }

    /** Recording listener for arrival/failure bookkeeping. */
    private static final class Recorder implements FLPeerExchangeEvent.ExchangeListener {
        int arrived;
        int failed;

        @Override
        public void onArrived(FLPeerExchangeEvent ev) {
            arrived++;
        }

        @Override
        public void onFailed(FLPeerExchangeEvent ev) {
            failed++;
        }
    }

    private FLGossipNode makeNode(int id, String repoName, Map<String, Integer> latency) {
        Repository repo = new Repository(1_000_000_000L, repoName, REPO_BW, REPO_BW, REPO_BW,
                latency, defaultStorageTransitions, defaultNetworkTransitions);
        try {
            repo.setState(NetworkNode.State.RUNNING);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        PhysicalMachine pm = new PhysicalMachine(4, 0.001, 8L * 1024 * 1024 * 1024, repo, 0, 0,
                defaultHostTransitions);
        RandomWalkMobilityStrategy mobility = new RandomWalkMobilityStrategy(
                new GeoLocation(47.0 + id, 19.0 + id), 0.0027, 0.0055, 10_000);
        FLEdgeDevice device = new FLEdgeDevice(
                DEVICE_START, DEVICE_START + 1000L, 1024L, 60_000L,
                mobility, new RandomDeviceStrategy(), pm,
                5.0e-6, 1, REPO_BW, 0.004, 0.0, 0.0, false);
        float[] sig = new float[SIG_DIM];
        for (int i = 0; i < SIG_DIM; i++) {
            sig[i] = 0.01f * (id + 1) * (i + 1);
        }
        return new FLGossipNode(id, device, sig, 10);
    }

    /** Fresh two-node setup with mutual repo latency entries. */
    private FLGossipNode[] freshPair() {
        Timed.resetTimed();
        EnergyDataCollectorFL.clearAll();
        Device.allDevices.clear();
        Map<String, Integer> latency = new HashMap<>();
        latency.put("repo-A", 1);
        latency.put("repo-B", 1);
        FLGossipNode a = makeNode(0, "repo-A", latency);
        FLGossipNode b = makeNode(1, "repo-B", latency);
        Timed.simulateUntil(SETTLE); // settle PM turn-on, well before DEVICE_START
        return new FLGossipNode[] { a, b };
    }

    /**
     * Raw energy delta {@code {srcΔ, dstΔ}} over the fixed {@link #WINDOW}, with
     * (optionally) one directed src→dst transfer of {@code payloadBytes} fired at
     * the window start. Because the window is fixed, idle accrual is identical
     * across calls and cancels when two calls are subtracted, isolating the
     * transfer-attributable energy (∝ bytes).
     */
    private double[] windowEnergy(boolean fireTransfer, long payloadBytes) {
        FLGossipNode[] nodes = freshPair();
        FLGossipNode src = nodes[0];
        FLGossipNode dst = nodes[1];
        double e0src = src.energyCollector().forceSample();
        double e0dst = dst.energyCollector().forceSample();

        Recorder rec = new Recorder();
        if (fireTransfer) {
            new FLPeerExchangeEvent(1L, 0, src, dst, src.signature(), src.sampleCount(),
                    payloadBytes, 1L, 0.0, true, rec);
        }
        Timed.simulateUntil(SETTLE + WINDOW);

        double e1src = src.energyCollector().forceSample();
        double e1dst = dst.energyCollector().forceSample();
        if (fireTransfer) {
            assertEquals(1, rec.arrived, "exactly one arrival for one directed transfer");
            assertEquals(0, rec.failed);
        }
        return new double[] { e1src - e0src, e1dst - e0dst };
    }

    @Test
    @DisplayName("(a)(c) one directed exchange raises BOTH endpoints above their idle baseline")
    void bothEndpointsMetered() {
        double[] idle = windowEnergy(false, 0L);
        double[] withT = windowEnergy(true, 1_000_000L);
        assertTrue(withT[0] - idle[0] > 0.0,
                "source (TX) transfer energy must be positive, got " + (withT[0] - idle[0]));
        assertTrue(withT[1] - idle[1] > 0.0,
                "destination (RX) transfer energy must be positive, got " + (withT[1] - idle[1]));
    }

    @Test
    @DisplayName("(b) transfer-attributable energy scales ~linearly with payload (P, 2P, 4P)")
    void energyScalesWithPayload() {
        long p = 1_000_000L;
        double idle = windowEnergy(false, 0L)[0];
        double eP = windowEnergy(true, p)[0] - idle;
        double e2P = windowEnergy(true, 2 * p)[0] - idle;
        double e4P = windowEnergy(true, 4 * p)[0] - idle;
        assertTrue(eP > 0 && e2P > 0 && e4P > 0,
                "transfer-only energy positive (eP=" + eP + ", e2P=" + e2P + ", e4P=" + e4P + ")");
        double r2 = e2P / eP;
        double r4 = e4P / eP;
        assertTrue(Math.abs(r2 - 2.0) < 0.30, "2P/P ratio ≈ 2, got " + r2);
        assertTrue(Math.abs(r4 - 4.0) < 0.60, "4P/P ratio ≈ 4, got " + r4);
    }

    @Test
    @DisplayName("(d) mutual selection de-dups to ONE pair: planner=2 transfers, energy ≈ 2× single")
    void mutualExchangeDeDup() {
        // Planner: i and j each select the other ⇒ exactly the two directions, once.
        Map<Integer, List<Integer>> peerSets = new HashMap<>();
        peerSets.put(0, List.of(1));
        peerSets.put(1, List.of(0));
        List<int[]> transfers = FLPeerExchangeEvent.planDirectedTransfers(peerSets);
        assertEquals(2, transfers.size(), "mutual exchange ⇒ one pair = two directed transfers, not four");

        long payload = 1_000_000L;
        double idle = windowEnergy(false, 0L)[0];
        double singleSrc = windowEnergy(true, payload)[0] - idle; // node 0 does one TX

        // Run BOTH directed transfers of the pair; measure node 0's transfer-only energy
        // (it is the TX source of [0,1] and the RX target of [1,0]).
        FLGossipNode[] nodes = freshPair();
        FLGossipNode a = nodes[0];
        FLGossipNode b = nodes[1];
        double e0 = a.energyCollector().forceSample();
        Recorder rec = new Recorder();
        for (int[] t : transfers) {
            FLGossipNode s = (t[0] == 0) ? a : b;
            FLGossipNode d = (t[1] == 0) ? a : b;
            new FLPeerExchangeEvent(1L, 0, s, d, s.signature(), s.sampleCount(), payload, 1L, 0.0, true, rec);
        }
        Timed.simulateUntil(SETTLE + WINDOW);
        double mutual = (a.energyCollector().forceSample() - e0) - idle;
        assertEquals(2, rec.arrived, "both directed transfers of the single pair arrive");

        // Node 0 participates in both directions (one TX + one RX) ⇒ ≈ 2× a single
        // directed transfer, not 4× (which double-counting would give).
        double ratio = mutual / singleSrc;
        assertTrue(ratio > 1.4 && ratio < 2.6, "mutual energy ≈ 2× single (got ratio " + ratio + ")");
    }
}
