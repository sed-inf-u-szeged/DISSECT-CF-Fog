package hu.u_szeged.inf.fog.simulator.agent;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.u_szeged.inf.fog.simulator.agent.AgentApplication.Component;
import hu.u_szeged.inf.fog.simulator.common.node.ComputingAppliance;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import hu.u_szeged.inf.fog.simulator.agent.offer.LocalOffer.ComponentPlacement;
import hu.u_szeged.inf.fog.simulator.common.util.SimLogger;
import org.apache.commons.lang3.tuple.Pair;

public class Capacity {

    public static class Utilisation {

        public enum State {
            RESERVED,

            ASSIGNED,

            ALLOCATED,

            RELEASED
        }

        public State state;

        public Component component;

        public double utilisedCpu;

        public long utilisedMemory;

        public long utilisedStorage;

        boolean leadResource;

        public VirtualMachine vm;

        public long initTime;

        public long endTime;

        public double actualCost;

        public ResourceAgent resourceAgent;

        public Utilisation() {
        }

        public Utilisation(Component component, State state, ResourceAgent ra) {
            this.component = component;
            this.utilisedCpu = safe(component.requirements.cpu, 0.0);
            this.utilisedMemory = safe(component.requirements.memory, 0L);
            this.utilisedStorage = safe(component.requirements.storage, 0L);
            this.state = state;
            this.resourceAgent = ra;
        }

        public void setToAllocated() {
            this.state = Utilisation.State.ALLOCATED;

            double demandShare = resourceAgent.calculateDemandShare(utilisedCpu, utilisedMemory, utilisedStorage);

            this.actualCost = resourceAgent.hourlyPrice * demandShare;
        }

        @Override
        public String toString() {
            return "Utilisation [state=" + state + ", resource=" + component.id + ", utilisedCpu=" + utilisedCpu
                    + ", utilisedMemory=" + utilisedMemory + ", utilisedStorage=" + utilisedStorage
                    + ", leadResource=" + leadResource + ", initTime=" + initTime + ", endTime=" + endTime  + ", actualCost=" + actualCost + ", vm=" + vm + "]";
        }
    }

    public double cpu;

    public long memory;

    public long storage;

    public final double totalCpu;

    public final long totalMemory;

    public final long totalStorage;

    public ComputingAppliance node;

    public List<Utilisation> utilisations;

    public Capacity(ComputingAppliance node, double cpu, long memory, long storage) {
        this.node = node;
        this.cpu = cpu;
        this.memory = memory;
        this.totalCpu = cpu;
        this.totalMemory = memory;
        this.totalStorage = storage;
        this.storage = storage;
        this.utilisations = new ArrayList<>();
    }

    public void reserveCapacity(Component component, ResourceAgent ra) {
        Utilisation utilisation = new Utilisation(component, Utilisation.State.RESERVED, ra);
        this.utilisations.add(utilisation);
        this.cpu -= safe(component.requirements.cpu, 0.0);
        this.memory -= safe(component.requirements.memory, 0L);
        this.storage -= safe(component.requirements.storage, 0L);
    }

    public void releaseReservation(Utilisation utilisation) {
        if (utilisation.state != Utilisation.State.RESERVED) {
            return;
        }

        if (!utilisations.remove(utilisation)) {
            return;
        }

        cpu += utilisation.utilisedCpu;
        memory += utilisation.utilisedMemory;
        storage += utilisation.utilisedStorage;
    }

    public void releaseAllocatedCapacityForShutdown(Component component) {
        //List<Utilisation> utilisationsToBeRemoved = new ArrayList<>();
        for (Utilisation utilisation : utilisations) {
            if (utilisation.component == component && utilisation.state.equals(Utilisation.State.ALLOCATED)) {
                this.cpu += utilisation.utilisedCpu;
                this.memory += utilisation.utilisedMemory;
                this.storage += utilisation.utilisedStorage;

                // we have to destroy the VM too, otherwise VM request could fail when trying to deploy new application
                try {
                    utilisation.vm.destroy(true);
                } catch (Exception e) {
                    SimLogger.logError("Exception occurred while destroying VM: " + e.getMessage());
                }
                utilisation.state = Utilisation.State.RELEASED;
                //utilisationsToBeRemoved.add(utilisation);
            }
        }
        //utilisations.removeAll(utilisationsToBeRemoved);
    }

    public void assignPlacement(ComponentPlacement placement, Offer offer) {

        if (placement.capacity != this) {
            throw new IllegalArgumentException(
                    "The placement belongs to a different capacity.");
        }

        for (Utilisation utilisation : utilisations) {
            if (utilisation.component == placement.component
                    && utilisation.state == Utilisation.State.RESERVED) {

                utilisation.state = Utilisation.State.ASSIGNED;

                offer.utilisations.add(
                        Pair.of(this.node, utilisation));

                return;
            }
        }

        throw new IllegalStateException(
                "No reserved utilisation was found for component: "
                        + placement.component.id);
    }

    @Override
    public String toString() {
        return "Capacity [node=" + node.name + ", cpu=" + cpu + ", totalCpu=" + totalCpu + ", memory=" + memory + ", totalMemory=" + totalMemory
                + ", storage=" + storage + ", totalStorage=" + totalStorage + "]";
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