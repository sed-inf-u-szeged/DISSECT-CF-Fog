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
import hu.u_szeged.inf.fog.simulator.agent.strategy.selection.sa.AtomicCoverageState;
import hu.u_szeged.inf.fog.simulator.agent.strategy.mapping.offer.LocalOffer;
import hu.u_szeged.inf.fog.simulator.agent.strategy.selection.sa.AtomicCoverageSimulatedAnnealing;
import hu.u_szeged.inf.fog.simulator.agent.strategy.mapping.pareto.ExhaustiveMappingStrategy;
import hu.u_szeged.inf.fog.simulator.agent.strategy.mapping.MappingStrategy;
import hu.u_szeged.inf.fog.simulator.agent.strategy.message.MessagingStrategy;
import hu.u_szeged.inf.fog.simulator.agent.strategy.selection.BacktrackingOfferSelectionStrategy;
import hu.u_szeged.inf.fog.simulator.agent.util.AgentOfferWriter;
import hu.u_szeged.inf.fog.simulator.agent.util.AgentOfferWriter.JsonOfferData;
import hu.u_szeged.inf.fog.simulator.agent.util.AgentOfferWriter.QosPriority;
import hu.u_szeged.inf.fog.simulator.common.node.ComputingAppliance;
import hu.u_szeged.inf.fog.simulator.common.util.ScenarioBase;
import hu.u_szeged.inf.fog.simulator.agent.strategy.mapping.offer.LocalOffer.ComponentPlacement;
import hu.u_szeged.inf.fog.simulator.common.util.SimLogger;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import org.apache.commons.lang3.SystemUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;

public class ResourceAgent {

    public static Map<String, ResourceAgent> allResourceAgents = new HashMap<>();
    
    public final String name;

    public ComputingAppliance hostNode;

    public VirtualMachine raService;

    public double hourlyPrice;

    public final double baseHourlyPrice;

    public Map<String, Capacity> capacities = new LinkedHashMap<>();

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
        this.baseHourlyPrice = hourlyPrice;
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
            boolean atomicOffers = (boolean) Config.APP_TYPE.get("atomicOffers");
            boolean onlyFirstOffer = (boolean) Config.APP_TYPE.get("onlyFirstOffer");

