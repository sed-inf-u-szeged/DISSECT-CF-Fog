package hu.u_szeged.inf.fog.simulator.iot.strategy;

import hu.u_szeged.inf.fog.simulator.application.Application;
import hu.u_szeged.inf.fog.simulator.iot.Device;
import java.util.ArrayList;
import java.util.stream.Collectors;

/**
 * This abstract class represents a strategy for finding an IoT application suitable for an IoT device.
 */
public abstract class DeviceStrategy {

    /**
     * The device for which the strategy is being applied.
     */
    public Device device;

    /**
     * The application chosen based on the strategy.
     */
    public Application chosenApplication;

    /**
     * The abstract method to be overridden, which defines the logic for finding the application.
     */
    public abstract void findApplication();

    /**
     * Returns with the list of available applications, which are able to receive IoT data
     * directly from the IoT device. The device must be located inside of the applications range.
     */
    public ArrayList<Application> getAvailableApplications() {
        ArrayList<Application> availableApplications = Application.allApplications.stream()
                .filter(app -> app.serviceable && this.device.geoLocation
                        .calculateDistance(app.computingAppliance.geoLocation) <= app.computingAppliance.range * 1000)
                .collect(Collectors.toCollection(ArrayList::new));

        return availableApplications;
    }
}