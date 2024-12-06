package hu.u_szeged.inf.fog.simulator.agent;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.AlterableResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;
import hu.u_szeged.inf.fog.simulator.agent.AgentApplication.Resource;
import hu.u_szeged.inf.fog.simulator.agent.Capacity.Utilisation;
import hu.u_szeged.inf.fog.simulator.agent.strategy.AgentStrategy;
import hu.u_szeged.inf.fog.simulator.demo.ScenarioBase;
import hu.u_szeged.inf.fog.simulator.node.ComputingAppliance;
import hu.u_szeged.inf.fog.simulator.util.SimLogger;
import hu.u_szeged.inf.fog.simulator.util.agent.AgentOfferWriter;
import hu.u_szeged.inf.fog.simulator.util.agent.AgentOfferWriter.JsonOfferData;
import hu.u_szeged.inf.fog.simulator.util.agent.AgentOfferWriter.QosPriority;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.SystemUtils;
import org.apache.commons.lang3.tuple.Pair;

public class ResourceAgent {
    
    public static String rankingMethodName;
    
    public static String rankingScriptDir;
    
    public String name;
    
    ComputingAppliance hostNode;
    
    VirtualMachine service;
    
    public double hourlyPrice;
    
    public List<Capacity> capacities;
    
    AgentStrategy agentStrategy;
    
    public static ArrayList<ResourceAgent> resourceAgents = new ArrayList<>();
    
    
    public ResourceAgent(String name, double hourlyPrice, VirtualAppliance resourceAgentVa,
            AlterableResourceConstraints resourceAgentArc, AgentStrategy agentStrategy, Capacity ...capacities) {
        this.capacities = new ArrayList<>();
        this.name = name;
        this.hourlyPrice = hourlyPrice;
        ResourceAgent.resourceAgents.add(this);
        this.agentStrategy = agentStrategy;
        this.capacities.addAll(Arrays.asList(capacities));
        this.initResourceAgent(resourceAgentVa, resourceAgentArc);
    }
    
    public void registerCapacity(Capacity capacity) {
        this.capacities.add(capacity);
    }
    
