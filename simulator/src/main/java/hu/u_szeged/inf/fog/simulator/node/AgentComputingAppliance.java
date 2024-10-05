package hu.u_szeged.inf.fog.simulator.node;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.AlterableResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ConsumptionEventAdapter;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;
import hu.mta.sztaki.lpds.cloud.simulator.io.StorageObject;
import hu.u_szeged.inf.fog.simulator.agent.AgentApplication;
import hu.u_szeged.inf.fog.simulator.agent.ResourceAgent;
import hu.u_szeged.inf.fog.simulator.iot.mobility.GeoLocation;
import java.util.HashSet;

public class AgentComputingAppliance extends ComputingAppliance implements ResourceAgent {
    
    String location;

    public AgentComputingAppliance(String file, String name, GeoLocation geoLocation, String location)  {
        super(file, name, geoLocation, 0);
        this.location = location;
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
        System.out.println(this.name + " picked up " + app.name);
        AgentComputingAppliance initialAgent = this;
        for (ComputingAppliance neighbor : this.neighbors) {
            
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
                                                    if (app.bcastCounter == (neighbors.size() - 1)) {
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
        
        System.out.println("Offer List " + offerList);
    }

    /*
    for(PhysicalMachine pm : iaas.machines) {
        pmAavailableCpu += pm.availableCapacities.getRequiredCPUs() ;
        pmGetCpu +=  pm.getCapacities().getRequiredCPUs();
        pmAvailableMem += pm.availableCapacities.getRequiredMemory();
        pmGetMem += pm.getCapacities().getRequiredMemory();
    }
    */
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
        return app.offerList;
    }

    @Override
    public String toString() {
        return "AgentComputingAppliance [name=" + name + "]";
    }
}