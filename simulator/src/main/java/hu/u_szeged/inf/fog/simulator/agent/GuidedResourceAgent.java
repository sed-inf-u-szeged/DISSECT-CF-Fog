package hu.u_szeged.inf.fog.simulator.agent;

import hu.mta.sztaki.lpds.cloud.simulator.DeferredEvent;
import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.AlterableResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;
import hu.u_szeged.inf.fog.simulator.agent.decision.DecisionMaker;
import hu.u_szeged.inf.fog.simulator.agent.agentstrategy.AgentStrategy;
import hu.u_szeged.inf.fog.simulator.node.ComputingAppliance;
import hu.u_szeged.inf.fog.simulator.util.SimLogger;
import hu.u_szeged.inf.fog.simulator.util.agent.AgentOfferWriter;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import hu.u_szeged.inf.fog.simulator.agent.AgentApplication.Resource;
import hu.u_szeged.inf.fog.simulator.agent.Capacity.Utilisation;
import hu.u_szeged.inf.fog.simulator.agent.agentstrategy.SimulatedAnnealing;
import hu.u_szeged.inf.fog.simulator.agent.messagestrategy.MessagingStrategy;
import hu.u_szeged.inf.fog.simulator.agent.messagestrategy.GuidedSearchMessagingStrategy;

import java.util.*;
import java.util.stream.Collectors;

public class GuidedResourceAgent extends ResourceAgent {
    public static ArrayList<GuidedResourceAgent> GuidedResourceAgents = new ArrayList<>();

    public int servedAsGatewayCount = 0;
    public int winningOfferSelectionCount = 0;
    public Map<GuidedResourceAgent, Double> staticScores = new HashMap<>();
    public Map<GuidedResourceAgent, Double> reputationScores = new HashMap<>();
    private final MessagingStrategy messagingStrategy;
    public static int MAX_REBROADCAST_COUNT = Math.max(5, Math.min(AgentApplication.agentApplications.size() / 2, 10));

    public GuidedResourceAgent(String name, double hourlyPrice, VirtualAppliance resourceAgentVa, AlterableResourceConstraints resourceAgentArc, AgentStrategy agentStrategy, MessagingStrategy messagingStrategy, Capacity... capacities) {
        super(name, hourlyPrice, resourceAgentVa, resourceAgentArc, agentStrategy, capacities);
        this.messagingStrategy = messagingStrategy;
        GuidedResourceAgents.add(this);
    }

    public GuidedResourceAgent(String name, double hourlyPrice, VirtualAppliance resourceAgentVa, AlterableResourceConstraints resourceAgentArc, AgentStrategy agentStrategy, MessagingStrategy messagingStrategy) {
        super(name, hourlyPrice, resourceAgentVa, resourceAgentArc, agentStrategy);
        this.messagingStrategy = messagingStrategy;
        GuidedResourceAgents.add(this);
    }

    @Override
    public void registerCapacity(Capacity capacity) {
        validateAndAddCapacitiesLimit(Arrays.asList(capacity));
    }


    //TODO: broadcast needs a null for the third parameter (decision maker), need a better solution.
    // Submission calls broadcast on the parent (ResourceAgent), so parent has to have all parameters
    @Override
    public void broadcast(AgentApplication app, int bcastMessageSize, DecisionMaker decisionMaker) {
        app.broadcastCount++;
        GuidedMessageHandler.executeMessaging(messagingStrategy, this, app, bcastMessageSize, "bcast", () -> {
            deploy(app, bcastMessageSize, null);
        });
    }

    public Triple<Double, Long, Long> getAllFreeResources() {
        double totalFreeCpu = capacities.stream().mapToDouble(cap -> cap.cpu).sum();
        long totalFreeMemory = capacities.stream().mapToLong(cap -> cap.memory).sum();
        long totalFreeStorage = capacities.stream().mapToLong(cap -> cap.storage).sum();

        return Triple.of(totalFreeCpu, totalFreeMemory, totalFreeStorage);
    }

    public double getPrice() {
        double total = 0, used = 0;

        for (final Capacity cap : capacities) {
            total += cap.cpu;
            for (final Capacity.Utilisation u : cap.utilisations) {
                if (u.state != null) {
                    total += u.utilisedCpu;
                    used += u.utilisedCpu;
                }
            }
        }

        double utilization = used / total;
        double multiplier = 1;

        if (utilization > 0.5) {
            multiplier = 1.1 + (utilization - 0.5);
        }

        return hourlyPrice * multiplier;
    }

