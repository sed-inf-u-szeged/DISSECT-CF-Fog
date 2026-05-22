package hu.u_szeged.inf.fog.simulator.agent;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.AlterableResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;
import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;
import hu.u_szeged.inf.fog.simulator.agent.decision.DecisionMaker;
import hu.u_szeged.inf.fog.simulator.agent.strategy.mapping.MappingStrategy;
import hu.u_szeged.inf.fog.simulator.agent.strategy.message.MessagingStrategy;
import hu.u_szeged.inf.fog.simulator.agent.util.AgentOfferWriter;
import hu.u_szeged.inf.fog.simulator.common.node.ComputingAppliance;
import hu.u_szeged.inf.fog.simulator.common.util.ScenarioBase;
import hu.u_szeged.inf.fog.simulator.common.util.SimLogger;
import java.util.*;
import org.apache.commons.lang3.tuple.Pair;

public abstract class ResourceAgent {
    public static Map<String, ResourceAgent> allResourceAgents = new HashMap<>();

    public final String name;

    public ComputingAppliance hostNode;

    public VirtualMachine raService;

    protected double hourlyPrice;

    public Map<String, Capacity> capacities = new HashMap<>();

    public MappingStrategy agentStrategy;

    MessagingStrategy messagingStrategy;

    protected boolean isTurnedOn;

    public static int maxRebroadcast = 2;
    
    public static int failedDeployments = 0;

    protected DecisionMaker decisionMaker;
    protected int bcastMessageSize;

    public static long minBW = Long.MAX_VALUE;
    public static long maxBW = Long.MIN_VALUE;
    public static Integer minLatency = Integer.MAX_VALUE;
    public static Integer maxLatency = Integer.MIN_VALUE;
    public static double minPrice = Double.MAX_VALUE;
    public static double maxPrice = Double.MIN_VALUE;
    public static double minEnergy = Double.MAX_VALUE;
    public static double maxEnergy = Double.MIN_VALUE;

    public static long getAvgBW(ResourceAgent agent) {
        long bw = 0;
        int sum = 0;

        for (Capacity capacity : agent.capacities.values()) {
            for (int i = 0; i < capacity.node.iaas.repositories.size(); i++) {
                bw += capacity.node.iaas.repositories.get(i).getDiskbw();
                sum++;
            }

            for (int i = 0; i < capacity.node.iaas.machines.size(); i++) {
                bw += capacity.node.iaas.machines.get(i).localDisk.getDiskbw();
                sum++;
            }
        }

        return bw/sum;
    }

    public static Integer getAvgLatency(ResourceAgent agent) {
        int latency = 0;
        int sum = 0;

        for (Capacity capacity : agent.capacities.values()) {
            //nodeRepo, localRepo, but in current one: internal/external repo (name change, fixed)
            // Find a better way for the latency
            for (int i = 0; i < capacity.node.iaas.repositories.size(); i++) {
                latency += capacity.node.iaas.repositories.get(i).getLatencies().get(capacity.node.name + "-internalRepo");
                sum++;
            }

            for (int i = 0; i < capacity.node.iaas.machines.size(); i++) {
                latency += capacity.node.iaas.machines.get(i).localDisk.getLatencies().get(capacity.node.name + "-externalRepo");
                sum++;
            }
        }

        return latency/sum;
    }

    public static double getAvgEnergy(ResourceAgent agent) {
        double energy = 0;
        int sum = 0;

        for (Capacity capacity : agent.capacities.values()) {
            for (int i = 0; i < capacity.node.iaas.machines.size(); i++) {
                energy += capacity.node.iaas.machines.get(i).getCurrentPowerBehavior().getMinConsumption() + capacity.node.iaas.machines.get(i).getCurrentPowerBehavior().getConsumptionRange();
                sum++;
            }
        }

        return energy/sum;
    }

    public static double normalize(double value, double min, double max) {
        if (min == max) {
            return 0.5;
        }

        return ((value - min) / (max - min));
    }

