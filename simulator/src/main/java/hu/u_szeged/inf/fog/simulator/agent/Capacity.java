package hu.u_szeged.inf.fog.simulator.agent;

import hu.u_szeged.inf.fog.simulator.agent.AgentApplication.Resource;
import hu.u_szeged.inf.fog.simulator.node.ComputingAppliance;
import java.util.ArrayList;
import java.util.List;

public class Capacity {
   
    public static class Utilisation {
        
        enum State {              
            RESERVED,
            
            ASSIGNED,
            
            ALLOCATED
        }
        
        State state;
        
        Resource resource;
        
        double utilisedCpu;
        
        long utilisedMemory;
        
        long utilisedStorage;
        
        private Utilisation(Resource resource, double cpu, long memory, long storage, State state) {
            this.resource = resource;
            this.utilisedCpu = cpu;
            this.utilisedMemory = memory;
            this.utilisedStorage = storage;
            this.state = state;
        }

        @Override
        public String toString() {
            return "Utilisation [state=" + state + ", resource=" + resource.name + ", utilisedCpu=" + utilisedCpu
                    + ", utilisedMemory=" + utilisedMemory + ", utilisedStorage=" + utilisedStorage + "]";
        }
    }
    
    public double cpu;
    
    public long memory;
    
    public long storage;
    
    public ComputingAppliance node;
    
    public List<Utilisation> utilisations;
    
    
    public Capacity(ComputingAppliance node, double cpu, long memory, long storage) {
        this.node = node;
        this.cpu = cpu;
        this.memory = memory;
        this.storage = storage;
        this.utilisations = new ArrayList<>();
    }
    
    public void reserveCapacity(Resource resource, double cpu, long memory, long storage) {
        Utilisation utilision = new Utilisation(resource, cpu, memory, storage, Utilisation.State.RESERVED);
        this.utilisations.add(utilision);
        this.cpu -= cpu;
        this.memory -= memory;
        this.storage -= storage;
    }
    
    public void releaseCapacity(Resource resource) {
        List<Utilisation> utilisationsToBeRemoved = new ArrayList<>();
        for (Utilisation utilisation : utilisations) {
            if (utilisation.resource == resource) {
                this.cpu += Double.parseDouble(resource.cpu);
                this.memory += Long.parseLong(resource.memory);
                this.storage += resource.size == null ? 0 : Long.parseLong(resource.size);
                utilisationsToBeRemoved.add(utilisation);
            }
        }
        utilisations.removeAll(utilisationsToBeRemoved);
    }
    
    public void assignCapacity() {
        // to be implemented
    }
    
    public void allocateCapacity() {
       // to be implemented 
    }

    @Override
    public String toString() {
        return "Capacity [node=" + node.name + ", cpu=" + cpu + ", memory=" + memory + ", storage=" + storage + "]";
    }
}