    private void freeReservedResourcesExceptWinningOffer(final String appName, final Capacity capacity, final Offer winningOffer) {
        List<Resource> resourcesToBeRemoved = new ArrayList<>();

        Set<Resource> winningResources = new HashSet<>();
        for (Map.Entry<ResourceAgent, Set<Resource>> entry : winningOffer.agentResourcesMap.entrySet()) {
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

    private void generateOffers(AgentApplication app) {
        List<Pair<ResourceAgent, Resource>> agentResourcePairs = new ArrayList<>();
        app.networkingAgents.add(this);
        for (ResourceAgent agent : app.networkingAgents) {
            agentResourcePairs.addAll(agent.agentStrategy.canFulfill(agent, app.resources));
        }

        generateUniqueOfferCombinations(agentResourcePairs, app);

        // TODO: only for debugging, needs to be deleted
        // System.out.println("Offers for: " + app.name);
        for (Offer o : app.offers) {
            // System.out.println(o);
        }
    }

    private void generateUniqueOfferCombinations(List<Pair<ResourceAgent, Resource>> pairs, AgentApplication app) {
        Set<Set<Pair<ResourceAgent, Resource>>> uniqueCombinations = new LinkedHashSet<>();

        generateCombinations(pairs, app.resources.size(), uniqueCombinations,
                new LinkedHashSet<>(), new LinkedHashSet<>(), new LinkedHashSet<>());

        for (Set<Pair<ResourceAgent, Resource>> combination : uniqueCombinations) {
            Map<ResourceAgent, Set<Resource>> agentResourcesMap = new HashMap<>();

            for (Pair<ResourceAgent, Resource> pair : combination) {
                ResourceAgent agent = pair.getLeft();
                Resource resource = pair.getRight();

                agentResourcesMap.putIfAbsent(agent, new LinkedHashSet<>());
                agentResourcesMap.get(agent).add(resource);
            }

            app.offers.add(new Offer(agentResourcesMap, app.offers.size()));
        }
    }

    private void generateCombinations(List<Pair<ResourceAgent, Resource>> pairs, int resourceCount,
                                      Set<Set<Pair<ResourceAgent, Resource>>> uniqueCombinations,
                                      Set<Pair<ResourceAgent, Resource>> currentCombination,
                                      Set<Resource> includedResources,
                                      Set<String> seenStates) {

        if (includedResources.size() == resourceCount) {
            uniqueCombinations.add(new LinkedHashSet<>(currentCombination));
            return;
        }

        String stateKey = includedResources.stream()
                .map(r -> r.name)
                .sorted()
                .collect(Collectors.joining(","));
        if (!seenStates.add(stateKey)) {
            return;
        }

        for (Pair<ResourceAgent, Resource> pair : pairs) {
            if (!currentCombination.contains(pair) && !includedResources.contains(pair.getRight())) {
                currentCombination.add(pair);
                includedResources.add(pair.getRight());

                generateCombinations(pairs, resourceCount, uniqueCombinations, currentCombination, includedResources, seenStates);

                currentCombination.remove(pair);
                includedResources.remove(pair.getRight());
            }
        }
    }

    @Override
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
                    averagePrice += getPrice() * resource.getTotalReqCpu();
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

            AgentOfferWriter.QosPriority qosPriority = new AgentOfferWriter.QosPriority(app.energyPriority, app.bandwidthPriority, app.latencyPriority, app.pricePriority);
            AgentOfferWriter.JsonOfferData jsonData = new AgentOfferWriter.JsonOfferData(qosPriority, reliabilityList, energyList, bandwidthList, latencyList, priceList);
            AgentOfferWriter.writeOffers(jsonData, app.name);
        }
    }

