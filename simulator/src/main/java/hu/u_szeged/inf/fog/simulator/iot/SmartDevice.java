package hu.u_szeged.inf.fog.simulator.iot;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;
import hu.mta.sztaki.lpds.cloud.simulator.io.StorageObject;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;
import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;
import hu.u_szeged.inf.fog.simulator.application.strategy.PliantApplicationStrategy;
import hu.u_szeged.inf.fog.simulator.iot.mobility.GeoLocation;
import hu.u_szeged.inf.fog.simulator.iot.mobility.MobilityEvent;
import hu.u_szeged.inf.fog.simulator.iot.mobility.MobilityStrategy;
import hu.u_szeged.inf.fog.simulator.iot.mobility.StaticMobilityStrategy;
import hu.u_szeged.inf.fog.simulator.iot.strategy.DeviceStrategy;
import java.util.ArrayList;
import hu.u_szeged.inf.fog.simulator.util.SimLogger;

public class SmartDevice extends Device {
    
    private int iteractionCounter;
    
    public static long stuckData = 0;

    public SmartDevice(long startTime, long stopTime, long fileSize, long freq,
            MobilityStrategy mobilityStrategy, DeviceStrategy deviceStrategy, PhysicalMachine localMachine, int latency,
            boolean pathLogging) {
        long delay = Math.abs(SeedSyncer.centralRnd.nextLong() % 1) * 60 * 1000; // TODO: fix this delay value
        this.startTime = startTime + delay;
        this.stopTime = stopTime + delay;
        this.fileSize = fileSize;
        this.geoLocation = mobilityStrategy.startPosition;
        this.freq = freq;
        this.localMachine = localMachine;
        this.mobilityStrategy = mobilityStrategy;
        Device.allDevices.add(this);
        this.isPathLogged = pathLogging;
        this.devicePath = new ArrayList<GeoLocation>();
        this.deviceStrategy = deviceStrategy;
        this.deviceStrategy.device = this;
        this.latency = latency;
        this.iteractionCounter = 0;
        this.startMeter();
        /*
        if (Device.longestRunningDevice < this.stopTime) {
            Device.longestRunningDevice = this.stopTime;
        }
        */
    }

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

        if (this.localMachine.localDisk.getFreeStorageCapacity() == this.localMachine.localDisk.getMaxStorageCapacity()
                && Timed.getFireCount() > stopTime) {
            this.stopMeter();
        }
        
        if (Timed.getFireCount() > stopTime && this.localMachine.localDisk.getMaxStorageCapacity()
                != this.localMachine.localDisk.getFreeStorageCapacity()) {
            if (++this.iteractionCounter > 100) {
                SimLogger.logRun("WARNING: 100 iteration after the device stopped metering, "
                        + "IoT data are still stuck in " +this.localMachine.localDisk.getName()+ "'s local storage because "
                        + "the device's actual position are not covered by any node.");
                this.stopMeter();
                SmartDevice.stuckData+=calculateStuckData();
            }            
        }
    }

}