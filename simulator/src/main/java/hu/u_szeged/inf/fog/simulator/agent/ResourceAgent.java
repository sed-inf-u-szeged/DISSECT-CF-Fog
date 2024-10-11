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
import hu.u_szeged.inf.fog.simulator.node.ComputingAppliance;
import hu.u_szeged.inf.fog.simulator.util.SimLogger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

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
    }

    
/* 
    Default:
    "name": "Res-1",
    "cpu": "4",
    "memory": "4294967296",
    "instances": "3",
    "provider": "Amazon",
    "location": "EU"

    Extreme:    
    "name": "Res-2",
    "cpu": "2-8",
    "memory": "4294967296",
    "instances": "3",
    "provider": "Amazon"
    

*/
    private void checkCpuAndMemory(Resource resource, ComputingAppliance ca, Capacity capacity, ResourceAgent agent) {
        if (resource.cpu.contains("-")) { // cpu range
            
        } else { // no cpu range 
            double reqCpu = (Double.parseDouble(resource.cpu) * Integer.parseInt(resource.instances));
            long reqMemory = (Long.parseLong(resource.memory) * Integer.parseInt(resource.instances));
            AlterableResourceConstraints arc = new AlterableResourceConstraints(reqCpu, 0.001, reqMemory);
            if (reqCpu  <= capacity.cpu && reqMemory <= reqMemory) {
                boolean isHostable = false;
                for (PhysicalMachine pm : agent.computingAppliance.iaas.machines) {
                    if (pm.isCurrentlyHostableRequest(arc)) {
                        isHostable = true;
                        break;
                    }
                }
                if (isHostable) {
                    System.out.println("Offer: " + agent.name + " - " + ca.name  + " - " + resource.name);
                }
            }
        }
    }
    
    private void generateOffers(AgentApplication app) {
        for (Resource resource : app.resources) {
            for (ResourceAgent agent : ResourceAgent.resourceAgents) {
                for (Map.Entry<ComputingAppliance, Capacity> entry : agent.capacityOfferings.entrySet()) {
                    
                    Capacity capacity = entry.getValue();
                    ComputingAppliance ca = entry.getKey();
                    
                    if (resource.size == null) { // compute type
                        this.checkCpuAndMemory(resource, ca, capacity, agent);
                    } else { // storage type
                        if (Long.parseLong(resource.size) <= capacity.storage) {
                            System.out.println("Offer: " + agent.name + " - " + ca.name  + " - " + resource.name);
                        }
                    }
                }
            }
        }
    }  
}