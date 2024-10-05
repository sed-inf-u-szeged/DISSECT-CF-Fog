package hu.u_szeged.inf.fog.simulator.demo;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;
import hu.u_szeged.inf.fog.simulator.agent.AgentApplication;
import hu.u_szeged.inf.fog.simulator.agent.ResourceAgent.Deployment;
import hu.u_szeged.inf.fog.simulator.iot.mobility.GeoLocation;
import hu.u_szeged.inf.fog.simulator.node.AgentComputingAppliance;
import hu.u_szeged.inf.fog.simulator.node.ComputingAppliance;
import hu.u_szeged.inf.fog.simulator.util.SimLogger;

public class AgentTest {
    
    public static void main(String[] args) throws NetworkException {
        SimLogger.setLogging(1, true);
        
        String cloudfile = ScenarioBase.resourcePath + "ELKH_original.xml";

        // agents
        AgentComputingAppliance cloud1 = new AgentComputingAppliance(cloudfile, "cloud1", new GeoLocation(47.45, 19.04),  "EU");
        AgentComputingAppliance cloud2 = new AgentComputingAppliance(cloudfile, "cloud2", new GeoLocation(52.52, 13.40),  "EU");
        AgentComputingAppliance cloud3 = new AgentComputingAppliance(cloudfile, "cloud3", new GeoLocation(48.85, 2.35),   "EU");
        AgentComputingAppliance cloud4 = new AgentComputingAppliance(cloudfile, "cloud4", new GeoLocation(40.71, -74.00), "US");
        AgentComputingAppliance cloud5 = new AgentComputingAppliance(cloudfile, "cloud5", new GeoLocation(43.7, -79.42),  "US");
        
        AgentComputingAppliance.setConnection(cloud1, 65);
        AgentComputingAppliance.setConnection(cloud2, 76);
        AgentComputingAppliance.setConnection(cloud3, 95);
        AgentComputingAppliance.setConnection(cloud4, 51);
        AgentComputingAppliance.setConnection(cloud5, 74);
        
        // generating an application demand 
        AgentApplication app1 = new AgentApplication("app1");
        app1.requirements.put("CPU", 4.0);
        app1.requirements.put("RAM", 4_294_967_296L);
        app1.requirements.put("Location", "EU");

        new Deployment(app1, 100);
        
        /* */
        AgentApplication app2 = new AgentApplication("app2");
        app2.requirements.put("CPU", 6.0);
        app2.requirements.put("RAM", 4_294_967_296L);
        app2.requirements.put("Location", "US");
        
        new Deployment(app2, 100);
        
        
        Timed.simulateUntilLastEvent();
        
        // logging
        for(ComputingAppliance ca : ComputingAppliance.getAllComputingAppliances()) {
            for(VirtualMachine vm : ca.iaas.listVMs()) {
                SimLogger.logRes(vm.toString());
            }
        }
        SimLogger.logRes(Timed.getFireCount());
    }
}