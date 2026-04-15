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
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.io.StorageObject;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;
import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;
import hu.u_szeged.inf.fog.simulator.iot.mobility.GeoLocation;
import hu.u_szeged.inf.fog.simulator.iot.mobility.MobilityEvent;
import hu.u_szeged.inf.fog.simulator.iot.mobility.MobilityStrategy;
import hu.u_szeged.inf.fog.simulator.iot.strategy.DeviceStrategy;
import hu.u_szeged.inf.fog.simulator.util.SimLogger;
import hu.u_szeged.inf.fog.simulator.util.SimRandom;
import hu.u_szeged.inf.fog.simulator.util.TimelineVisualiser.TimelineEntry;

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
     * The communication protocols the edge device can use.
     */
    public Map<String, Repository> communicationProtocols = new HashMap<String, Repository>();


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
            setCommunicationProtocols(true, true, true);
        } catch (NetworkException e) {
            throw new RuntimeException(e);
        }
        //ez fogja inicializálni a current commprot adattagot és állítja be a repot is a localMachinenál
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
     * Sets what communication protocols the edge device can use.
     */
    public void setCommunicationProtocols(boolean wifi, boolean _5g, boolean lora) throws NetworkException {
        communicationProtocols.clear();
        if(wifi){
            communicationProtocols.put("WIFI", CommunicationProtocol.getInstance().newWifiRepository());
        }
        if(_5g){
            communicationProtocols.put("5G", CommunicationProtocol.getInstance().new5GRepository());
        }
        if(lora){
            communicationProtocols.put("LORA", CommunicationProtocol.getInstance().newLoRaRepository());
        }
    }

    /**
     * Select what communication protocol the edge device should use given its battery state and the server(s) in its vicinity.
     */
    public void selectBestCommunicationProtocol(){
        if(this.battery == null){ // már egyszer checkolva van a tick()-ben szóval lehet fölös de elfér egyelőre
            return;

        }

        //probléma volt akkor ha lokális feldolgozás során report váltok szóval ez megment
        if (this.localVm != null && this.localVm.getState().equals(VirtualMachine.State.RUNNING)){
            return;
        }

        // feltöltjük ezt a listát azokkal a communication protokollokkal amik közül válaszhatunk, és utána választunk (ezek azok amiket kezel az MEC szerver)
        List<String> options = new ArrayList<>();
        this.deviceStrategy.findApplication(); // ezzel elvileg a legjobb szervert választjuk ki a stratégiának megfelelően szóval arra nem kell feltételt írni?
        if(this.deviceStrategy.chosenApplication != null){
            options = this.deviceStrategy.chosenApplication.computingAppliance.communicationProtocols;
        }

        //van legalább egy közös commprot, ha nincs akkor lehet le kéne valahogy kezelni, de akkor csak megtartanánk a jelenlegi repot nem tudom lehet túl gondolom
        boolean atLeastOne = false;
        for (String commProtocol : options) {
            if(communicationProtocols.containsKey(commProtocol)){
                atLeastOne = true;
                break;
            }
        }
        if(!atLeastOne){
            return;
        }


        String first = "WIFI";
        String second = "5G";
        String third = "LORA";
        /*
        sorrend felállítás, hogy melyik a legjobb commprot
        ezen bőven lehetne finomítani elég sok feltétellel meg szebb kivitelezéssel hogyha több commprot is lenne,
        de egyelőre csak töltöttséget nézünk és csak a 3 commprotot vizsgáljuk -> elég statikus döntések lesznek amég más feltétel nincs
        a másik feltétel meg az hogy választható ami a kövi résznél lesz kiválasztva
        */


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

        Repository swap = null;

        //választás, a sorrend alapján
        if(options.contains(first)){
            swap = communicationProtocols.get(first);
        } else if(options.contains(second)){
            swap = communicationProtocols.get(second);
        } else if(options.contains(third)){
            swap = communicationProtocols.get(third);
        }

        // TODO figyelni arra hogy ha váltunk akkor a repokba levő (adat /) TASK maradjon meg
        if(swap != null && swap != localMachine.localDisk){

            for (var connection : localMachine.localDisk.getLatencies().entrySet()){
                swap.addLatencies(connection.getKey(), connection.getValue());
            }

            for (StorageObject c : localMachine.localDisk.contents()){
                //időt nem befolyásolva átmásoljuk a elemeket -> csak commprot váltás
                swap.registerObject(c);
                // meg egyéb adattagok is vannak amivel nem tudom kéne e törődni
                // Taskal ez is változik
            }

            // törölni kell a másolás után mert amúgy duplikátumok lesznek
            for (StorageObject c : swap.contents()){
                localMachine.localDisk.deregisterObject(c);
            }

            //System.out.println("Changed CommunicationProtocol(?)");
            localMachine.localDisk = swap;
        }

        //egyéb opciók
        //pl task priority / deadline sürget -> gyorsabb kapcsolat, de ehhez előbb kell a task absztrakció
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
            selectBestCommunicationProtocol(); //ebbe benne van a this.deviceStrategy.findApplication();
        } else{
            this.deviceStrategy.findApplication();
        }

        try {
            if (this.deviceStrategy.chosenApplication != null) {
                // TODO átküldött taskok -> application
                // egyelőre applicationbe jönnek létre a taskok

                this.startDataTransfer();
                this.stopVm();
            } else {
                if (this.localVm == null || this.localVm.getState().equals(VirtualMachine.State.SHUTDOWN)) {
                    this.startVm();
                } else {
                    // TODO lokálisan feldolgozott taskok

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