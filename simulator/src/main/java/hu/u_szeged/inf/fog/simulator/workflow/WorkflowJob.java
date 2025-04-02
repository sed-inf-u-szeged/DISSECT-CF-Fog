package hu.u_szeged.inf.fog.simulator.workflow;

import hu.u_szeged.inf.fog.simulator.iot.Actuator;
import hu.u_szeged.inf.fog.simulator.node.WorkflowComputingAppliance;
import java.util.ArrayList;

public class WorkflowJob {

    /**
     * A list containing jobs created so far.
     */
    public static ArrayList<WorkflowJob> workflowJobs = new ArrayList<>();

    public long bytesRecieved;

    //public int fileRecievedByAssigning;

    //public ArrayList<StorageObject> filesRecieved;

    /**
     * Unique identifier or a job.
     */
    public String id;

    public State state;

    public double runtime;

    public double longitude;

    public double latitude;

    public int underRecieving;

    public boolean reAssign = false;

    public ArrayList<Uses> inputs;
    public ArrayList<Uses> outputs;

    public WorkflowComputingAppliance ca;

    public Actuator actuator;

    public static enum State {

        SUBMITTED,

        STARTED,

        COMPLETED,

        REASSIGNING,
    }

    @Override
    public String toString() {
        return "WorkflowJob [id=" + id + ", state=" + state + ", ca=" + ca.name +  ", runtime=" + runtime + ", longitude=" + longitude
                + ", latitude=" + latitude + ", inputs=" + inputs + ", outputs=" + outputs + "]";
    }

    public WorkflowJob(String id, double runtime, double longitude, double latitude, State state,
            ArrayList<Uses> inputs, ArrayList<Uses> outputs) {
        this.id = id;
        this.runtime = runtime;
        this.state = WorkflowJob.State.SUBMITTED;
        this.inputs = inputs;
        this.outputs = outputs;
        this.longitude = longitude;
        this.latitude = latitude;
        this.bytesRecieved = 0;
        //this.filesRecieved = new ArrayList<StorageObject>();
        WorkflowJob.workflowJobs.add(this);
    }

    public static class Uses {

        public static enum Type {
            DATA,

            COMPUTE,

            ACTUATE
        }

        public Type type;

        public long size;

        public double runtime;

        public long activate;

        public int amount;

        public String id;

        public Uses(Type type, long size, double runtime, long activate, int amount, String id) {
            this.type = type;
            this.size = size;
            this.runtime = runtime;
            this.activate = activate;
            this.amount = amount;
            this.id = id;
        }

        @Override
        public String toString() {
            return "Uses [type=" + type + ", size=" + size + ", runtime=" + runtime + ", activate=" + activate
                    + ", amount=" + amount + ", id=" + id + "]";
        }
    }
}
