package hu.u_szeged.inf.fog.simulator.iot;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;
import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;
import hu.u_szeged.inf.fog.simulator.iot.mobility.GeoLocation;
import hu.u_szeged.inf.fog.simulator.iot.mobility.MobilityEvent;
import hu.u_szeged.inf.fog.simulator.iot.mobility.MobilityStrategy;
import hu.u_szeged.inf.fog.simulator.iot.strategy.DeviceStrategy;
import hu.u_szeged.inf.fog.simulator.util.SimLogger;
import java.util.ArrayList;

/**
 * With this implementation, simple but mobility-enabled IoT devices can be created. 
 * The device is not capable of local data processing, so a certain time 
 * after the last data generation, if the local storage is still not empty,
 * the data is considered as stuck. This is necessary to avoid endless 
 * running of the simulation. So when creating a scenario, make sure that 
 * the position of the moving device is (at least temporarily) covered by a node.
 */
public class SmartDevice extends Device {
    
    /**
     * This value is used to calculate how many times the tick method can be called after the last data is created.
     * After that the content of the local storage will be registered as stuck data.
     */
    private int iterationCounter;
    
    /**
     * The amount of the total stuck data in all devices' storage.
     */
    public static long stuckData = 0;

    /**
     * Defines a new smart device. To avoid a completely periodic behavior, a random 
     * value between 0-3 minutes is added to the initial start time of the device.
     * @param startTime the time when the device starts generating data (ms)
     * @param stopTime the time when the device stops generating data (ms)
     * @param fileSize the size of the generated data (byte)
     * @param freq the time interval between two data measurement (ms)
     * @param mobilityStrategy the strategy that defines the route of the device
     * @param deviceStrategy the strategy that defines to which IoT application this device connects
     * @param localMachine the physical machine for networking and storing 
     * @param latency the minimum latency of data sending (ms)
     * @param pathLogging true if the route of the device needs to be logged
     */
    public SmartDevice(long startTime, long stopTime, long fileSize, long freq,
            MobilityStrategy mobilityStrategy, DeviceStrategy deviceStrategy, 
            PhysicalMachine localMachine, int latency, boolean pathLogging) {
        Device.allDevices.add(this);
        long delay = Math.abs(SeedSyncer.centralRnd.nextLong() % 180) * 1000; 
        this.startTime = startTime + delay;
        this.stopTime = stopTime + delay;
        this.fileSize = fileSize;
        this.geoLocation = mobilityStrategy.startPosition;
        this.freq = freq;
        this.localMachine = localMachine;
        this.mobilityStrategy = mobilityStrategy;
        this.isPathLogged = pathLogging;
        this.devicePath = new ArrayList<GeoLocation>();
        this.deviceStrategy = deviceStrategy;
        this.deviceStrategy.device = this;
        this.latency = latency;
        this.iterationCounter = 0;
        this.startMeter();
        /*
        if (Device.longestRunningDevice < this.stopTime) {
            Device.longestRunningDevice = this.stopTime;
        }
        */
    }
    
    /**
     * The method defines the operation of the smart device, which is as follows.<br/>
     * &ensp; 1 - data generation <br/>
     * &ensp; 2 - movement <br/>
     * &ensp; 3 - connecting to an application <br/>
     * &ensp; 4 - data transfer <br/>
     * &ensp; 5 - evaluation of the stop condition
     */
    @Override
    public void tick(long fires) {
        if (Timed.getFireCount() < stopTime && Timed.getFireCount() >= startTime) {
            new Sensor(this, 1);
        }

        GeoLocation newLocation = this.mobilityStrategy.move(this, freq);
        if (this.isPathLogged) {
            this.devicePath.add(new GeoLocation(this.geoLocation.latitude, this.geoLocation.longitude));
        }
        MobilityEvent.changePositionEvent(this, newLocation);

        this.deviceStrategy.findApplication();

        try {
            if (this.deviceStrategy.chosenApplication != null) {
                this.startDataTransfer();
            }

        } catch (NetworkException e) {
            e.printStackTrace();
        }

        if (Timed.getFireCount() > stopTime) {
            
            if (this.localMachine.localDisk.getFreeStorageCapacity() 
                    == this.localMachine.localDisk.getMaxStorageCapacity()) {
                this.stopMeter();
            } else {
                if (++this.iterationCounter > 100) {
                    SimLogger.logRun("WARNING: 100 iteration after the device stopped metering, "
                            + "IoT data are still stuck in " + this.localMachine.localDisk.getName() 
                            + "'s local storage (the device's actual position are not covered by any node.)");
                    this.stopMeter();
                    SmartDevice.stuckData += calculateStuckData();
                }     
            }
        }
    }

}