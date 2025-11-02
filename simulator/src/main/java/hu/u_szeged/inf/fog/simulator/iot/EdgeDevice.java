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
import hu.u_szeged.inf.fog.simulator.util.SimLogger;
import hu.u_szeged.inf.fog.simulator.util.TimelineVisualiser.TimelineEntry;

import java.util.ArrayList;

/**
 * The EdgeDevice class represents a device at the edge of a network, inheriting from the Device class.
 * With this implementation, a more complex, mobility-enabled IoT devices can be created. 
 * The device is capable of local data processing by initializing a local VM.
 */
public class EdgeDevice extends Device {

    /**
     * The VM image file associated to the local virtual machine.
     */
    public static VirtualAppliance edgeDeviceVa = 
            new VirtualAppliance("edgeDeviceVa", 1, 0, false, 1073741824L); // 1 GB

    /**
     * The resource requirement associated to the local virtual machine.
     */
    public AlterableResourceConstraints edgeDeviceArc;

    /**
     * The VirtualMachine instance representing the local virtual machine on the edge device.
     */
    public VirtualMachine localVm;

    /**
     * The instruction per byte ratio used for data processing tasks.
     */
    private double instructionPerByte;

    /**
     * This list stores the start and end timestamps of the locally processed tasks.
     */
    public ArrayList<TimelineEntry> timelineEntries = new ArrayList<TimelineEntry>();

    /**
     * Constructs a new EdgeDevice instance.
     *
     * @param startTime          the start time of the edge device
     * @param stopTime           the stop time of the edge device
     * @param fileSize           the file size associated with the edge device
     * @param freq               the frequency of operations for the edge device
     * @param mobilityStrategy   the mobility strategy for the edge device
     * @param deviceStrategy     the device strategy for the edge device
     * @param localMachine       the local physical machine for the edge device
     * @param instructionPerByte the instruction per byte ratio for data processing
     * @param latency            the base network latency for the edge device
     * @param pathLogging        flag indicating if the path logging is enabled for the edge device
     */
    public EdgeDevice(long startTime, long stopTime, long fileSize, long freq, MobilityStrategy mobilityStrategy, 
            DeviceStrategy deviceStrategy, PhysicalMachine localMachine, double instructionPerByte, int latency, 
            boolean pathLogging) {
        this.battery = null;  //default erre inicializálódik but for good measure
        long delay = Math.abs(SeedSyncer.centralRnd.nextLong() % 180) * 1000;
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
        this.edgeDeviceArc = new AlterableResourceConstraints(localMachine.getCapacities().getRequiredCPUs(),
                localMachine.getCapacities().getRequiredProcessingPower(),
                localMachine.getCapacities().getRequiredMemory());
        this.startMeter();
        this.localMachine.turnon();
    }

    /**
     * Starts the virtual machine if it is not already running.
     */
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

    /**
     * Stops the virtual machine if it is currently running.
     */
    private void stopVm() {
        if (this.localVm != null && this.localVm.getState().equals(VirtualMachine.State.RUNNING)) {
            try {
                this.localVm.switchoff(true);
            } catch (StateChangeException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * The tick method is called to simulate a time step for the edge device.
     * It handles data transfer, mobility updates, and local processing.
     */
    @Override
    public void tick(long fires) {
        if (Timed.getFireCount() < stopTime && Timed.getFireCount() >= startTime) {
            new Sensor(this, 1);
        }

        GeoLocation newLocation = this.mobilityStrategy.move(this);
        if (this.isPathLogged) {
            this.devicePath.add(new GeoLocation(this.geoLocation.latitude, this.geoLocation.longitude));
        }
        MobilityEvent.changePositionEvent(this, newLocation);

        //üres battery esetén nem képes működni az eszköz de mozogni tud(hat)? telefon igen, drón nem nagyon
        if(battery != null && battery.isCharging()){
            return;
        }

        this.deviceStrategy.findApplication(); 

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
                                        //System.out.println("Start task "+ this.hashCode() + ": " + taskStartTime);

                                        locallyProcessedData += currentlyProcessedData;
                                        timelineEntries.add(new TimelineEntry(taskStartTime, Timed.getFireCount(),
                                                Integer.toString(edgeDevice.hashCode())));
                                        SimLogger.logRun("Device-" + edgeDevice.hashCode() + " started at: "
                                                + taskStartTime + " finished at: " + Timed.getFireCount() + " bytes: "
                                                + currentlyProcessedData + " took: "
                                                + (Timed.getFireCount() - taskStartTime) + " instructions: " + noi);

                                        //System.out.println("End task "+ this.hashCode() + ": " + Timed.getFireCount());

                                        if(battery != null) {
                                            /*
                                            //battery számítások
                                            cpuUtilization = numberOfInstructions / (numberOfCpuCores * instructionsPerTick * taskTime) (taskTime tickben)
                                            0-1 közötti érték kihasználtságra

                                            avgPowerConsumption = idlePower + cpuUtilization * (maxPower − idlePower),
                                            de ez lekérhető a PM cpuTransitionjéből getCurrentPowerrel ahol a paraméter a cpuUtilization
                                            ez a CPU fogyasztása W-ban

                                            totalEnergyConsumedDuringTask = avgPowerConsumption * (t/3600000)
                                            (ha t msbe van, és igy lesz akkor végeredmény Wh)
                                            a tényleges energiaigénye a tasknak Wh-ban

                                            battery csökkentéshez mértékegység váltás kell Wh -> mAh
                                            energyConsumed_Ah = energyConsumed_Wh / voltage
                                            energyConsumed_mAh = energyConsumed_Ah * 1000
                                            newBatteryLvl = max(0, currentLvl-energyConsumed_mAh)
                                            */

                                            //consumption
                                            double taskTime = Timed.getFireCount() - taskStartTime;
                                            double cpuUtilization = noi / (localMachine.getCapacities().getTotalProcessingPower() * taskTime);
                                            double avgPowerConsumption = localMachine.getCurrentPowerBehavior().getCurrentPower(cpuUtilization);
                                            /* //alternative calculation, same result
                                            double avgPowerConsumption = localMachine.getCurrentPowerBehavior().getMinConsumption() +
                                                cpuUtilization * localMachine.getCurrentPowerBehavior().getConsumptionRange();*/
                                            double taskEnergyConsumption = avgPowerConsumption * (taskTime / 3600000);

                                            //conversion to mAh
                                            taskEnergyConsumption = taskEnergyConsumption / battery.getVoltage() * 1000;

                                            //drain
                                            battery.setCurrLevel(Math.max(0, battery.getCurrLevel() - taskEnergyConsumption));
                                            battery.readings.put(Timed.getFireCount(), battery.getCurrLevel());
                                        }
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