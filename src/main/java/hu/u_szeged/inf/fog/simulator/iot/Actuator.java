package hu.u_szeged.inf.fog.simulator.iot;

import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.u_szeged.inf.fog.simulator.util.TimelineVisualiser.TimelineEntry;
import hu.u_szeged.inf.fog.simulator.workflow.WorkflowJob;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

public class Actuator {

    public static List<Actuator> allActuators = new ArrayList<Actuator>();

    public Queue<WorkflowJob> actuatorWorkflowQueue;

    public ArrayList<TimelineEntry> actuatorEventList = new ArrayList<TimelineEntry>();

    public String name;

    public long delay;

    public String topic;

    Repository repo;

    int latency;

    public boolean isWorking;

    public Actuator(String name, String topic, long delay) {
        this.name = name;
        this.topic = topic;
        this.delay = delay;
        Actuator.allActuators.add(this);
    }

}