    private void initResourceAgent(VirtualAppliance resourceAgentVa, AlterableResourceConstraints resourceAgentArc) {
        try {
            this.hostNode = this.capacities.get(new Random().nextInt(this.capacities.size())).node;
            VirtualAppliance va = resourceAgentVa.newCopy(this.name + "-VA");
            this.hostNode.iaas.repositories.get(0).registerObject(va);
            VirtualMachine vm = this.hostNode.iaas.requestVM(va, resourceAgentArc,
                    this.hostNode.iaas.repositories.get(0), 1)[0];
            this.service = vm;
                
            SimLogger.logRun(name + " agent started working at: " + Timed.getFireCount() 
                    + ", hosted on: " + this.hostNode.name);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void broadcast(AgentApplication app, int bcastMessageSize) {
        MessageHandler.executeMessaging(this, app, bcastMessageSize, "bcast", () -> {
            deploy(app, bcastMessageSize);
        });
    }

    private void deploy(AgentApplication app, int bcastMessageSize) {
        this.generateOffers(app);
        if (!app.offers.isEmpty()) {
            this.writeFile(app);
            app.winningOffer = this.callRankingScript(app);
            acknowledgeAndInitSwarmAgent(app, app.offers.get(app.winningOffer), bcastMessageSize);
        } else {  
            acknowledgeAndInitSwarmAgent(app, new Offer(new HashMap<>(), -1), bcastMessageSize);
            app.deploymentTime = -1;
        }
    }

    private void generateOffers(AgentApplication app) {
        List<Pair<ResourceAgent, Resource>> agentResourcePairs = new ArrayList<>();

        for (ResourceAgent agent : ResourceAgent.resourceAgents) {               
            agentResourcePairs.addAll(agent.agentStrategy.canFulfill(agent, app.resources));
        }
        
        generateUniqueOfferCombinations(agentResourcePairs, app);

        // TODO: only for debugging, needs to be deleted
        for (Offer o : app.offers) {
            System.out.println(o);
        }
    } 
    
    private void generateUniqueOfferCombinations(List<Pair<ResourceAgent, Resource>> pairs, AgentApplication app) {
        
        Set<Set<Pair<ResourceAgent, Resource>>> uniqueCombinations = new HashSet<>();
        Set<Pair<ResourceAgent, Resource>> currentCombination = new HashSet<>();
        Set<Resource> includedResources = new HashSet<>();

        generateCombinations(pairs, app.resources.size(), uniqueCombinations, currentCombination, includedResources);
        
        for (Set<Pair<ResourceAgent, Resource>> combination : uniqueCombinations) {
            Map<ResourceAgent, Set<Resource>> agentResourcesMap = new HashMap<>();

            for (Pair<ResourceAgent, Resource> pair : combination) {
                ResourceAgent agent = pair.getLeft();
                Resource resource = pair.getRight();
                
                agentResourcesMap.putIfAbsent(agent, new HashSet<>());
                agentResourcesMap.get(agent).add(resource);
            }
            
            app.offers.add(new Offer(agentResourcesMap, app.offers.size()));
        }
    }

    private void generateCombinations(List<Pair<ResourceAgent, Resource>> pairs, int resourceCount,
                                      Set<Set<Pair<ResourceAgent, Resource>>> uniqueCombinations,
                                      Set<Pair<ResourceAgent, Resource>> currentCombination,
                                      Set<Resource> includedResources) {

        if (includedResources.size() == resourceCount) {
            uniqueCombinations.add(new HashSet<>(currentCombination));
            return;
        }
        
        for (Pair<ResourceAgent, Resource> pair : pairs) {      
            if (!currentCombination.contains(pair) && !includedResources.contains(pair.getRight())) {
                currentCombination.add(pair);
                includedResources.add(pair.getRight());

                generateCombinations(pairs, resourceCount, uniqueCombinations, currentCombination, includedResources);

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
                    System.out.println(line);
                    arrayContent.append(line).append(" ");
                }
                
                String content = arrayContent.toString();
                
                content = content.replaceAll("[^0-9\\s]", ""); 
                
                List<Integer> numberList = Arrays.stream(content.split("\\s+"))
                        .filter(token -> !token.isEmpty()) 
                        .map(Integer::parseInt) 
                        .collect(Collectors.toList());
                
                int firstNumber = numberList.get(0);
                int lastNumber = numberList.get(numberList.size() - 1);
                
                SimLogger.logRun(app.offers.size() + " offers were ranked for " 
                        + app.name + " at: " + Timed.getFireCount() 
                        + " as follows: first = " + firstNumber + ", last = " + lastNumber);
                return lastNumber;
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
            
            // TODO: the aggregation needs refactoring
            for (ResourceAgent agent : offer.agentResourcesMap.keySet()) {

                averageLatency += agent.hostNode.iaas.repositories.get(0).getLatencies().get(
                        agent.hostNode.iaas.repositories.get(0).getName());
                
                averageBandwidth += agent.hostNode.iaas.repositories.get(0).inbws.getPerTickProcessingPower();
                // averageBandwidth += agent.hostNode.iaas.repositories.get(0).outbws.getPerTickProcessingPower();

                //double energy = 0;
                //for (PhysicalMachine pm : agent.hostNode.iaas.machines) {
                averageEnergy += agent.hostNode.iaas.machines.get(0).getCurrentPowerBehavior().getMinConsumption() 
                        + agent.hostNode.iaas.machines.get(0).getCurrentPowerBehavior().getConsumptionRange();
                //}
                //averageEnergy += energy / agent.hostNode.iaas.machines.size();
                
                for (Resource resource : offer.agentResourcesMap.get(agent)) {
                    averagePrice += agent.hourlyPrice * resource.getTotalReqCpu();
                }
            }
            
            //averageLatency /= offer.agentResourcesMap.keySet().size();
            //averageBandwidth /= offer.agentResourcesMap.keySet().size();
            //averageEnergy /= offer.agentResourcesMap.keySet().size();
            //averagePrice /= offer.agentResourcesMap.keySet().size();
            
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
            SimLogger.logRun("All ack. messages receieved for " + app.name
                    + " at: " + Timed.getFireCount());
            
            if (offer.id == -1) {
                SimLogger.logRun(app.name + "'s requirements cannot be fulfilled!");
                // TODO: if none of the applications reaches the deployment phase, the sim. won't terminate due to the energy metering
                for (ResourceAgent agent : ResourceAgent.resourceAgents) {
                    for (Capacity capacity : agent.capacities) {                        
                        List<Resource> resourcesToBeRemoved = new ArrayList<>();
                        for (Utilisation util : capacity.utilisations) {
                            if (util.resource.name.contains(app.name) && util.state.equals(Utilisation.State.RESERVED)) {
                                resourcesToBeRemoved.add(util.resource);
                            }
                        }
                        for (Resource r : resourcesToBeRemoved) {
                            capacity.releaseCapacity(r);
                        }
                    }
                }
            } else {
                for (ResourceAgent agent : ResourceAgent.resourceAgents) {
                    for (Capacity capacity : agent.capacities) {
                        
                        if (offer.agentResourcesMap.containsKey(agent)) {
                            capacity.assignCapacity(offer.agentResourcesMap.get(agent), offer);
                        }
                        
                        List<Resource> resourcesToBeRemoved = new ArrayList<>();
                        for (Utilisation util : capacity.utilisations) {
                            if (util.resource.name.contains(app.name) && util.state.equals(Utilisation.State.RESERVED)) {
                                resourcesToBeRemoved.add(util.resource);
                            }
                        }
                        for (Resource r : resourcesToBeRemoved) {
                            capacity.releaseCapacity(r);
                        }
                    }
                }

                Pair<ComputingAppliance, Utilisation> leadResource = findLeadResource(offer.utilisations);
                
                new Deployment(leadResource, offer, app);
            }
        });
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
}