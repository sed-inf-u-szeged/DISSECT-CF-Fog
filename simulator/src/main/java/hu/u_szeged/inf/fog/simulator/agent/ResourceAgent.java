package hu.u_szeged.inf.fog.simulator.agent;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.AlterableResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ConsumptionEventAdapter;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;
import hu.mta.sztaki.lpds.cloud.simulator.io.StorageObject;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;
import hu.u_szeged.inf.fog.simulator.agent.AgentApplication.Resource;
import hu.u_szeged.inf.fog.simulator.node.ComputingAppliance;
import hu.u_szeged.inf.fog.simulator.util.SimLogger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
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
    
    
    public ResourceAgent(String name, HashMap<ComputingAppliance, Capacity> capacityOfferings) {
        this.capacityOfferings = capacityOfferings;
        this.name = name;
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
        
        this.offerRanking();
    }
    
    private void offerRanking() {       
        // To be implemented..
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
        
        List<Set<Pair<ResourceAgent, Resource>>>  combinations = generateUniqueCombinations(agentResourcePairs, app.resources);

        System.out.println(combinations.size());
        
        for (Set<Pair<ResourceAgent, Resource>> combination : combinations) {
            Map<ResourceAgent, Set<Resource>> agentResourcesMap = new HashMap<>();

            for (Pair<ResourceAgent, Resource> pair : combination) {
                ResourceAgent agent = pair.getLeft();
                Resource resource = pair.getRight();
                
                agentResourcesMap.putIfAbsent(agent, new HashSet<>());
                agentResourcesMap.get(agent).add(resource);
            }
            System.out.println("Offer:");
            for (Map.Entry<ResourceAgent, Set<Resource>> entry : agentResourcesMap.entrySet()) {

                ResourceAgent agent = entry.getKey();
                Set<Resource> resources = entry.getValue();
                System.out.print(agent.name + ": ");

                for (Resource resource : resources) {
                    System.out.print(resource.name + " ");
                }
                System.out.println();
            }
        }
    } 
    
    public List<Set<Pair<ResourceAgent, Resource>>> generateUniqueCombinations(
           List<Pair<ResourceAgent, Resource>> pairs, List<Resource> resources) {
        Set<Set<Pair<ResourceAgent, Resource>>> uniqueCombinations = new HashSet<>();
        List<Resource> resourcesList = new ArrayList<>(resources);
        
        generateCombinations(pairs, new HashSet<>(), uniqueCombinations, resourcesList, new HashSet<>());
        
        return new ArrayList<>(uniqueCombinations);
    }

    private void generateCombinations(List<Pair<ResourceAgent, Resource>> pairs,
                                      Set<Pair<ResourceAgent, Resource>> currentCombination,
                                      Set<Set<Pair<ResourceAgent, Resource>>> uniqueCombinations,
                                      List<Resource> resources,
                                      Set<Resource> includedResources) {

        if (includedResources.size() == resources.size()) {
            uniqueCombinations.add(new HashSet<>(currentCombination));
            return;
        }
        
        for (Pair<ResourceAgent, Resource> pair : pairs) {
            if (!currentCombination.contains(pair) && !includedResources.contains(pair.getRight())) {

                currentCombination.add(pair);
                includedResources.add(pair.getRight());
                
                generateCombinations(pairs, currentCombination, uniqueCombinations, resources, includedResources);

                currentCombination.remove(pair);
                includedResources.remove(pair.getRight());
            }
        }
    }
}