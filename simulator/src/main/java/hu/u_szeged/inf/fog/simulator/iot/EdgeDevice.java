package hu.u_szeged.inf.fog.simulator.iot;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.PowerState;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager.VMManagementException;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine.StateChangeException;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.AlterableResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.pmscheduling.AlwaysOnMachines;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ConsumptionEventAdapter;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ResourceConsumption;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling.FirstFitScheduler;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.io.StorageObject;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;
import hu.mta.sztaki.lpds.cloud.simulator.util.PowerTransitionGenerator;
import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;
import hu.u_szeged.inf.fog.simulator.common.util.EnergyDataCollector;
import hu.u_szeged.inf.fog.simulator.demo.ScenarioBase;
import hu.u_szeged.inf.fog.simulator.iot.mobility.GeoLocation;
import hu.u_szeged.inf.fog.simulator.iot.mobility.MobilityEvent;
import hu.u_szeged.inf.fog.simulator.iot.mobility.MobilityStrategy;
import hu.u_szeged.inf.fog.simulator.iot.strategy.DeviceStrategy;
import hu.u_szeged.inf.fog.simulator.util.SimLogger;
import hu.u_szeged.inf.fog.simulator.util.TimelineVisualiser.TimelineEntry;

import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.*;

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
        try{
            //emiatt összetörik ez a konstruktor, de mivel a többi része is erre épül a classnak ezért mindegy hogy marad-e
            setUsableCommunicationProtocols(true, true, true);
        } catch (NetworkException e) {
            throw new RuntimeException(e);
        }
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


    public EdgeDevice(double cores, double perCoreProcessing, long memory, int onD, int offD,
                      double cpuMinPower, double cpuIdlePower, double cpuMaxPower, double diskDivider, double netDivider,
                      long startTime, long stopTime, long fileSize, long freq, MobilityStrategy mobilityStrategy,
                      DeviceStrategy deviceStrategy, double instructionPerByte, int latency, Battery battery, TaskType type,
                      boolean pathLogging) {

        try{
            this.communicationProtocolManager = new IaaSService(FirstFitScheduler.class, AlwaysOnMachines.class);
        } catch(InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException
                | NoSuchMethodException | SecurityException e){
            e.printStackTrace();
        }


        try{
            // ez inicializálja a communicationProtocols mapet és az IaasService (commProtManager) commprot repoit is
            setUsableCommunicationProtocols(true, true, true);
        } catch (NetworkException e) {
            throw new RuntimeException(e);
        }

        EnumMap<PowerTransitionGenerator.PowerStateKind, Map<String, PowerState>> transitions =
                PowerTransitionGenerator.generateTransitions( cpuMinPower,  cpuIdlePower, cpuMaxPower, diskDivider, netDivider);

        // "fake" repo a PM-nek közel 0 fogyasztással, a ténylegesen használt repok a commProtosok
        EnumMap<PowerTransitionGenerator.PowerStateKind, Map<String, PowerState>> PMrepoTransitions =
                PowerTransitionGenerator.generateTransitions(0.02, 0.25, 2.2, 1000000, 1000000);
        final Map<String, PowerState> PMrepoStTransitions = PMrepoTransitions.get(PowerTransitionGenerator.PowerStateKind.storage);
        final Map<String, PowerState> PMrepoNwTransitions = PMrepoTransitions.get(PowerTransitionGenerator.PowerStateKind.network);
        Repository physicalMachineRepo = new Repository(4_294_967_296L, "Device-" + this.hashCode() + "-PM_repo",
                1,1,5000, new HashMap<>(),
                PMrepoStTransitions, PMrepoNwTransitions);
        this.localMachine = new PhysicalMachine(cores,perCoreProcessing,memory,physicalMachineRepo,onD,offD,
                                                transitions.get(PowerTransitionGenerator.PowerStateKind.host));

        this.communicationProtocolManager.registerHost(localMachine);

        long delay = Math.abs(SeedSyncer.centralRnd.nextLong() % 180) * 1000;
        this.startTime = startTime + delay;
        this.stopTime = stopTime + delay;
        this.fileSize = fileSize;
        this.geoLocation = mobilityStrategy.startPosition;
        this.freq = freq;
        this.mobilityStrategy = mobilityStrategy;
        Device.allDevices.add(this);
        this.instructionPerByte = instructionPerByte;
        this.isPathLogged = pathLogging;
        this.devicePath = new ArrayList<GeoLocation>();
        this.deviceStrategy = deviceStrategy;
        this.deviceStrategy.device = this;
        this.latency = latency;
        this.type = type;

        battery.setEnergyDataCollector(new EnergyDataCollector("Device-" + this.hashCode() + "-battery", this.communicationProtocolManager, true, true));
        battery.setStopTime(stopTime);
        this.battery = battery;
        
        this.edgeDeviceArc = new AlterableResourceConstraints(localMachine.getCapacities().getRequiredCPUs(),
                localMachine.getCapacities().getRequiredProcessingPower(),
                localMachine.getCapacities().getRequiredMemory());
        this.startMeter();
        //this.localMachine.turnon(); iaasservice elinditja őket
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
                this.localVm.switchoff(false);
            } catch (StateChangeException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Sets what communication protocols the edge device can use.
     */
    public void setUsableCommunicationProtocols(boolean wifi, boolean _5g, boolean lora) throws NetworkException {
        //első meghivásnál feltöltjük az EdgeDevicehoz generált 3 repot (akkor is mind3 ha nem használja mind3at, lehet wasteful de egyszerűbb kezelni)
        //főleg hogy LORA repo a default amikor nincs szerver rangeben
        //ez a rész lehet okosabb lenne a konstruktorba de az is igyis már túl hosszú
        if(communicationProtocolRepos.isEmpty()){
            communicationProtocolRepos.add(CommunicationProtocol.getInstance().newWifiRepository());
            communicationProtocolRepos.add(CommunicationProtocol.getInstance().new5GRepository());
            communicationProtocolRepos.add(CommunicationProtocol.getInstance().newLoRaRepository());

            //mindet hozzáadjuk a managerhez
            for (Repository r : communicationProtocolRepos) {
                communicationProtocolManager.registerRepository(r);
            }
        }

        //a ténylegesen használható repok megadása / változtatása az újonnali meghívásnál
        usableCommunicationProtocols.clear();
        if(wifi || (!_5g && !lora)){ //ha mind false akkor nem tudna kommunikálni, szóval csak tudja mit akar a szimuláció készítő, de mégis le lesz kezelve, így mert a repo kell
            usableCommunicationProtocols.add("WIFI");
            this.currentCommunicationProtocol = "WIFI";
        }
        if(_5g){
            usableCommunicationProtocols.add("5G");
            this.currentCommunicationProtocol = "5G";
        }
        if(lora){
            usableCommunicationProtocols.add("LORA");
            this.currentCommunicationProtocol = "LORA";
        }

        swapCommunicationProtocolTo(this.currentCommunicationProtocol); //defaultba LORA
    }

    /**
     * This method does the actual swapping of the communicationProtocols.
     */
    private void swapCommunicationProtocolTo(String newCommunicationProtocol) throws NetworkException {
        //logging, törölhető
        if(battery != null){
            if(currentCommunicationProtocol.equals(newCommunicationProtocol)){
                return;
            }
            try( FileWriter fw = new FileWriter(ScenarioBase.resultDirectory + "/swap.txt",true)){
                fw.write(Timed.getFireCount() + " " + this.battery.getName() + " from:" + currentCommunicationProtocol + " to:" + newCommunicationProtocol + "\n");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        this.currentCommunicationProtocol = newCommunicationProtocol;
        for (Repository r : communicationProtocolRepos) {
            if (r.getName().contains(currentCommunicationProtocol)){
                currentCommunicationProtocolRepo = r;
                break;
            }
        }


        //ha van adat amit át kell vinni a repok között azt lementjük, majd lejebb a tényleges "váltásnál" hozzáadjuk őket
        Map<String, Integer> swapLatencies = new HashMap<>();
        Collection<StorageObject> swapContents = new ArrayList<>();
        for (Repository r : this.communicationProtocolRepos){
            if(!r.contents().isEmpty() || !r.getLatencies().isEmpty()){
                swapLatencies.putAll(r.getLatencies());
                swapContents.addAll(r.contents());

                //amiben volt azt töröljük
                r.getLatencies().clear();
                for (StorageObject so : swapContents) {
                    r.deregisterObject(so);
                }
                break; //mivel csak egy running reponak kéne lenni ezért, ha egy meg van nem kell megnézni a többi tartalmát (?)
            }
        }

        //leállít minden repot kivéve a paraméterben levőt (mert az elinditja ha nem az), majd megkéne hivni a konstruktorba is, vagy a setCommprotba okosabb
        for (Repository r : this.communicationProtocolRepos){
            if(r.getName().contains(newCommunicationProtocol)){
                r.getLatencies().putAll(swapLatencies);
                for (StorageObject so : swapContents) {
                    r.registerObject(so);
                }

                r.setState(NetworkNode.State.RUNNING);
            } else{
                r.setState(NetworkNode.State.OFF);
            }
        }
    }

    /**
     * Select what communication protocol the edge device should use given its battery state and the server(s) in its vicinity.
     */
    public void selectBestCommunicationProtocol() throws NetworkException {
        if(this.battery == null){
            return;
        }

        // feltöltjük ezt a listát azokkal a communication protokollokkal amik közül válaszhatunk, és utána választunk
        List<String> options = new ArrayList<>();
        this.deviceStrategy.findApplication(); // ezzel elvileg a legközelebbi szervert választjuk ki mert distanceandtype based strategyt használunk
        if(this.deviceStrategy.chosenApplication != null){
            options = this.deviceStrategy.chosenApplication.computingAppliance.communicationProtocols;
        }
        else{
            // ha nem tudja használni az eszköz a szervert feldolgozásra akkor a lora repo legyen "használva"
            if(!("LORA".equals(currentCommunicationProtocol))){
                swapCommunicationProtocolTo("LORA");
            }
        }

        //TODO ez a: "nem lehet kapcsolatot létesíteni a szerverrel" az majd átmegy a strategybe a findAppba(?)
        //van legalább egy közös commprot, ha nincs akkor lehet le kéne valahogy kezelni, de akkor csak megtartanánk a jelenlegi repot nem tudom lehet túl gondolom
        boolean atLeastOne = false;
        for (String commProtocol : options) {
            if(usableCommunicationProtocols.contains(commProtocol)){
                atLeastOne = true;
                break;
            }
        }
        if(!atLeastOne){
            return;
        }


        /*
        TODO értelmes heurisztika, figyelembe véve a taskokat itt is szerveren is, a batteryt,
        deadlineokat (pl közelebbi deadline -> gyorsabb kapcsolat jobb heurisztika érték), minden ami csak eszembe jut és logikus
        */
        String first = "WIFI";
        String second = "5G";
        String third = "LORA";

        if(this.battery.getCurrentPercentage() > 70){
            first = "WIFI";
            second = "5G";
            third = "LORA";
        } else if(this.battery.getCurrentPercentage() > 50){
            first = "5G";
            second = "WIFI";
            third = "LORA";
        } else if(this.battery.getCurrentPercentage() > 30){
            first = "5G";
            second = "LORA";
            third = "WIFI";
        } else if(this.battery.getCurrentPercentage() <= 30){ //lehetne else de ha változnak a feltételek vszeg kellene?
            first = "LORA";
            second = "5G";
            third = "WIFI";
        }

        //választás, a sorrend alapján
        if(options.contains(first) && usableCommunicationProtocols.contains(first)){
            swapCommunicationProtocolTo(first);
        } else if(options.contains(second) && usableCommunicationProtocols.contains(second)){
            swapCommunicationProtocolTo(second);
        } else if(options.contains(third) && usableCommunicationProtocols.contains(third)){
            swapCommunicationProtocolTo(third);
        }
    }


    /**
     * The tick method is called to simulate a time step for the edge device.
     * It handles data transfer, mobility updates, and local processing.
     */
    @Override
    public void tick(long fires) {
        if(battery != null && battery.isCharging()){
            return;
        }

        if (Timed.getFireCount() < stopTime && Timed.getFireCount() >= startTime) {
            new Sensor(this, 1);
        }


        GeoLocation newLocation = this.mobilityStrategy.move(this);
        if (this.isPathLogged) {
            this.devicePath.add(new GeoLocation(this.geoLocation.latitude, this.geoLocation.longitude));
        }
        MobilityEvent.changePositionEvent(this, newLocation);

        if (battery != null) {
            try {
                selectBestCommunicationProtocol(); //ebbe benne van a this.deviceStrategy.findApplication();
            } catch (NetworkException e) {
                throw new RuntimeException(e);
            }
        } else{
            this.deviceStrategy.findApplication();
        }

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
                    for (StorageObject so : this.currentCommunicationProtocolRepo.contents()) {
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
                                    }

                                    @Override
                                    public void conCancelled(ResourceConsumption problematic) {
                                        SimLogger.logRun("Device-" + edgeDevice.hashCode() + " started at: "
                                                + taskStartTime + " failed to process " + currentlyProcessedData + " bytes.");
                                    }
                                });
                        if (rc != null) {
                            for (StorageObject so : dataToBeRemoved) {
                                this.currentCommunicationProtocolRepo.deregisterObject(so);
                            }
                        }
                    }
                }
            }
        } catch (NetworkException e) {
            e.printStackTrace();
        }

        if (Timed.getFireCount() > stopTime && (this.locallyProcessedData + this.sentData) == this.generatedData) {
            SimLogger.logRun("Device-"+this.hashCode()+" has stopped.");
            this.stopMeter();
        }
    }
}