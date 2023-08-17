package hu.u_szeged.inf.fog.simulator.iot;

import hu.mta.sztaki.lpds.cloud.simulator.DeferredEvent;
import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.energy.specialized.IaaSEnergyMeter;
import hu.mta.sztaki.lpds.cloud.simulator.energy.specialized.PhysicalMachineEnergyMeter;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ResourceConsumption;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ResourceConsumption.ConsumptionEvent;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.State;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.io.StorageObject;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;
import hu.u_szeged.inf.fog.simulator.application.Application;
import hu.u_szeged.inf.fog.simulator.iot.mobility.GeoLocation;
import hu.u_szeged.inf.fog.simulator.iot.mobility.MobilityStrategy;
import hu.u_szeged.inf.fog.simulator.iot.strategy.DeviceStrategy;
import java.util.ArrayList;

public abstract class Device extends Timed {

    public static long lastAction;

    public static ArrayList<Device> allDevices = new ArrayList<Device>();

    public static long longestRunningDevice = Long.MIN_VALUE;

    public static long totalGeneratedSize = 0;

    public GeoLocation geoLocation;

    public long startTime;

    public long stopTime;

    public int sensorCount;

    public long fileSize;

    public long freq;

    public int latency;

    public PhysicalMachine localMachine;

    public Repository caRepository;

    public Application application;

    public long generatedData;

    public MobilityStrategy mobilityStrategy;

    public DeviceStrategy deviceStrategy;

    public ArrayList<GeoLocation> devicePath;

    public boolean pathLogging;

    public int messageCount;

    public double energyConsumption;

    long sentData;

    public long locallyProcessedData;

    void startMeter() {
        if (this.isSubscribed() == false) {
            new DeferredEvent(this.startTime) {

                @Override
                protected void eventAction() {
                    subscribe(freq);
                    readEnergy();
                    try {
                        localMachine.localDisk.setState(State.RUNNING);
                    } catch (NetworkException e) {
                        e.printStackTrace();
                    }
                }
            };
        }
    }

    protected void readEnergy() {
        final PhysicalMachineEnergyMeter pmEnergyMeter = new PhysicalMachineEnergyMeter(this.localMachine);
        final Device device = this;
        class DataCollector extends Timed {
            public void start() {
                subscribe(device.freq);
            }

            public void stop() {
                unsubscribe();
            }

            @Override
            public void tick(final long fires) {
                energyConsumption = pmEnergyMeter.getTotalConsumption();
                if (!device.isSubscribed()) {
                    this.stop();
                    pmEnergyMeter.stopMeter();
                }
            }
        }
        
        final DataCollector dc = new DataCollector();
        pmEnergyMeter.startMeter(device.freq, true);
        dc.start();

    }

    public void stopMeter() {
        unsubscribe();
        Device.lastAction = Timed.getFireCount();
    }

    public long calculateCurrentStuckData() {
        long temp = 0;
        for (StorageObject so : this.localMachine.localDisk.contents()) {
            if (!(so instanceof VirtualAppliance)) {
                temp += so.size;
            }
        }
        return temp;
    }
    
    private void realTransfer() {
        this.localMachine.localDisk.contents().stream()
        .filter(storageObject -> !(storageObject instanceof VirtualAppliance)).forEach(storageObject -> {
            DeviceDataEvent soe = new DeviceDataEvent(this, storageObject);
            // this.localRepo.requestContentDelivery(so.id, this.caRepository, soe);
            try {
                NetworkNode.initTransfer(storageObject.size, ResourceConsumption.unlimitedProcessing,
                        this.localMachine.localDisk, this.caRepository, soe);
            } catch (NetworkException e) {
                e.printStackTrace();
            }
        });
    }
    
    protected void startDataTransfer() throws NetworkException {
        if (this.deviceStrategy.chosenApplication.computingAppliance.gateway.vm.getState()
                .equals(VirtualMachine.State.RUNNING)) {
        	this.realTransfer();
        } else if (!this.deviceStrategy.chosenApplication.isSubscribed()) {
            this.deviceStrategy.chosenApplication.subscribeApplication();
        }
    }

    class DeviceDataEvent implements ConsumptionEvent {

        private StorageObject so;
        private Device device;

        protected DeviceDataEvent(Device device, StorageObject so) {
            this.so = so;
            this.device = device;
        }

        @Override
        public void conComplete() {
            localMachine.localDisk.deregisterObject(this.so);
            application.receivedData += this.so.size;
            this.device.sentData += this.so.size;
        }

        @Override
        public void conCancelled(ResourceConsumption problematic) {
            try {
                System.err.println(
                        "Error in Device.java: Deleting StorageObject from the local repository is unsuccessful.");
                System.exit(0);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }
}
