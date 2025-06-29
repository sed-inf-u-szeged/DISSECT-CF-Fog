package hu.u_szeged.inf.fog.simulator.application;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine.ResourceAllocation;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager.VMManagementException;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine.StateChangeException;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ConsumptionEventAdapter;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ResourceConsumption;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;
import hu.mta.sztaki.lpds.cloud.simulator.io.StorageObject;
import hu.u_szeged.inf.fog.simulator.application.strategy.ApplicationStrategy;
import hu.u_szeged.inf.fog.simulator.iot.Device;
import hu.u_szeged.inf.fog.simulator.node.ComputingAppliance;
import hu.u_szeged.inf.fog.simulator.prediction.FeatureManager;
import hu.u_szeged.inf.fog.simulator.provider.Instance;
import hu.u_szeged.inf.fog.simulator.util.SimLogger;
import hu.u_szeged.inf.fog.simulator.util.TimelineVisualiser.TimelineEntry;
import java.util.ArrayList;

/**
 * This class is an abstract representation of an IoT application. It receives data
 * from IoT devices, which are then processed by virtual machines (initialized on the
 * physical architecture) in the form of computational tasks. 
 * In the current implementation, one virtual machine is capable of processing one task at a time. 
 * This class also handles decisions related to VM scaling and data offloading (load balancing).
 * The class is responsible for batch-processing, as it extends  the Timed class:
 * it will handle IoT data only at certain intervals (according to its frequency).
 * An application only begins processing tasks when a so-called broker (service)
 * - represented by a VM - is already running.
 */
public class Application extends Timed {

    /**
     * A list containing references to all applications.
     * Each element in the list is an instance of the {@code Application} class.
     */
    public static ArrayList<Application> allApplications = new ArrayList<>();

    /**
     * It aggregates the time of each file transfer during offloading decisions.
     */
    public static long totalTimeOnNetwork;

    /**
     * It aggregates the size of each file transfer during offloading decisions.
     */
    public static long totalBytesOnNetwork;
    
    /**
     * A helper variable for monitoring the timestamp of the most recent task processing.
     */
    public static long lastAction;
    
    /**
     * A helper variable to track the amount of data processed by the application.
     */
    public static long totalProcessedSize;

    /**
     * A list containing references to the VMs utilized by the application.
     */
    public ArrayList<AppVm> utilisedVms;

    /**
     * A physical resource that the application uses (where the application has been 'deployed').
     */
    public ComputingAppliance computingAppliance;

    /**
     * Name of the application.
     */
    public String name;

    /**
     * The frequency of periodic task execution (time interval in ms).
     */
    protected long freq;

    /**
     * A list of IoT devices that transmit data to this application.
     */
    public ArrayList<Device> deviceList;

    /**
     * Maximum size of a task in bytes.
     */
    public long tasksize;

    /**
     * False if the application cannot receive data directly from IoT devices, only from another application.
     */
    public boolean serviceable;
    
    /**
     * The number of instructions associated to a task with maximum size in order to simulate VM utilization.
     */
    double instructions;

    /**
     * The logic determining which other IoT application receives IoT data in case of offloading.
     */
    ApplicationStrategy applicationStrategy;

    /**
     * The amount of data received by the application.
     */
    public long receivedData;

    /**
     * The amount of data processed by the application.
     */
    public long processedData;

    /**
     * It defines the type of the VMs (image, flavor, price, etc.) used by this application. 
     */
    public Instance instance;
    
    /**
     * It denotes if this application is supposed to receive data from another application.
     */
    public int incomingData;

    /**
     * It indicates how many tasks are currently being processed.
     */
    private int taskInProgress;

    /**
     * This list stores the start and end timestamps of the tasks.
     */
    public ArrayList<TimelineEntry> timelineEntries = new ArrayList<TimelineEntry>();

    /**
     * Constructs a new Application with the specified parameters.
     *
     * @param name the name of the application
     * @param freq the frequency of the periodic task execution (time interval in ms)
     * @param tasksize the max size of a task (in bytes)
     * @param instructions the number of instructions that a task with max size can represent
     * @param serviceable indicates whether the application is able to receive data from IoT devices
     * @param applicationStrategy the logic for finding another application in case of offloading
     * @param instance the type of the VMs used by this application
     */
    public Application(String name, long freq, long tasksize, double instructions, boolean serviceable,
            ApplicationStrategy applicationStrategy, Instance instance) {
        Application.allApplications.add(this);
        this.deviceList = new ArrayList<>();
        this.utilisedVms = new ArrayList<>();
        this.name = name;
        this.instance = instance;
        this.freq = freq;
        this.tasksize = tasksize;
        this.serviceable = serviceable;
        this.instructions = instructions;
        this.applicationStrategy = applicationStrategy;
        this.applicationStrategy.application = this;
    }

