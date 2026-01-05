package hu.u_szeged.inf.fog.simulator.agent;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.u_szeged.inf.fog.simulator.agent.AgentApplication.Resource;
import hu.u_szeged.inf.fog.simulator.node.ComputingAppliance;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.apache.commons.lang3.tuple.Pair;

public class Capacity {

    public static class Utilisation {

        enum State {
            RESERVED,

            ASSIGNED,

            ALLOCATED
        }

        State state;

        public Resource resource;

        public double utilisedCpu;

        public long utilisedMemory;

        public long utilisedStorage;

        String type;

        public VirtualMachine vm;

        public long initTime;

        private Utilisation(Resource resource, State state) {
            this.resource = resource;
            this.utilisedCpu = safe(resource.cpu, 0.0);
            this.utilisedMemory = safe(resource.memory, 0L);
            this.utilisedStorage = safe(resource.size, 0L);
            this.state = state;
        }

        public void setToAllocated() {
            this.state = Utilisation.State.ALLOCATED;
        }

        @Override
        public String toString() {
            return "Utilisation [state=" + state + ", resource=" + resource.name + ", utilisedCpu=" + utilisedCpu
                    + ", utilisedMemory=" + utilisedMemory + ", utilisedStorage=" + utilisedStorage
                    + ", type=" + type + ", initTime=" + initTime + ", vm=" + vm + "]";
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

    public void reserveCapacity(Resource resource) {
        Utilisation utilisation = new Utilisation(resource, Utilisation.State.RESERVED);
        this.utilisations.add(utilisation);
        this.cpu -= safe(resource.cpu, 0.0);
        this.memory -= safe(resource.memory, 0L);
        this.storage -= safe(resource.size, 0L);
    }

    public void releaseCapacity(Resource resource) {
        List<Utilisation> utilisationsToBeRemoved = new ArrayList<>();
        for (Utilisation utilisation : utilisations) {
            if (utilisation.resource == resource && utilisation.state.equals(Utilisation.State.RESERVED)) {
                this.cpu += utilisation.utilisedCpu;
                this.memory += utilisation.utilisedMemory;
                this.storage += utilisation.utilisedStorage;
                utilisationsToBeRemoved.add(utilisation);
            }
        }
        utilisations.removeAll(utilisationsToBeRemoved);
    }

    public void assignCapacity(Set<Resource> resources, Offer offer) {
        for (Resource resource : resources) {
            for (Utilisation util : utilisations) {
                if (util.resource == resource) {
                    util.state = Utilisation.State.ASSIGNED;
                    offer.utilisations.add(Pair.of(this.node, util));
                }
            }
        }
    }

    @Override
    public String toString() {
        return "Capacity [node=" + node.name + ", cpu=" + cpu + ", memory=" + memory + ", storage=" + storage + "]";
    }

    /**
     * Returns the given value if it is not null; otherwise, returns the default value.
     *
     * @param <T>           The type of the number (e.g., Integer, Double, Long).
     * @param value         The value to check.
     * @param defaultValue  The default value to return if {@code value} is null.
     * @return The value if it is not null; otherwise, the default value.
     */
    private static <T extends Number> T safe(T value, T defaultValue) {
        return value != null ? value : defaultValue;
    }

}