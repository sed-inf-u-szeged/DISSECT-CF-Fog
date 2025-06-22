package hu.u_szeged.inf.fog.simulator.demo;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;
import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.AlterableResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;
import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;
import hu.u_szeged.inf.fog.simulator.iot.mobility.GeoLocation;
import hu.u_szeged.inf.fog.simulator.node.ComputingAppliance;
import hu.u_szeged.inf.fog.simulator.node.WorkflowComputingAppliance;
import hu.u_szeged.inf.fog.simulator.provider.Instance;
import hu.u_szeged.inf.fog.simulator.util.EnergyDataCollector;
import hu.u_szeged.inf.fog.simulator.util.MapVisualiser;
import hu.u_szeged.inf.fog.simulator.util.SimLogger;
import hu.u_szeged.inf.fog.simulator.util.TimelineVisualiser;
import hu.u_szeged.inf.fog.simulator.util.WorkflowGraphVisualiser;
import hu.u_szeged.inf.fog.simulator.util.xml.WorkflowJobModel;
import hu.u_szeged.inf.fog.simulator.workflow.WorkflowExecutor;
import hu.u_szeged.inf.fog.simulator.workflow.WorkflowJob;
import hu.u_szeged.inf.fog.simulator.workflow.aco.CentralisedAntOptimiser;
import hu.u_szeged.inf.fog.simulator.workflow.aco.ClusterMessenger;
import hu.u_szeged.inf.fog.simulator.workflow.aco.DecentralisedAntOptimiser;
import hu.u_szeged.inf.fog.simulator.workflow.aco.Medoids;
import hu.u_szeged.inf.fog.simulator.workflow.scheduler.MaxMinScheduler;

public class WorkflowSimulation {

