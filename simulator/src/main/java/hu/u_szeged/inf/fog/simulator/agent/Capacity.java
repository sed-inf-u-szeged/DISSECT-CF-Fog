package hu.u_szeged.inf.fog.simulator.agent;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.u_szeged.inf.fog.simulator.agent.AgentApplication.Component;
import hu.u_szeged.inf.fog.simulator.agent.strategy.mapping.MappingStrategy;
import hu.u_szeged.inf.fog.simulator.agent.strategy.mapping.offer.LocalOffer;
import hu.u_szeged.inf.fog.simulator.common.node.ComputingAppliance;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import hu.u_szeged.inf.fog.simulator.agent.strategy.mapping.offer.LocalOffer.ComponentPlacement;
import hu.u_szeged.inf.fog.simulator.common.util.SimLogger;
import org.apache.commons.lang3.tuple.Pair;

public class Capacity {

    public static class Utilisation {

        public enum State {
            RESERVED,

            ASSIGNED,

            ALLOCATED,

            TERMINATED
        }

        public State state;

        public double utilisedCpu;

        public long utilisedMemory;

        public long utilisedStorage;

        boolean leadResource;

        public VirtualMachine vm;

        public long initTime;

        public long endTime;

        public double actualCost;

        public ResourceAgent resourceAgent;

        public Component component;

        public boolean envelopeReservation;

        public List<LocalOffer> coveredOffers;

        public static Utilisation createNonAtomicReservation(LocalOffer localOffer, Component component, ResourceAgent resourceAgent) {
            Utilisation utilisation = new Utilisation();

            utilisation.state = State.RESERVED;
            utilisation.envelopeReservation = false;
            utilisation.coveredOffers = List.of(localOffer);
            utilisation.component = component;
            utilisation.utilisedCpu = MappingStrategy.requiredCpu(component);
            utilisation.utilisedMemory = MappingStrategy.requiredMemory(component);
            utilisation.utilisedStorage = MappingStrategy.requiredStorage(component);
            utilisation.resourceAgent = resourceAgent;

            double demandShare = resourceAgent.calculateDemandShare(utilisation.utilisedCpu, utilisation.utilisedMemory, utilisation.utilisedStorage);

            utilisation.actualCost = localOffer.offeredHourlyPrice * demandShare;

            return utilisation;
        }

        public static Utilisation createAtomicReservation(
                List<LocalOffer> coveredOffers,
                double reservedCpu,
                long reservedMemory,
                long reservedStorage,
                ResourceAgent resourceAgent) {

            if (coveredOffers.isEmpty()) {
                throw new IllegalArgumentException(
                        "Atomic reservation must cover at least one LocalOffer.");
            }

            Utilisation utilisation = new Utilisation();

            utilisation.state = State.RESERVED;
            utilisation.envelopeReservation  = true;
            utilisation.coveredOffers = List.copyOf(coveredOffers);
            utilisation.component = null;
            utilisation.utilisedCpu = reservedCpu;
            utilisation.utilisedMemory = reservedMemory;
            utilisation.utilisedStorage = reservedStorage;
            utilisation.resourceAgent = resourceAgent;
            utilisation.actualCost = 0.0;

            return utilisation;
        }

        public void setToAllocated() {
            this.state = Utilisation.State.ALLOCATED;
        }

        @Override
        public String toString() {
            String coveredOfferComponents = coveredOffers == null
                            ? "[]" : coveredOffers.stream()
                            .map(localOffer -> localOffer.placements.stream()
                                    .map(placement -> placement.component.id)
                                    .sorted()
                                    .collect(Collectors.joining(",","{","}")))
                    .collect(Collectors.joining(",","[","]"));

            return "Utilisation [state=" + state
                    + ", envelopeReservation=" + envelopeReservation
                    + ", component="
                    + (component == null ? null : component.id)
                    + ", coveredOffers=" + coveredOfferComponents
                    + ", utilisedCpu=" + utilisedCpu
                    + ", utilisedMemory=" + utilisedMemory
                    + ", utilisedStorage=" + utilisedStorage
                    + ", leadResource=" + leadResource
                    + ", initTime=" + initTime
                    + ", endTime=" + endTime
                    + ", actualCost=" + actualCost
                    + ", vm=" + vm
                    + "]";
        }
    }

    public double cpu;

    public long memory;

    public long storage;

    public double totalCpu;

    public long totalMemory;

    public long totalStorage;

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

    public void reserveCapacity(Component component, ResourceAgent ra, LocalOffer localOffer) {
        Utilisation utilisation = Utilisation.createNonAtomicReservation(localOffer, component, ra);
        this.utilisations.add(utilisation);

        this.cpu -= utilisation.utilisedCpu;
        this.memory -= utilisation.utilisedMemory;
        this.storage -= utilisation.utilisedStorage;
    }

    public void reserveAtomicOffers(List<LocalOffer> coveredOffers, ResourceAgent resourceAgent, double reservedCpu, long reservedMemory, long reservedStorage) {
        if (coveredOffers.isEmpty()) {
            throw new IllegalArgumentException( "Atomic reservation must cover at least one LocalOffer.");
        }

        if (reservedCpu < 0.0 || reservedMemory < 0L || reservedStorage < 0L) {
            throw new IllegalArgumentException("Reserved resources cannot be negative.");
        }

        if (reservedCpu > this.cpu || reservedMemory > this.memory || reservedStorage > this.storage) {
            throw new IllegalStateException( "Atomic reservation exceeds the currently available capacity.");
        }

        Utilisation utilisation = Utilisation.createAtomicReservation(coveredOffers, reservedCpu, reservedMemory, reservedStorage, resourceAgent);

        this.utilisations.add(utilisation);

        this.cpu -= reservedCpu;
        this.memory -= reservedMemory;
        this.storage -= reservedStorage;
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
        for (Utilisation utilisation : utilisations) {
            if (utilisation.component != component || utilisation.state != Utilisation.State.ALLOCATED) {
                continue;
            }

            if (utilisation.vm == null) {
                SimLogger.logError("Allocated utilisation has no VM for component: " + component.id);
            }

            try {
                utilisation.vm.destroy(true);
            } catch (Exception exception) {
                SimLogger.logError("Exception occurred while destroying VM: " + exception.getMessage());
                return;
            }

            cpu += utilisation.utilisedCpu;
            memory += utilisation.utilisedMemory;
            storage += utilisation.utilisedStorage;
            utilisation.state = Utilisation.State.TERMINATED;
        }
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
}