package hu.u_szeged.inf.fog.simulator.node;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager.VMManagementException;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.AlterableResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ConsumptionEventAdapter;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;
import hu.mta.sztaki.lpds.cloud.simulator.io.StorageObject;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;
import hu.u_szeged.inf.fog.simulator.agent.AgentApplication;
import hu.u_szeged.inf.fog.simulator.agent.ResourceAgent;
import hu.u_szeged.inf.fog.simulator.iot.mobility.GeoLocation;
import hu.u_szeged.inf.fog.simulator.util.SimLogger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

public class AgentComputingAppliance extends ComputingAppliance implements ResourceAgent {
    
    String location;

    public VirtualMachine agent;
    
    public AgentComputingAppliance(String file, String name, GeoLocation geoLocation, String location)  {
        super(file, name, geoLocation, 0);
        this.location = location;
        this.startAgent();
    }
    
    private void startAgent() {
        try {
            this.iaas.repositories.get(0).registerObject(AgentComputingAppliance.agentVa);
            VirtualMachine vm = this.iaas.requestVM(AgentComputingAppliance.agentVa, AgentComputingAppliance.agentArc,
                    this.iaas.repositories.get(0), 1)[0];
            this.agent = vm;
            SimLogger.logRun(name + " agent is turned on at: " + Timed.getFireCount());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void setConnection(ComputingAppliance that, int latency) {
        for (ComputingAppliance ca : ComputingAppliance.getAllComputingAppliances()) {
            if (ca != that) {
                ca.neighbors.add(that);
                ca.iaas.repositories.get(0).addLatencies(that.iaas.repositories.get(0).getName(), latency);
            }
        }
    }

    @Override
    public void broadcast(AgentApplication app, int bcastMessageSize) {
        AgentComputingAppliance initialAgent = this;
        
        List<AgentComputingAppliance> filteredNeighbors = this.neighbors.stream()
                .map(aca -> (AgentComputingAppliance) aca) 
                .filter(aca -> aca.agent.getState().equals(VirtualMachine.State.RUNNING))  
                .collect(Collectors.toList());

        for (ComputingAppliance neighbor : filteredNeighbors) {
            
            String reqName = this.name + "-" + neighbor.name + "-" + app.name + "-req";
            StorageObject reqMessage = new StorageObject(reqName, bcastMessageSize, false);
            this.iaas.repositories.get(0).registerObject(reqMessage);
            try {
                this.iaas.repositories.get(0).requestContentDelivery(
                        reqName, neighbor.iaas.repositories.get(0), new ConsumptionEventAdapter() {
                            
                            @Override
                            public void conComplete() {
                                iaas.repositories.get(0).deregisterObject(reqName);
                                String resName = neighbor.name + "-" + name + "-" + app.name + "-res";
                                StorageObject resMessage = new StorageObject(resName, bcastMessageSize, false);
                                neighbor.iaas.repositories.get(0).registerObject(resMessage);
                                
                                try {
                                    neighbor.iaas.repositories.get(0).requestContentDelivery(
                                            resName, iaas.repositories.get(0), new ConsumptionEventAdapter() {
                                                
                                                @Override
                                                public void conComplete() {
                                                    neighbor.iaas.repositories.get(0).deregisterObject(reqMessage);
                                                    neighbor.iaas.repositories.get(0).deregisterObject(resMessage);
                                                    app.bcastCounter++;
                                                    if (app.bcastCounter == filteredNeighbors.size()) {
                                                        initialAgent.deploy(app);
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
        HashSet<AgentComputingAppliance> offerList = this.generateOffers(app);
        SimLogger.logRun("Offer list for " + app.name + " at: " + Timed.getFireCount() + " " + offerList);
        
        ArrayList<AgentComputingAppliance> offers = new ArrayList<>(offerList);
        int random = new Random().nextInt(offers.size());
        AgentComputingAppliance aca = offers.get(random);
        
        double cpu = (double) app.requirements.get("CPU");
        long memory = (long) app.requirements.get("RAM");       
        AlterableResourceConstraints arc = new AlterableResourceConstraints(cpu, 0.001, memory);
        VirtualAppliance va = new VirtualAppliance("agentVa", 1, 0, false, 536870912L); 
        aca.iaas.repositories.get(0).registerObject(va);
        
        try {
            aca.iaas.requestVM(va, arc, aca.iaas.repositories.get(0), 1);
        } catch (VMManagementException e) {
            e.printStackTrace();
        }
    }

    
    private HashSet<AgentComputingAppliance> generateOffers(AgentApplication app) {
        double cpu = (double) app.requirements.get("CPU");
        long memory = (long) app.requirements.get("RAM");
        String location = (String) app.requirements.get("Location");
        
        AlterableResourceConstraints arc = new AlterableResourceConstraints(cpu, 0.001, memory);
        for (ComputingAppliance ca : ComputingAppliance.getAllComputingAppliances()) {
            AgentComputingAppliance aca = (AgentComputingAppliance) ca;

            if (aca.location.equals(location)) {
                for (PhysicalMachine pm : aca.iaas.machines) {
                    if (pm.isCurrentlyHostableRequest(arc)) {
                        app.offerList.add(aca);
                        break;
                    }
                }
            }   
        }
        /*
        for(PhysicalMachine pm : iaas.machines) {
            pmAavailableCpu += pm.availableCapacities.getRequiredCPUs() ;
            pmGetCpu +=  pm.getCapacities().getRequiredCPUs();
        }
        */
        
        return app.offerList;
    }

    @Override
    public String toString() {
        return "AgentComputingAppliance [name=" + name + "]";
    }
}