package hu.u_szeged.inf.fog.simulator.iot;

import java.util.ArrayList;

import hu.mta.sztaki.lpds.cloud.simulator.DeferredEvent;
import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ResourceConsumption;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ResourceConsumption.ConsumptionEvent;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.State;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.io.StorageObject;
import hu.u_szeged.inf.fog.simulator.application.Application;
import hu.u_szeged.inf.fog.simulator.iot.mobility.GeoLocation;
import hu.u_szeged.inf.fog.simulator.iot.mobility.MobilityStrategy;
import hu.u_szeged.inf.fog.simulator.iot.strategy.DeviceStrategy;

public abstract class Device extends Timed{
	
	public static long lastAction;

	public static ArrayList < Device > allDevices = new ArrayList < Device > ();
	
	public static long longestRunningDevice = Long.MIN_VALUE;
	
	public static long totalGeneratedSize = 0;  
	
	public GeoLocation geoLocation;
	
	public long startTime;
	
	public long stopTime;
	
	int sensorCount;
	
	public long fileSize;
	
	long freq;
	
	public int latency;
	
	public Repository localRepo;
	
	public Repository caRepository;
	
	public Application application;
	
	public long generatedData;
	
	public MobilityStrategy mobilityStrategy;
	
	public DeviceStrategy deviceStrategy;
	
	public ArrayList<GeoLocation> devicePath;
	
	public boolean pathLogging;
	
	public int messageCount;
		
	void startMeter() {
        if (this.isSubscribed() == false) {
            new DeferredEvent(this.startTime) {

                @Override
                protected void eventAction() {
                    subscribe(freq);
                    try {
						localRepo.setState(State.RUNNING);
					} catch (NetworkException e) {
						e.printStackTrace();
					}
                }
            };
        }
    }
	
	void stopMeter() {
	        unsubscribe();
	}
	
	protected void startDataTransfer() throws NetworkException {
		if(this.deviceStrategy.chosenApplication.computingAppliance.gateway.vm.getState().equals(VirtualMachine.State.RUNNING)) {
			for (StorageObject so: this.localRepo.contents()) {
	            DeviceDataEvent soe = new DeviceDataEvent(so);
	            //this.localRepo.requestContentDelivery(so.id, this.caRepository, soe);
	            NetworkNode.initTransfer(so.size, ResourceConsumption.unlimitedProcessing, this.localRepo, this.caRepository, soe);
	        }
		}else if (!this.deviceStrategy.chosenApplication.isSubscribed()) {
            try {
            	this.deviceStrategy.chosenApplication.subscribeApplication();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }     
	}

	class DeviceDataEvent implements ConsumptionEvent {

        private StorageObject so;

        protected DeviceDataEvent(StorageObject so) {
            this.so = so;
        }

        @Override
        public void conComplete() {
        	localRepo.deregisterObject(this.so);
        	application.receivedData += this.so.size;
        }

        @Override
        public void conCancelled(ResourceConsumption problematic) {
            try {
                System.err.println("Error in Device.java: Deleting StorageObject from the local repository is unsuccessful.");
                System.exit(0);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }
}
