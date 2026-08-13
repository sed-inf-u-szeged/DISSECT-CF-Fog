package hu.u_szeged.inf.fog.simulator.agent.offer;

import hu.u_szeged.inf.fog.simulator.agent.AgentApplication.Component;
import hu.u_szeged.inf.fog.simulator.agent.Capacity;
import hu.u_szeged.inf.fog.simulator.agent.ResourceAgent;

import java.util.List;

public class LocalOffer {

    public static class LocalMetrics {

        public final double balance;

        public final double utilisation;

        public final double fragmentation;

        public final double compactness;

        public final double cost;

        public final double energy;

        public final double latency;

        public final double bandwidth;

        public LocalMetrics(double balance, double utilisation, double fragmentation, double compactness, double cost, double energy, double latency, double bandwidth) {
            this.balance = balance;
            this.utilisation = utilisation;
            this.fragmentation = fragmentation;
            this.compactness = compactness;
            this.cost = cost;
            this.energy = energy;
            this.latency = latency;
            this.bandwidth = bandwidth;
        }
    }

    public static class ComponentPlacement {

        public final Component component;
        public final Capacity capacity;

        public ComponentPlacement(Component component, Capacity capacity) {
            this.component = component;
            this.capacity = capacity;
        }
    }

    public final ResourceAgent agent;

    public final List<ComponentPlacement> placements;

    public final LocalMetrics metrics;

    public final double offeredHourlyPrice;

    public LocalOffer(ResourceAgent agent, List<ComponentPlacement> placements, LocalMetrics metrics) {
        this.agent = agent;
        this.placements = placements;
        this.metrics = metrics;
        this.offeredHourlyPrice = agent.hourlyPrice;
    }
}