    protected static void setMinimumsMaximums(ResourceAgent agent) {
        //BW
        if (getAvgBW(agent) < minBW) {
            minBW = getAvgBW(agent);
        }

        if (getAvgBW(agent) > maxBW) {
            maxBW = getAvgBW(agent);
        }

        //Latency
        if (getAvgLatency(agent) < minLatency) {
            minLatency = getAvgLatency(agent);
        }

        if (getAvgLatency(agent) > maxLatency) {
            maxLatency = getAvgLatency(agent);
        }

        //Energy
        if (getAvgEnergy(agent) < minEnergy) {
            minEnergy = getAvgEnergy(agent);
        }

        if (getAvgEnergy(agent) > maxEnergy) {
            maxEnergy = getAvgEnergy(agent);
        }

        //Price
        if (agent.getPrice() < minPrice) {
            minPrice = agent.getPrice();
        }

        if (agent.getPrice() > maxPrice) {
            maxPrice = agent.getPrice();
        }
    }

    public ResourceAgent(String name, double hourlyPrice, MappingStrategy agentStrategy, MessagingStrategy messagingStrategy) {
        this.name = name;
        this.hourlyPrice = hourlyPrice;
        this.agentStrategy = agentStrategy;
        this.messagingStrategy = messagingStrategy;

        if (allResourceAgents.containsKey(name)) {
            SimLogger.logError("Resource Agent with name '" + name + "' already exists");
        }
        allResourceAgents.put(name, this);
    }

    public void initResourceAgent(VirtualAppliance resourceAgentVa, AlterableResourceConstraints resourceAgentArc, Capacity...capacity) {
        if(validateAndAddCapacitiesLimit(capacity)) {
            if (!isTurnedOn) {
                isTurnedOn = true;
                List<Capacity> values = new ArrayList<>(this.capacities.values());
                this.hostNode = values.get(SeedSyncer.centralRnd.nextInt(values.size())).node;
                VirtualAppliance va = resourceAgentVa.newCopy(this.name + "-VA");
                this.hostNode.iaas.repositories.get(0).registerObject(va);
                try {
                    this.raService = this.hostNode.iaas.requestVM(va, resourceAgentArc,
                            this.hostNode.iaas.repositories.get(0), 1)[0];
                } catch (VMManager.VMManagementException e) {
                    SimLogger.logError(name + "(RA) service cannot be created: " + e);
                }
                SimLogger.logRun(name + " (RA) was assigned to: " + this.hostNode.name + " at: "
                        + Timed.getFireCount() / (double) ScenarioBase.MINUTE_IN_MILLISECONDS + " min.");
            }
        } else {
            SimLogger.logError("Resource Agent with name '" + name + "' creation has failed with the given capacities");
        }
    }

    protected boolean validateAndAddCapacitiesLimit(Capacity... newCaps) {

        Set<String> seenNodeNames = new HashSet<>();
        for (Capacity cap : newCaps) {
            if (!seenNodeNames.add(cap.node.name)) {
                return false;
            }
        }

        Map<ComputingAppliance, Double> totalCpuByNode = new HashMap<>();
        Map<ComputingAppliance, Long> totalMemoryByNode = new HashMap<>();
        Map<ComputingAppliance, Long> totalStorageByNode = new HashMap<>();

        for (ResourceAgent agent : ResourceAgent.allResourceAgents.values()) {
            for (Capacity existingCap : agent.capacities.values()) {

                ComputingAppliance node = existingCap.node;

                totalCpuByNode.merge(node, existingCap.cpu, Double::sum);
                totalMemoryByNode.merge(node, existingCap.memory, Long::sum);
                totalStorageByNode.merge(node, existingCap.storage, Long::sum);
            }
        }

        for (Capacity cap : newCaps) {
            ComputingAppliance node = cap.node;

            totalCpuByNode.merge(node, cap.cpu, Double::sum);
            totalMemoryByNode.merge(node, cap.memory, Long::sum);
            totalStorageByNode.merge(node, cap.storage, Long::sum);
        }

        for (ComputingAppliance node : totalCpuByNode.keySet()) {

            double totalCpu = totalCpuByNode.getOrDefault(node, 0.0);
            long totalMemory = totalMemoryByNode.getOrDefault(node, 0L);
            long totalStorage = totalStorageByNode.getOrDefault(node, 0L);

            double maxCpu = node.iaas.getCapacities().getRequiredCPUs();
            long maxMemory = node.iaas.getCapacities().getRequiredMemory();
            long maxStorage = node.iaas.repositories.get(0).getMaxStorageCapacity();

            if (totalCpu > maxCpu || totalMemory > maxMemory || totalStorage > maxStorage) {
                return false;
            }
        }

        for (Capacity cap : newCaps) {
            this.capacities.put(cap.node.name, cap);
        }

        return true;
    }

