package hu.u_szeged.inf.fog.simulator.agent;

import hu.mta.sztaki.lpds.cloud.simulator.DeferredEvent;
import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.AlterableResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;
import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;
import hu.u_szeged.inf.fog.simulator.agent.AgentApplication.Component;
import hu.u_szeged.inf.fog.simulator.agent.Capacity.Utilisation;
import hu.u_szeged.inf.fog.simulator.agent.demo.Config;
import hu.u_szeged.inf.fog.simulator.agent.strategy.mapping.MappingStrategy;
import hu.u_szeged.inf.fog.simulator.agent.strategy.message.MessagingStrategy;
import hu.u_szeged.inf.fog.simulator.agent.util.AgentOfferWriter;
import hu.u_szeged.inf.fog.simulator.agent.util.AgentOfferWriter.JsonOfferData;
import hu.u_szeged.inf.fog.simulator.agent.util.AgentOfferWriter.QosPriority;
import hu.u_szeged.inf.fog.simulator.common.node.ComputingAppliance;
import hu.u_szeged.inf.fog.simulator.common.util.ScenarioBase;
import hu.u_szeged.inf.fog.simulator.common.util.SimLogger;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.commons.lang3.SystemUtils;
import org.apache.commons.lang3.tuple.Pair;

public class ResourceAgent {

    public static Map<String, ResourceAgent> allResourceAgents = new HashMap<>();
    
    public final String name;

    public ComputingAppliance hostNode;

    public VirtualMachine raService;

    private double hourlyPrice;

    public Map<String, Capacity> capacities = new HashMap<>();

    MappingStrategy agentStrategy;
    
    MessagingStrategy messagingStrategy;
    
    private boolean isTurnedOn;
    
    public static int maxRebroadcast = 2;

    public static int failedDeployments = 0;

    //public int servedAsGatewayCount = 0;
    //public int winningOfferSelectionCount = 0;
    //public Map<ResourceAgent, Double> staticScores = new HashMap<>();
    //public Map<ResourceAgent, Double> reputationScores = new HashMap<>();
    
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

