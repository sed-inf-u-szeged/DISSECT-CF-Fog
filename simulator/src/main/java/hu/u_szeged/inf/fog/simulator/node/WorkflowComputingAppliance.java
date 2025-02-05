package hu.u_szeged.inf.fog.simulator.node;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.u_szeged.inf.fog.simulator.iot.mobility.GeoLocation;
import hu.u_szeged.inf.fog.simulator.workflow.WorkflowJob;
import java.util.ArrayList;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeSet;

/**
 * The {@code WorkflowComputingAppliance} class extends the {@link ComputingAppliance} class
 * to represent a computing appliance tailored for workflow management.
 */
public class WorkflowComputingAppliance extends ComputingAppliance {

    /**
     * A list of virtual machines utilized by this appliance for job processing.
     */
    public ArrayList<VirtualMachine> workflowVms = new ArrayList<VirtualMachine>();

    /**
     * The total running time of the utilized virtual machines.
     */
    public long vmTime;

    /**
     * A priority queue for managing workflow jobs and to apply different ordering
     * of jobs.
     */
    public PriorityQueue<WorkflowJob> workflowQueue;
    
    public Set<String> cluster;
    
    public static ComputingAppliance getNodeByName(String name) {
        for (ComputingAppliance ca : ComputingAppliance.allComputingAppliances) {
            if (ca.name.equals(name)) {
                return ca;
            }
        }
        return null;
    }
    
    /**
     * Constructs a new {@code WorkflowComputingAppliance} with the specified parameters.
     *
     * @param file        the file path defining an IaaSService
     * @param name        the name of the computing appliance
     * @param geoLocation the geographical location of the computing appliance
     * @param range       the range of the computing appliance
     */
    public WorkflowComputingAppliance(String file, String name, GeoLocation geoLocation, long range) throws Exception {
        super(file, name, geoLocation, range);
        this.cluster = new TreeSet<>();
    }

    public static void setDistanceBasedLatency() {
        for (ComputingAppliance ca1 : ComputingAppliance.allComputingAppliances) {
            for (ComputingAppliance ca2 : ComputingAppliance.allComputingAppliances) {
                if (ca1 != ca2) {
                    int distance = (int) ca1.geoLocation.calculateDistance(ca2.geoLocation) / 10_000;
                    ca1.iaas.repositories.get(0).addLatencies(ca2.iaas.repositories.get(0).getName(), distance);
                    ca2.iaas.repositories.get(0).addLatencies(ca1.iaas.repositories.get(0).getName(), distance);
                }
            }
        }
    }
}
