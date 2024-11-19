package hu.u_szeged.inf.fog.simulator.agent;

import hu.u_szeged.inf.fog.simulator.agent.AgentApplication.Resource;
import hu.u_szeged.inf.fog.simulator.node.ComputingAppliance;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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
            if (utilisation.resource == resource && utilisation.state.equals(Utilisation.State.RESERVED)) {
                this.cpu += utilisation.resource.cpu == null ? 0 : Double.parseDouble(utilisation.resource.cpu);
                this.memory += utilisation.resource.memory == null ? 0 : Long.parseLong(utilisation.resource.memory);
                this.storage += utilisation.resource.size == null ? 0 : Long.parseLong(utilisation.resource.size);
                utilisationsToBeRemoved.add(utilisation);
            }
        }
        utilisations.removeAll(utilisationsToBeRemoved);
    }
    
    public void assignCapacity(Set<Resource> set) {
        for (Resource resource : set) {
            for (Utilisation util : utilisations) {
                if (util.resource == resource) {
                    util.state = Utilisation.State.ASSIGNED;
                }
            }
        }
    }
    
    public void allocateCapacity() {
       // to be implemented 
    }

    @Override
    public String toString() {
        return "Capacity [node=" + node.name + ", cpu=" + cpu + ", memory=" + memory + ", storage=" + storage + "]";
    }
}