    @Override
    protected void deploy(AgentApplication app, int bcastMessageSize, DecisionMaker decisionMaker) {
        this.generateOffers(app);
        if (!app.offers.isEmpty()) {
            this.writeFile(app); // TODO: this takes time..
            app.winningOffer = 0;
            Offer winningOffer = app.offers.get(app.winningOffer);
            for (ResourceAgent agent : ResourceAgent.resourceAgents) {
                for (Capacity capacity : agent.capacities) {
                    freeReservedResourcesExceptWinningOffer(app.name, capacity, winningOffer);
                }
            }

            acknowledgeAndInitSwarmAgent(app, app.offers.get(app.winningOffer), bcastMessageSize);
        } else {
            new DeferredEvent(1000 * 10) {
                @Override
                protected void eventAction() {
                    if (app.broadcastCount - 1 <= MAX_REBROADCAST_COUNT) {
                        broadcast(app, bcastMessageSize, null);
                        SimLogger.logRun("Rebroadcast " + (app.broadcastCount - 1) + " for " + app.name);
                        ResourceAgent.resourceAgents
                                .forEach(agent -> ((SimulatedAnnealing)agent.agentStrategy).switchCoolingTactic());
                    }
                }
            };

            acknowledgeAndInitSwarmAgent(app, new Offer(new HashMap<>(), -1), bcastMessageSize);
            app.deploymentTime = -1;
        }
    }

    //TODO: turn this into an interface that the others implement?
    @Override
    public void processAppOffer(AgentApplication app) {

    }

    @Override
    protected void releaseResourcesAndNotifyNoOffers(AgentApplication app) {
        SimLogger.logRun(app.name + "'s requirements cannot be fulfilled!");

        for (GuidedResourceAgent agent : GuidedResourceAgent.GuidedResourceAgents) {
            for (Capacity capacity : agent.capacities) {
                freeReservedResources(app.name, capacity);
            }
        }
    }

    @Override
    protected void acknowledgeAndInitSwarmAgent(AgentApplication app, Offer offer, int bcastMessageSize) {
        if (messagingStrategy instanceof GuidedSearchMessagingStrategy) {
            ((GuidedSearchMessagingStrategy) messagingStrategy).setWinningOffer(offer);
        }

        GuidedMessageHandler.executeMessaging(messagingStrategy, this, app, bcastMessageSize, "ack", () -> {
            SimLogger.logRun("All acknowledge messages received for " + app.name
                    + " at: " + Timed.getFireCount() / 1000.0 / 60.0 + " min.");

            if (offer.id == -1) {
                releaseResourcesAndNotifyNoOffers(app);
                return;
            }

            for (ResourceAgent agent : ResourceAgent.resourceAgents) {
                for (Capacity capacity : agent.capacities) {
                    if (offer.agentResourcesMap.containsKey(agent)) {
                        capacity.assignCapacity(offer.agentResourcesMap.get(agent), offer);
                    }

                    freeReservedResources(app.name, capacity);
                }
            }
            Pair<ComputingAppliance, Utilisation> leadResource = findLeadResource(offer.utilisations);
            new Deployment(leadResource, offer, app);
        });
    }

    @Override
    protected void validateAndAddCapacitiesLimit(List<Capacity> capacities) {
        Map<ComputingAppliance, List<Capacity>> capacitiesByNode = new HashMap<>();

        for (ResourceAgent agent : ResourceAgent.resourceAgents) {
            for (Capacity existingCap : agent.capacities) {
                capacitiesByNode.computeIfAbsent(existingCap.node, k -> new ArrayList<>()).add(existingCap);
            }
        }

        for (Capacity cap : capacities) {
            capacitiesByNode.computeIfAbsent(cap.node, k -> new ArrayList<>()).add(cap);
        }

        for (Map.Entry<ComputingAppliance, List<Capacity>> entry : capacitiesByNode.entrySet()) {
            ComputingAppliance node = entry.getKey();
            List<Capacity> nodeCaps = entry.getValue();

            double totalCpuRequested = 0.0;
            long totalMemoryRequested = 0L;
            long totalStorageRequested = 0L;

            for (Capacity cap : nodeCaps) {
                totalCpuRequested += cap.cpu;
                totalMemoryRequested += cap.memory;
                totalStorageRequested += cap.storage;
            }

            double maxCpu = node.iaas.getCapacities().getRequiredCPUs();
            long maxMemory = node.iaas.getCapacities().getRequiredMemory();
            long maxStorage = node.iaas.repositories.stream()
                    .mapToLong(repo -> repo.getMaxStorageCapacity())
                    .sum();

            if (totalCpuRequested > maxCpu || totalMemoryRequested > maxMemory || totalStorageRequested > maxStorage) {
                throw new IllegalArgumentException(
                        String.format("Requested resources exceed available capacities of %s ", node.name)
                );
            }
        }
    }
}