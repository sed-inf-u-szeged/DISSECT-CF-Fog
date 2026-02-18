package hu.u_szeged.inf.fog.simulator.agent;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.u_szeged.inf.fog.simulator.agent.AgentApplication.Component;
import hu.u_szeged.inf.fog.simulator.common.node.ComputingAppliance;
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

        public Component component;

        public double utilisedCpu;

        public long utilisedMemory;

        public long utilisedStorage;

        boolean leadResource;

        public VirtualMachine vm;

        public long initTime;

        public long endTime; // TODO: for shutdown purposes

        private Utilisation(Component component, State state) {
            this.component = component;
            this.utilisedCpu = safe(component.requirements.cpu, 0.0);
            this.utilisedMemory = safe(component.requirements.memory, 0L);
            this.utilisedStorage = safe(component.requirements.storage, 0L);
            this.state = state;
        }

        public void setToAllocated() {
            this.state = Utilisation.State.ALLOCATED;
        }

        @Override
        public String toString() {
            return "Utilisation [state=" + state + ", resource=" + component.id + ", utilisedCpu=" + utilisedCpu
                    + ", utilisedMemory=" + utilisedMemory + ", utilisedStorage=" + utilisedStorage
                    + ", leadResource=" + leadResource + ", initTime=" + initTime + ", vm=" + vm + "]";
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

    public void reserveCapacity(Component component) {
        Utilisation utilisation = new Utilisation(component, Utilisation.State.RESERVED);
        this.utilisations.add(utilisation);
        this.cpu -= safe(component.requirements.cpu, 0.0);
        this.memory -= safe(component.requirements.memory, 0L);
        this.storage -= safe(component.requirements.storage, 0L);
    }

    public void releaseCapacity(Component component) {
        List<Utilisation> utilisationsToBeRemoved = new ArrayList<>();
        for (Utilisation utilisation : utilisations) {
            if (utilisation.component == component && utilisation.state.equals(Utilisation.State.RESERVED)) {
                this.cpu += utilisation.utilisedCpu;
                this.memory += utilisation.utilisedMemory;
                this.storage += utilisation.utilisedStorage;
                utilisationsToBeRemoved.add(utilisation);
            }
        }
        utilisations.removeAll(utilisationsToBeRemoved);
    }

    public void assignCapacity(Set<Component> components, Offer offer) {
        for (Component component : components) {
            for (Utilisation util : utilisations) {
                if (util.component == component) {
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