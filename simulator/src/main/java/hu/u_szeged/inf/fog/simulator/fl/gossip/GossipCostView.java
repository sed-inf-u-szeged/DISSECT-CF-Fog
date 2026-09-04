package hu.u_szeged.inf.fog.simulator.fl.gossip;

import hu.u_szeged.inf.fog.simulator.fl.selection.CostView;
import hu.u_szeged.inf.fog.simulator.fl.topology.FLTopology;
import hu.u_szeged.inf.fog.simulator.fl.topology.FLTopology.Edge;

/**
 * Assembles the four peer-selection cost terms (§8.2) for a deciding node
 * {@code i} at a given round, from the live simulator state:
 * <ul>
 *   <li>{@code L_ij} — the edge latency from {@link FLTopology};</li>
 *   <li>{@code C_j} — peer load from the {@link LoadProfile} (Pass-1 schedule);</li>
 *   <li>{@code D̂_ij} — cached selection-time divergence, the ℓ₂ distance in
 *       signature space between {@code i}'s current signature and the cached
 *       signature it last saw from {@code j} ({@link FLGossipNode} cache);</li>
 *   <li>{@code B_ij} — transfer cost {@code payloadBytes / bandwidth_ij}.</li>
 * </ul>
 * and the topology degree of {@code j} for the degree-based selector.
 */
public final class GossipCostView implements CostView {

    private final FLTopology topology;
    private final FLGossipNode self;
    private final LoadProfile loadProfile;
    private final int round;
    private final long payloadBytes;

    /** Assembles the view for {@code self} at {@code round} ({@code loadProfile} may be null). */
    public GossipCostView(FLTopology topology, FLGossipNode self, LoadProfile loadProfile,
                          int round, long payloadBytes) {
        this.topology = topology;
        this.self = self;
        this.loadProfile = loadProfile;
        this.round = round;
        this.payloadBytes = payloadBytes;
    }

    @Override
    public double latency(int neighbour) {
        Edge e = topology.edge(self.nodeId(), neighbour);
        return e == null ? Double.MAX_VALUE : e.latencyTicks;
    }

    @Override
    public double load(int neighbour) {
        return loadProfile == null ? 0.0 : loadProfile.load(neighbour, round);
    }

    @Override
    public double divergence(int neighbour) {
        float[] cached = self.cachedSignature(neighbour);
        if (cached == null) {
            return 0.0; // not present; hasDivergence() returns false so this is ignored
        }
        return FLGossipNode.l2(self.signature(), cached);
    }

    @Override
    public boolean hasDivergence(int neighbour) {
        return self.hasCached(neighbour);
    }

    @Override
    public double bandwidthCost(int neighbour) {
        Edge e = topology.edge(self.nodeId(), neighbour);
        if (e == null) {
            return Double.MAX_VALUE;
        }
        return (double) payloadBytes / (double) e.bandwidthBytesPerTick;
    }

    @Override
    public int degree(int neighbour) {
        return topology.degree(neighbour);
    }
}
