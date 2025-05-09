package hu.u_szeged.inf.fog.simulator.iot.mobility;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.u_szeged.inf.fog.simulator.application.Application;
import hu.u_szeged.inf.fog.simulator.iot.Device;

/**
 * The class represents events related to mobility of IoT devices, which 
 * are triggered whenever a device has to update its connection to an 
 * application (running on a node) for a reason.
 */
public class MobilityEvent {
    
    /**
     * Calculates how many times a device changed position.
     */
    public static long changePositionEventCounter;

    /**
     * Calculates how many times a device connected to a node.
     */
    public static long connectToNodeEventCounter;

    /**
     * Calculates how many times a device disconnected to a node.
     */
    public static long disconnectFromNodeEventCounter;

    /**
     * Calculates how many times an already connected device changes
     * connection to another node.
     */
    public static long changeNodeEventCounter;

    /**
     * Increments the count of position changes for a device.
     *
     * @param device the device that has a new position
     * @param geoLocation the new position, which needs to be set
     */
    public static void changePositionEvent(Device device, GeoLocation geoLocation) {
        if (geoLocation != null) {
            device.geoLocation = geoLocation;
            changePositionEventCounter++;
        }
    }

    /**
     * Connects a device to an application (running on a node) and sets the
     * network between the device and the node with a base latency determined
     * by the device and weighted with the physical distance.
     *
     * @param device      the device to connect
     * @param application the application running on a node to connect to
     */
    public static void connectToNodeEvent(Device device, Application application) {
        if (application.computingAppliance.broker.vm.getState().equals(VirtualMachine.State.RUNNING)) {
            device.application = application;
            application.deviceList.add(device);
            device.caRepository = application.computingAppliance.iaas.repositories.get(0);
            device.localMachine.localDisk.addLatencies(
                    device.application.computingAppliance.iaas.repositories.get(0).getName(),
                    device.latency + (int) (device.geoLocation
                            .calculateDistance(device.application.computingAppliance.geoLocation) / 1000));

            connectToNodeEventCounter++;

            if (!device.application.isSubscribed()) {
                try {
                    device.application.subscribeApplication();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Changes the device's current application (running on a node) to another one. 
     * The network is also set between the device and the new node with a base latency 
     * determined by the device and weighted with the physical distance.
     *
     * @param device      the device whose node is being changed
     * @param application the application running on a node to connect to
     */
    public static void changeNodeEvent(Device device, Application application) {
        device.application.deviceList.remove(device);
        device.application = application;
        device.caRepository = application.computingAppliance.iaas.repositories.get(0);
        application.deviceList.add(device);
        device.localMachine.localDisk.getLatencies()
                .remove(device.application.computingAppliance.iaas.repositories.get(0).getName());
        device.localMachine.localDisk.addLatencies(
                device.application.computingAppliance.iaas.repositories.get(0).getName(),
                device.latency
                        + (int) (device.geoLocation.calculateDistance(device.application.computingAppliance.geoLocation)
                                / 1000));

        changeNodeEventCounter++;

        if (!device.application.isSubscribed()) {
            try {
                device.application.subscribeApplication();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Disconnects a device from an application (running on a node)
     * and disconnects the device from its network as well.
     *
     * @param device      the device to disconnect
     * @param application the application from which the device disconnects
     */
    public static void disconnectFromNodeEvent(Device device, Application application) {
        device.localMachine.localDisk.getLatencies()
                .remove(device.application.computingAppliance.iaas.repositories.get(0).getName());
        device.application.deviceList.remove(device);
        device.application = null;
        device.caRepository = null;

        disconnectFromNodeEventCounter++;
    }

    /**
     * Refreshes the device's connection with an application node based on
     * the current and the future connection of a device.
     *
     * @param device      the device to refresh
     * @param application the new application to be associated with the device
     */
    public static void refresh(Device device, Application application) {
        if (application == null && device.application != null) {
            MobilityEvent.disconnectFromNodeEvent(device, application);
        }
        if (application != null && device.application == null) {
            MobilityEvent.connectToNodeEvent(device, application);
        }
        if (application != null && device.application != null && application != device.application) {
            MobilityEvent.changeNodeEvent(device, application);
        }
    }
}