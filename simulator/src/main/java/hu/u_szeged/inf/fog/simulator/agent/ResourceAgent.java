package hu.u_szeged.inf.fog.simulator.agent;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.AlterableResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ConsumptionEventAdapter;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;
import hu.mta.sztaki.lpds.cloud.simulator.io.StorageObject;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;
import hu.u_szeged.inf.fog.simulator.agent.AgentApplication.Resource;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.Pair;

public class ResourceAgent {
      
    public static class Capacity {
        double cpu;
        long memory;
        long storage;
        
        public Capacity(double cpu, long memory, long storage) {
            this.cpu = cpu;
            this.memory = memory;
            this.storage = storage;
        }
        
        public Capacity(Capacity other) {
            this.cpu = other.cpu;
            this.memory = other.memory;
            this.storage = other.storage;
        }
    }
    
    HashMap<ComputingAppliance, Capacity> capacityOfferings; 
    
    public String name;
    
    ComputingAppliance computingAppliance;
    
    VirtualMachine service;
    
    double hourlyPrice;
    
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
    
    
    public ResourceAgent(String name, HashMap<ComputingAppliance, Capacity> capacityOfferings, double hourlyPrice) {
        this.capacityOfferings = capacityOfferings;
        this.name = name;
        this.hourlyPrice = hourlyPrice;
        ResourceAgent.resourceAgents.add(this);
        this.computingAppliance = this.getRandomAppliance();
        this.startAgent();
    }
    
