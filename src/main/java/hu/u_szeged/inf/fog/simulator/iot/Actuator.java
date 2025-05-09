package hu.u_szeged.inf.fog.simulator.iot;

import hu.u_szeged.inf.fog.simulator.util.TimelineVisualiser.TimelineEntry;
import hu.u_szeged.inf.fog.simulator.workflow.WorkflowJob;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

/**
 * The class is a simple IoT actuator representation for workflow-based evaluations.
 */
public class Actuator {

    /**
     * A list of all actuators.
     */
    public static List<Actuator> allActuators = new ArrayList<Actuator>();

    /**
     * A queue that holds workflow jobs for this actuator.
     */
    public Queue<WorkflowJob> actuatorWorkflowQueue;

    /**
     * A list of past events of this actuator object.
     */
    public ArrayList<TimelineEntry> actuatorEventList = new ArrayList<TimelineEntry>();

    /**
     * The name of the actuator.
     */
    public String name;

    /**
     * The delay associated with the actuator (in ms).
     */
    public long delay;

    /**
     * The topic associated with the actuator.
     */
    public String topic;

    /**
     * Indicates whether the actuator is currently working.
     */
    public boolean isWorking;
    
    //Repository repo;

    //int latency;

    /**
     * Constructs a new Actuator with the specified name, topic, and delay.
     *
     * @param name  the name of the actuator
     * @param topic the topic associated with the actuator
     * @param delay the delay associated with the actuator (in ms)
     */
    public Actuator(String name, String topic, long delay) {
        this.name = name;
        this.topic = topic;
        this.delay = delay;
        Actuator.allActuators.add(this);
    }
}