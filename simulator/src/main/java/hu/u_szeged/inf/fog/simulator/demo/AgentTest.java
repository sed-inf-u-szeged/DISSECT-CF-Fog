package hu.u_szeged.inf.fog.simulator.demo;

import java.io.File;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.PowerState;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;
import hu.mta.sztaki.lpds.cloud.simulator.util.PowerTransitionGenerator;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.io.StorageObject;
import hu.u_szeged.inf.fog.simulator.agent.AgentApplication;
import hu.u_szeged.inf.fog.simulator.agent.Deployment;
import hu.u_szeged.inf.fog.simulator.agent.ResourceAgent;
import hu.u_szeged.inf.fog.simulator.agent.ResourceAgent.Capacity;
import hu.u_szeged.inf.fog.simulator.iot.mobility.GeoLocation;
import hu.u_szeged.inf.fog.simulator.node.ComputingAppliance;
import hu.u_szeged.inf.fog.simulator.util.SimLogger;
import hu.u_szeged.inf.fog.simulator.util.agent.AgentApplicationReader;

public class AgentTest {
	
    public static String appInputFile = ScenarioBase.resourcePath + "AGENT_examples" + File.separator + "app_input.json";
    
    public static String rankingScriptDir = "D:\\Documents\\swarm-deployment\\for_simulator";
    
    public static String rankingMethodName = "vote_wo_reliability";
    
    
    public static void main(String[] args) throws NetworkException {
        
        SimLogger.setLogging(1, true);
        
        String cloudfile = ScenarioBase.resourcePath + "ELKH_original.xml";
        
        // clouds
        ComputingAppliance cloud1 = new ComputingAppliance(cloudfile, "cloud1", new GeoLocation(47.45, 19.04),  "EU", "AWS");
        ComputingAppliance cloud2 = new ComputingAppliance(cloudfile, "cloud2", new GeoLocation(52.52, 13.40),  "EU", "Google");
        ComputingAppliance cloud3 = new ComputingAppliance(cloudfile, "cloud3", new GeoLocation(48.85, 2.35),   "EU", "Azure");
        
        ComputingAppliance cloud4 = new ComputingAppliance(cloudfile, "cloud4", new GeoLocation(40.71, -74.00), "US", "AWS");
        ComputingAppliance cloud5 = new ComputingAppliance(cloudfile, "cloud5", new GeoLocation(43.7, -79.42),  "US", "Google");
        
        ComputingAppliance.setConnection(cloud1, 65);
        ComputingAppliance.setConnection(cloud2, 76);
        ComputingAppliance.setConnection(cloud3, 95);
        ComputingAppliance.setConnection(cloud4, 51);
        ComputingAppliance.setConnection(cloud5, 74);
                
        // agents - https://aws.amazon.com/ec2/pricing/on-demand/ 
        HashMap<ComputingAppliance, Capacity> capacityOffer1 = new HashMap<ComputingAppliance, Capacity>(); 
        capacityOffer1.put(cloud1, new Capacity(12.0, 20 * 1_073_741_824L, 100 * 1_073_741_824L)); // 12 CPU - 20 GB memory - 100 GB storage
        new ResourceAgent("Agent-1", capacityOffer1, 0.0116);
        
        HashMap<ComputingAppliance, Capacity> capacityOffer2 = new HashMap<ComputingAppliance, Capacity>(); 
        capacityOffer2.put(cloud2, new Capacity(4.0, 10 * 20 * 1_073_741_824L, 100 * 1_073_741_824L)); 
        new ResourceAgent("Agent-2", capacityOffer2, 0.0110);
        
        HashMap<ComputingAppliance, Capacity> capacityOffer3 = new HashMap<ComputingAppliance, Capacity>(); 
        capacityOffer3.put(cloud3, new Capacity(10.0, 4 * 1_073_741_824L, 100 * 1_073_741_824L)); 
        new ResourceAgent("Agent-3", capacityOffer3, 0.0115);
                
        // image registry service
        final EnumMap<PowerTransitionGenerator.PowerStateKind, Map<String, PowerState>> transitions =
                PowerTransitionGenerator.generateTransitions(20, 200, 300, 10, 20);
        HashMap<String, Integer> latencyMap = new HashMap<String, Integer>();
        Repository repository = new Repository(Long.MAX_VALUE, "Image Service", 125_000, 125_000, 125_000, latencyMap, 
                transitions.get(PowerTransitionGenerator.PowerStateKind.storage), 
                transitions.get(PowerTransitionGenerator.PowerStateKind.network));
        
        Deployment.setImageRegistry(repository, 100);
        
        // generating an application demand 
        AgentApplication app1 = AgentApplicationReader.readAgentApplications(appInputFile);
        
        new Deployment(app1, 100);
        
        Timed.simulateUntilLastEvent();
        
        // logging
        for(ComputingAppliance ca : ComputingAppliance.getAllComputingAppliances()) {
            SimLogger.logRes(ca.name + ":");
            SimLogger.logRes("\t Contents:");
            for(StorageObject so : ca.iaas.repositories.get(0).contents()) {
                SimLogger.logRes("\t\t" + so);
            }
            SimLogger.logRes("\t VMs:");
            for(VirtualMachine vm : ca.iaas.listVMs()) {
                SimLogger.logRes("\t\t" + vm.toString());
            }
        }
        
        SimLogger.logRes("Image Registry's contents");
        for(StorageObject so : Deployment.imageRegistry.contents()) {
            SimLogger.logRes("\t" + so);
        }
        
        SimLogger.logRes("Completed at: " + Timed.getFireCount());
    }
}