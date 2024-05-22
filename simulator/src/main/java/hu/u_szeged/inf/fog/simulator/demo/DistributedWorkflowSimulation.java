package hu.u_szeged.inf.fog.simulator.demo;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.AlterableResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;
import hu.u_szeged.inf.fog.simulator.iot.Actuator;
import hu.u_szeged.inf.fog.simulator.iot.mobility.GeoLocation;
import hu.u_szeged.inf.fog.simulator.node.ComputingAppliance;
import hu.u_szeged.inf.fog.simulator.provider.Instance;
import hu.u_szeged.inf.fog.simulator.util.xmlhandler.WorkflowJobModel;
import hu.u_szeged.inf.fog.simulator.workflow.DecentralizedWorkflowExecutor;
import hu.u_szeged.inf.fog.simulator.workflow.aco.ACO;
import hu.u_szeged.inf.fog.simulator.workflow.aco.ACOC;
import hu.u_szeged.inf.fog.simulator.workflow.scheduler.DecentralizedWorkflowScheduler;

import java.util.ArrayList;
import java.util.LinkedHashMap;

public class DistributedWorkflowSimulation {

    public static void main(String[] args) throws Exception {
        String cloudfile = ScenarioBase.resourcePath+"LPDS_original.xml";
        ArrayList<ComputingAppliance> centerNodes = new ArrayList<>();
        //LinkedHashMap<ComputingAppliance, Instance> workflowArchitecture = getWorkflowArchitecutre();
        LinkedHashMap<ComputingAppliance, Instance> workflowArchitecture = getWorkflowArchitecutre(centerNodes);


        ArrayList<Actuator> actuatorArchitecture = getActuatorArchitecture();

        String workflowFile = ScenarioBase.resourcePath + "/WORKFLOW_examples/IoT_CyberShake_100.xml";

        ArrayList<LinkedHashMap<ComputingAppliance, Instance>> workflowArchitectures;
        ArrayList<DecentralizedWorkflowScheduler> workflowSchedulers = new ArrayList<>();

        //ACO aco = new ACO(6, 20, 60, 0.2, 100, 0.3,20);
        //workflowArchitectures = aco.runACO(workflowArchitecture, centerNodes);

        ACOC acoc = new ACOC(0.2,100,0.2,20);
        workflowArchitectures = acoc.runACOC(workflowArchitecture,actuatorArchitecture);

        for(LinkedHashMap<ComputingAppliance, Instance> architecture : workflowArchitectures){
            DecentralizedWorkflowScheduler actual = new DecentralizedWorkflowScheduler(architecture, actuatorArchitecture, 10);
            workflowSchedulers.add(actual);
        }

        for(DecentralizedWorkflowScheduler dws : workflowSchedulers){

            WorkflowJobModel.loadWorkflowXML(workflowFile,dws,50);
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

    private static LinkedHashMap<ComputingAppliance, Instance> getWorkflowArchitecutre(ArrayList<ComputingAppliance> centerNodes) throws Exception {
        String cloudfile = ScenarioBase.resourcePath+"LPDS_original.xml";

        ComputingAppliance fog1 = new ComputingAppliance(cloudfile, "fog1", new GeoLocation(0, 10), 1000);
        ComputingAppliance fog2 = new ComputingAppliance(cloudfile, "fog1", new GeoLocation(50, 15), 1000);
        ComputingAppliance fog3 = new ComputingAppliance(cloudfile, "fog3", new GeoLocation(20, 0), 1000);
        ComputingAppliance fog4 = new ComputingAppliance(cloudfile, "fog4", new GeoLocation(10, -10), 1000);
        ComputingAppliance fog5 = new ComputingAppliance(cloudfile, "fog5", new GeoLocation(25, 100), 1000);
        ComputingAppliance fog6 = new ComputingAppliance(cloudfile, "fog6", new GeoLocation(30, -100), 1000);
        ComputingAppliance fog7 = new ComputingAppliance(cloudfile, "fog7", new GeoLocation(-25, 23), 1000);
        ComputingAppliance fog8 = new ComputingAppliance(cloudfile, "fog8", new GeoLocation(40, 10), 1000);
        ComputingAppliance fog9 = new ComputingAppliance(cloudfile, "fog9", new GeoLocation(35, -105), 1000);
        ComputingAppliance fog10 = new ComputingAppliance(cloudfile, "fog10", new GeoLocation(-20, 130), 1000);
        ComputingAppliance fog11 = new ComputingAppliance(cloudfile, "fog11", new GeoLocation(-30, 140), 1000);
        ComputingAppliance fog12= new ComputingAppliance(cloudfile, "fog12", new GeoLocation(-30, 150), 1000);
        ComputingAppliance fog13= new ComputingAppliance(cloudfile, "fog13", new GeoLocation(-40, -70), 1000);
        ComputingAppliance fog14= new ComputingAppliance(cloudfile, "fog14", new GeoLocation(-30, -60), 1000);
        ComputingAppliance fog15= new ComputingAppliance(cloudfile, "fog15", new GeoLocation(-10, -40), 1000);
        ComputingAppliance fog16= new ComputingAppliance(cloudfile, "fog16", new GeoLocation(40, -120), 1000);
        ComputingAppliance fog17 = new ComputingAppliance(cloudfile, "fog17", new GeoLocation(35, 80), 1000);
        ComputingAppliance fog18 = new ComputingAppliance(cloudfile, "fog18", new GeoLocation(70, -40), 1000);
        ComputingAppliance fog19 = new ComputingAppliance(cloudfile, "fog19", new GeoLocation(30, 100), 1000);
        ComputingAppliance fog20 = new ComputingAppliance(cloudfile, "fog20", new GeoLocation(50, 50), 1000);

        VirtualAppliance va = new VirtualAppliance("va", 100, 0, false, 1073741824L);

        AlterableResourceConstraints arc1 = new AlterableResourceConstraints(2, 0.001, 4294967296L);
        AlterableResourceConstraints arc2 = new AlterableResourceConstraints(4, 0.001, 4294967296L);

        Instance instance1 = new Instance("instance1", va, arc1, 0.051 / 60 / 60 / 1000, 1);
        Instance instance2 = new Instance("instance2", va, arc2, 0.102 / 60 / 60 / 1000, 1);

        LinkedHashMap<ComputingAppliance, Instance> workflowArchitecture = new LinkedHashMap<ComputingAppliance, Instance>();
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

    private static LinkedHashMap<ComputingAppliance, Instance> getWorkflowArchitecutre() throws Exception {

        String cloudfile = ScenarioBase.resourcePath+"LPDS_original.xml";

        ComputingAppliance fog1 = new ComputingAppliance(cloudfile, "fog1", new GeoLocation(0, 10), 1000);
        ComputingAppliance fog2 = new ComputingAppliance(cloudfile, "fog1", new GeoLocation(50, 15), 1000);
        ComputingAppliance fog3 = new ComputingAppliance(cloudfile, "fog3", new GeoLocation(20, 0), 1000);
        ComputingAppliance fog4 = new ComputingAppliance(cloudfile, "fog4", new GeoLocation(10, -10), 1000);
        ComputingAppliance fog5 = new ComputingAppliance(cloudfile, "fog5", new GeoLocation(25, 100), 1000);
        ComputingAppliance fog6 = new ComputingAppliance(cloudfile, "fog6", new GeoLocation(30, -100), 1000);
        ComputingAppliance fog7 = new ComputingAppliance(cloudfile, "fog7", new GeoLocation(-25, 23), 1000);
        ComputingAppliance fog8 = new ComputingAppliance(cloudfile, "fog8", new GeoLocation(40, 10), 1000);
        ComputingAppliance fog9 = new ComputingAppliance(cloudfile, "fog9", new GeoLocation(35, -105), 1000);
        ComputingAppliance fog10 = new ComputingAppliance(cloudfile, "fog10", new GeoLocation(-20, 130), 1000);
        ComputingAppliance fog11 = new ComputingAppliance(cloudfile, "fog11", new GeoLocation(-30, 140), 1000);
        ComputingAppliance fog12= new ComputingAppliance(cloudfile, "fog12", new GeoLocation(-30, 150), 1000);
        ComputingAppliance fog13= new ComputingAppliance(cloudfile, "fog13", new GeoLocation(-40, -70), 1000);
        ComputingAppliance fog14= new ComputingAppliance(cloudfile, "fog14", new GeoLocation(-30, -60), 1000);
        ComputingAppliance fog15= new ComputingAppliance(cloudfile, "fog15", new GeoLocation(-10, -40), 1000);
        ComputingAppliance fog16= new ComputingAppliance(cloudfile, "fog16", new GeoLocation(40, -120), 1000);
        ComputingAppliance fog17 = new ComputingAppliance(cloudfile, "fog17", new GeoLocation(35, 80), 1000);
        ComputingAppliance fog18 = new ComputingAppliance(cloudfile, "fog18", new GeoLocation(70, -40), 1000);
        ComputingAppliance fog19 = new ComputingAppliance(cloudfile, "fog19", new GeoLocation(30, 100), 1000);
        ComputingAppliance fog20 = new ComputingAppliance(cloudfile, "fog20", new GeoLocation(50, 50), 1000);

        VirtualAppliance va = new VirtualAppliance("va", 100, 0, false, 1073741824L);

        AlterableResourceConstraints arc1 = new AlterableResourceConstraints(2, 0.001, 4294967296L);
        AlterableResourceConstraints arc2 = new AlterableResourceConstraints(4, 0.001, 4294967296L);

        Instance instance1 = new Instance("instance1", va, arc1, 0.051 / 60 / 60 / 1000, 1);
        Instance instance2 = new Instance("instance2", va, arc2, 0.102 / 60 / 60 / 1000, 1);

        LinkedHashMap<ComputingAppliance, Instance> workflowArchitecture = new LinkedHashMap<ComputingAppliance, Instance>();
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

        return workflowArchitecture;
    }

}