    public double getPrice() {
        double totalCpu = 0.0;
        double totalStorage = 0.0;
        double allocatedCpu = 0.0;
        double allocatedStorage = 0.0;

        for (final Capacity capacity : capacities.values()) {
            totalCpu += capacity.cpu;
            totalStorage += capacity.storage;

            for (final Capacity.Utilisation utilisation : capacity.utilisations) {
                if (utilisation.state == Capacity.Utilisation.State.ALLOCATED) {
                    allocatedCpu += utilisation.utilisedCpu;
                    allocatedStorage += utilisation.utilisedStorage;
                }
            }
        }

        double cpuUtilisation = 0.0;
        if (totalCpu > 0.0) {
            cpuUtilisation = allocatedCpu / totalCpu;
        }
        cpuUtilisation = Math.max(0.0, Math.min(1.0, cpuUtilisation));

        double storageUtilisation = 0.0;
        if (totalStorage > 0.0) {
            storageUtilisation = allocatedStorage / totalStorage;
        }
        storageUtilisation = Math.max(0.0, Math.min(1.0, storageUtilisation));

        final double overallUtilisation = (cpuUtilisation + storageUtilisation) / 2.0;

        final double minMultiplier = 0.6;
        final double multiplier = 1.0 - (overallUtilisation * (1.0 - minMultiplier));

        //return hourlyPrice * multiplier;
        return hourlyPrice;
    }

    public void broadcast(AgentApplication app, int bcastMessageSize, DecisionMaker decisionMaker) {
        app.broadcastCount++;
        MessageHandler.executeMessaging(messagingStrategy, this, app, bcastMessageSize, "bcast", () -> {
            deploy(app, bcastMessageSize, decisionMaker);
        });
    }

    protected abstract void deploy(AgentApplication app, int bcastMessageSize, DecisionMaker decisionMaker);

    public abstract void processAppOffer(AgentApplication app);

    protected String writeFile(AgentApplication app) {
        List<Double> reliabilityList = new ArrayList<>();
        List<Double> energyList = new ArrayList<>();
        List<Double> bandwidthList = new ArrayList<>();
        List<Double> latencyList = new ArrayList<>();
        List<Double> priceList = new ArrayList<>();

        for (Offer offer : app.offers) {
            double averageLatency = 0;
            double averageBandwidth = 0;
            double averageEnergy = 0;
            double averagePrice = 0;

            for (ResourceAgent agent : offer.agentComponentsMap.keySet()) {

                averageLatency += agent.hostNode.iaas.repositories.get(0).getLatencies().get(
                        agent.hostNode.iaas.repositories.get(0).getName());

                averageBandwidth += agent.hostNode.iaas.repositories.get(0).inbws.getPerTickProcessingPower();

                averageEnergy += agent.hostNode.iaas.machines.get(0).getCurrentPowerBehavior().getMinConsumption();

                for (AgentApplication.Component component : offer.agentComponentsMap.get(agent)) {
                    averageEnergy += agent.hostNode.iaas.machines.get(0).getCurrentPowerBehavior().getConsumptionRange()
                            * (component.requirements.cpu > 0 ? component.requirements.cpu / 100 : 1);
                    // TODO: fix this price calculation
                    averagePrice += getPrice() * (component.requirements.cpu > 0 ? component.requirements.cpu / 100 : 1);
                }
            }

            /*
            averageLatency /= offer.agentResourcesMap.keySet().size();
            averageBandwidth /= offer.agentResourcesMap.keySet().size();
            averageEnergy /= offer.agentResourcesMap.keySet().size();
            averagePrice /= offer.agentResourcesMap.keySet().size();
            */

            reliabilityList.add(1.0);

            //double epsilon = averageEnergy * 1e-10 * r.nextDouble();
            energyList.add(averageEnergy);
            bandwidthList.add(averageBandwidth);
            latencyList.add(averageLatency);
            priceList.add(averagePrice);

            /*
            System.out.println("avg. latency: " + averageLatency + " avg. bandwidth: "
                + averageBandwidth + " avg. energy: " + averageEnergy +  " avg. price: " + averagePrice);
            */
        }
        // TODO: ??
        AgentOfferWriter.QosPriority qosPriority = new AgentOfferWriter.QosPriority(app.energy, app.bandwidth, app.latency, app.price);
        AgentOfferWriter.JsonOfferData jsonData = new AgentOfferWriter.JsonOfferData(qosPriority, reliabilityList, energyList, bandwidthList, latencyList, priceList);
        return AgentOfferWriter.writeOffers(jsonData, app.name);
    }

