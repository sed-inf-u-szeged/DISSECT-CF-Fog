package hu.u_szeged.inf.fog.simulator.demo;

import java.util.ArrayList;
import java.util.HashMap;

import hu.u_szeged.inf.fog.simulator.iot.mobility.GeoLocation;
import hu.u_szeged.inf.fog.simulator.node.WorkflowComputingAppliance;
import hu.u_szeged.inf.fog.simulator.workflow.aco.CentralisedAntOptimiser;

public class CentralisedAcoTest {

    public static void main(String[] args) throws Exception {
        
        String cloudfile = ScenarioBase.resourcePath+"LPDS_original.xml";

        WorkflowComputingAppliance fog1 = new WorkflowComputingAppliance(cloudfile, "fog1", new GeoLocation(0, 10), 1000);
        WorkflowComputingAppliance fog2 = new WorkflowComputingAppliance(cloudfile, "fog2", new GeoLocation(50, 15), 1000);
        WorkflowComputingAppliance fog3 = new WorkflowComputingAppliance(cloudfile, "fog3", new GeoLocation(20, 0), 1000);
        WorkflowComputingAppliance fog4 = new WorkflowComputingAppliance(cloudfile, "fog4", new GeoLocation(10, -10), 1000);
        WorkflowComputingAppliance fog5 = new WorkflowComputingAppliance(cloudfile, "fog5", new GeoLocation(25, 100), 1000);
        WorkflowComputingAppliance fog6 = new WorkflowComputingAppliance(cloudfile, "fog6", new GeoLocation(30, -100), 1000);
        WorkflowComputingAppliance fog7 = new WorkflowComputingAppliance(cloudfile, "fog7", new GeoLocation(-25, 23), 1000);
        WorkflowComputingAppliance fog8 = new WorkflowComputingAppliance(cloudfile, "fog8", new GeoLocation(40, 10), 1000);
        WorkflowComputingAppliance fog9 = new WorkflowComputingAppliance(cloudfile, "fog9", new GeoLocation(35, -105), 1000);
        WorkflowComputingAppliance fog10 = new WorkflowComputingAppliance(cloudfile, "fog10", new GeoLocation(-20, 130), 1000);
        WorkflowComputingAppliance fog11 = new WorkflowComputingAppliance(cloudfile, "fog11", new GeoLocation(-30, 140), 1000);
        WorkflowComputingAppliance fog12 = new WorkflowComputingAppliance(cloudfile, "fog12", new GeoLocation(-30, 150), 1000);
        WorkflowComputingAppliance fog13 = new WorkflowComputingAppliance(cloudfile, "fog13", new GeoLocation(-40, -70), 1000);
        WorkflowComputingAppliance fog14 = new WorkflowComputingAppliance(cloudfile, "fog14", new GeoLocation(-30, -60), 1000);
        WorkflowComputingAppliance fog15 = new WorkflowComputingAppliance(cloudfile, "fog15", new GeoLocation(-10, -40), 1000);
        WorkflowComputingAppliance fog16 = new WorkflowComputingAppliance(cloudfile, "fog16", new GeoLocation(40, -120), 1000);
        WorkflowComputingAppliance fog17 = new WorkflowComputingAppliance(cloudfile, "fog17", new GeoLocation(35, 80), 1000);
        WorkflowComputingAppliance fog18 = new WorkflowComputingAppliance(cloudfile, "fog18", new GeoLocation(70, -40), 1000);
        WorkflowComputingAppliance fog19 = new WorkflowComputingAppliance(cloudfile, "fog19", new GeoLocation(30, 100), 1000);
        WorkflowComputingAppliance fog20 = new WorkflowComputingAppliance(cloudfile, "fog20", new GeoLocation(50, 50), 1000);
        
        ArrayList<WorkflowComputingAppliance> centerNodes = new ArrayList<WorkflowComputingAppliance>();
        centerNodes.add(fog1);
        centerNodes.add(fog2);
        centerNodes.add(fog3);
        
        ArrayList<WorkflowComputingAppliance> nodesToBeClustered = new ArrayList<WorkflowComputingAppliance>();
        
        nodesToBeClustered.add(fog4);
        nodesToBeClustered.add(fog5);
        nodesToBeClustered.add(fog6);
        nodesToBeClustered.add(fog7);
        nodesToBeClustered.add(fog8);
        nodesToBeClustered.add(fog9);
        nodesToBeClustered.add(fog10);
        nodesToBeClustered.add(fog11);
        nodesToBeClustered.add(fog12);
        nodesToBeClustered.add(fog13);
        nodesToBeClustered.add(fog14);
        nodesToBeClustered.add(fog15);
        nodesToBeClustered.add(fog16);
        nodesToBeClustered.add(fog17);
        nodesToBeClustered.add(fog18);
        nodesToBeClustered.add(fog19);
        nodesToBeClustered.add(fog20);
        
        HashMap<WorkflowComputingAppliance, ArrayList<WorkflowComputingAppliance>> clusterAssignment;
        
        clusterAssignment = CentralisedAntOptimiser.runOptimiser(centerNodes, nodesToBeClustered, 10, 50, 0.9, 0.3, 0.1, 0.1);
        
        CentralisedAntOptimiser.printClusterAssignments(clusterAssignment);
    }
}