    public static void main(String[] args) throws Exception {

        SeedSyncer.modifySeed(System.currentTimeMillis());
        
        SimLogger.setLogging(1, true);
        
        String cloudfile = ScenarioBase.resourcePath + "ELKH_original.xml";
        String fogfile = ScenarioBase.resourcePath + "XML_examples/LPDS_16.xml";

        WorkflowComputingAppliance node0 = new WorkflowComputingAppliance(cloudfile, "node0", new GeoLocation(48.8566, 2.3522), 0);  
        WorkflowComputingAppliance node1 = new WorkflowComputingAppliance(fogfile, "node1", new GeoLocation(51.5074, -0.1278), 0);  
        WorkflowComputingAppliance node3 = new WorkflowComputingAppliance(fogfile, "node3", new GeoLocation(41.9028, 12.4964), 0);  
        WorkflowComputingAppliance node5 = new WorkflowComputingAppliance(fogfile, "node5", new GeoLocation(43.7102, 7.2620), 0); 
        WorkflowComputingAppliance node6 = new WorkflowComputingAppliance(fogfile, "node6", new GeoLocation(55.6761, 12.5683), 0);  
        WorkflowComputingAppliance node8 = new WorkflowComputingAppliance(fogfile, "node8", new GeoLocation(48.2082, 16.3738), 0); 
        WorkflowComputingAppliance node9 = new WorkflowComputingAppliance(fogfile, "node9", new GeoLocation(50.8503, 4.3517), 0);  
        WorkflowComputingAppliance node10 = new WorkflowComputingAppliance(fogfile, "node10", new GeoLocation(46.7762, 23.6213), 0);  
        WorkflowComputingAppliance node11 = new WorkflowComputingAppliance(cloudfile, "node11", new GeoLocation(48.1351, 11.5820), 0);  
        WorkflowComputingAppliance node12 = new WorkflowComputingAppliance(fogfile, "node12", new GeoLocation(53.9076, 27.5754), 0);  
        WorkflowComputingAppliance node13 = new WorkflowComputingAppliance(fogfile, "node13", new GeoLocation(60.1695, 24.9354), 0); 
        WorkflowComputingAppliance node14 = new WorkflowComputingAppliance(fogfile, "node14", new GeoLocation(39.9334, 32.8597), 0);   
        WorkflowComputingAppliance node16 = new WorkflowComputingAppliance(fogfile, "node16", new GeoLocation(37.9838, 23.7275), 0);  
        WorkflowComputingAppliance node17 = new WorkflowComputingAppliance(fogfile, "node17", new GeoLocation(52.3702, 4.8952), 0);  
        WorkflowComputingAppliance node19 = new WorkflowComputingAppliance(cloudfile, "node19", new GeoLocation(51.1657, 10.4515), 0); 
   
        WorkflowComputingAppliance node18 = new WorkflowComputingAppliance(
                AgentTest.createNode("node18", 4, 0.001, 4 * 1_073_741_824L, 32 * 1_073_741_824L, 
                		1, 3.5, 7.5, 62_500, 15, new HashMap<>()),
                new GeoLocation(55.9533, -3.1883));
        
        WorkflowComputingAppliance node15 = new WorkflowComputingAppliance(
                AgentTest.createNode("node15", 4, 0.001, 4 * 1_073_741_824L, 32 * 1_073_741_824L, 
                		1, 3.5, 7.5, 62_500, 15, new HashMap<>()),
                new GeoLocation(40.4168, -3.7038));
        
        WorkflowComputingAppliance node4 = new WorkflowComputingAppliance(
                AgentTest.createNode("node4", 4, 0.001, 4 * 1_073_741_824L, 32 * 1_073_741_824L, 
                		1, 3.5, 7.5, 62_500, 15, new HashMap<>()),
                new GeoLocation(41.0082, 28.9784));
        
        WorkflowComputingAppliance node7 = new WorkflowComputingAppliance(
                AgentTest.createNode("node7", 4, 0.001, 4 * 1_073_741_824L, 32 * 1_073_741_824L, 
                		1, 3.5, 7.5, 62_500, 15, new HashMap<>()),
                new GeoLocation(59.3293, 18.0686));
        
        WorkflowComputingAppliance node2 = new WorkflowComputingAppliance(
                AgentTest.createNode("node2", 4, 0.001, 4 * 1_073_741_824L, 32 * 1_073_741_824L, 
                		1, 3.5, 7.5, 62_500, 15, new HashMap<>()),
                new GeoLocation(52.5200, 13.4050));

        WorkflowComputingAppliance.setDistanceBasedLatency();
        
        HashMap<Integer, ArrayList<WorkflowComputingAppliance>> clusterAssignments = new HashMap<>();
        
        /** --- Single Cluster Approach 
        ArrayList<WorkflowComputingAppliance> nodes = new ArrayList<>(List.of(
            node0, node2, node3, node4, node5, node6, node7, node8, node9, node10, 
            node11, node12, node13, node14, node15, node16, node17, node18, node19
        ));

        clusterAssignments.put(node1, nodes);
        --- */
        
        /** --- Centralised Clustering Approach --- */ 
        ArrayList<WorkflowComputingAppliance> nodesToBeClustered = new ArrayList<>();
        
        nodesToBeClustered.add(node0);
        nodesToBeClustered.add(node1);
        nodesToBeClustered.add(node2);
        nodesToBeClustered.add(node3);
        nodesToBeClustered.add(node4);
        nodesToBeClustered.add(node5);
        nodesToBeClustered.add(node6);
        nodesToBeClustered.add(node7);
        nodesToBeClustered.add(node8);
        nodesToBeClustered.add(node9);
        nodesToBeClustered.add(node10);
        nodesToBeClustered.add(node11);
        nodesToBeClustered.add(node12);
        nodesToBeClustered.add(node13);
        nodesToBeClustered.add(node14);
        nodesToBeClustered.add(node15);
        nodesToBeClustered.add(node16);
        nodesToBeClustered.add(node17);
        nodesToBeClustered.add(node18);
        nodesToBeClustered.add(node19);
        
        clusterAssignments = CentralisedAntOptimiser.runOptimiser(4, nodesToBeClustered, 50, 200, 0.5, 0.2, 0.15, 0.3);
        //clusterAssignments = Medoids.runOptimiser(new ArrayList<>(List.of(4, 18, 15, 13)), nodesToBeClustered);
        //System.exit(0);;
        
        /** --- Self-organising Clustering Approach 
        double[][] globalPheromoneMatrix = DecentralisedAntOptimiser.runOptimiser(ComputingAppliance.allComputingAppliances, 50, 200, 0.75, 0.15);
        ClusterMessenger cm = new ClusterMessenger(globalPheromoneMatrix, ComputingAppliance.allComputingAppliances, 1 * 60 * 1000);
        Timed.simulateUntilLastEvent();
        clusterAssignments = cm.clusterAssignments;
        --- */
        
        // Energy meters
        new EnergyDataCollector("node-0", node0.iaas, true);
        new EnergyDataCollector("node-1", node1.iaas, true);
        new EnergyDataCollector("node-2", node2.iaas, true);
        new EnergyDataCollector("node-3", node3.iaas, true);
        new EnergyDataCollector("node-4", node4.iaas, true);
        new EnergyDataCollector("node-5", node5.iaas, true);
        new EnergyDataCollector("node-6", node6.iaas, true);
        new EnergyDataCollector("node-7", node7.iaas, true);
        new EnergyDataCollector("node-8", node8.iaas, true);
        new EnergyDataCollector("node-9", node9.iaas, true);
        new EnergyDataCollector("node-10", node10.iaas, true);
        new EnergyDataCollector("node-11", node11.iaas, true);
        new EnergyDataCollector("node-12", node12.iaas, true);
        new EnergyDataCollector("node-13", node13.iaas, true);
        new EnergyDataCollector("node-14", node14.iaas, true);
        new EnergyDataCollector("node-15", node15.iaas, true);
        new EnergyDataCollector("node-16", node16.iaas, true);
        new EnergyDataCollector("node-17", node17.iaas, true);
        new EnergyDataCollector("node-18", node18.iaas, true);
        new EnergyDataCollector("node-19", node19.iaas, true);
        
        // The result of the clustering
        CentralisedAntOptimiser.printClusterAssignments(clusterAssignments);
        
        List<ArrayList<WorkflowComputingAppliance>> clusterList = CentralisedAntOptimiser.sortClustersByAveragePairwiseDistance(clusterAssignments);
       
        // Creating the executor engine
        WorkflowExecutor executor = WorkflowExecutor.getIstance();
        
        // Importing and submitting the workflow jobs to each cluster
        String workflowFile = ScenarioBase.resourcePath + "/WORKFLOW_examples/IoT_CyberShake_100.xml";
        
        VirtualAppliance va = new VirtualAppliance("va", 100, 0, false, 1_073_741_824L);
        AlterableResourceConstraints arc = new AlterableResourceConstraints(1, 0.001, 1_073_741_824L);
        Instance instance = new Instance("instance", va, arc, 0.102 / 60 / 60 / 1000, 1);
        
        for (int i = 0; i < clusterList.size(); i++) {
            Pair<String, ArrayList<WorkflowJob>> jobs = WorkflowJobModel.loadWorkflowXml(workflowFile, Integer.toString(i));
            executor.submitJobs(new MaxMinScheduler(clusterList.get(i), instance, null, jobs));
        }

        // Logging
        Timed.simulateUntilLastEvent();
        ScenarioBase.logStreamProcessing();
        WorkflowGraphVisualiser.generateDag(ScenarioBase.scriptPath, ScenarioBase.resultDirectory, workflowFile);
        TimelineVisualiser.generateTimeline(ScenarioBase.resultDirectory);
        MapVisualiser.clusterMapGenerator(clusterAssignments, ScenarioBase.scriptPath, ScenarioBase.resultDirectory);
        EnergyDataCollector.writeToFile(ScenarioBase.resultDirectory);
    }
}