            if (atomicOffers) {
                app.winningOffer = 0;

                SimLogger.logRun("The simulated annealing selected an offer for " + app.name + " at: "
                        + Timed.getFireCount() / (double) ScenarioBase.MINUTE_IN_MILLISECONDS + " min.");
            } else if (onlyFirstOffer) {
                app.winningOffer = 0;

                SimLogger.logRun("The first hard-valid offer was selected for " + app.name + " at: "
                        + Timed.getFireCount() / (double) ScenarioBase.MINUTE_IN_MILLISECONDS + " min.");
            } else if (Config.APP_TYPE.get("rankingMethod").equals("random")) {
                app.winningOffer = SeedSyncer.centralRnd.nextInt(app.offers.size());

                SimLogger.logRun(app.offers.size() + " offers were generated for " + app.name + " at: "
                        + Timed.getFireCount() / (double) ScenarioBase.MINUTE_IN_MILLISECONDS
                        + " min., randomly selected offer index is: " + app.winningOffer);
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

    private void generateOffers(AgentApplication app) {
        List<LocalOffer> localOffers = new ArrayList<>();

        app.offerGeneratingAgents.add(this);

        boolean atomicOffers = (boolean) Config.APP_TYPE.get("atomicOffers");

        for (ResourceAgent agent : app.offerGeneratingAgents) {
            if (atomicOffers && !(agent.agentStrategy instanceof ExhaustiveMappingStrategy)) {
                throw new IllegalStateException( "Atomic offer generation requires ExhaustiveMappingStrategy");
            }

            List<LocalOffer> agentLocalOffers = agent.agentStrategy.generateLocalOffers(agent, app);

            if (atomicOffers) {
                agent.reserveAtomicLocalOffers(agentLocalOffers);
            } else {
                agent.reserveLocalOffers(agentLocalOffers);
            }
            localOffers.addAll(agentLocalOffers);
        }

        if (atomicOffers) {
            generateAtomicOfferCombinations(localOffers, app);
        } else {
            generateNonAtomicOfferCombinations(localOffers, app);
        }
    }

    private void reserveAtomicLocalOffers(List<LocalOffer> localOffers) {
        if (localOffers.isEmpty()) {
            return;
        }

        for (LocalOffer localOffer : localOffers) {
            if (localOffer.agent != this) {
                throw new IllegalArgumentException("Atomic reservation contains an offer from another ResourceAgent.");
            }
        }

        Map<Capacity, Triple<Double, Long, Long>> reservationEnvelope = calculateAtomicReservationEnvelope(localOffers);

        for (Map.Entry<Capacity, Triple<Double, Long, Long>> entry : reservationEnvelope.entrySet()) {
            Capacity capacity = entry.getKey();
            Triple<Double, Long, Long> reservedResources = entry.getValue();

            List<LocalOffer> coveredOffers =
                    localOffers.stream()
                            .filter(localOffer -> localOffer.placements.stream()
                                    .anyMatch(placement -> placement.capacity== capacity))
                            .toList();

            double reservedCpu = reservedResources.getLeft();
            long reservedMemory = reservedResources.getMiddle();
            long reservedStorage = reservedResources.getRight();

            capacity.reserveAtomicOffers(coveredOffers,this, reservedCpu, reservedMemory, reservedStorage);
        }
    }

    private void reserveLocalOffers(List<LocalOffer> localOffers) {
        for (LocalOffer localOffer : localOffers) {
            for (ComponentPlacement placement : localOffer.placements) {
                placement.capacity.reserveCapacity(placement.component, localOffer.agent,  localOffer);
            }
        }
    }

    private Map<Capacity, Triple<Double, Long, Long>> calculateAtomicReservationEnvelope(List<LocalOffer> localOffers) {
        Map<Capacity, Triple<Double, Long, Long>> reservationEnvelope = new LinkedHashMap<>();

        for (LocalOffer localOffer : localOffers) {
            Map<Capacity, Triple<Double, Long, Long>> offerDemandByCapacity = new LinkedHashMap<>();

            for (ComponentPlacement placement : localOffer.placements) {
                Triple<Double, Long, Long> currentDemand = offerDemandByCapacity.getOrDefault(placement.capacity, Triple.of(0.0, 0L, 0L));

                Triple<Double, Long, Long> updatedDemand = Triple.of(
                        currentDemand.getLeft() + MappingStrategy.requiredCpu( placement.component),
                        currentDemand.getMiddle() + MappingStrategy.requiredMemory(placement.component),
                        currentDemand.getRight()+ MappingStrategy.requiredStorage(placement.component));

                offerDemandByCapacity.put(placement.capacity,updatedDemand);
            }

            for (Map.Entry<Capacity, Triple<Double, Long, Long>> entry :offerDemandByCapacity.entrySet()) {
                Triple<Double, Long, Long> currentEnvelope = reservationEnvelope.getOrDefault(entry.getKey(), Triple.of(0.0, 0L, 0L));

                Triple<Double, Long, Long> offerDemand = entry.getValue();

                Triple<Double, Long, Long> updatedEnvelope = Triple.of(
                                Math.max(currentEnvelope.getLeft(), offerDemand.getLeft()),
                                Math.max(currentEnvelope.getMiddle(), offerDemand.getMiddle()),
                                Math.max(currentEnvelope.getRight(), offerDemand.getRight()));

                reservationEnvelope.put(entry.getKey(), updatedEnvelope);
            }
        }

        return reservationEnvelope;
    }

    private void generateAtomicOfferCombinations(List<LocalOffer> localOffers, AgentApplication app) {
        AtomicCoverageSimulatedAnnealing simulatedAnnealing = new AtomicCoverageSimulatedAnnealing();

        AtomicCoverageState winningState = simulatedAnnealing.optimize(
                app,
                localOffers,
                (int) Config.APP_TYPE.get("atomicConstructionRestarts"),
                (int) Config.APP_TYPE.get("atomicRepairRestarts"),
                (int) Config.APP_TYPE.get("saNeighborAttempts"),
                (int) Config.APP_TYPE.get("saMaxIterations"),
                (double) Config.APP_TYPE.get("saInitialTemperature"),
                (double) Config.APP_TYPE.get("saMinimumTemperature"),
                (double) Config.APP_TYPE.get("saCoolingRate"),
                (double) Config.APP_TYPE.get("atomicSaInitialHardPenaltyWeight"),
                (double) Config.APP_TYPE.get("atomicSaFinalHardPenaltyWeight"));

        if (winningState == null) {
            return;
        }

        materializeWinningAtomicReservations(app, winningState);

        Map<ResourceAgent, Set<Component>> agentComponentsMap = new LinkedHashMap<>();

        List<ComponentPlacement> selectedPlacements = new ArrayList<>();

        for (LocalOffer localOffer : winningState.selectedOffers) {
            Set<Component> selectedComponents = agentComponentsMap.computeIfAbsent(localOffer.agent,ignored -> new LinkedHashSet<>());

            for (ComponentPlacement placement : localOffer.placements) {
                selectedComponents.add(placement.component);
                selectedPlacements.add(placement);
            }
        }

        Offer winningOffer = new Offer(agentComponentsMap, app.offers.size());

        winningOffer.selectedPlacements.addAll(selectedPlacements);

        app.offers.add(winningOffer);
    }

    private void generateNonAtomicOfferCombinations(List<LocalOffer> localOffers, AgentApplication app) {
        boolean onlyFirstOffer = (boolean) Config.APP_TYPE.get("onlyFirstOffer");

        BacktrackingOfferSelectionStrategy selectionStrategy = new BacktrackingOfferSelectionStrategy(onlyFirstOffer);

        app.offers.addAll(selectionStrategy.selectOffers(localOffers, app));
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
            if (offer.metrics == null) {
                throw new IllegalStateException("Global metrics are missing from offer: " + offer.id);
            }

            reliabilityList.add(1.0);
            energyList.add(offer.metrics.energy);
            bandwidthList.add(offer.metrics.bandwidth);
            latencyList.add(offer.metrics.latency);
            priceList.add(offer.metrics.cost);
        }

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
                List<Integer> submissionCounts = (List<Integer>) Config.APP_TYPE.get("submissionDelay");
                if (failedDeployments == submissionCounts.size()) {
                    SimLogger.logError("All deployment attempts (" + failedDeployments + ") for " + app.name + " have failed.");
                }
                return;
            }
            for (ComponentPlacement placement : offer.selectedPlacements) {
                placement.capacity.assignPlacement(placement, offer);
            }

