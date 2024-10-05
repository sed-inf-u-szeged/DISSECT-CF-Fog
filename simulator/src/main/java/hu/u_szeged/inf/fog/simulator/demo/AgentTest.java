package hu.u_szeged.inf.fog.simulator.demo;

import java.util.Random;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;
import hu.u_szeged.inf.fog.simulator.agent.AgentApplication;
import hu.u_szeged.inf.fog.simulator.iot.mobility.GeoLocation;
import hu.u_szeged.inf.fog.simulator.node.AgentComputingAppliance;
import hu.u_szeged.inf.fog.simulator.node.ComputingAppliance;


public class AgentTest {
    
    public static void main(String[] args) throws NetworkException {
        
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

        // randomly submit the app 
        int random = new Random().nextInt(ComputingAppliance.getAllComputingAppliances().size());
        AgentComputingAppliance initalRa = (AgentComputingAppliance) ComputingAppliance.getAllComputingAppliances().get(random);
        
        initalRa.broadcast(app1, 100);
        
        /* */
        AgentApplication app2 = new AgentApplication("app2");
        app2.requirements.put("CPU", 6.0);
        app2.requirements.put("RAM", 4_294_967_296L);
        app2.requirements.put("Location", "US");
        
        random = new Random().nextInt(ComputingAppliance.getAllComputingAppliances().size());
        initalRa = (AgentComputingAppliance) ComputingAppliance.getAllComputingAppliances().get(random);
        initalRa.broadcast(app2, 100);
        
        
        Timed.simulateUntilLastEvent();
        
        for(ComputingAppliance ca : ComputingAppliance.getAllComputingAppliances()) {
            System.out.println(ca.iaas.repositories.get(0).contents());
        }
        System.out.println(Timed.getFireCount());
    }
    
    

}
