package hu.u_szeged.inf.fog.simulator.agent;

import hu.mta.sztaki.lpds.cloud.simulator.DeferredEvent;
import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.AlterableResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;
import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;
import hu.u_szeged.inf.fog.simulator.agent.AgentApplication.Resource;
import hu.u_szeged.inf.fog.simulator.agent.Capacity.Utilisation;
import hu.u_szeged.inf.fog.simulator.agent.strategy.AgentStrategy;
import hu.u_szeged.inf.fog.simulator.agent.strategy.SimulatedAnnealing;
import hu.u_szeged.inf.fog.simulator.demo.ScenarioBase;
import hu.u_szeged.inf.fog.simulator.node.ComputingAppliance;
import hu.u_szeged.inf.fog.simulator.util.SimLogger;
import hu.u_szeged.inf.fog.simulator.util.agent.AgentApplicationReader;
import hu.u_szeged.inf.fog.simulator.util.agent.AgentOfferWriter;
import hu.u_szeged.inf.fog.simulator.util.agent.AgentOfferWriter.JsonOfferData;
import hu.u_szeged.inf.fog.simulator.util.agent.AgentOfferWriter.QosPriority;
import org.apache.commons.lang3.SystemUtils;
import org.apache.commons.lang3.tuple.Pair;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.stream.Collectors;

public class ResourceAgent {

    public static String rankingMethodName;

    public static String rankingScriptDir;

    public String name;

    public ComputingAppliance hostNode;

    VirtualMachine service;

    private final double hourlyPrice;

    public List<Capacity> capacities;

    AgentStrategy agentStrategy;

    public static ArrayList<ResourceAgent> resourceAgents = new ArrayList<>();

    public int reBroadcastCounter;

    int callcounter;

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

    public void broadcast(AgentApplication app, int bcastMessageSize) {
        MessageHandler.executeMessaging(this, app, bcastMessageSize, "bcast", () -> {
            deploy(app, bcastMessageSize);
        });
    }

    public double getPrice() {
        double total = 0, used = 0;

        for (final Capacity cap : capacities) {
            total += cap.cpu;
            for (final Utilisation u : cap.utilisations) {
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

    private void deploy(AgentApplication app, int bcastMessageSize) {
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
                    if (reBroadcastCounter < AgentApplicationReader.appCount * 2) {
                        broadcast(app, bcastMessageSize);
                        // TODO: this var is handled at RA level, not at app level (what if RA has more than one app)
                        reBroadcastCounter++;
                        SimLogger.logRun("Rebroadcast " + reBroadcastCounter + " for " + app.name);
                        if (reBroadcastCounter > 1 && agentStrategy instanceof SimulatedAnnealing) {
                            ((SimulatedAnnealing) agentStrategy).switchToRandomCoolingSchedule();
                        }
                    }
                }
            };

            acknowledgeAndInitSwarmAgent(app, new Offer(new HashMap<>(), -1), bcastMessageSize);
            app.deploymentTime = -1;
        }
    }

    private void releaseResourcesAndNotifyNoOffers(AgentApplication app) {
        SimLogger.logRun(app.name + "'s requirements cannot be fulfilled!");

        for (ResourceAgent agent : ResourceAgent.resourceAgents) {
            for (Capacity capacity : agent.capacities) {
                freeReservedResources(app.name, capacity);
            }
        }
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

        for (ResourceAgent agent : ResourceAgent.resourceAgents) {
            agentResourcePairs.addAll(agent.agentStrategy.canFulfill(agent, app.resources));
        }

        generateUniqueOfferCombinations(agentResourcePairs, app);
        
        /* TODO: only for debugging, needs to be deleted
        System.out.println(app.name);
        for (Offer o : app.offers) {
            System.out.println(o);
        }
        */
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

    private int callRankingScript(AgentApplication app) {
        String inputfile = ScenarioBase.resultDirectory + File.separator + app.name + "-offers.json";
        try {
            String command;
            ProcessBuilder processBuilder;

            // TODO: revise these commands
            if (SystemUtils.IS_OS_WINDOWS) {
                command = "cd /d " + rankingScriptDir
                        + " && conda activate swarmchestrate && python call_ranking_func.py --method_name " + rankingMethodName
                        + " --offers_loc " + inputfile;
                processBuilder = new ProcessBuilder("cmd.exe", "/c", command);
            } else if (SystemUtils.IS_OS_LINUX) {
                command = "cd " + rankingScriptDir
                        + " && python3 call_ranking_func.py --method_name " + rankingMethodName
                        + " --offers_loc " + inputfile;

                processBuilder = new ProcessBuilder("bash", "-c", command);
            } else {
                throw new UnsupportedOperationException("Unsupported operating system");
            }

            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
            process.waitFor();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                StringBuilder arrayContent = new StringBuilder();

                while ((line = reader.readLine()) != null) {
                    //System.out.println(line);
                    arrayContent.append(line).append(" ");
                }

                String content = arrayContent.toString();

                content = content.replaceAll("[^0-9\\s]", "");

                List<Integer> numberList = Arrays.stream(content.split("\\s+"))
                        .filter(token -> !token.isEmpty())
                        .map(Integer::parseInt)
                        .collect(Collectors.toList());

                int firstNumber = numberList.get(0);
                //int lastNumber = numberList.get(numberList.size() - 1);

                SimLogger.logRun(app.offers.size() + " offers were ranked for "
                        + app.name + " at: " + Timed.getFireCount() / 1000.0 / 60.
                        + " min., the winning offer index is: " + firstNumber);

                return firstNumber;
                //return lastNumber;
            }
        } catch (IOException | InterruptedException e) {
            e.getStackTrace();
        }

        return -1;
    }

    private void writeFile(AgentApplication app) {
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

                for (Resource resource : offer.agentResourcesMap.get(agent)) {
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

            QosPriority qosPriority = new QosPriority(app.energyPriority, app.bandwidthPriority, app.latencyPriority, app.pricePriority);
            JsonOfferData jsonData = new JsonOfferData(qosPriority, reliabilityList, energyList, bandwidthList, latencyList, priceList);
            AgentOfferWriter.writeOffers(jsonData, app.name);
        }
    }

    private void acknowledgeAndInitSwarmAgent(AgentApplication app, Offer offer, int bcastMessageSize) {
        MessageHandler.executeMessaging(this, app, bcastMessageSize, "ack", () -> {
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
            SimLogger.logRun("Winning offer for " + app.name + " was " + offer);
            Pair<ComputingAppliance, Utilisation> leadResource = findLeadResource(offer.utilisations);
            new Deployment(leadResource, offer, app);
        });
    }

    private void freeReservedResources(final String appName, final Capacity capacity) {
        List<Resource> resourcesToBeRemoved = new ArrayList<>();

        for (Utilisation util : capacity.utilisations) {
            if (util.resource.name.contains(appName) && util.state.equals(Utilisation.State.RESERVED)) {
                resourcesToBeRemoved.add(util.resource);
            }
        }
        for (Resource resource : resourcesToBeRemoved) {
            capacity.releaseCapacity(resource);
        }
    }

    private Pair<ComputingAppliance, Utilisation> findLeadResource(List<Pair<ComputingAppliance, Utilisation>> utilisations) {
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

    private void validateAndAddCapacitiesLimit(List<Capacity> capacities) {
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