    private void startAgent() {
        try {
            VirtualAppliance va = ResourceAgent.agentVa.newCopy(this.name + "-VA");
            this.computingAppliance.iaas.repositories.get(0).registerObject(va);
            VirtualMachine vm = this.computingAppliance.iaas.requestVM(va, ResourceAgent.agentArc,
                    this.computingAppliance.iaas.repositories.get(0), 1)[0];
            this.service = vm;
            
            SimLogger.logRun(name + " agent was turned on at: " + Timed.getFireCount() 
                + ", hosted on: " + this.computingAppliance.name);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private ComputingAppliance getRandomAppliance() {
        Object[] keys = this.capacityOfferings.keySet().toArray();
        Random random = new Random();
        int randomIndex = random.nextInt(keys.length);
        return (ComputingAppliance) keys[randomIndex];
    }

    public void broadcast(AgentApplication app, int bcastMessageSize) {
        List<ResourceAgent> filteredAgents = ResourceAgent.resourceAgents.stream()
                .filter(agent -> agent.service.getState().equals(VirtualMachine.State.RUNNING))
                .filter(agent -> !agent.equals(this))
                .collect(Collectors.toList());

        for (ResourceAgent neighbor : filteredAgents) {
            String reqName = this.name + "-" + neighbor.name + "-" + app.name + "-req";
            StorageObject reqMessage = new StorageObject(reqName, bcastMessageSize, false);
            this.computingAppliance.iaas.repositories.get(0).registerObject(reqMessage);
            try {
                this.computingAppliance.iaas.repositories.get(0).requestContentDelivery(
                        reqName, neighbor.computingAppliance.iaas.repositories.get(0), new ConsumptionEventAdapter() {

                            @Override
                            public void conComplete() {
                                computingAppliance.iaas.repositories.get(0).deregisterObject(reqName);
                                String resName = neighbor.name + "-" + name + "-" + app.name + "-res";
                                StorageObject resMessage = new StorageObject(resName, bcastMessageSize, false);
                                neighbor.computingAppliance.iaas.repositories.get(0).registerObject(resMessage);
                                app.bcastCounter++;
                                try {
                                    neighbor.computingAppliance.iaas.repositories.get(0).requestContentDelivery(
                                            resName, computingAppliance.iaas.repositories.get(0), new ConsumptionEventAdapter() {
                                                
                                                @Override
                                                public void conComplete() {
                                                    neighbor.computingAppliance.iaas.repositories.get(0).deregisterObject(resMessage);
                                                    neighbor.computingAppliance.iaas.repositories.get(0).deregisterObject(reqMessage);
                                                    computingAppliance.iaas.repositories.get(0).deregisterObject(resMessage);
                                                    app.bcastCounter--;
                                                    if (app.bcastCounter == 0) {
                                                        deploy(app);
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
    }
    
        
    private void deploy(AgentApplication app) {
        this.generateOffers(app);
        
        this.writeFile(app.offers);
        
        this.callRankingScript();
    }
    
    private void callRankingScript() {
        String path = "D:\\Documents\\swarm-deployment\\scripts";
        String inputfile = ScenarioBase.resultDirectory + File.separator + "offer.json";
        String targetDir = "D:\\Documents\\swarm-deployment\\resource_offers";
        String targetFilePath = targetDir + File.separator + "offer.json";

        try {
            Files.copy(Path.of(inputfile), Path.of(targetFilePath), StandardCopyOption.REPLACE_EXISTING);

            String command = "cd /d " + path + " && conda activate swarmchestrate && python offer_evaluator.py ";

            ProcessBuilder processBuilder = new ProcessBuilder("cmd.exe", "/c", command);
            processBuilder.redirectErrorStream(true);

            Process process = processBuilder.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println(line);
                }
            }

            int exitCode = process.waitFor();
            System.out.println("Exited with code: " + exitCode);
        } catch (IOException | InterruptedException e) {
            e.getStackTrace();
        }
    }

    private void writeFile(List<Offer> offers) {     

        List<Double> reliabilityList = new ArrayList<Double>(); 
        List<Double> energyList = new ArrayList<Double>(); 
        List<Double> bandwidthList = new ArrayList<Double>(); 
        List<Double> latencyList = new ArrayList<Double>(); 
        List<Double> priceList = new ArrayList<Double>(); 

        for (Offer offer : offers) {
            double averageLatency = 0;
            double averageBandwidth = 0;
            double averageEnergy = 0;
            double averagePrice = 0;
            
            for (ResourceAgent agent : offer.agentResourcesMap.keySet()) {

                averageLatency += agent.computingAppliance.iaas.repositories.get(0).getLatencies().get(
                        agent.computingAppliance.iaas.repositories.get(0).getName());
                

                averageBandwidth += agent.computingAppliance.iaas.repositories.get(0).inbws.getPerTickProcessingPower() / 2.0;
                averageBandwidth += agent.computingAppliance.iaas.repositories.get(0).outbws.getPerTickProcessingPower() / 2.0;

                double energy = 0;
                for (PhysicalMachine pm : agent.computingAppliance.iaas.machines) {
                    energy += pm.getCurrentPowerBehavior().getConsumptionRange();
                }
                averageEnergy += energy / agent.computingAppliance.iaas.machines.size();
                
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
            
            double epsilon = averageEnergy * 1e-10 * r.nextDouble();
            energyList.add(averageEnergy + epsilon);

            epsilon = averageBandwidth * 1e-10 * r.nextDouble();
            bandwidthList.add(averageBandwidth + epsilon);
            
            epsilon = averageLatency * 1e-10 * r.nextDouble();
            latencyList.add(averageLatency + epsilon);
            
            epsilon = averagePrice * 1e-10 * r.nextDouble();
            priceList.add(averagePrice + epsilon);
                        
            System.out.println("avg. latency: " + averageLatency + " avg. bandwidth: " 
                + averageBandwidth + " avg. energy: " + averageEnergy +  " avg. price: " + averagePrice);
            
            QosPriority qosPriority = new QosPriority(r.nextDouble(), r.nextDouble(), r.nextDouble(), r.nextDouble());

            JsonOfferData jsonData = new JsonOfferData(qosPriority, reliabilityList, energyList, bandwidthList, latencyList, priceList);
            
            AgentOfferWriter.writeOffers(jsonData);
            
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
    
    private boolean checkInstanceAvailability(Resource resource, 
            HashMap<ComputingAppliance, Capacity> copiedCapacityOfferings) {
        if (resource.size == null) { // compute type 
            int isAbleToHost = 0;
            
            int instances = resource.instances == null ? 1 : Integer.parseInt(resource.instances);
            double reqCpu = (Double.parseDouble(resource.cpu));
            long reqMemory = (Long.parseLong(resource.memory));
            
            for (int i = 0; i < instances; i++) {
                for (Map.Entry<ComputingAppliance, Capacity> entry : copiedCapacityOfferings.entrySet()) {
                    ComputingAppliance ca = entry.getKey();
                    Capacity capacity = entry.getValue();
                    
                    if ((resource.provider == null || resource.provider.equals(ca.provider))
                            && (resource.location == null || resource.location.equals(ca.location))
                            && reqCpu <= capacity.cpu && reqMemory <= capacity.memory) {
                        isAbleToHost++;
                        capacity.cpu -= reqCpu;
                        capacity.memory -= reqMemory;
                        break;
                    }
                }
            }
            return instances == isAbleToHost;
            
        } else { // storage type
            for (Map.Entry<ComputingAppliance, Capacity> entry : copiedCapacityOfferings.entrySet()) {
                ComputingAppliance ca = entry.getKey();
                Capacity capacity = entry.getValue();
                if ((resource.provider == null || resource.provider.equals(ca.provider))
                        && (resource.location == null || resource.location.equals(ca.location))
                        && Long.parseLong(resource.size) <= capacity.storage) {
                    capacity.storage -= (Long.parseLong(resource.size));
                    return true;
                }
            }
            return false;
        }
    }
    
    private void generateOffers(AgentApplication app) {
        List<Pair<ResourceAgent, Resource>> agentResourcePairs = new ArrayList<>();
        
        for (ResourceAgent agent : ResourceAgent.resourceAgents) {   
            
            HashMap<ComputingAppliance, Capacity> copiedCapacityOfferings = new HashMap<>();
            for (Map.Entry<ComputingAppliance, Capacity> entry : agent.capacityOfferings.entrySet()) {
                copiedCapacityOfferings.put(entry.getKey(), new Capacity(entry.getValue()));
            }
            
            List<Resource> sortedResources = AgentApplication.getSortedResourcesByCpuThenSize(app.resources);
            
            for (Resource resource : sortedResources) {
                boolean isAbleToHost = this.checkInstanceAvailability(resource, copiedCapacityOfferings);
                if (isAbleToHost) {
                    agentResourcePairs.add(Pair.of(agent, resource));
                }
            }
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
            
            app.offers.add(new Offer(agentResourcesMap));
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
}