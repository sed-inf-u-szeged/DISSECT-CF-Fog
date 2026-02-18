package hu.u_szeged.inf.fog.simulator.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * An application consists of a set of components and global optimization
 * priorities. The class is mainly a data model intended to be populated from
 * a JSON application description.
 */
public class AgentApplication {

    public String name; 

    public String type;

    public double energy;
    
    public double price;
    
    public double latency;
    
    public double bandwidth;
        
    public List<Component> components;
    
    public double deploymentTime;

    public long terminationTime;

    public long submissionTime;

    public int broadcastCount;
    
    public Set<ResourceAgent> offerGeneratingAgents;
    
    public int activeBroadcastingMessages;
    
    public List<Offer> offers = new ArrayList<>();
    
    public int winningOffer;

    /**
     * Represents a single component of an application.
     */
    public static class Component {
        
        public String id;
        
        public ComponentRequirements requirements;
        
        public ComponentProperties properties;

        @Override
        public String toString() {
            return "Component [id=" + id + ", requirements=" + requirements + ", properties=" + properties + "]";
        }
    }

    /**
     * Defines resource requirements of a component.
     */
    public static class ComponentRequirements {
        
        public Double cpu;
        
        public Long memory;
        
        public Long storage;
        
        public String location;
        
        public String provider;
        
        public Boolean edge;

        @Override
        public String toString() {
            return "ComponentRequirements [cpu=" + cpu + ", memory=" + memory + ", storage=" + storage + ", location="
                    + location + ", provider=" + provider + ", edge=" + edge + "]";
        }
    }

    /**
     * Defines application-specific properties of a component.
     */
    public static class ComponentProperties {
        
        public String kind;

        public Long image;
        
        public Boolean inside;
        
        public Boolean sun;

        @Override
        public String toString() {
            return "ComponentProperties [kind=" + kind + ", image=" + image + ", inside=" + inside + ", sun=" + sun
                    + "]";
        }
    }

    /**
     * Prefixes component ids with the application name to ensure uniqueness.
     */
    public void reName() {
        for (Component component : components) {
            component.id = name + "-" + component.properties.kind + "-" + component.id;
        }
    }

    @Override
    public String toString() {
        return "AgentApplication [name=" + name + ", energy=" + energy + ", price=" + price + ", latency=" + latency
                + ", bandwidth=" + bandwidth + ", components=" + components + "]";
    }
}