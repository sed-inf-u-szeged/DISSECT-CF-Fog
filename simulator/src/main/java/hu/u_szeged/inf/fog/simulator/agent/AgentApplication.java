package hu.u_szeged.inf.fog.simulator.agent;

import java.util.ArrayList;
import java.util.List;

import hu.u_szeged.inf.fog.simulator.agent.AgentApplication.Mapping;

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
        public String cpu; 
        public String memory; 
        public String instances;
        public String provider;
        public String location;
        public String size;
        
        public double getTotalReqCpu() {
            if (cpu != null) {
                return Double.parseDouble(cpu) * (instances == null ? 1 : Double.parseDouble(instances));
            }
            return 1;
        }

        public String toString() {
            return "Resource [name=" + name + ", cpu=" + cpu + ", memory=" + memory + ", instances=" + instances
                    + ", provider=" + provider + ", location=" + location + ", size=" + size + "]";
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
        
    protected int bcastCounter;

    public AgentApplication() {
        this.offers = new ArrayList<>();
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
    
    public String getComponentForResource(String resource) {
        for (Mapping m : this.mapping) {
            if (m.resource.equals(resource)) {
                return m.component;
            }
        }
        return null;
    }
}