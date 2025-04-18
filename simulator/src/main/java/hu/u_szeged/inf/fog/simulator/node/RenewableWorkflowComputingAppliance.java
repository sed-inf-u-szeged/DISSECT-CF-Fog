package hu.u_szeged.inf.fog.simulator.node;

import hu.u_szeged.inf.fog.simulator.energyprovider.Provider;
import hu.u_szeged.inf.fog.simulator.iot.mobility.GeoLocation;
import hu.u_szeged.inf.fog.simulator.workflow.WorkflowJob;

import java.util.PriorityQueue;

public class RenewableWorkflowComputingAppliance extends WorkflowComputingAppliance {

    Provider provider;
    public String file;

    /**
     * Constructs a new {@code WorkflowComputingAppliance} with the specified parameters.
     *
     * @param file        the file path defining an IaaSService
     * @param name        the name of the computing appliance
     * @param geoLocation the geographical location of the computing appliance
     * @param range       the range of the computing appliance
     */
    public RenewableWorkflowComputingAppliance(String file, String name, GeoLocation geoLocation, long range, Provider provider) throws Exception {
        super(file, name, geoLocation, range);
        this.file = file;
        this.provider = provider;
    }
}
