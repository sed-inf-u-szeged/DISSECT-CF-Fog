package hu.u_szeged.inf.fog.simulator.demo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;
import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.AlterableResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;
import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;
import hu.u_szeged.inf.fog.simulator.iot.mobility.GeoLocation;
import hu.u_szeged.inf.fog.simulator.node.WorkflowComputingAppliance;
import hu.u_szeged.inf.fog.simulator.provider.Instance;
import hu.u_szeged.inf.fog.simulator.util.MapVisualiser;
import hu.u_szeged.inf.fog.simulator.util.SimLogger;
import hu.u_szeged.inf.fog.simulator.util.TimelineVisualiser;
import hu.u_szeged.inf.fog.simulator.util.WorkflowGraphVisualiser;
import hu.u_szeged.inf.fog.simulator.util.xml.WorkflowJobModel;
import hu.u_szeged.inf.fog.simulator.workflow.WorkflowExecutor;
import hu.u_szeged.inf.fog.simulator.workflow.WorkflowJob;
import hu.u_szeged.inf.fog.simulator.workflow.aco.CentralisedAntOptimiser;
import hu.u_szeged.inf.fog.simulator.workflow.aco.DistributedAntOptimiser;
import hu.u_szeged.inf.fog.simulator.workflow.scheduler.MaxMinScheduler;

public class AcoTest {

    public static void main(String[] args) throws Exception {

        SeedSyncer.modifySeed(System.currentTimeMillis());
        
        SimLogger.setLogging(1, true);
        
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
        WorkflowComputingAppliance fog11 = new WorkflowComputingAppliance(cloudfile, "fog11", new GeoLocation(46.7762, 23.6213), 0);  
        WorkflowComputingAppliance fog12 = new WorkflowComputingAppliance(cloudfile, "fog12", new GeoLocation(48.1351, 11.5820), 0);  
        WorkflowComputingAppliance fog13 = new WorkflowComputingAppliance(cloudfile, "fog13", new GeoLocation(53.9076, 27.5754), 0);  
        WorkflowComputingAppliance fog14 = new WorkflowComputingAppliance(cloudfile, "fog14", new GeoLocation(60.1695, 24.9354), 0); 
        WorkflowComputingAppliance fog15 = new WorkflowComputingAppliance(cloudfile, "fog15", new GeoLocation(39.9334, 32.8597), 0); 
        WorkflowComputingAppliance fog16 = new WorkflowComputingAppliance(cloudfile, "fog16", new GeoLocation(40.4168, -3.7038), 0);  
        WorkflowComputingAppliance fog17 = new WorkflowComputingAppliance(cloudfile, "fog17", new GeoLocation(37.9838, 23.7275), 0);  
        WorkflowComputingAppliance fog18 = new WorkflowComputingAppliance(cloudfile, "fog18", new GeoLocation(52.3702, 4.8952), 0);  
        WorkflowComputingAppliance fog19 = new WorkflowComputingAppliance(cloudfile, "fog19", new GeoLocation(55.9533, -3.1883), 0); 
        WorkflowComputingAppliance fog20 = new WorkflowComputingAppliance(cloudfile, "fog20", new GeoLocation(51.1657, 10.4515), 0); 
        
        WorkflowComputingAppliance.setDistanceBasedLatency();

        HashMap<WorkflowComputingAppliance, ArrayList<WorkflowComputingAppliance>> clusterAssignments;
        
        // Centralised approach
        /*
        ArrayList<WorkflowComputingAppliance> centerNodes = new ArrayList<WorkflowComputingAppliance>();

        centerNodes.add(fog5);
        centerNodes.add(fog12);
        centerNodes.add(fog13);
        centerNodes.add(fog19);
       
        ArrayList<WorkflowComputingAppliance> nodesToBeClustered = new ArrayList<WorkflowComputingAppliance>();
        
        nodesToBeClustered.add(fog1);
        nodesToBeClustered.add(fog2);
        nodesToBeClustered.add(fog3);
        nodesToBeClustered.add(fog4);
        nodesToBeClustered.add(fog6);
        nodesToBeClustered.add(fog7);
        nodesToBeClustered.add(fog8);
        nodesToBeClustered.add(fog9);
        nodesToBeClustered.add(fog10);
        nodesToBeClustered.add(fog11);
        nodesToBeClustered.add(fog14);
        nodesToBeClustered.add(fog15);
        nodesToBeClustered.add(fog16);
        nodesToBeClustered.add(fog17);
        nodesToBeClustered.add(fog18);
        nodesToBeClustered.add(fog20);

        clusterAssignments = CentralisedAntOptimiser.runOptimiser(centerNodes, nodesToBeClustered, 50, 50, 0.75, 0.75, 0.25, 0.15);
        */
        
        // Decentralised approach    
        clusterAssignments = DistributedAntOptimiser.runOptimiser(WorkflowComputingAppliance.allComputingAppliances, 10, 50, 0.75, 0.15);
        
        CentralisedAntOptimiser.printClusterAssignments(clusterAssignments);
        List<ArrayList<WorkflowComputingAppliance>> clusterList = CentralisedAntOptimiser.sortClustersByAveragePairwiseDistance(clusterAssignments);
                
        // Creating the executor engine
        WorkflowExecutor executor = WorkflowExecutor.getIstance();
        
        // Importing and submitting the workflow jobs to a cluster with the corresponding scheduler
        String workflowFile = ScenarioBase.resourcePath + "/WORKFLOW_examples/IoT_CyberShake_100.xml";
        
        VirtualAppliance va = new VirtualAppliance("va", 100, 0, false, 1073741824L);
        AlterableResourceConstraints arc = new AlterableResourceConstraints(4, 0.001, 4294967296L);
        Instance instance = new Instance("instance", va, arc, 0.102 / 60 / 60 / 1000, 1);
        
        for (int i = 0; i < clusterList.size(); i++) {
            Pair<String, ArrayList<WorkflowJob>> jobs = WorkflowJobModel.loadWorkflowXml(workflowFile, "-" + i);
            executor.submitJobs(new MaxMinScheduler(clusterList.get(i), instance, null, jobs));
            //break;
        }
        
        Timed.simulateUntilLastEvent();
        ScenarioBase.logStreamProcessing();
        WorkflowGraphVisualiser.generateDag(ScenarioBase.scriptPath, ScenarioBase.resultDirectory, workflowFile);
        TimelineVisualiser.generateTimeline(ScenarioBase.resultDirectory);
        MapVisualiser.clusterMapGenerator(clusterAssignments, ScenarioBase.scriptPath, ScenarioBase.resultDirectory);
    }
}
