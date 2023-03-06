package hu.u_szeged.inf.fog.simulator.iot;

import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.EnumMap;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.PowerState;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.util.PowerTransitionGenerator;
import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;
import hu.u_szeged.inf.fog.simulator.iot.mobility.GeoLocation;
import hu.u_szeged.inf.fog.simulator.iot.mobility.MobilityEvent;
import hu.u_szeged.inf.fog.simulator.iot.mobility.MobilityStrategy;
import hu.u_szeged.inf.fog.simulator.iot.strategy.DeviceStrategy;

public class SmartDevice extends Device{
	
	public SmartDevice(long startTime, long stopTime, long fileSize, int sensorCount, long freq,
			MobilityStrategy mobilityStrategy, DeviceStrategy deviceStrategy, int latency, boolean pathLogging) {
		long delay = Math.abs(SeedSyncer.centralRnd.nextLong() % 1) * 60 * 1000; // TODO: fix this delay value
        this.startTime = startTime + delay;
        this.stopTime = stopTime + delay;
        this.fileSize = fileSize * sensorCount;
        this.sensorCount = sensorCount;
        this.geoLocation = mobilityStrategy.startPosition;
        this.freq = freq;
        this.mobilityStrategy = mobilityStrategy;
        this.localRepo = this.createLocalRepo();
        Device.allDevices.add(this);
        this.pathLogging = pathLogging;
        this.devicePath = new ArrayList<GeoLocation>();
        this.deviceStrategy = deviceStrategy;
        this.deviceStrategy.device =this;
        this.latency = latency;
        this.startMeter();
        if(Device.longestRunningDevice < this.stopTime) {
        	Device.longestRunningDevice = this.stopTime;
        }
	}
	
	@Override
	public void tick(long fires) {
		if (Timed.getFireCount() < stopTime && Timed.getFireCount() >= startTime) {
            new Sensor(this, 1);
        }

		GeoLocation newLocation = this.mobilityStrategy.move(freq);
		if(this.pathLogging) {
			this.devicePath.add(new GeoLocation(this.geoLocation.latitude, this.geoLocation.longitude));
		}
		MobilityEvent.changePositionEvent(this, newLocation);	
		
		this.deviceStrategy.findApplication();

		try {
			if(this.deviceStrategy.chosenApplication != null) {
				this.startDataTransfer();
			}
			
		} catch (NetworkException e) {
			e.printStackTrace();
		}
		
		if (this.localRepo.getFreeStorageCapacity() == this.localRepo.getMaxStorageCapacity() &&  Timed.getFireCount() > stopTime) {
            this.stopMeter();
        }

		if(Timed.getFireCount() > stopTime && (this.localRepo.getMaxStorageCapacity()-this.localRepo.getFreeStorageCapacity())>0) {
			System.err.println("Warning in SmartDevice.java: The local storage of the device did not empty out even after the sensor meterings stopped "
					+ "(one possible cause: the device's position might be not covered by any node).");
		}
	}

	// TODO: refactor!
	private Repository createLocalRepo() {
		
		HashMap < String, Integer > latencyMap = new HashMap < String, Integer > ();

        EnumMap < PowerTransitionGenerator.PowerStateKind, Map < String, PowerState >> transitions = null;

        try {
            transitions = PowerTransitionGenerator.generateTransitions(0, 0, 0, 0, 0);
        } catch (Exception e) {
            e.printStackTrace();
        }

        final Map < String, PowerState > stTransitions = transitions.get(PowerTransitionGenerator.PowerStateKind.storage);
        final Map < String, PowerState > nwTransitions = transitions.get(PowerTransitionGenerator.PowerStateKind.network);
        
        return new Repository(1073741824L, Integer.toString(this.hashCode()), 3250, 3250, 3250, latencyMap, stTransitions, nwTransitions); // 26 Mbit/s 
	}
}
