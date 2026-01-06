package hu.u_szeged.inf.fog.simulator.agent;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.*;

public class AgentApplication {
    
    static class Component {
        
        public String name;
        public String image; 
        public String type;
        
        public String toString() {
            return "Component [name=" + name + ", image=" + image + ", type=" + type + "]";
        }
    }
    
    public static class Resource {
        
        public String name;
        public Double cpu;
        public Long memory;
        public Integer instances;
        public String provider;
        public String location;
        public Long size;
        public Boolean edge;
        
        public double getTotalReqCpu() {
            if (cpu != null) {
                return cpu * (instances == null ? 1 : instances);
            }
            return 1;
        }

        public String toString() {
            return "Resource [name=" + name + ", cpu=" + cpu + ", memory=" + memory + ", instances=" + instances
                    + ", provider=" + provider + ", location=" + location + ", size=" + size + ", edge=" + edge + "]";
        } 
    }

    static class Mapping {
        
        public String component;
        public String resource;

        public String toString() {
            return "Mapping [component=" + component + ", resource=" + resource + "]";
        }
    }
    
    public String name;
    public List<Component> components;
    public List<Resource> resources;
    public List<Mapping> mapping;

    public List<Offer> offers;
    
    public int winningOffer;

    protected int agentsNotifiedCounter;
    
    public double deploymentTime;
    
    public double energyPriority;
    
    public double pricePriority;
    
    public double latencyPriority;
    
    public double bandwidthPriority;
    
    public HashMap<String, Number> configuration;
        
    public static List<AgentApplication> agentApplications = new ArrayList<>();

    @JsonIgnore
    public Set<GuidedResourceAgent> networkingAgents;
    @JsonIgnore
    public int broadcastCount = 0;

    public AgentApplication() {
        this.offers = new ArrayList<>();
        agentApplications.add(this);

        if (GuidedResourceAgent.GuidedResourceAgents != null) {
            networkingAgents = new HashSet<>(GuidedResourceAgent.GuidedResourceAgents);
        }
    }
        
    public String toString() {
        return "AgentApplication [name=" + name + ", components=" + components + ", resources=" + resources
                + ", mapping=" + mapping + "]";
    }
    
    public void reName() {
        for (Component c : this.components) {
            c.name = this.name + "-" + c.name;
        }
        for (Resource r : this.resources) {
            r.name = this.name + "-" + r.name;
        }
        for (Mapping m : this.mapping) {
            m.resource = this.name + "-" + m.resource;
            m.component = this.name + "-" + m.component;
        }
    }
    
    public String getComponentName(String resource) {
        for (Mapping m : this.mapping) {
            if (m.resource.equals(resource)) {
                return m.component;
            }
        }
        return null;
    }
    
    public Component getComponent(String name) {
        for (Component component : this.components) {
            if (component.name.equals(name)) {
                return component;
            }
        }
        return null;
    }

    public void normalizePriorities() {
        double sum = bandwidthPriority + energyPriority + pricePriority + latencyPriority;

        if (sum != 1.0 && sum != 0.0) {
            bandwidthPriority /= sum;
            energyPriority /= sum;
            pricePriority /= sum;
            latencyPriority /= sum;
        }

        if (sum == 0.0) {
            bandwidthPriority = 0.25;
            energyPriority = 0.25;
            pricePriority = 0.25;
            latencyPriority = 0.25;
        }
    }
}