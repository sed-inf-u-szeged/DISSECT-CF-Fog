package hu.u_szeged.inf.fog.simulator.iot;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager.VMManagementException;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine.StateChangeException;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.AlterableResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ConsumptionEventAdapter;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ResourceConsumption;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;
import hu.mta.sztaki.lpds.cloud.simulator.io.StorageObject;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;
import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;
import hu.u_szeged.inf.fog.simulator.iot.mobility.GeoLocation;
import hu.u_szeged.inf.fog.simulator.iot.mobility.MobilityEvent;
import hu.u_szeged.inf.fog.simulator.iot.mobility.MobilityStrategy;
import hu.u_szeged.inf.fog.simulator.iot.strategy.DeviceStrategy;
import hu.u_szeged.inf.fog.simulator.prediction.mobility.Predictor;
import hu.u_szeged.inf.fog.simulator.util.SimLogger;
import hu.u_szeged.inf.fog.simulator.util.TimelineVisualiser.TimelineEntry;
import java.util.ArrayList;

public class EdgeDevice extends Device {

    Predictor predictor;

    public static VirtualAppliance edgeDeviceVa = 
            new VirtualAppliance("edgeDeviceVa", 1, 0, false, 1073741824L); // 1 GB

    public AlterableResourceConstraints edgeDeviceArc;

    public VirtualMachine localVm;

    // public static int success=0, all=0; // TODO: rename!

    // public static int vmShutdown = 0, vmStart = 0, vmReq = 0;

    private double instructionPerByte;

    public ArrayList<TimelineEntry> timelineEntries = new ArrayList<TimelineEntry>();

    public EdgeDevice(long startTime, long stopTime, long fileSize, long freq,
            MobilityStrategy mobilityStrategy, int kkOrder, DeviceStrategy deviceStrategy, PhysicalMachine localMachine,
            double instructionPerByte, int latency, boolean pathLogging) {
        long delay = Math.abs(SeedSyncer.centralRnd.nextLong() % 1) * 60 * 1000; // TODO: fix this delay value
        this.startTime = startTime + delay;
        this.stopTime = stopTime + delay;
        this.fileSize = fileSize;
        this.geoLocation = mobilityStrategy.startPosition;
        this.freq = freq;
        this.localMachine = localMachine;
        this.mobilityStrategy = mobilityStrategy;
        Device.allDevices.add(this);
        this.instructionPerByte = instructionPerByte;
        this.isPathLogged = pathLogging;
        this.devicePath = new ArrayList<GeoLocation>();
        this.deviceStrategy = deviceStrategy;
        this.deviceStrategy.device = this;
        this.latency = latency;
        this.predictor = new Predictor(kkOrder);
        this.edgeDeviceArc = new AlterableResourceConstraints(localMachine.getCapacities().getRequiredCPUs(),
                localMachine.getCapacities().getRequiredProcessingPower(),
                localMachine.getCapacities().getRequiredMemory());
        this.startMeter();
        this.localMachine.turnon();
        /*
        if (Device.longestRunningDevice < this.stopTime) {
            Device.longestRunningDevice = this.stopTime;
        }*/

    }

    private void startVm() {
        if (this.localVm == null) {
            this.localMachine.localDisk.registerObject(edgeDeviceVa);
            try {
                this.localVm = this.localMachine.requestVM(EdgeDevice.edgeDeviceVa, this.edgeDeviceArc,
                        this.localMachine.localDisk, 1)[0];
            } catch (VMManagementException | NetworkException e) {
                e.printStackTrace();
            }
        } else if (this.localVm.getState().equals(VirtualMachine.State.SHUTDOWN)) {
            try {
                this.localVm.switchOn(
                        this.localMachine.allocateResources(edgeDeviceArc, false, PhysicalMachine.defaultAllocLen),
                        this.localMachine.localDisk);
            } catch (VMManagementException | NetworkException e) {
                e.printStackTrace();
            }
        }
    }

