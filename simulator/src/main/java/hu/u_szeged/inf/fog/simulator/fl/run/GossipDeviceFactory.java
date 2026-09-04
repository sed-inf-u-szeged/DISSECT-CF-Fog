package hu.u_szeged.inf.fog.simulator.fl.run;

import hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.PowerState;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.util.PowerTransitionGenerator;
import hu.u_szeged.inf.fog.simulator.fl.FLEdgeDevice;
import hu.u_szeged.inf.fog.simulator.fl.gossip.FLGossipNode;
import hu.u_szeged.inf.fog.simulator.fl.topology.FLTopology;
import hu.u_szeged.inf.fog.simulator.iot.mobility.GeoLocation;
import hu.u_szeged.inf.fog.simulator.iot.mobility.RandomWalkMobilityStrategy;
import hu.u_szeged.inf.fog.simulator.iot.strategy.RandomDeviceStrategy;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the {@link FLGossipNode} set for a topology's Pass-3 replay: one
 * {@link PhysicalMachine} + {@link Repository} per node, wired with a mutual
 * latency map over all node repositories so device↔device peer-exchange
 * transfers resolve, and an {@link FLEdgeDevice} whose own lifecycle is parked
 * far in the future (so it never ticks during the bounded gossip simulation).
 * Each {@link FLGossipNode} registers its PM with the energy collector (R1).
 *
 * <p>Repository bandwidth is set high so the native energy-metering transfer
 * completes within the analytic round (keeping the round barrier quiescent for
 * the per-round energy snapshot — see {@code FLGossipOrchestrator}).</p>
 */
public final class GossipDeviceFactory {

    /** Device start parked beyond any bounded gossip run, so devices never tick. */
    public static final long DEVICE_START = 1_000_000_000L;
    /** Fast repo bandwidth so the native metering transfer fits within a round. */
    public static final long REPO_BW = 100_000_000L;

    private GossipDeviceFactory() {
    }

    /**
     * Builds one gossip node per topology vertex (PM + repository + device +
     * energy meter).
     *
     * @param topo the topology (defines node count and cluster labels).
     * @param initialSignatures per-node initial model signatures (n × d).
     * @param sampleCounts per-node local sample counts.
     * @return the gossip nodes, index = node id.
     */
    public static List<FLGossipNode> build(FLTopology topo, float[][] initialSignatures,
                                           int[] sampleCounts) {
        int n = topo.size();
        Map<String, Integer> latency = new HashMap<>();
        for (int i = 0; i < n; i++) {
            latency.put(repoName(i), 1);
        }
        int[] labels = topo.clusterLabels();
        List<FLGossipNode> nodes = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            nodes.add(buildNode(i, labels[i], latency, initialSignatures[i], sampleCounts[i]));
        }
        return nodes;
    }

    private static String repoName(int i) {
        return "gnode" + i;
    }

    private static FLGossipNode buildNode(int id, int cluster, Map<String, Integer> latency,
                                          float[] sig0, int sampleCount) {
        try {
            EnumMap<PowerTransitionGenerator.PowerStateKind, Map<String, PowerState>> transitions =
                    PowerTransitionGenerator.generateTransitions(2.5, 10, 1.0, 3, 3);
            Map<String, PowerState> diskT = transitions.get(PowerTransitionGenerator.PowerStateKind.storage);
            Map<String, PowerState> netT = transitions.get(PowerTransitionGenerator.PowerStateKind.network);
            Map<String, PowerState> cpuT = transitions.get(PowerTransitionGenerator.PowerStateKind.host);

            Repository repo = new Repository(256L * 1024 * 1024 * 1024, repoName(id),
                    REPO_BW, REPO_BW, REPO_BW, latency, diskT, netT);

            // Near-edge (cluster 0) vs far-edge tiers — indicative compute profiles.
            int cores = cluster == 0 ? 8 : 4;
            double mips = cluster == 0 ? 0.004 : 0.001;
            long ram = (cluster == 0 ? 16L : 8L) * 1024 * 1024 * 1024;
            PhysicalMachine pm = new PhysicalMachine(cores, mips, ram, repo, 0, 0, cpuT);

            RandomWalkMobilityStrategy mobility = new RandomWalkMobilityStrategy(
                    new GeoLocation(47.0 + id * 0.01, 19.0 + id * 0.01), 0.0027, 0.0055, 10_000);
            FLEdgeDevice device = new FLEdgeDevice(
                    DEVICE_START, DEVICE_START + 1000L, 1024L, 60_000L,
                    mobility, new RandomDeviceStrategy(), pm,
                    5.0e-6, 1, REPO_BW, cores * mips, 0.0, 0.0, false);

            return new FLGossipNode(id, device, sig0, Math.max(1, sampleCount));
        } catch (Exception e) {
            throw new RuntimeException("failed to build gossip node " + id, e);
        }
    }
}
