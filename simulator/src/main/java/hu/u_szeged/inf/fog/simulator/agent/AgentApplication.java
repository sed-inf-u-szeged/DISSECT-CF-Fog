package hu.u_szeged.inf.fog.simulator.agent;

import java.util.ArrayList;
import java.util.Collections;
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
    
    public static class Resource {
        
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
    
    public static List<Resource> getSortedResourcesByCpuThenSize(List<Resource> originalResources) {
        List<Resource> sortedResources = new ArrayList<>(originalResources);

        Collections.sort(sortedResources, (r1, r2) -> {
            if (r1.cpu != null && r2.cpu != null) {
                double cpu1 = Double.parseDouble(r1.cpu);
                double cpu2 = Double.parseDouble(r2.cpu);
                if (r1.instances != null) {
                    cpu1 *= Double.parseDouble(r1.instances);
                }
                if (r2.instances != null) {
                    cpu2 *= Double.parseDouble(r2.instances);
                }
                return Double.compare(cpu2, cpu1);
            } else if (r1.cpu == null && r2.cpu == null) {
                double size1 = Double.parseDouble(r1.size);
                double size2 = Double.parseDouble(r2.size);
                return Double.compare(size2, size1);
            }
            return (r1.cpu == null) ? 1 : -1;
        });

        // Visszatérünk a rendezett listával
        return sortedResources;
    }
}