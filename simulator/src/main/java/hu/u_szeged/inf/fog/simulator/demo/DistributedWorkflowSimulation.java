package hu.u_szeged.inf.fog.simulator.demo;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.AlterableResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;
import hu.u_szeged.inf.fog.simulator.iot.Actuator;
import hu.u_szeged.inf.fog.simulator.iot.mobility.GeoLocation;
import hu.u_szeged.inf.fog.simulator.node.WorkflowComputingAppliance;
import hu.u_szeged.inf.fog.simulator.provider.Instance;
import hu.u_szeged.inf.fog.simulator.util.xml.WorkflowJobModel;
import hu.u_szeged.inf.fog.simulator.workflow.DecentralizedWorkflowExecutor;
import hu.u_szeged.inf.fog.simulator.workflow.aco.Acoc;
import hu.u_szeged.inf.fog.simulator.workflow.scheduler.DecentralizedWorkflowScheduler;

import java.util.ArrayList;
import java.util.LinkedHashMap;

public class DistributedWorkflowSimulation {

    public static void main(String[] args) throws Exception {
        //String cloudfile = ScenarioBase.resourcePath+"LPDS_original.xml";
        ArrayList<WorkflowComputingAppliance> centerNodes = new ArrayList<>();
        //LinkedHashMap<WorkflowComputingAppliance, Instance> workflowArchitecture = getWorkflowArchitecutre();
        LinkedHashMap<WorkflowComputingAppliance, Instance> workflowArchitecture = getWorkflowArchitecutre(centerNodes);


        ArrayList<Actuator> actuatorArchitecture = getActuatorArchitecture();

        String workflowFile = ScenarioBase.resourcePath + "/WORKFLOW_examples/IoT_CyberShake_100.xml";

        ArrayList<LinkedHashMap<WorkflowComputingAppliance, Instance>> workflowArchitectures;
        ArrayList<DecentralizedWorkflowScheduler> workflowSchedulers = new ArrayList<>();

        //ACO aco = new ACO(6, 20, 60, 0.2, 100, 0.3,20);
        //workflowArchitectures = aco.runACO(workflowArchitecture, centerNodes);

        Acoc acoc = new Acoc(0.2,100,0.2,20);
        workflowArchitectures = acoc.runAcoc(workflowArchitecture,actuatorArchitecture);

        for(LinkedHashMap<WorkflowComputingAppliance, Instance> architecture : workflowArchitectures){
            DecentralizedWorkflowScheduler actual = new DecentralizedWorkflowScheduler(architecture, actuatorArchitecture, 10);
            workflowSchedulers.add(actual);
        }

        for(DecentralizedWorkflowScheduler dws : workflowSchedulers){

            WorkflowJobModel.loadWorkflowXml(workflowFile,dws,50);
        }

        new DecentralizedWorkflowExecutor(workflowSchedulers,actuatorArchitecture);

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

    private static LinkedHashMap<WorkflowComputingAppliance, Instance> getWorkflowArchitecutre(ArrayList<WorkflowComputingAppliance> centerNodes) throws Exception {
        String cloudfile = ScenarioBase.resourcePath+"LPDS_original.xml";

        WorkflowComputingAppliance fog1 = new WorkflowComputingAppliance(cloudfile, "fog1", new GeoLocation(0, 10), 1000);
        WorkflowComputingAppliance fog2 = new WorkflowComputingAppliance(cloudfile, "fog1", new GeoLocation(50, 15), 1000);
        WorkflowComputingAppliance fog3 = new WorkflowComputingAppliance(cloudfile, "fog3", new GeoLocation(20, 0), 1000);
        WorkflowComputingAppliance fog4 = new WorkflowComputingAppliance(cloudfile, "fog4", new GeoLocation(10, -10), 1000);
        WorkflowComputingAppliance fog5 = new WorkflowComputingAppliance(cloudfile, "fog5", new GeoLocation(25, 100), 1000);
        WorkflowComputingAppliance fog6 = new WorkflowComputingAppliance(cloudfile, "fog6", new GeoLocation(30, -100), 1000);
        WorkflowComputingAppliance fog7 = new WorkflowComputingAppliance(cloudfile, "fog7", new GeoLocation(-25, 23), 1000);
        WorkflowComputingAppliance fog8 = new WorkflowComputingAppliance(cloudfile, "fog8", new GeoLocation(40, 10), 1000);
        WorkflowComputingAppliance fog9 = new WorkflowComputingAppliance(cloudfile, "fog9", new GeoLocation(35, -105), 1000);
        WorkflowComputingAppliance fog10 = new WorkflowComputingAppliance(cloudfile, "fog10", new GeoLocation(-20, 130), 1000);
        WorkflowComputingAppliance fog11 = new WorkflowComputingAppliance(cloudfile, "fog11", new GeoLocation(-30, 140), 1000);
        WorkflowComputingAppliance fog12= new WorkflowComputingAppliance(cloudfile, "fog12", new GeoLocation(-30, 150), 1000);
        WorkflowComputingAppliance fog13= new WorkflowComputingAppliance(cloudfile, "fog13", new GeoLocation(-40, -70), 1000);
        WorkflowComputingAppliance fog14= new WorkflowComputingAppliance(cloudfile, "fog14", new GeoLocation(-30, -60), 1000);
        WorkflowComputingAppliance fog15= new WorkflowComputingAppliance(cloudfile, "fog15", new GeoLocation(-10, -40), 1000);
        WorkflowComputingAppliance fog16= new WorkflowComputingAppliance(cloudfile, "fog16", new GeoLocation(40, -120), 1000);
        WorkflowComputingAppliance fog17 = new WorkflowComputingAppliance(cloudfile, "fog17", new GeoLocation(35, 80), 1000);
        WorkflowComputingAppliance fog18 = new WorkflowComputingAppliance(cloudfile, "fog18", new GeoLocation(70, -40), 1000);
        WorkflowComputingAppliance fog19 = new WorkflowComputingAppliance(cloudfile, "fog19", new GeoLocation(30, 100), 1000);
        WorkflowComputingAppliance fog20 = new WorkflowComputingAppliance(cloudfile, "fog20", new GeoLocation(50, 50), 1000);

        VirtualAppliance va = new VirtualAppliance("va", 100, 0, false, 1073741824L);

        //AlterableResourceConstraints arc1 = new AlterableResourceConstraints(2, 0.001, 4294967296L);
        AlterableResourceConstraints arc2 = new AlterableResourceConstraints(4, 0.001, 4294967296L);

        //Instance instance1 = new Instance("instance1", va, arc1, 0.051 / 60 / 60 / 1000, 1);
        Instance instance2 = new Instance("instance2", va, arc2, 0.102 / 60 / 60 / 1000, 1);

        LinkedHashMap<WorkflowComputingAppliance, Instance> workflowArchitecture = new LinkedHashMap<WorkflowComputingAppliance, Instance>();
        workflowArchitecture.put(fog1, instance2);
        workflowArchitecture.put(fog2, instance2);
        workflowArchitecture.put(fog3, instance2);
        workflowArchitecture.put(fog4, instance2);
        workflowArchitecture.put(fog5, instance2);
        workflowArchitecture.put(fog6, instance2);
        workflowArchitecture.put(fog7, instance2);
        workflowArchitecture.put(fog8, instance2);
        workflowArchitecture.put(fog9, instance2);
        workflowArchitecture.put(fog10, instance2);
        workflowArchitecture.put(fog11, instance2);
        workflowArchitecture.put(fog12, instance2);
        workflowArchitecture.put(fog13, instance2);
        workflowArchitecture.put(fog14, instance2);
        workflowArchitecture.put(fog15, instance2);
        workflowArchitecture.put(fog16, instance2);
        workflowArchitecture.put(fog17, instance2);
        workflowArchitecture.put(fog18, instance2);
        workflowArchitecture.put(fog19, instance2);
        workflowArchitecture.put(fog20, instance2);

        centerNodes.add(fog6);
        centerNodes.add(fog3);
        centerNodes.add(fog20);
        centerNodes.add(fog19);
        centerNodes.add(fog10);
        centerNodes.add(fog15);

        return workflowArchitecture;
    }

    

}
