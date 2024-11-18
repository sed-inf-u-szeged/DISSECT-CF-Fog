package hu.u_szeged.inf.fog.simulator.agent;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.AlterableResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ConsumptionEventAdapter;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ResourceConsumption;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;
import hu.mta.sztaki.lpds.cloud.simulator.io.StorageObject;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;
import hu.u_szeged.inf.fog.simulator.agent.AgentApplication.Resource;
import hu.u_szeged.inf.fog.simulator.agent.Capacity.Utilisation;
import hu.u_szeged.inf.fog.simulator.agent.strategy.AgentStrategy;
import hu.u_szeged.inf.fog.simulator.demo.AgentTest;
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
    
    public String name;
    
    ComputingAppliance hostNode;
    
    VirtualMachine service;
    
    double hourlyPrice;
    
    public List<Capacity> capacities;
    
    AgentStrategy agentStrategy;
    
    /**
     * It defines the agent's virtual image file with 0.5 GB of disk size requirement.
     */
    public static final VirtualAppliance agentVa = new VirtualAppliance("agentVa", 1, 0, false, 536870912L); 
    
    /**
     * It defines the agent's resource requirements for 
     * (1 CPU core, 0.001 processing speed and 0.5 GB of memory) the agent VM.
     */
    public static final AlterableResourceConstraints agentArc = new AlterableResourceConstraints(1, 0.001, 536870912L);
    
    public static ArrayList<ResourceAgent> resourceAgents = new ArrayList<>();
    
    
    public ResourceAgent(String name, double hourlyPrice, AgentStrategy agentStrategy, Capacity ...capacities) {
        this.capacities = new ArrayList<>();
        this.name = name;
        this.hourlyPrice = hourlyPrice;
        ResourceAgent.resourceAgents.add(this);
        this.agentStrategy = agentStrategy;
        this.capacities.addAll(Arrays.asList(capacities));
        this.startAgent();
    }
    
    public void registerCapacity(Capacity capacity) {
        this.capacities.add(capacity);
    }
    
    private void startAgent() {
        try {
            this.hostNode = this.capacities.get(new Random().nextInt(this.capacities.size())).node;
            VirtualAppliance va = ResourceAgent.agentVa.newCopy(this.name + "-VA");
            this.hostNode.iaas.repositories.get(0).registerObject(va);
            VirtualMachine vm = this.hostNode.iaas.requestVM(va, ResourceAgent.agentArc,
                    this.hostNode.iaas.repositories.get(0), 1)[0];
            this.service = vm;
                
            SimLogger.logRun(name + " agent started working at: " + Timed.getFireCount() 
                    + ", hosted on: " + this.hostNode.name);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void broadcast(AgentApplication app, int bcastMessageSize) {
        List<ResourceAgent> filteredAgents = ResourceAgent.resourceAgents.stream()
                .filter(agent -> agent.service.getState().equals(VirtualMachine.State.RUNNING))
                .filter(agent -> !agent.equals(this))
                .collect(Collectors.toList());
        
        
       if (ResourceAgent.resourceAgents.size() != 1) {
            for (ResourceAgent neighbor : filteredAgents) {
                String reqName = this.name + "-" + neighbor.name + "-" + app.name + "-req";
                StorageObject reqMessage = new StorageObject(reqName, bcastMessageSize, false);
                this.hostNode.iaas.repositories.get(0).registerObject(reqMessage);
                app.bcastCounter++;
                try {
                    this.hostNode.iaas.repositories.get(0).requestContentDelivery(
                            reqName, neighbor.hostNode.iaas.repositories.get(0), new ConsumptionEventAdapter() {

                                @Override
                                public void conComplete() {
                                    
                                    hostNode.iaas.repositories.get(0).deregisterObject(reqName);
                                    String resName = neighbor.name + "-" + name + "-" + app.name + "-res";
                                    StorageObject resMessage = new StorageObject(resName, bcastMessageSize, false);
                                    neighbor.hostNode.iaas.repositories.get(0).registerObject(resMessage);
                                    try {
                                        neighbor.hostNode.iaas.repositories.get(0).requestContentDelivery(
                                                resName, hostNode.iaas.repositories.get(0), new ConsumptionEventAdapter() {
                                                    
                                                    @Override
                                                    public void conComplete() {
                                                        neighbor.hostNode.iaas.repositories.get(0).deregisterObject(resMessage);
                                                        neighbor.hostNode.iaas.repositories.get(0).deregisterObject(reqMessage);
                                                        hostNode.iaas.repositories.get(0).deregisterObject(resMessage);
                                                        app.bcastCounter--;
                                                        if (app.bcastCounter == 0) {
                                                            deploy(app, bcastMessageSize);
                                                        }
                                                    }
                                                });
                                    } catch (NetworkException e) {
                                        e.printStackTrace();
                                    }
                                }
                            }
                    );
                } catch (NetworkException e) {
                    e.printStackTrace();
                }
            }
        } else {
            deploy(app, bcastMessageSize);
        }
    }
    
    private void deploy(AgentApplication app, int bcastMessageSize) {
        this.generateOffers(app);
        this.writeFile(app);
        int preferredIndex = this.callRankingScript(app);
        sendAcknowledgements(app, app.offers.get(preferredIndex), bcastMessageSize);
    }
    
    private void sendAcknowledgements(AgentApplication app, Offer offer, int bcastMessageSize) {
        for (Map.Entry<ResourceAgent, Set<Resource>> entry : offer.agentResourcesMap.entrySet()) {
            ResourceAgent agent = entry.getKey();  
            // Set<Resource> resources = entry.getValue(); 
            
            if (agent != this) {
                String reqName = this.name + "-" + agent.name + "-" + app.name + "-ack_req";
                StorageObject reqMessage = new StorageObject(reqName, bcastMessageSize, false);
                this.hostNode.iaas.repositories.get(0).registerObject(reqMessage);
                app.bcastCounter++;
                try {
                    this.hostNode.iaas.repositories.get(0).requestContentDelivery(
                            reqName, agent.hostNode.iaas.repositories.get(0), new ConsumptionEventAdapter() {

                                @Override
                                public void conComplete() {
                                    hostNode.iaas.repositories.get(0).deregisterObject(reqName);
                                    String resName = agent.name + "-" + name + "-" + app.name + "-ack_res";
                                    StorageObject resMessage = new StorageObject(resName, bcastMessageSize, false);
                                    agent.hostNode.iaas.repositories.get(0).registerObject(resMessage);
                                    for (Capacity capacity : agent.capacities) {
                                        capacity.assignCapacity(entry.getValue());
                                        capacity.releaseCapacity(null, app.name);
                                    }
                                    
                                    try {
                                        agent.hostNode.iaas.repositories.get(0).requestContentDelivery(
                                                resName, hostNode.iaas.repositories.get(0), new ConsumptionEventAdapter() {
                                                    
                                                    @Override
                                                    public void conComplete() {
                                                        agent.hostNode.iaas.repositories.get(0).deregisterObject(resMessage);
                                                        agent.hostNode.iaas.repositories.get(0).deregisterObject(reqMessage);
                                                        hostNode.iaas.repositories.get(0).deregisterObject(resMessage);
                                                        app.bcastCounter--;
                                                        if (app.bcastCounter == 0) {
                                                            SimLogger.logRun("All ack. messages receieved for " + app.name
                                                                    + " at: " + Timed.getFireCount());
                                                            
                                                            deploySwarmAgent();
                                                        }
                                                    }
                                                });
                                    } catch (NetworkException e) {
                                        e.printStackTrace();
                                    }
                                }
                            }
                    );
                } catch (NetworkException e) {
                    e.printStackTrace();
                }
            } else {
                for (Capacity capacity : this.capacities) {
                    // TODO: revise!
                    capacity.assignCapacity(entry.getValue());
                    capacity.releaseCapacity(null, app.name);
                }
            }
        }
    }

    private void generateOffers(AgentApplication app) {
        List<Pair<ResourceAgent, Resource>> agentResourcePairs = new ArrayList<>();

        for (ResourceAgent agent : ResourceAgent.resourceAgents) {               
            agentResourcePairs.addAll(agent.agentStrategy.canFulfill(agent, app.resources));
        }
        
        // TODO: only for debugging, needs to be deleted
        for (Pair<ResourceAgent, Resource> pair : agentResourcePairs) {
            ResourceAgent agent = pair.getLeft();
            Resource resource = pair.getRight();
            System.out.println("Agent: " + agent.name + ", Resource: " + resource.name);
        }

        generateUniqueCombinations(agentResourcePairs, app);

        for (Offer o : app.offers) {
            System.out.println(o);
        }
    } 
    
    private void generateUniqueCombinations(List<Pair<ResourceAgent, Resource>> pairs, AgentApplication app) {
        
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
                command = "cd /d " + AgentTest.rankingScriptDir
                    + " && conda activate swarmchestrate && python call_ranking_func.py --method_name " + AgentTest.rankingMethodName
                    + " --offers_loc " + inputfile;
                processBuilder = new ProcessBuilder("cmd.exe", "/c", command);
            } else if (SystemUtils.IS_OS_LINUX) {
                command = "cd " + AgentTest.rankingScriptDir
                    + " && python3 call_ranking_func.py --method_name " + AgentTest.rankingMethodName
                    + " --offers_loc " + inputfile;

                processBuilder = new ProcessBuilder("bash", "-c", command);
            } else {
                throw new UnsupportedOperationException("Unsupported operating system");
            }

            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    // System.out.println(line);
                    
                    if (line.startsWith("[")) {

                        String numbers = line.substring(1, line.length() - 1);
                        List<Integer> numberList = Arrays.stream(numbers.trim().split("\\s+")) 
                                                         .map(Integer::parseInt) 
                                                         //.map(n -> n + 1)
                                                         .collect(Collectors.toList());   
                        
                        SimLogger.logRun(app.offers.size() + " offers were ranked for " 
                                + app.name + " at: " + Timed.getFireCount() + " as follows: " + numberList);
                        
                        return numberList.get(0);
                    }
                }
            }
            process.waitFor();
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
                
                averageBandwidth += agent.hostNode.iaas.repositories.get(0).inbws.getPerTickProcessingPower() / 2.0;
                averageBandwidth += agent.hostNode.iaas.repositories.get(0).outbws.getPerTickProcessingPower() / 2.0;

                double energy = 0;
                for (PhysicalMachine pm : agent.hostNode.iaas.machines) {
                    energy += pm.getCurrentPowerBehavior().getConsumptionRange();
                }
                averageEnergy += energy / agent.hostNode.iaas.machines.size();
                
                for (Resource resource : offer.agentResourcesMap.get(agent)) {
                    averagePrice += agent.hourlyPrice * resource.getTotalReqCpu();
                }
            }
            
            averageLatency /= offer.agentResourcesMap.keySet().size();
            averageBandwidth /= offer.agentResourcesMap.keySet().size();
            averageEnergy /= offer.agentResourcesMap.keySet().size();
            averagePrice /= offer.agentResourcesMap.keySet().size();
            
            Random r = new Random();
            reliabilityList.add(r.nextDouble());
            
            //double epsilon = averageEnergy * 1e-10 * r.nextDouble();
            energyList.add(averageEnergy);
            bandwidthList.add(averageBandwidth);
            latencyList.add(averageLatency);
            priceList.add(averagePrice);
                        
            System.out.println("avg. latency: " + averageLatency + " avg. bandwidth: " 
                + averageBandwidth + " avg. energy: " + averageEnergy +  " avg. price: " + averagePrice);
            
            QosPriority qosPriority = new QosPriority(r.nextDouble(), r.nextDouble(), r.nextDouble(), r.nextDouble());

            JsonOfferData jsonData = new JsonOfferData(qosPriority, reliabilityList, energyList, bandwidthList, latencyList, priceList);
            
            AgentOfferWriter.writeOffers(jsonData, app.name);
        }
    }
    
    private void deploySwarmAgent() {
        for (ResourceAgent agent : ResourceAgent.resourceAgents) {
            for (Capacity cap : agent.capacities) {
                System.out.println(cap);
                for (Utilisation util : cap.utilisations) {
                    System.out.println(util);
                }
            }
        }
    }

    /*
    for(PhysicalMachine pm : iaas.machines) {
    pmAavailableCpu += pm.availableCapacities.getRequiredCPUs() ;
    pmGetCpu +=  pm.getCapacities().getRequiredCPUs();
    }
     
    if (resource.cpu.contains("-")) { // cpu range
        String[] parts = resource.cpu.split("-");
        double minCpu = Double.parseDouble(parts[0]);
        double maxCpu = Double.parseDouble(parts[1]);
    }
    */
}