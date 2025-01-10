package hu.u_szeged.inf.fog.simulator.demo;

import java.util.ArrayList;
import java.util.HashMap;
import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;
import hu.u_szeged.inf.fog.simulator.iot.mobility.GeoLocation;
import hu.u_szeged.inf.fog.simulator.node.WorkflowComputingAppliance;
import hu.u_szeged.inf.fog.simulator.util.MapVisualiser;
import hu.u_szeged.inf.fog.simulator.workflow.aco.CentralisedAntOptimiser;

public class CentralisedAcoTest {

    public static void main(String[] args) throws Exception {

        SeedSyncer.modifySeed(System.currentTimeMillis());
        
        String cloudfile = ScenarioBase.resourcePath+"LPDS_original.xml";

        WorkflowComputingAppliance fog1 = new WorkflowComputingAppliance(cloudfile, "fog1", new GeoLocation(48.8566, 2.3522), 0);  
        WorkflowComputingAppliance fog2 = new WorkflowComputingAppliance(cloudfile, "fog2", new GeoLocation(51.5074, -0.1278), 0);  
        WorkflowComputingAppliance fog3 = new WorkflowComputingAppliance(cloudfile, "fog3", new GeoLocation(52.5200, 13.4050), 0);  
        WorkflowComputingAppliance fog4 = new WorkflowComputingAppliance(cloudfile, "fog4", new GeoLocation(41.9028, 12.4964), 0);  
        WorkflowComputingAppliance fog5 = new WorkflowComputingAppliance(cloudfile, "fog5", new GeoLocation(41.0082, 28.9784), 0);  
        WorkflowComputingAppliance fog6 = new WorkflowComputingAppliance(cloudfile, "fog6", new GeoLocation(43.7102, 7.2620), 0); 
        WorkflowComputingAppliance fog7 = new WorkflowComputingAppliance(cloudfile, "fog7", new GeoLocation(55.6761, 12.5683), 0);  
        WorkflowComputingAppliance fog8 = new WorkflowComputingAppliance(cloudfile, "fog8", new GeoLocation(59.3293, 18.0686), 0);  
        WorkflowComputingAppliance fog9 = new WorkflowComputingAppliance(cloudfile, "fog9", new GeoLocation(48.2082, 16.3738), 0); 
        WorkflowComputingAppliance fog10 = new WorkflowComputingAppliance(cloudfile, "fog10", new GeoLocation(50.8503, 4.3517), 0);  
        WorkflowComputingAppliance fog11 = new WorkflowComputingAppliance(cloudfile, "fog11", new GeoLocation(51.1657, 10.4515), 0);  
        WorkflowComputingAppliance fog12 = new WorkflowComputingAppliance(cloudfile, "fog12", new GeoLocation(48.1351, 11.5820), 0);  
        WorkflowComputingAppliance fog13 = new WorkflowComputingAppliance(cloudfile, "fog13", new GeoLocation(55.7558, 37.6173), 0);  
        WorkflowComputingAppliance fog14 = new WorkflowComputingAppliance(cloudfile, "fog14", new GeoLocation(60.1695, 24.9354), 0); 
        WorkflowComputingAppliance fog15 = new WorkflowComputingAppliance(cloudfile, "fog15", new GeoLocation(39.9334, 32.8597), 0); 
        WorkflowComputingAppliance fog16 = new WorkflowComputingAppliance(cloudfile, "fog16", new GeoLocation(40.4168, -3.7038), 0);  
        WorkflowComputingAppliance fog17 = new WorkflowComputingAppliance(cloudfile, "fog17", new GeoLocation(37.9838, 23.7275), 0);  
        WorkflowComputingAppliance fog18 = new WorkflowComputingAppliance(cloudfile, "fog18", new GeoLocation(52.3702, 4.8952), 0);  
        WorkflowComputingAppliance fog19 = new WorkflowComputingAppliance(cloudfile, "fog19", new GeoLocation(55.9533, -3.1883), 0); 
        WorkflowComputingAppliance fog20 = new WorkflowComputingAppliance(cloudfile, "fog20", new GeoLocation(51.1657, 10.4515), 0); 


        ArrayList<WorkflowComputingAppliance> centerNodes = new ArrayList<WorkflowComputingAppliance>();
        centerNodes.add(fog4);
        centerNodes.add(fog13);
        centerNodes.add(fog19);
        
        ArrayList<WorkflowComputingAppliance> nodesToBeClustered = new ArrayList<WorkflowComputingAppliance>();
        
        nodesToBeClustered.add(fog1);
        nodesToBeClustered.add(fog2);
        nodesToBeClustered.add(fog3);
        nodesToBeClustered.add(fog5);
        nodesToBeClustered.add(fog6);
        nodesToBeClustered.add(fog7);
        nodesToBeClustered.add(fog8);
        nodesToBeClustered.add(fog9);
        nodesToBeClustered.add(fog10);
        nodesToBeClustered.add(fog11);
        nodesToBeClustered.add(fog12);
        nodesToBeClustered.add(fog14);
        nodesToBeClustered.add(fog15);
        nodesToBeClustered.add(fog16);
        nodesToBeClustered.add(fog17);
        nodesToBeClustered.add(fog18);
        nodesToBeClustered.add(fog20);
        
        HashMap<WorkflowComputingAppliance, ArrayList<WorkflowComputingAppliance>> clusterAssignment;
        
        clusterAssignment = CentralisedAntOptimiser.runOptimiser(centerNodes, nodesToBeClustered, 200, 10, 0.75, 0.75, 0.25, 0.15);
        
        CentralisedAntOptimiser.printClusterAssignments(clusterAssignment);
        MapVisualiser.clusterMapGenerator(clusterAssignment, ScenarioBase.scriptPath, ScenarioBase.resultDirectory);
    }
}
