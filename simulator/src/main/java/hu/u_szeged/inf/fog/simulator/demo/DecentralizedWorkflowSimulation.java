package hu.u_szeged.inf.fog.simulator.demo;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.AlterableResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;
import hu.u_szeged.inf.fog.simulator.aco.ACOC;
import hu.u_szeged.inf.fog.simulator.aco.ACO;
import hu.u_szeged.inf.fog.simulator.iot.Actuator;
import hu.u_szeged.inf.fog.simulator.iot.mobility.GeoLocation;
import hu.u_szeged.inf.fog.simulator.physical.ComputingAppliance;
import hu.u_szeged.inf.fog.simulator.provider.Instance;
import hu.u_szeged.inf.fog.simulator.util.SimLogger;
import hu.u_szeged.inf.fog.simulator.util.TimelineVisualiser;
import hu.u_szeged.inf.fog.simulator.util.WorkflowGraphVisualiser;
import hu.u_szeged.inf.fog.simulator.util.xmlhandler.WorkflowJobModel;
import hu.u_szeged.inf.fog.simulator.workflow.DecentralizedWorkflowExecutor;
import hu.u_szeged.inf.fog.simulator.workflow.WorkflowExecutor;
import hu.u_szeged.inf.fog.simulator.workflow.WorkflowJob;
import hu.u_szeged.inf.fog.simulator.workflow.scheduler.DecentralizedWorkflowScheduler;

import java.util.ArrayList;
import java.util.LinkedHashMap;

public class DecentralizedWorkflowSimulation {

    public static void main(String[] args) throws Exception {
        String cloudfile = ScenarioBase.resourcePath+"LPDS_original.xml";
        LinkedHashMap<ComputingAppliance, Instance> workflowArchitecture = getWorkflowArchitecutre();

        ArrayList<Actuator> actuatorArchitecture = getActuatorArchitecture();

        // String workflowFile = ScenarioBase.resourcePath +
        // "/WORKFLOW_examples/CyberShake_100.xml";
        // workflowFile = ScientificWorkflowParser.parseToIoTWorkflow(workflowFile);

        String workflowFile = ScenarioBase.resourcePath + "/WORKFLOW_examples/IoT_workflow.xml";
        
        //ACO aco = new ACO(3, 5, 10, 0.01, 50, 0.5);
        //aco.runACO(workflowArchitecture, centerNodes);

        ArrayList<LinkedHashMap<ComputingAppliance, Instance>> workflowArchitectures;
        ArrayList<DecentralizedWorkflowScheduler> workflowSchedulers = new ArrayList<>();

        ACOC acoc = new ACOC(0.8,50,0.2,15);
        workflowArchitectures = acoc.runACOC(workflowArchitecture,actuatorArchitecture);

        for(LinkedHashMap<ComputingAppliance, Instance> architecture : workflowArchitectures){
            DecentralizedWorkflowScheduler actual = new DecentralizedWorkflowScheduler(architecture, actuatorArchitecture, 10);
            workflowSchedulers.add(actual);
        }

        for(DecentralizedWorkflowScheduler dws : workflowSchedulers){
            WorkflowJobModel.loadWorkflowXML(workflowFile,dws);
        }

        new DecentralizedWorkflowExecutor(workflowSchedulers,actuatorArchitecture);

        //new WorkflowExecutor(new MaxMinScheduler(workflowArchitecture));
        //new WorkflowExecutor(new IoTWorkflowScheduler(workflowArchitecture, actuatorArchitecture, 1000));

        Timed.simulateUntilLastEvent();
        ScenarioBase.logStreamProcessing(workflowSchedulers);
        //WorkflowGraphVisualiser.generateDAG(ScenarioBase.scriptPath, ScenarioBase.resultDirectory, workflowFile);
        //TimelineVisualiser.generateTimeline(ScenarioBase.resultDirectory);
    }

    private static ArrayList<Actuator> getActuatorArchitecture() {
        ArrayList<Actuator> actuatorArchitecture = new ArrayList<Actuator>();
        actuatorArchitecture.add(new Actuator("actuator1", "coffee", 25 * 1000));
        actuatorArchitecture.add(new Actuator("actuator2", "newspaper", 20 * 1000));
        return actuatorArchitecture;
    }

    private static LinkedHashMap<ComputingAppliance, Instance> getWorkflowArchitecutre() throws Exception {

        String cloudfile = ScenarioBase.resourcePath+"LPDS_original.xml";

        ComputingAppliance cloud1 = new ComputingAppliance(cloudfile, "cloud1", new GeoLocation(0, 0), 1000);

        ComputingAppliance fog1 = new ComputingAppliance(cloudfile, "fog1", new GeoLocation(0, 10), 1000);
       
        ComputingAppliance fog3 = new ComputingAppliance(cloudfile, "fog3", new GeoLocation(20, 0), 1000);
        ComputingAppliance fog4 = new ComputingAppliance(cloudfile, "fog4", new GeoLocation(10, -10), 1000);
       
        ComputingAppliance fog7 = new ComputingAppliance(cloudfile, "fog7", new GeoLocation(-25, 23), 1000);
        ComputingAppliance fog8 = new ComputingAppliance(cloudfile, "fog8", new GeoLocation(40, 10), 1000);

        VirtualAppliance va = new VirtualAppliance("va", 100, 0, false, 1073741824L);

        AlterableResourceConstraints arc1 = new AlterableResourceConstraints(2, 0.001, 4294967296L);
        AlterableResourceConstraints arc2 = new AlterableResourceConstraints(4, 0.001, 4294967296L);

        Instance instance1 = new Instance("instance1", va, arc1, 0.051 / 60 / 60 / 1000, 1);
        Instance instance2 = new Instance("instance2", va, arc2, 0.102 / 60 / 60 / 1000, 1);

        LinkedHashMap<ComputingAppliance, Instance> workflowArchitecture = new LinkedHashMap<ComputingAppliance, Instance>();
        workflowArchitecture.put(fog1, instance1);
        workflowArchitecture.put(fog3, instance2);
        workflowArchitecture.put(fog4, instance1);
        workflowArchitecture.put(fog7, instance2);
        workflowArchitecture.put(fog8, instance2);

        return workflowArchitecture;
    }

}