    private void stopVm() {
        if (this.localVm != null && this.localVm.getState().equals(VirtualMachine.State.RUNNING)) {
            try {
                this.localVm.switchoff(true);
            } catch (StateChangeException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void tick(long fires) {
        if (Timed.getFireCount() < stopTime && Timed.getFireCount() >= startTime) {
            new Sensor(this, 1);
        }

        /*
         * int direction = -1; if (!(this.mobilityStrategy instanceof
         * StaticMobilityStrategy)) { GeoLocation prev = new
         * GeoLocation(this.geoLocation.latitude, this.geoLocation.longitude);
         * GeoLocation newLocation = this.mobilityStrategy.move(freq);
         * if(this.pathLogging) { this.devicePath.add(new
         * GeoLocation(this.geoLocation.latitude, this.geoLocation.longitude)); }
         * MobilityEvent.changePositionEvent(this, newLocation);
         * 
         * double angle = prev.angle(newLocation); if (!prev.equals(newLocation)) {
         * predictor.updateBacklog(prev, newLocation); // TODO: reduce number of
         * prediction direction = predictor.predictDirection();
         * System.out.println("PREDICTED: " + direction + " vs. PREV ACTUAL: " + angle);
         * 
         * if (direction == (int) angle) { success++; } all++;
         * 
         * } }
         */
        GeoLocation newLocation = this.mobilityStrategy.move(this);
        if (this.isPathLogged) {
            this.devicePath.add(new GeoLocation(this.geoLocation.latitude, this.geoLocation.longitude));
        }
        MobilityEvent.changePositionEvent(this, newLocation);

        this.deviceStrategy.findApplication(); // TODO: Objects.nonNull/isNull

        /*
         * if(predictor.predictConnection(this, direction, this.mobilityStrategy.speed))
         * { if(this.localVm != null &&
         * this.localVm.getState().equals(VirtualMachine.State.RUNNING)) { try {
         * this.localVm.switchoff(true); vmShutdown++; } catch (StateChangeException e)
         * { e.printStackTrace(); } } }else if(predictor.predictDisconnection(this,
         * direction, this.mobilityStrategy.speed)) { if(this.localVm == null) {
         * this.localMachine.localDisk.registerObject(edgeDeviceVa); try { this.localVm
         * = this.localMachine.requestVM(EdgeDevice.edgeDeviceVa, this.edgeDeviceArc,
         * this.localMachine.localDisk, 1)[0]; vmReq++; } catch (VMManagementException |
         * NetworkException e) { e.printStackTrace(); } }else
         * if(this.localVm.getState().equals(VirtualMachine.State.SHUTDOWN)) { try {
         * this.localVm.switchOn(this.localMachine.allocateResources(edgeDeviceArc,
         * false, PhysicalMachine.defaultAllocLen), this.localMachine.localDisk);
         * vmStart++; } catch (VMManagementException | NetworkException e) {
         * e.printStackTrace(); } } }
         */

        try {
            if (this.deviceStrategy.chosenApplication != null) {
                this.startDataTransfer();
                this.stopVm();
            } else {
                if (this.localVm == null || this.localVm.getState().equals(VirtualMachine.State.SHUTDOWN)) {
                    this.startVm();
                } else {
                    long dataToBeProcessed = 0;
                    ArrayList<StorageObject> dataToBeRemoved = new ArrayList<>();
                    for (StorageObject so : this.localMachine.localDisk.contents()) {
                        if (!(so instanceof VirtualAppliance)) {
                            dataToBeProcessed += so.size;
                            dataToBeRemoved.add(so);
                        }
                    }
                    if (dataToBeProcessed > 0) {
                        final EdgeDevice edgeDevice = this;
                        final long currentlyProcessedData = dataToBeProcessed;
                        double noi = currentlyProcessedData * this.instructionPerByte;

                        ResourceConsumption rc = this.localVm.newComputeTask(noi,
                                ResourceConsumption.unlimitedProcessing, new ConsumptionEventAdapter() {
                                    final long taskStartTime = Timed.getFireCount();

                                    @Override
                                    public void conComplete() {
                                        locallyProcessedData += currentlyProcessedData;
                                        timelineEntries.add(new TimelineEntry(taskStartTime, Timed.getFireCount(),
                                                Integer.toString(edgeDevice.hashCode())));
                                        SimLogger.logRun("Device-" + edgeDevice.hashCode() + " started at: "
                                                + taskStartTime + " finished at: " + Timed.getFireCount() + " bytes: "
                                                + currentlyProcessedData + " took: "
                                                + (Timed.getFireCount() - taskStartTime) + " instructions: " + noi);
                                    }
                                });
                        if (rc != null) {
                            for (StorageObject so : dataToBeRemoved) {
                                this.localMachine.localDisk.deregisterObject(so);
                            }
                        }
                    }
                }
            }
        } catch (NetworkException e) {
            e.printStackTrace();
        }
        if (Timed.getFireCount() > stopTime && (this.locallyProcessedData + this.sentData) == this.generatedData) {
            this.stopMeter();
        }
    }
}
