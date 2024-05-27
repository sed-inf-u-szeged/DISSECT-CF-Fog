package hu.u_szeged.inf.fog.simulator.demo;



import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.AlterableResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;
import hu.u_szeged.inf.fog.simulator.iot.Actuator;
import hu.u_szeged.inf.fog.simulator.iot.mobility.GeoLocation;
import hu.u_szeged.inf.fog.simulator.node.WorkflowComputingAppliance;
import hu.u_szeged.inf.fog.simulator.provider.Instance;
import hu.u_szeged.inf.fog.simulator.util.TimelineVisualiser;
import hu.u_szeged.inf.fog.simulator.util.WorkflowGraphVisualiser;
import hu.u_szeged.inf.fog.simulator.util.xmlhandler.WorkflowJobModel;
import hu.u_szeged.inf.fog.simulator.workflow.WorkflowExecutor;
import hu.u_szeged.inf.fog.simulator.workflow.scheduler.IotWorkflowScheduler;

import java.util.ArrayList;
import java.util.HashMap;

public class WorkflowSimulation {

    public static void main(String[] args) throws Exception {

        HashMap<WorkflowComputingAppliance, Instance> workflowArchitecture = getWorkflowArchitecutre();
        ArrayList<Actuator> actuatorArchitecture = getActuatorArchitecture();

        // String workflowFile = ScenarioBase.resourcePath +
        // "/WORKFLOW_examples/CyberShake_100.xml";
        // workflowFile = ScientificWorkflowParser.parseToIoTWorkflow(workflowFile);

        String workflowFile = ScenarioBase.resourcePath + "/WORKFLOW_examples/IoT_workflow.xml";

        WorkflowJobModel.loadWorkflowXml(workflowFile);

        // new WorkflowExecutor(new MaxMinScheduler(workflowArchitecture));
        new WorkflowExecutor(new IotWorkflowScheduler(workflowArchitecture, actuatorArchitecture, 1000));

        Timed.simulateUntilLastEvent();
        ScenarioBase.logStreamProcessing();
        WorkflowGraphVisualiser.generateDag(ScenarioBase.scriptPath, ScenarioBase.resultDirectory, workflowFile);
        TimelineVisualiser.generateTimeline(ScenarioBase.resultDirectory);
    }

    private static ArrayList<Actuator> getActuatorArchitecture() {
        ArrayList<Actuator> actuatorArchitecture = new ArrayList<Actuator>();
        actuatorArchitecture.add(new Actuator("actuator1", "coffee", 25 * 1000));
        actuatorArchitecture.add(new Actuator("actuator2", "newspaper", 20 * 1000));
        return actuatorArchitecture;
    }

    private static HashMap<WorkflowComputingAppliance, Instance> getWorkflowArchitecutre() throws Exception {

        String cloudfile = ScenarioBase.resourcePath + "LPDS_original.xml";

        WorkflowComputingAppliance cloud1 = new WorkflowComputingAppliance(cloudfile, "cloud1", new GeoLocation(0, 0), 1000);

        WorkflowComputingAppliance fog1 = new WorkflowComputingAppliance(cloudfile, "fog1", new GeoLocation(0, 10), 1000);
        WorkflowComputingAppliance fog2 = new WorkflowComputingAppliance(cloudfile, "fog2", new GeoLocation(10, 10), 1000);
        WorkflowComputingAppliance fog3 = new WorkflowComputingAppliance(cloudfile, "fog3", new GeoLocation(20, 0), 1000);
        WorkflowComputingAppliance fog4 = new WorkflowComputingAppliance(cloudfile, "fog4", new GeoLocation(10, -10), 1000);

        fog1.addNeighbor(fog2, 100);
        fog1.addNeighbor(fog3, 110);
        fog1.addNeighbor(fog4, 120);
        fog2.addNeighbor(fog3, 130);
        fog2.addNeighbor(fog4, 140);
        fog3.addNeighbor(fog4, 150);

        fog1.setParent(cloud1, 60);
        fog2.setParent(cloud1, 70);
        fog3.setParent(cloud1, 80);
        fog4.setParent(cloud1, 90);

        VirtualAppliance va = new VirtualAppliance("va", 100, 0, false, 1073741824L);

        AlterableResourceConstraints arc1 = new AlterableResourceConstraints(2, 0.001, 4294967296L);
        AlterableResourceConstraints arc2 = new AlterableResourceConstraints(4, 0.001, 4294967296L);

        Instance instance1 = new Instance("instance1", va, arc1, 0.051 / 60 / 60 / 1000, 1);
        Instance instance2 = new Instance("instance2", va, arc2, 0.102 / 60 / 60 / 1000, 1);

        HashMap<WorkflowComputingAppliance, Instance> workflowArchitecture = new HashMap<WorkflowComputingAppliance, Instance>();
        workflowArchitecture.put(cloud1, instance1);
        workflowArchitecture.put(fog1, instance2);
        workflowArchitecture.put(fog2, instance2);
        workflowArchitecture.put(fog3, instance2);
        workflowArchitecture.put(fog4, instance2);

        return workflowArchitecture;
    }

}