package hu.u_szeged.inf.fog.simulator.demo;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.AlterableResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;
import hu.u_szeged.inf.fog.simulator.iot.Actuator;
import hu.u_szeged.inf.fog.simulator.iot.mobility.GeoLocation;
import hu.u_szeged.inf.fog.simulator.node.WorkflowComputingAppliance;
import hu.u_szeged.inf.fog.simulator.provider.Instance;
import hu.u_szeged.inf.fog.simulator.util.EnergyDataCollector;
import hu.u_szeged.inf.fog.simulator.util.SimLogger;
import hu.u_szeged.inf.fog.simulator.util.TimelineVisualiser;
import hu.u_szeged.inf.fog.simulator.util.WorkflowGraphVisualiser;
import hu.u_szeged.inf.fog.simulator.util.xml.WorkflowJobModel;
import hu.u_szeged.inf.fog.simulator.workflow.WorkflowExecutor;
import hu.u_szeged.inf.fog.simulator.workflow.WorkflowJob;
import hu.u_szeged.inf.fog.simulator.workflow.scheduler.IotWorkflowScheduler;
import java.util.ArrayList;
import org.apache.commons.lang3.tuple.Pair;

public class IoTWorkflowSimulation {

    public static void main(String[] args) throws Exception {
        
    	SimLogger.setLogging(1, true);
    	
    	String workflowFile = ScenarioBase.resourcePath + "/WORKFLOW_examples/IoT_workflow.xml";
    	Pair<String, ArrayList<WorkflowJob>> jobs = WorkflowJobModel.loadWorkflowXml(workflowFile, "");

    	// Creating the compute and actuator architecture, and a VM instance
    	String cloudfile = ScenarioBase.resourcePath + "ELKH_original.xml";

    	ArrayList<WorkflowComputingAppliance> computeArchitecture = new ArrayList<>();
    	WorkflowComputingAppliance node1 = new WorkflowComputingAppliance(cloudfile, "node1", new GeoLocation(0, 0), 0);
    	WorkflowComputingAppliance node2 = new WorkflowComputingAppliance(cloudfile, "node2", new GeoLocation(0, 10), 0);
    	WorkflowComputingAppliance node3 = new WorkflowComputingAppliance(cloudfile, "node3", new GeoLocation(10, 10), 0);
       
    	WorkflowComputingAppliance.setDistanceBasedLatency();
    	
    	computeArchitecture.add(node1);
        computeArchitecture.add(node2);
        computeArchitecture.add(node3);
        
    	new EnergyDataCollector("node-1", node1.iaas, true);
        new EnergyDataCollector("node-2", node2.iaas, true);
        new EnergyDataCollector("node-3", node3.iaas, true);
        
        ArrayList<Actuator> actuatorArchitecture = new ArrayList<Actuator>();
        actuatorArchitecture.add(new Actuator("actuator1", "coffee", 25 * 1000));
        actuatorArchitecture.add(new Actuator("actuator2", "newspaper", 20 * 1000));
        
        VirtualAppliance va = new VirtualAppliance("va", 100, 0, false, 1073741824L);
        AlterableResourceConstraints arc = new AlterableResourceConstraints(2, 0.001, 4294967296L);
        Instance instance = new Instance("instance", va, arc, 0.051 / 60 / 60 / 1000, 1);

    	// Creating the executor engine
        WorkflowExecutor executor = WorkflowExecutor.getIstance();
        executor.submitJobs(new IotWorkflowScheduler(computeArchitecture, instance, actuatorArchitecture, jobs, 50));
        
        Timed.simulateUntilLastEvent();
        ScenarioBase.logStreamProcessing();
        WorkflowGraphVisualiser.generateDag(ScenarioBase.scriptPath, ScenarioBase.resultDirectory, workflowFile);
        TimelineVisualiser.generateTimeline(ScenarioBase.resultDirectory);
        EnergyDataCollector.writeToFile(ScenarioBase.resultDirectory);
        // TODO: showing sensors and actuators on the map as well
        //MapVisualiser.clusterMapGenerator(clusterAssignments, ScenarioBase.scriptPath, ScenarioBase.resultDirectory);
    }
}