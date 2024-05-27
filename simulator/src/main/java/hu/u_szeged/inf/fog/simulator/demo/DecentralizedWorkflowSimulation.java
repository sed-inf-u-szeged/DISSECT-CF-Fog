package hu.u_szeged.inf.fog.simulator.demo;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.AlterableResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;
import hu.u_szeged.inf.fog.simulator.iot.Actuator;
import hu.u_szeged.inf.fog.simulator.iot.mobility.GeoLocation;
import hu.u_szeged.inf.fog.simulator.node.ComputingAppliance;
import hu.u_szeged.inf.fog.simulator.node.WorkflowComputingAppliance;
import hu.u_szeged.inf.fog.simulator.provider.Instance;
import hu.u_szeged.inf.fog.simulator.util.xml.WorkflowJobModel;
import hu.u_szeged.inf.fog.simulator.workflow.DecentralizedWorkflowExecutor;
import hu.u_szeged.inf.fog.simulator.workflow.aco.Acoc;
import hu.u_szeged.inf.fog.simulator.workflow.scheduler.DecentralizedWorkflowScheduler;

import java.util.ArrayList;
import java.util.LinkedHashMap;

public class DecentralizedWorkflowSimulation {

    public static void main(String[] args) throws Exception {
        //String cloudfile = ScenarioBase.resourcePath+"LPDS_original.xml";
        ArrayList<WorkflowComputingAppliance> centerNodes = new ArrayList<>();
        LinkedHashMap<WorkflowComputingAppliance, Instance> workflowArchitecture = getWorkflowArchitecutre(centerNodes);
        //LinkedHashMap<ComputingAppliance, Instance> workflowArchitecture = getWorkflowArchitecutre();


        ArrayList<Actuator> actuatorArchitecture = getActuatorArchitecture();

        String workflowFile = ScenarioBase.resourcePath + "/WORKFLOW_examples/IoT_CyberShake_100.xml";

        ArrayList<LinkedHashMap<WorkflowComputingAppliance, Instance>> workflowArchitectures;
        ArrayList<DecentralizedWorkflowScheduler> workflowSchedulers = new ArrayList<>();

        //ACO aco = new ACO(2, 5, 20, 0.1, 50, 0.3,20);
        //workflowArchitectures = aco.runACO(workflowArchitecture, centerNodes);

        Acoc acoc = new Acoc(0.1,50,0.3,20);
        workflowArchitectures = acoc.runAcoc(workflowArchitecture,actuatorArchitecture);

        for(LinkedHashMap<WorkflowComputingAppliance, Instance> architecture : workflowArchitectures){
            DecentralizedWorkflowScheduler actual = new DecentralizedWorkflowScheduler(architecture, actuatorArchitecture, 10);
            workflowSchedulers.add(actual);
        }

        for(DecentralizedWorkflowScheduler dws : workflowSchedulers){

            WorkflowJobModel.loadWorkflowXml(workflowFile,dws);
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

        WorkflowComputingAppliance fog1 = new WorkflowComputingAppliance(cloudfile, "fog1", new GeoLocation(52.5, 13.4), 500);

        WorkflowComputingAppliance fog2 = new WorkflowComputingAppliance(cloudfile, "fog2", new GeoLocation(47.5, 19), 500);
        WorkflowComputingAppliance fog3 = new WorkflowComputingAppliance(cloudfile, "fog3", new GeoLocation(51.5, 0), 500);

        WorkflowComputingAppliance fog4 = new WorkflowComputingAppliance(cloudfile, "fog4", new GeoLocation(40.5, -3.5), 500);
        WorkflowComputingAppliance fog5 = new WorkflowComputingAppliance(cloudfile, "fog5", new GeoLocation(42, 12.5), 500);

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

        centerNodes.add(fog3);
        centerNodes.add(fog1);

        return workflowArchitecture;
    }

    

}