    protected abstract void acknowledgeAndInitSwarmAgent(AgentApplication app, Offer offer, int bcastMessageSize);

    protected abstract void releaseResourcesDueToNoOffers(AgentApplication app);

    protected void freeReservedResources(final String appName, final Capacity capacity) {
        List<AgentApplication.Component> resourcesToBeRemoved = new ArrayList<>();

        for (Capacity.Utilisation util : capacity.utilisations) {
            if (util.component.id.contains(appName) && util.state.equals(Capacity.Utilisation.State.RESERVED)) {
                resourcesToBeRemoved.add(util.component);
            }
        }
        for (AgentApplication.Component component : resourcesToBeRemoved) {
            capacity.releaseCapacity(component);
        }
    }

    protected Pair<ComputingAppliance, Capacity.Utilisation> setLeadResource(List<Pair<ComputingAppliance, Capacity.Utilisation>> resources) {
        Pair<ComputingAppliance, Capacity.Utilisation> resource = null;
        double maxCpu = Integer.MIN_VALUE;

        for (Pair<ComputingAppliance, Capacity.Utilisation> pair : resources) {
            if (pair.getRight().utilisedCpu > maxCpu) {
                maxCpu = pair.getRight().utilisedCpu;
                resource = pair;
            }
        }

        resource.getRight().leadResource = true;
        return resource;
    }

    /*
    public Triple<Double, Long, Long> getAllFreeResources() {
        double totalFreeCpu = capacities.stream().mapToDouble(cap -> cap.cpu).sum();
        long totalFreeMemory = capacities.stream().mapToLong(cap -> cap.memory).sum();
        long totalFreeStorage = capacities.stream().mapToLong(cap -> cap.storage).sum();

        return Triple.of(totalFreeCpu, totalFreeMemory, totalFreeStorage);
    }
    */

    /*
    private void freeReservedResourcesExceptWinningOffer(final String appName, final Capacity capacity, final Offer winningOffer) {
        List<Resource> resourcesToBeRemoved = new ArrayList<>();

        Set<Resource> winningResources = new HashSet<>();
        for (Map.Entry<ResourceAgent, Set<Resource>> entry : winningOffer.agentComponentsMap.entrySet()) {
            if (entry.getKey().capacities.contains(capacity)) {
                winningResources.addAll(entry.getValue());
            }
        }

        for (Utilisation util : capacity.utilisations) {
            if (util.resource.name.contains(appName)
                    && util.state.equals(Utilisation.State.RESERVED)
                    && !winningResources.contains(util.resource)) {
                resourcesToBeRemoved.add(util.resource);
            }
        }

        for (Resource resource : resourcesToBeRemoved) {
            capacity.releaseCapacity(resource);
        }
    }
    */
}