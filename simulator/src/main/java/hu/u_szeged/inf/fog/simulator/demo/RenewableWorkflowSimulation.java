package hu.u_szeged.inf.fog.simulator.demo;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.AlterableResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;
import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;
import hu.u_szeged.inf.fog.simulator.energyprovider.*;
import hu.u_szeged.inf.fog.simulator.iot.mobility.GeoLocation;
import hu.u_szeged.inf.fog.simulator.node.RenewableWorkflowComputingAppliance;
import hu.u_szeged.inf.fog.simulator.node.WorkflowComputingAppliance;
import hu.u_szeged.inf.fog.simulator.provider.Instance;
import hu.u_szeged.inf.fog.simulator.util.*;
import hu.u_szeged.inf.fog.simulator.util.xml.WorkflowJobModel;
import hu.u_szeged.inf.fog.simulator.workflow.WorkflowExecutor;
import hu.u_szeged.inf.fog.simulator.workflow.WorkflowJob;
import hu.u_szeged.inf.fog.simulator.workflow.aco.CentralisedAntOptimiser;
import hu.u_szeged.inf.fog.simulator.workflow.scheduler.RenewableScheduler;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class RenewableWorkflowSimulation {

    public static void main(String[] args) throws Exception {

        SeedSyncer.modifySeed(System.currentTimeMillis());

        SimLogger.setLogging(1, true);

        String cloudfile = ScenarioBase.resourcePath+"ELKH_original.xml";

        Battery battery1 = new Battery(1000,  0);
        Battery battery2 = new Battery(1000,  0);

        FossilSource fossilSource = new FossilSource(1);

        Provider provider1 = new Provider("01",battery1, new ArrayList<EnergySource>(), fossilSource, 1000, 0.2889F, 0.2889F, 50,50);
        Provider provider2 = new Provider("02",battery2, new ArrayList<EnergySource>(), fossilSource, 1000, 0.2889F, 0.2889F, 50,50);

        Solar solar1 = new Solar(1,100);
        Solar solar2 = new Solar(1,100);
        Wind wind1 = new Wind(1, 100);
        Wind wind2 = new Wind(1, 100);

        provider1.addEnergySource(solar1);
        provider1.addEnergySource(wind1);
        provider2.addEnergySource(solar2);
        provider2.addEnergySource(wind2);

        RenewableWorkflowComputingAppliance node0 = new RenewableWorkflowComputingAppliance(cloudfile, "node0", new GeoLocation(48.8566, 2.3522), 0, provider1);
        RenewableWorkflowComputingAppliance node2 = new RenewableWorkflowComputingAppliance(cloudfile, "node2", new GeoLocation(52.5200, 13.4050), 0, provider1);
        RenewableWorkflowComputingAppliance node3 = new RenewableWorkflowComputingAppliance(cloudfile, "node3", new GeoLocation(41.9028, 12.4964), 0, provider1);
        RenewableWorkflowComputingAppliance node4 = new RenewableWorkflowComputingAppliance(cloudfile, "node4", new GeoLocation(41.0082, 28.9784), 0, provider1);
        RenewableWorkflowComputingAppliance node1 = new RenewableWorkflowComputingAppliance(cloudfile, "node1", new GeoLocation(51.5074, -0.1278), 0, provider1);
        RenewableWorkflowComputingAppliance node5 = new RenewableWorkflowComputingAppliance(cloudfile, "node5", new GeoLocation(43.7102, 7.2620), 0, provider1);
        RenewableWorkflowComputingAppliance node6 = new RenewableWorkflowComputingAppliance(cloudfile, "node6", new GeoLocation(55.6761, 12.5683), 0, provider1);
        RenewableWorkflowComputingAppliance node7 = new RenewableWorkflowComputingAppliance(cloudfile, "node7", new GeoLocation(59.3293, 18.0686), 0, provider1);
        RenewableWorkflowComputingAppliance node8 = new RenewableWorkflowComputingAppliance(cloudfile, "node8", new GeoLocation(48.2082, 16.3738), 0, provider1);
        RenewableWorkflowComputingAppliance node9 = new RenewableWorkflowComputingAppliance(cloudfile, "node9", new GeoLocation(50.8503, 4.3517), 0, provider1);
        RenewableWorkflowComputingAppliance node10 = new RenewableWorkflowComputingAppliance(cloudfile, "node10", new GeoLocation(46.7762, 23.6213), 0, provider2);
        RenewableWorkflowComputingAppliance node11 = new RenewableWorkflowComputingAppliance(cloudfile, "node11", new GeoLocation(48.1351, 11.5820), 0, provider2);
        RenewableWorkflowComputingAppliance node12 = new RenewableWorkflowComputingAppliance(cloudfile, "node12", new GeoLocation(53.9076, 27.5754), 0, provider2);
        RenewableWorkflowComputingAppliance node13 = new RenewableWorkflowComputingAppliance(cloudfile, "node13", new GeoLocation(60.1695, 24.9354), 0, provider2);
        RenewableWorkflowComputingAppliance node14 = new RenewableWorkflowComputingAppliance(cloudfile, "node14", new GeoLocation(39.9334, 32.8597), 0, provider2);
        RenewableWorkflowComputingAppliance node15 = new RenewableWorkflowComputingAppliance(cloudfile, "node15", new GeoLocation(40.4168, -3.7038), 0, provider2);
        RenewableWorkflowComputingAppliance node16 = new RenewableWorkflowComputingAppliance(cloudfile, "node16", new GeoLocation(37.9838, 23.7275), 0, provider2);
        RenewableWorkflowComputingAppliance node17 = new RenewableWorkflowComputingAppliance(cloudfile, "node17", new GeoLocation(52.3702, 4.8952), 0, provider2);
        RenewableWorkflowComputingAppliance node18 = new RenewableWorkflowComputingAppliance(cloudfile, "node18", new GeoLocation(55.9533, -3.1883), 0, provider2);
        RenewableWorkflowComputingAppliance node19 = new RenewableWorkflowComputingAppliance(cloudfile, "node19", new GeoLocation(51.1657, 10.4515), 0, provider2);

        WorkflowComputingAppliance.setDistanceBasedLatency();

        HashMap<Integer, ArrayList<WorkflowComputingAppliance>> clusterAssignments = new HashMap<>();

        /** --- Single Cluster Approach --- */
        ArrayList<WorkflowComputingAppliance> nodes = new ArrayList<>(List.of(
                node1, node2, node3, node4, node5, node6, node7, node8, node9, node10,
                node11, node12, node13, node14, node15, node16, node17, node18, node19
        ));

        clusterAssignments.put(1, nodes);



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
        new EnergyDataCollector("node-0", node0.iaas, false);
        new EnergyDataCollector("node-1", node1.iaas, false);
        new EnergyDataCollector("node-2", node2.iaas, false);
        new EnergyDataCollector("node-3", node3.iaas, false);
        new EnergyDataCollector("node-4", node4.iaas, false);
        new EnergyDataCollector("node-5", node5.iaas, false);
        new EnergyDataCollector("node-6", node6.iaas, false);
        new EnergyDataCollector("node-7", node7.iaas, false);
        new EnergyDataCollector("node-8", node8.iaas, false);
        new EnergyDataCollector("node-9", node9.iaas, false);
        new EnergyDataCollector("node-10", node10.iaas, false);
        new EnergyDataCollector("node-11", node11.iaas, false);
        new EnergyDataCollector("node-12", node12.iaas, false);
        new EnergyDataCollector("node-13", node13.iaas, false);
        new EnergyDataCollector("node-14", node14.iaas, false);
        new EnergyDataCollector("node-15", node15.iaas, false);
        new EnergyDataCollector("node-16", node16.iaas, false);
        new EnergyDataCollector("node-17", node17.iaas, false);
        new EnergyDataCollector("node-18", node18.iaas, false);
        new EnergyDataCollector("node-19", node19.iaas, false);


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
        ArrayList<Provider> providers = new ArrayList<>();
        providers.add(provider1);
        providers.add(provider2);

        for (int i = 0; i < clusterList.size(); i++) {
            Pair<String, ArrayList<WorkflowJob>> jobs = WorkflowJobModel.loadWorkflowXml(workflowFile, "-" + i);
            executor.submitJobs(new RenewableScheduler(clusterList.get(i), instance, null, jobs, providers, 10, false));
        }

        // Logging
        Timed.simulateUntilLastEvent();
        ScenarioBase.logStreamProcessing();
        ScenarioBase.logRenewableStreamProcessing();
        WorkflowGraphVisualiser.generateDag(ScenarioBase.scriptPath, ScenarioBase.resultDirectory, workflowFile);
        RenewableVisualiser.visualiseStoredEnergy(providers);
        RenewableVisualiser.visualiseSolar(providers);
        RenewableVisualiser.visualiseWind(providers);
        TimelineVisualiser.generateTimeline(ScenarioBase.resultDirectory);
        MapVisualiser.clusterMapGenerator(clusterAssignments, ScenarioBase.scriptPath, ScenarioBase.resultDirectory);
        EnergyDataCollector.writeToFile(ScenarioBase.resultDirectory);
    }
}