    /**
     * It sets the physical resource for this application and it also
     * registers VM image file in the resource's first repository.
     *
     * @param ca a physical resource that this application uses
     */
    public void setComputingAppliance(ComputingAppliance ca) {
        this.computingAppliance = ca;
        this.computingAppliance.iaas.repositories.get(0).registerObject(this.instance.va);
    }

    /**
     * It returns the current cost of the application based on the total work time of all utilized VMs.
     */
    public double getCurrentCost() {
        long totalWorkTime = 0;
        for (AppVm appVm : this.utilisedVms) {
            totalWorkTime += appVm.workTime;
        }
        return this.instance.calculateCloudCost(totalWorkTime);
    }

    /**
     * Subscribes the application and ensures that the broker VM is running. 
     * If the broker VM is shut down, it tries to switch on it.
     */
    public void subscribeApplication() {
        subscribe(this.freq);

        if (this.computingAppliance.broker.vm.getState().equals(VirtualMachine.State.SHUTDOWN)) {
            try {
                ResourceAllocation ra = this.computingAppliance.broker.pm
                        .allocateResources(ComputingAppliance.brokerArc, false, PhysicalMachine.defaultAllocLen);
                this.computingAppliance.broker.vm.switchOn(ra, null);
                this.computingAppliance.broker.restartCounter++;
                this.computingAppliance.broker.runningPeriod = Timed.getFireCount();
                SimLogger.logRun(this.computingAppliance.name + " broker is turned on at: " + Timed.getFireCount());
            } catch (VMManagementException | NetworkException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * It returns with an available VM that is not currently working,
     * otherwise it returns with null.
     */
    AppVm vmSearch() {
        for (AppVm appVm : this.utilisedVms) {
            if (!appVm.isWorking && appVm.vm.getState().equals(VirtualMachine.State.RUNNING)) {
                return appVm;
            }
        }
        return null;
    }

    /**
     * It tries to turn on an existing, but not running VM. If it failed, it tries to add a new VM.
     *
     * @return {@code true} if a new VM is successfully created/turned on, {@code false} otherwise
     */
    private boolean createVm() {
        try {
            if (this.turnOnVm() == false) {
                for (PhysicalMachine pm : this.computingAppliance.iaas.machines) {
                    if (pm.isCurrentlyHostableRequest(this.instance.arc)) {
                        VirtualMachine vm = pm.requestVM(this.instance.va, this.instance.arc,
                                this.computingAppliance.iaas.repositories.get(0), 1)[0];
                        if (vm != null) {
                            AppVm appVm = new AppVm(vm);
                            appVm.pm = pm;
                            this.utilisedVms.add(appVm);
                            SimLogger.logRun("\tVM-" + appVm.id + " is requested at: " + Timed.getFireCount());
                            return true;
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * It attempts to turn on an existing VM that is currently in the SHUTDOWN state.
     *
     * @return {@code true} if a VM is successfully turned on, {@code false} otherwise
     */
    private boolean turnOnVm() {
        for (AppVm appVm : this.utilisedVms) {
            if (appVm.vm.getState().equals(VirtualMachine.State.SHUTDOWN)
                    && appVm.pm.isCurrentlyHostableRequest(this.instance.arc)) {
                try {
                    ResourceAllocation ra = appVm.pm.allocateResources(this.instance.arc, false,
                            PhysicalMachine.defaultAllocLen);
                    appVm.vm.switchOn(ra, null);
                    appVm.restartCounter++;
                    appVm.runningPeriod = Timed.getFireCount();
                    SimLogger.logRun("\tVM-" + appVm.id + " is turned on at: " + Timed.getFireCount());
                    return true;
                } catch (NetworkException | VMManagementException e) {
                    e.printStackTrace();
                }
            }
        }
        return false;
    }

    /**
     * It checks the state of all connected devices to determine if any device is subscribed.
     *
     * @return {@code true} if no devices are subscribed, {@code false} if at least one device is subscribed
     */
    private boolean checkDeviceState() {
        for (Device d : this.deviceList) {
            if (d.isSubscribed()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Turns off any VM that is currently in the RUNNING state and not working.
     */
    private void turnOffVm() {
        for (AppVm appVm : this.utilisedVms) {
            if (appVm.vm.getState().equals(VirtualMachine.State.RUNNING) && appVm.isWorking == false) {
                try {
                    appVm.vm.switchoff(false);
                    SimLogger.logRun("\t" + name + " VM-" + appVm.id + " is turned off at: " + Timed.getFireCount());
                } catch (StateChangeException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * It updates the running  VMs' statistics related to up-times. 
     */
    private void countVmRunningTime() {
        for (AppVm appVm : this.utilisedVms) {
            if (appVm.vm.getState().equals(VirtualMachine.State.RUNNING)) {
                appVm.workTime += (Timed.getFireCount() - appVm.runningPeriod);
                appVm.runningPeriod = Timed.getFireCount();
            }
        }
    }

    /**
     * Creates a new StorageObject (i.e. file) with the specified amount of data to be saved 
     * and attempts to store it in the first repository of the physical resource.
     *
     * @param dataToBeSaved the amount of data to be saved
     */
    private void saveStorageObject(long dataToBeSaved) {
        StorageObject so = new StorageObject(this.name, dataToBeSaved, false);
        if (!this.computingAppliance.iaas.repositories.get(0).registerObject(so)) {
            System.err.println("Error in Application.java: Processed data cannot be saved.");
            System.exit(0);
        }
    }

    /**
     * The main logic of the application including the management of the unprocessed data,
     * task allocation, and scaling- and offloading decision making.
     */
    @Override
    public void tick(long fires) {
        for (String featureName : FeatureManager.getInstance().getFeatureNames("::")) {
            FeatureManager.getInstance().getFeatureByName(
                    String.format("%s::%s", computingAppliance.name, featureName)).computeValue();
        }
        
        long unprocessedData = (this.receivedData - this.processedData);
        if (unprocessedData > 0) {
            long alreadyProcessedData = 0;
            while (unprocessedData != alreadyProcessedData) {
                long allocatedData = Math.min(unprocessedData - alreadyProcessedData, this.tasksize);
                final AppVm appVm = this.vmSearch();
                if (appVm == null) {
                    double ratio = (double) unprocessedData / this.tasksize;
                    SimLogger.logRun(name + " has " + unprocessedData + " bytes left, "
                            + this.computingAppliance.getLoadOfResource() + " load (%),"
                            + " unprocessed data / tasksize ratio: " + ratio + ". Decision: ");
                    if (Double.compare(ratio, this.applicationStrategy.activationRatio) > 0) {
                        long dataForTransfer = ((long) ((unprocessedData - alreadyProcessedData)
                                / this.applicationStrategy.transferDivider));
                        SimLogger.logRun("\tdata is ready to be transferred: " + dataForTransfer + " ");
                        this.applicationStrategy.findApplication(dataForTransfer);
                    }
                    this.createVm();
                    break;
                }

                final double noi = allocatedData == this.tasksize ? this.instructions
                        : (this.instructions * allocatedData / this.tasksize);
                alreadyProcessedData += allocatedData;
                this.processedData += allocatedData;
                Application.totalProcessedSize += allocatedData;
                appVm.isWorking = true;
                this.taskInProgress++;
                try {
                    appVm.vm.newComputeTask(noi, ResourceConsumption.unlimitedProcessing,
                            new ConsumptionEventAdapter() {
                                final long taskStartTime = Timed.getFireCount();
                                final long allocatedDataTemp = allocatedData;
                                final double noiTemp = noi;

                                @Override
                                public void conComplete() {
                                    saveStorageObject(allocatedData);
                                    appVm.isWorking = false;
                                    appVm.taskCounter++;
                                    taskInProgress--;
                                    Application.lastAction = Timed.getFireCount();
                                    timelineEntries.add(new TimelineEntry(taskStartTime, Timed.getFireCount(),
                                            Integer.toString(appVm.id)));
                                    SimLogger.logRun(name + " VM-" + appVm.id + " started at: " + taskStartTime
                                            + " finished at: " + Timed.getFireCount() + " bytes: " + allocatedDataTemp
                                            + " took: " + (Timed.getFireCount() - taskStartTime) + " instructions: "
                                            + noiTemp);
                                }
                            });
                } catch (NetworkException e) {
                    e.printStackTrace();
                }

            }
        }
        this.countVmRunningTime();
        this.turnOffVm();

        if (this.incomingData == 0 && this.taskInProgress == 0 && this.processedData == this.receivedData
                && this.checkDeviceState()) {
            unsubscribe();
            ComputingAppliance.stopEnergyMetering();
            try {
                if (this.computingAppliance.broker.vm.getState().equals(VirtualMachine.State.RUNNING)) {
                    this.computingAppliance.broker.pm = this.computingAppliance.broker.vm.getResourceAllocation()
                            .getHost();
                    this.computingAppliance.broker.vm.switchoff(false);
                    this.computingAppliance.broker.workTime += (Timed.getFireCount()
                            - this.computingAppliance.broker.runningPeriod);
                    timelineEntries.add(new TimelineEntry(this.computingAppliance.broker.runningPeriod,
                            Timed.getFireCount(), this.computingAppliance.name + "-broker"));
                    SimLogger.logRun(this.computingAppliance.name
                            + " broker is turned off at: " + Timed.getFireCount() + " ");
                }
            } catch (StateChangeException e) {
                e.printStackTrace();
            }
        }
    }
}