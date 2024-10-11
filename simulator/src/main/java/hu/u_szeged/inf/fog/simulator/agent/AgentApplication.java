package hu.u_szeged.inf.fog.simulator.agent;

import java.util.List;

public class AgentApplication {
    
    static class Component {
        
        public String name;
        public String image; 
        public String type;
        
        public String toString() {
            return "Component [name=" + name + ", image=" + image + ", type=" + type + "]";
        }
    }
    
    static class Resource {
        
        public String name;
        public String cpu; 
        public String memory; 
        public String instances;
        public String provider;
        public String location;
        public String size;

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
    
    protected int bcastCounter;

    public String toString() {
        return "AgentApplication [name=" + name + ", components=" + components + ", resources=" + resources
                + ", mapping=" + mapping + "]";
    }
}