package hu.u_szeged.inf.fog.simulator.demo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import hu.u_szeged.inf.fog.simulator.energyprovider.*;
import hu.u_szeged.inf.fog.simulator.node.RenewableWorkflowComputingAppliance;
import hu.u_szeged.inf.fog.simulator.workflow.scheduler.RenewableScheduler;
import org.apache.commons.lang3.tuple.Pair;
import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.AlterableResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;
import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;
import hu.u_szeged.inf.fog.simulator.iot.mobility.GeoLocation;
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
import hu.u_szeged.inf.fog.simulator.workflow.scheduler.MaxMinScheduler;

public class RenewableWorkflowSimulation {

    public static void main(String[] args) throws Exception {

        SeedSyncer.modifySeed(System.currentTimeMillis());

        SimLogger.setLogging(1, true);

        String cloudfile = ScenarioBase.resourcePath+"ELKH_original.xml";

        Battery battery = new Battery(10_000, 1000, 3000, 100);
        FossilSource fossilSource = new FossilSource(5000);

        Provider provider = new Provider(battery, new ArrayList<EnergySource>(), fossilSource, 3_600_000, 3_600_001, 1, 1, 50);

        Solar solar = new Solar(1,200);
        Wind wind = new Wind(1, 100);

        provider.addEnergySource(solar);
        provider.addEnergySource(wind);

        RenewableWorkflowComputingAppliance node0 = new RenewableWorkflowComputingAppliance(cloudfile, "node0", new GeoLocation(48.8566, 2.3522), 0, provider);
        RenewableWorkflowComputingAppliance node2 = new RenewableWorkflowComputingAppliance(cloudfile, "node2", new GeoLocation(52.5200, 13.4050), 0, provider);
        RenewableWorkflowComputingAppliance node3 = new RenewableWorkflowComputingAppliance(cloudfile, "node3", new GeoLocation(41.9028, 12.4964), 0, provider);
        RenewableWorkflowComputingAppliance node4 = new RenewableWorkflowComputingAppliance(cloudfile, "node4", new GeoLocation(41.0082, 28.9784), 0, provider);
        RenewableWorkflowComputingAppliance node1 = new RenewableWorkflowComputingAppliance(cloudfile, "node1", new GeoLocation(51.5074, -0.1278), 0, provider);
        RenewableWorkflowComputingAppliance node5 = new RenewableWorkflowComputingAppliance(cloudfile, "node5", new GeoLocation(43.7102, 7.2620), 0, provider);
        RenewableWorkflowComputingAppliance node6 = new RenewableWorkflowComputingAppliance(cloudfile, "node6", new GeoLocation(55.6761, 12.5683), 0, provider);
        RenewableWorkflowComputingAppliance node7 = new RenewableWorkflowComputingAppliance(cloudfile, "node7", new GeoLocation(59.3293, 18.0686), 0, provider);
        RenewableWorkflowComputingAppliance node8 = new RenewableWorkflowComputingAppliance(cloudfile, "node8", new GeoLocation(48.2082, 16.3738), 0, provider);
        RenewableWorkflowComputingAppliance node9 = new RenewableWorkflowComputingAppliance(cloudfile, "node9", new GeoLocation(50.8503, 4.3517), 0, provider);
        RenewableWorkflowComputingAppliance node10 = new RenewableWorkflowComputingAppliance(cloudfile, "node10", new GeoLocation(46.7762, 23.6213), 0, provider);
        RenewableWorkflowComputingAppliance node11 = new RenewableWorkflowComputingAppliance(cloudfile, "node11", new GeoLocation(48.1351, 11.5820), 0, provider);
        RenewableWorkflowComputingAppliance node12 = new RenewableWorkflowComputingAppliance(cloudfile, "node12", new GeoLocation(53.9076, 27.5754), 0, provider);
        RenewableWorkflowComputingAppliance node13 = new RenewableWorkflowComputingAppliance(cloudfile, "node13", new GeoLocation(60.1695, 24.9354), 0, provider);
        RenewableWorkflowComputingAppliance node14 = new RenewableWorkflowComputingAppliance(cloudfile, "node14", new GeoLocation(39.9334, 32.8597), 0, provider);
        RenewableWorkflowComputingAppliance node15 = new RenewableWorkflowComputingAppliance(cloudfile, "node15", new GeoLocation(40.4168, -3.7038), 0, provider);
        RenewableWorkflowComputingAppliance node16 = new RenewableWorkflowComputingAppliance(cloudfile, "node16", new GeoLocation(37.9838, 23.7275), 0, provider);
        RenewableWorkflowComputingAppliance node17 = new RenewableWorkflowComputingAppliance(cloudfile, "node17", new GeoLocation(52.3702, 4.8952), 0, provider);
        RenewableWorkflowComputingAppliance node18 = new RenewableWorkflowComputingAppliance(cloudfile, "node18", new GeoLocation(55.9533, -3.1883), 0, provider);
        RenewableWorkflowComputingAppliance node19 = new RenewableWorkflowComputingAppliance(cloudfile, "node19", new GeoLocation(51.1657, 10.4515), 0, provider);

        WorkflowComputingAppliance.setDistanceBasedLatency();

        HashMap<WorkflowComputingAppliance, ArrayList<WorkflowComputingAppliance>> clusterAssignments = new HashMap<>();

        /** --- Single Cluster Approach --- */
        ArrayList<WorkflowComputingAppliance> nodes = new ArrayList<>(List.of(
                node1, node2, node3, node4, node5, node6, node7, node8, node9, node10,
                node11, node12, node13, node14, node15, node16, node17, node18, node19
        ));

        clusterAssignments.put(node0, nodes);



        /** --- Predefined Centroids Approach
         ArrayList<WorkflowComputingAppliance> centerNodes = new ArrayList<>();

         centerNodes.add(node5);
         centerNodes.add(node12);
         centerNodes.add(node13);
         centerNodes.add(node19);

         ArrayList<WorkflowComputingAppliance> nodesToBeClustered = new ArrayList<>();

         nodesToBeClustered.add(node1);
         nodesToBeClustered.add(node2);
         nodesToBeClustered.add(node3);
         nodesToBeClustered.add(node4);
         nodesToBeClustered.add(node6);
         nodesToBeClustered.add(node7);
         nodesToBeClustered.add(node8);
         nodesToBeClustered.add(node9);
         nodesToBeClustered.add(node10);
         nodesToBeClustered.add(node11);
         nodesToBeClustered.add(node14);
         nodesToBeClustered.add(node15);
         nodesToBeClustered.add(node16);
         nodesToBeClustered.add(node17);
         nodesToBeClustered.add(node18);
         nodesToBeClustered.add(node20);

         clusterAssignments = CentralisedAntOptimiser.runOptimiser(centerNodes, nodesToBeClustered, 50, 50, 0.75, 0.75, 0.25, 0.15);
         --- */


        /** --- Adaptive Clustering Approach
         double[][] globalPheromoneMatrix = DecentralisedAntOptimiser.runOptimiser(ComputingAppliance.allComputingAppliances, 10, 50, 0.75, 0.15);
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

        VirtualAppliance va = new VirtualAppliance("va", 100, 0, false, 1073741824L);
        AlterableResourceConstraints arc = new AlterableResourceConstraints(4, 0.001, 4294967296L);
        Instance instance = new Instance("instance", va, arc, 0.102 / 60 / 60 / 1000, 1);

        for (int i = 0; i < clusterList.size(); i++) {
            Pair<String, ArrayList<WorkflowJob>> jobs = WorkflowJobModel.loadWorkflowXml(workflowFile, "-" + i);
            executor.submitJobs(new RenewableScheduler(clusterList.get(i), instance, null, jobs, provider, 50, true));
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