            for (ResourceAgent agent : ResourceAgent.allResourceAgents.values()) {
                for (Capacity capacity : agent.capacities.values()) {
                    freeReservedResources(app, capacity);
                }
            }

            Pair<ComputingAppliance, Utilisation> leadResource = setLeadResource(offer.utilisations);
            new Deployment(leadResource, offer, app);
        });
    }
    
    private void releaseResourcesDueToNoOffers(AgentApplication app) {
        for (ResourceAgent agent : ResourceAgent.allResourceAgents.values()) {
            for (Capacity capacity : agent.capacities.values()) {
                freeReservedResources(app, capacity);
            }
        }
    }

    private void freeReservedResources(AgentApplication app, Capacity capacity) {
        List<Utilisation> reservationsToRelease = new ArrayList<>();

        for (Utilisation utilisation : capacity.utilisations) {
            if (utilisation.state != Utilisation.State.RESERVED) {
                continue;
            }

            boolean belongsToApplication;

            if (utilisation.envelopeReservation) {
                belongsToApplication = utilisation.coveredOffers.stream()
                                .flatMap(localOffer ->
                                        localOffer.placements.stream())
                                .map(placement ->
                                        placement.component)
                                .anyMatch(component ->
                                        app.components.contains(
                                                component));
            } else {
                belongsToApplication = app.components.contains(utilisation.component);
            }

            if (belongsToApplication) {
                reservationsToRelease.add(utilisation);
            }
        }

        for (Utilisation utilisation : reservationsToRelease) {
            capacity.releaseReservation( utilisation);
        }
    }

    private void materializeWinningAtomicReservations(AgentApplication app, AtomicCoverageState winningState) {
        for (ResourceAgent agent : ResourceAgent.allResourceAgents.values()) {
            for (Capacity capacity : agent.capacities.values()) {
                releaseAtomicEnvelopeReservations(app, capacity);
            }
        }

        for (LocalOffer localOffer : winningState.selectedOffers) {
            for (ComponentPlacement placement : localOffer.placements) {
                placement.capacity.reserveCapacity(placement.component, localOffer.agent, localOffer);
            }
        }
    }

    private void releaseAtomicEnvelopeReservations(AgentApplication app, Capacity capacity) {
        List<Utilisation> envelopesToRelease = new ArrayList<>();

        for (Utilisation utilisation : capacity.utilisations) {

            if (utilisation.state != Utilisation.State.RESERVED || !utilisation.envelopeReservation) {
                continue;
            }

            boolean belongsToApplication = utilisation.coveredOffers.stream()
                            .flatMap(localOffer ->
                                    localOffer.placements.stream())
                            .map(placement ->
                                    placement.component)
                            .anyMatch(component ->
                                    app.components.contains(component));

            if (belongsToApplication) {
                envelopesToRelease.add(utilisation);
            }
        }

        for (Utilisation utilisation : envelopesToRelease) {
            capacity.releaseReservation(utilisation);
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

    public double calculateDemandShare(double requestedCpu, long requestedMemory, long requestedStorage) {
        double totalCpu = capacities.values()
                .stream()
                .mapToDouble(capacity -> capacity.totalCpu)
                .sum();

        long totalMemory = capacities.values()
                .stream()
                .mapToLong(capacity -> capacity.totalMemory)
                .sum();

        long totalStorage = capacities.values()
                .stream()
                .mapToLong(capacity -> capacity.totalStorage)
                .sum();

        if (totalCpu == 0.0 && requestedCpu > 0.0) {
            throw new IllegalStateException(this.name + " has no CPU capacity but CPU was requested.");
        }

        if (totalMemory == 0L && requestedMemory > 0L) {
            throw new IllegalStateException(this.name + " has no memory capacity but memory was requested.");
        }

        if (totalStorage == 0L && requestedStorage > 0L) {
            throw new IllegalStateException(this.name + " has no storage capacity but storage was requested.");
        }

        double demandShareSum = 0.0;
        int dimensions = 0;

        if (totalCpu > 0.0) {
            demandShareSum += requestedCpu / totalCpu;
            dimensions++;
        }

        if (totalMemory > 0L) {
            demandShareSum += requestedMemory / (double) totalMemory;
            dimensions++;
        }

        if (totalStorage > 0L) {
            demandShareSum += requestedStorage / (double) totalStorage;
            dimensions++;
        }

        if (dimensions == 0) {
            return 0.0;
        }

        return demandShareSum / dimensions;
    }

    public double getCurrentUtilisation() {

        double totalCpu = 0.0;
        double allocatedCpu = 0.0;

        long totalMemory = 0;
        long allocatedMemory = 0;

        long totalStorage = 0;
        long allocatedStorage = 0;

        for (Capacity capacity : capacities.values()) {

            totalCpu += capacity.totalCpu;
            allocatedCpu += capacity.totalCpu - capacity.cpu;

            totalMemory += capacity.totalMemory;
            allocatedMemory += capacity.totalMemory - capacity.memory;

            totalStorage += capacity.totalStorage;
            allocatedStorage += capacity.totalStorage - capacity.storage;
        }

        double utilisationSum = 0.0;
        int dimensions = 0;

        if (totalCpu > 0) {
            utilisationSum += allocatedCpu / totalCpu;
            dimensions++;
        }

        if (totalMemory > 0) {
            utilisationSum += allocatedMemory / (double) totalMemory;
            dimensions++;
        }

        if (totalStorage > 0) {
            utilisationSum += allocatedStorage / (double) totalStorage;
            dimensions++;
        }

        return dimensions == 0
                ? 0.0
                : utilisationSum / dimensions;
    }

    public void updateHourlyPrice() {
        double utilisationMultiplier = 0.85 + 0.30 * getCurrentUtilisation();
        this.hourlyPrice = this.baseHourlyPrice * utilisationMultiplier;
    }
}