    public void initResourceAgent(VirtualAppliance resourceAgentVa, AlterableResourceConstraints resourceAgentArc,  Capacity...capacity) {
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

    private boolean validateAndAddCapacitiesLimit(Capacity... newCaps) {

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

    public void broadcast(AgentApplication app, int bcastMessageSize) {
        app.broadcastCount++;
        MessageHandler.executeMessaging(messagingStrategy, this, app, bcastMessageSize, "bcast", () -> {
            deploy(app, bcastMessageSize);
        });
    }
    
    private void deploy(AgentApplication app, int bcastMessageSize) {
        this.generateOffers(app);
        
        if (!app.offers.isEmpty()) {
            if (Config.APP_TYPE.get("rankingMethod").equals("random")) {
                app.winningOffer = SeedSyncer.centralRnd.nextInt(app.offers.size());
            } else {
                app.winningOffer = callRankingScript(app);
            }

            /*
            Offer winningOffer = ;
            for (ResourceAgent agent : ResourceAgent.resourceAgents) {
                for (Capacity capacity : agent.capacities) {
                    freeReservedResourcesExceptWinningOffer(app.name, capacity, winningOffer);
                }
            }
            */

            acknowledgeAndInitSwarmAgent(app, app.offers.get(app.winningOffer), bcastMessageSize);
        } else {
            releaseResourcesDueToNoOffers(app);
            new DeferredEvent(10 * 1_000L) {

                @Override
                protected void eventAction() {
                    if (app.broadcastCount <= maxRebroadcast) {
                        SimLogger.logRun("Rebroadcast " + (app.broadcastCount) + " for " + app.name + " at: "
                                + Timed.getFireCount() / (double) ScenarioBase.MINUTE_IN_MILLISECONDS + " min.");
                        broadcast(app, bcastMessageSize);

                        /*
                        ResourceAgent.resourceAgents
                                .forEach(agent -> ((SimulatedAnnealingStrategy)agent.agentStrategy).switchCoolingTactic());
                        */
                    } else {
                        acknowledgeAndInitSwarmAgent(app, new Offer(new HashMap<>(), -1), bcastMessageSize);
                        app.deploymentTime = -1;
                    }
                }
            };
        }
    }

    public double getPrice() {
        double totalCpu = 0.0;
        double totalStorage = 0.0;
        double allocatedCpu = 0.0;
        double allocatedStorage = 0.0;

        for (final Capacity capacity : capacities.values()) {
            totalCpu += capacity.cpu;
            totalStorage += capacity.storage;

            for (final Utilisation utilisation : capacity.utilisations) {
                if (utilisation.state == Utilisation.State.ALLOCATED) {
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

        return hourlyPrice * multiplier;
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
    
    private void generateOffers(AgentApplication app) {
        List<Pair<ResourceAgent, Component>> agentResourcePairs = new ArrayList<>();
        
        app.offerGeneratingAgents.add(this);
        for (ResourceAgent agent : app.offerGeneratingAgents) {
            agentResourcePairs.addAll(agent.agentStrategy.canFulfill(agent, app.components));
        }

        //agentResourcePairs.forEach(p ->
        //        System.out.println(
        //                "Agent: " + p.getLeft().name +
        //                        " | Resource: " + p.getRight().id
        //        )
        //);

        generateUniqueOfferCombinations(agentResourcePairs, app);

        // System.out.println("Offers for: " + app.name);
        // for (Offer o : app.offers) {
        //     System.out.println(o);
        // }
    }

    private void generateUniqueOfferCombinations(List<Pair<ResourceAgent, Component>> pairs, AgentApplication app) {
        Set<Set<Pair<ResourceAgent, Component>>> uniqueCombinations = new LinkedHashSet<>();

        generateCombinations(pairs, app.components.size(), uniqueCombinations,
                new LinkedHashSet<>(), new LinkedHashSet<>(), new LinkedHashSet<>());

        for (Set<Pair<ResourceAgent, Component>> combination : uniqueCombinations) {
            Map<ResourceAgent, Set<Component>> agentResourcesMap = new HashMap<>();

            for (Pair<ResourceAgent, Component> pair : combination) {
                ResourceAgent agent = pair.getLeft();
                Component component = pair.getRight();

                agentResourcesMap.putIfAbsent(agent, new LinkedHashSet<>());
                agentResourcesMap.get(agent).add(component);
            }

            app.offers.add(new Offer(agentResourcesMap, app.offers.size()));
        }
    }

    private void generateCombinations(List<Pair<ResourceAgent, Component>> pairs, int componentCount,
                                      Set<Set<Pair<ResourceAgent, Component>>> uniqueCombinations,
                                      Set<Pair<ResourceAgent, Component>> currentCombination,
                                      Set<Component> includedComponents,
                                      Set<String> seenStates) {

        if (includedComponents.size() == componentCount) {
            uniqueCombinations.add(new LinkedHashSet<>(currentCombination));
            return;
        }

        String stateKey = includedComponents.stream()
                .map(r -> r.id)
                .sorted()
                .collect(Collectors.joining(","));
        if (!seenStates.add(stateKey)) {
            return;
        }

        for (Pair<ResourceAgent, Component> pair : pairs) {
            if (!currentCombination.contains(pair) && !includedComponents.contains(pair.getRight())) {
                currentCombination.add(pair);
                includedComponents.add(pair.getRight());

                generateCombinations(pairs, componentCount, uniqueCombinations, currentCombination, includedComponents, seenStates);

                currentCombination.remove(pair);
                includedComponents.remove(pair.getRight());
            }
        }
    }

    private int callRankingScript(AgentApplication app) {
        String inputfile = this.writeFile(app);

        try {
            String command;
            ProcessBuilder processBuilder;

            if (SystemUtils.IS_OS_LINUX) {
                command = "python3 " + Config.APP_TYPE.get("rankingScript")
                        + " --method_name " + Config.APP_TYPE.get("rankingMethod")
                        + " --offers_loc \"" + inputfile + "\"";

                processBuilder = new ProcessBuilder("bash", "-c", command);
            } else {
                SimLogger.logError("The ranking script cannot be called due to an unsupported operating system.");
                throw new UnsupportedOperationException();
                /*
                command = "cd /d \"" + AgentNoiseSimDemo.RANKING_SCRIPT + "\""
                        + " && conda activate swarmchestrate && python call_ranking_func.py --method_name " 
                        + AgentNoiseSimDemo.RANKING_METHOD
                        + " --offers_loc \"" + inputfile + "\"";
                 */
            }

            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
            process.waitFor();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                StringBuilder arrayContent = new StringBuilder();

                while ((line = reader.readLine()) != null) {
                    // System.out.println(line);
                    arrayContent.append(line).append(" ");
                }

                String content = arrayContent.toString();

                content = content.replaceAll("[^0-9\\s]", "");

                List<Integer> numberList = Arrays.stream(content.split("\\s+"))
                        .filter(token -> !token.isEmpty())
                        .map(Integer::parseInt)
                        .toList();

                int firstNumber = numberList.get(0);
                //int lastNumber = numberList.get(numberList.size() - 1);

                SimLogger.logRun(app.offers.size() + " offers were ranked for "
                        + app.name + " at: " + Timed.getFireCount() / (double) ScenarioBase.MINUTE_IN_MILLISECONDS
                        + " min., the winning offer index is: " + firstNumber);

                return firstNumber;
                //return lastNumber;
            }
        } catch (IOException | InterruptedException e) {
            e.getStackTrace();
        }

        return -1;
    }

    private String writeFile(AgentApplication app) {
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

                for (Component component : offer.agentComponentsMap.get(agent)) {
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
        QosPriority qosPriority = new QosPriority(app.energy, app.bandwidth, app.latency, app.price);
        JsonOfferData jsonData = new JsonOfferData(qosPriority, reliabilityList, energyList, bandwidthList, latencyList, priceList);
        return AgentOfferWriter.writeOffers(jsonData, app.name);
    }

    private void acknowledgeAndInitSwarmAgent(AgentApplication app, Offer offer, int bcastMessageSize) {
        
        /*
        if (messagingStrategy instanceof GuidedSearchMessagingStrategy) {
            ((GuidedSearchMessagingStrategy) messagingStrategy).setWinningOffer(offer);
        }
        */

        MessageHandler.executeMessaging(messagingStrategy, this, app, bcastMessageSize, "ack", () -> {
            SimLogger.logRun("Messaging are done for " + app.name
                    + " at: " + Timed.getFireCount() / (double) ScenarioBase.MINUTE_IN_MILLISECONDS + " min.");

            if (offer.id == -1) {
                SimLogger.logRun(app.name + "'s requirements cannot be fulfilled!");
                releaseResourcesDueToNoOffers(app);

                failedDeployments++;
                List<Integer> submissionCounts = (List<Integer>) Config.NOISE_CLASS_CONFIGURATION.get("submissionDelay");
                if (failedDeployments == submissionCounts.size()) {
                    SimLogger.logError("All deployment attempts have failed.");
                }
                return;
            }
            for (ResourceAgent agent : ResourceAgent.allResourceAgents.values()) {
                for (Capacity capacity : agent.capacities.values()) {
                    if (offer.agentComponentsMap.containsKey(agent)) {
                        capacity.assignCapacity(offer.agentComponentsMap.get(agent), offer);
                    }

                    freeReservedResources(app.name, capacity);
                }
            }
            Pair<ComputingAppliance, Utilisation> leadResource = setLeadResource(offer.utilisations);
            new Deployment(leadResource, offer, app);
        });
    }
    
    private void releaseResourcesDueToNoOffers(AgentApplication app) {
        for (ResourceAgent agent : ResourceAgent.allResourceAgents.values()) {
            for (Capacity capacity : agent.capacities.values()) {
                freeReservedResources(app.name, capacity);
            }
        }
    }

    private void freeReservedResources(final String appName, final Capacity capacity) {
        List<Component> resourcesToBeRemoved = new ArrayList<>();

        for (Utilisation util : capacity.utilisations) {
            if (util.component.id.contains(appName) && util.state.equals(Utilisation.State.RESERVED)) {
                resourcesToBeRemoved.add(util.component);
            }
        }
        for (Component component : resourcesToBeRemoved) {
            capacity.releaseCapacity(component);
        }
    }

    private Pair<ComputingAppliance, Utilisation> setLeadResource(List<Pair<ComputingAppliance, Utilisation>> resources) {
        Pair<ComputingAppliance, Utilisation> resource = null;
        double maxCpu = Integer.MIN_VALUE;

        for (Pair<ComputingAppliance, Utilisation> pair : resources) {
            if (pair.getRight().utilisedCpu > maxCpu) {
                maxCpu = pair.getRight().utilisedCpu;
                resource = pair;
            }
        }

        resource.getRight().leadResource = true;
        return resource;
    }
}