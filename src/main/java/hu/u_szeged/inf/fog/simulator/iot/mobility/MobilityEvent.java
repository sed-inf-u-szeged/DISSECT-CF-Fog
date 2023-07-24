package hu.u_szeged.inf.fog.simulator.iot.mobility;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.u_szeged.inf.fog.simulator.application.Application;
import hu.u_szeged.inf.fog.simulator.iot.Device;

public class MobilityEvent {

    public static long changePositionEventCounter = 0;

    public static long connectToNodeEventCounter = 0;

    public static long disconnectFromNodeEventCounter = 0;

    public static long changeNodeEventCounter = 0;

    public static void changePositionEvent(Device device, GeoLocation geoLocation) {
        device.geoLocation = geoLocation;
        changePositionEventCounter++;
    }

    public static void connectToNodeEvent(Device device, Application application) {
        if (application.computingAppliance.gateway.vm.getState().equals(VirtualMachine.State.RUNNING)) {
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

    public static void disconnectFromNodeEvent(Device device, Application application) {
        device.localMachine.localDisk.getLatencies()
                .remove(device.application.computingAppliance.iaas.repositories.get(0).getName());
        device.application.deviceList.remove(device);
        device.application = null;
        device.caRepository = null;

        disconnectFromNodeEventCounter++;
    }

    public static void refresh(Device device, Application application) {
        if (application == null && device.application != null) {
            // System.out.println("disconnect.. "+ Timed.getFireCount());
            MobilityEvent.disconnectFromNodeEvent(device, application);
        }
        if (application != null && device.application == null) {
            // System.out.println("connect to "+ Timed.getFireCount());
            MobilityEvent.connectToNodeEvent(device, application);
        }
        if (application != null && device.application != null && application != device.application) {
            // System.out.println("changed to " + Timed.getFireCount());
            MobilityEvent.changeNodeEvent(device, application);
        }
    }
}