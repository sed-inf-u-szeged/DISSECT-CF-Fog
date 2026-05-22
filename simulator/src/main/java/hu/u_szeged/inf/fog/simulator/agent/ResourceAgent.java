package hu.u_szeged.inf.fog.simulator.agent;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.AlterableResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;
import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;
import hu.u_szeged.inf.fog.simulator.agent.decision.DecisionMaker;
import hu.u_szeged.inf.fog.simulator.agent.Capacity.Utilisation;
import hu.u_szeged.inf.fog.simulator.agent.agentstrategy.AgentStrategy;
import hu.u_szeged.inf.fog.simulator.node.ComputingAppliance;
import hu.u_szeged.inf.fog.simulator.util.SimLogger;
import hu.u_szeged.inf.fog.simulator.util.agent.AgentOfferWriter;
import hu.u_szeged.inf.fog.simulator.util.agent.AgentOfferWriter.JsonOfferData;
import hu.u_szeged.inf.fog.simulator.util.agent.AgentOfferWriter.QosPriority;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class ResourceAgent {
    public String name;

    public ComputingAppliance hostNode;

    public VirtualMachine service;

    public double hourlyPrice;

    public List<Capacity> capacities;

    public AgentStrategy agentStrategy;

    public static ArrayList<ResourceAgent> resourceAgents = new ArrayList<>();

    public int reBroadcastCounter;

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

        for (Capacity capacity : agent.capacities) {
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

        for (Capacity capacity : agent.capacities) {
            for (int i = 0; i < capacity.node.iaas.repositories.size(); i++) {
                latency += capacity.node.iaas.repositories.get(i).getLatencies().get(capacity.node.name + "-nodeRepo");
                sum++;
            }

            for (int i = 0; i < capacity.node.iaas.machines.size(); i++) {
                latency += capacity.node.iaas.machines.get(i).localDisk.getLatencies().get(capacity.node.name + "-localRepo");
                sum++;
            }
        }

        return latency/sum;
    }

    public static double getAvgEnergy(ResourceAgent agent) {
        double energy = 0;
        int sum = 0;

        for (Capacity capacity : agent.capacities) {
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
        if (agent.hourlyPrice < minPrice) {
            minPrice = agent.hourlyPrice;
        }

        if (agent.hourlyPrice > maxPrice) {
            maxPrice = agent.hourlyPrice;
        }
    }


    public ResourceAgent(String name, double hourlyPrice, VirtualAppliance resourceAgentVa,
                         AlterableResourceConstraints resourceAgentArc, AgentStrategy agentStrategy, Capacity... capacities) {
        this.capacities = new ArrayList<>();
        validateAndAddCapacitiesLimit(Arrays.asList(capacities));
        this.name = name;
        this.hourlyPrice = hourlyPrice;
        ResourceAgent.resourceAgents.add(this);
        this.agentStrategy = agentStrategy;
        this.initResourceAgent(resourceAgentVa, resourceAgentArc);
    }

    public ResourceAgent(String name, double hourlyPrice, VirtualAppliance resourceAgentVa,
                         AlterableResourceConstraints resourceAgentArc, AgentStrategy agentStrategy) {
        this.capacities = new ArrayList<>();
        this.name = name;
        this.hourlyPrice = hourlyPrice;
        ResourceAgent.resourceAgents.add(this);
        this.agentStrategy = agentStrategy;
        //this.initResourceAgent(resourceAgentVa, resourceAgentArc);
    }

    public void registerCapacity(Capacity capacity) {
        this.capacities.add(capacity);
    }

    public void initResourceAgent(VirtualAppliance resourceAgentVa, AlterableResourceConstraints resourceAgentArc) {
        try {
            this.hostNode = this.capacities.get(SeedSyncer.centralRnd.nextInt(this.capacities.size())).node;
            VirtualAppliance va = resourceAgentVa.newCopy(this.name + "-VA");
            this.hostNode.iaas.repositories.get(0).registerObject(va);
            VirtualMachine vm = this.hostNode.iaas.requestVM(va, resourceAgentArc,
                    this.hostNode.iaas.repositories.get(0), 1)[0];
            this.service = vm;

            SimLogger.logRun(name + " (RA) was assigned to: " + this.hostNode.name + " at: "
                    + Timed.getFireCount() / 1000.0 / 60.0 + " min.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void broadcast(AgentApplication app, int bcastMessageSize, DecisionMaker decisionMaker) {
        MessageHandler.executeMessaging(this, app, bcastMessageSize, "bcast", () -> {
            deploy(app, bcastMessageSize, decisionMaker);
        });
    }

    protected abstract void deploy(AgentApplication app, int bcastMessageSize, DecisionMaker decisionMaker);

    public abstract void processAppOffer(AgentApplication app);

    protected abstract void releaseResourcesAndNotifyNoOffers(AgentApplication app);

    protected void writeFile(AgentApplication app) {
        List<Double> reliabilityList = new ArrayList<Double>();
        List<Double> energyList = new ArrayList<Double>();
        List<Double> bandwidthList = new ArrayList<Double>();
        List<Double> latencyList = new ArrayList<Double>();
        List<Double> priceList = new ArrayList<Double>();

        for (Offer offer : app.offers) {
            double averageLatency = 0;
            double averageBandwidth = 0;
            double averageEnergy = 0;
            double averagePrice = 0;

            for (ResourceAgent agent : offer.agentResourcesMap.keySet()) {

                averageLatency += agent.hostNode.iaas.repositories.get(0).getLatencies().get(
                        agent.hostNode.iaas.repositories.get(0).getName());

                averageBandwidth += agent.hostNode.iaas.repositories.get(0).inbws.getPerTickProcessingPower();

                averageEnergy += agent.hostNode.iaas.machines.get(0).getCurrentPowerBehavior().getMinConsumption();

                for (AgentApplication.Resource resource : offer.agentResourcesMap.get(agent)) {
                    averageEnergy += agent.hostNode.iaas.machines.get(0).getCurrentPowerBehavior().getConsumptionRange()
                            * (resource.getTotalReqCpu() / 100);
                    averagePrice += agent.hourlyPrice * resource.getTotalReqCpu();
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

            QosPriority qosPriority = new QosPriority(app.energyPriority, app.bandwidthPriority, app.latencyPriority, app.pricePriority);
            JsonOfferData jsonData = new JsonOfferData(qosPriority, reliabilityList, energyList, bandwidthList, latencyList, priceList);
            AgentOfferWriter.writeOffers(jsonData, app.name);
        }
    }

    protected abstract void acknowledgeAndInitSwarmAgent(AgentApplication app, Offer offer, int bcastMessageSize);

    protected void freeReservedResources(final String appName, final Capacity capacity) {
        List<AgentApplication.Resource> resourcesToBeRemoved = new ArrayList<>();

        for (Utilisation util : capacity.utilisations) {
            if (util.resource.name.contains(appName) && util.state.equals(Utilisation.State.RESERVED)) {
                resourcesToBeRemoved.add(util.resource);
            }
        }
        for (AgentApplication.Resource resource : resourcesToBeRemoved) {
            capacity.releaseCapacity(resource);
        }
    }


    protected Pair<ComputingAppliance, Utilisation> findLeadResource(List<Pair<ComputingAppliance, Utilisation>> utilisations) {
        Pair<ComputingAppliance, Utilisation> leadResource = null;

        double maxCpu = Integer.MIN_VALUE;

        for (Pair<ComputingAppliance, Utilisation> pair : utilisations) {
            if (pair.getRight().utilisedCpu > maxCpu) {
                maxCpu = pair.getRight().utilisedCpu;
                leadResource = pair;
            }
        }
        leadResource.getRight().type = "LR";

        return leadResource;
    }

    protected void validateAndAddCapacitiesLimit(List<Capacity> capacities) {
        double totalCpuRequested = 0.0;
        long totalMemoryRequested = 0L;
        long totalStorageRequested = 0L;
        ComputingAppliance commonNode = null;

        /* TODO: RA can offer resources from different providers
        for (Capacity cap : capacities) {
            totalCpuRequested += cap.cpu;
            totalMemoryRequested += cap.memory;
            totalStorageRequested += cap.storage;

            if (commonNode == null) {
                commonNode = cap.node;
            } else if (!commonNode.equals(cap.node)) {
                throw new IllegalArgumentException(
                        "All capacities for a single ResourceAgent must belong to the same ComputingAppliance node");
            }

        }

        double maxCpu = commonNode.iaas.getCapacities().getRequiredCPUs();
        long maxMemory = commonNode.iaas.getCapacities().getRequiredMemory();
        long maxStorage = commonNode.getAvailableStorage();

        if (totalCpuRequested > maxCpu || totalMemoryRequested > maxMemory || totalStorageRequested > maxStorage) {
            throw new IllegalArgumentException("Requested resources exceed available capacities of " + commonNode.name);
        }
        */

        this.capacities.addAll(capacities);
    }
}
