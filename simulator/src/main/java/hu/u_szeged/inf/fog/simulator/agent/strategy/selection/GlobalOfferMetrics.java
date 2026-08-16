package hu.u_szeged.inf.fog.simulator.agent.strategy.selection;

public class GlobalOfferMetrics {

    public final int providerCount;
    public final double cost;
    public final double energy;
    public final double latency;
    public final double bandwidth;

    public GlobalOfferMetrics(int providerCount, double cost, double energy, double latency, double bandwidth) {
        this.providerCount = providerCount;
        this.cost = cost;
        this.energy = energy;
        this.latency = latency;
        this.bandwidth = bandwidth;
    }
}
