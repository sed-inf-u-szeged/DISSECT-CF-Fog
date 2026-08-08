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

        public LocalMetrics(double balance, double utilisation, double fragmentation, double compactness) {
            this.balance = balance;
            this.utilisation = utilisation;
            this.fragmentation = fragmentation;
            this.compactness = compactness;
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

    public LocalOffer(ResourceAgent agent, List<ComponentPlacement> placements, LocalMetrics metrics) {
        this.agent = agent;
        this.placements = placements;
        this.metrics = metrics;
    }
}

