package hu.u_szeged.inf.fog.simulator.node;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.energy.specialized.IaaSEnergyMeter;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.AlterableResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;
import hu.mta.sztaki.lpds.cloud.simulator.util.CloudLoader;
import hu.u_szeged.inf.fog.simulator.application.AppVm;
import hu.u_szeged.inf.fog.simulator.application.Application;
import hu.u_szeged.inf.fog.simulator.iot.mobility.GeoLocation;
import hu.u_szeged.inf.fog.simulator.util.SimLogger;
import hu.u_szeged.inf.fog.simulator.util.TimelineVisualiser.TimelineEntry;
import java.io.IOException;
import java.util.ArrayList;
import javax.xml.parsers.ParserConfigurationException;
import org.xml.sax.SAXException;

/**
 * This component represents the physical resources (fog and cloud nodes) in the system.
 * They also defines the connection between distinct computing nodes, thus the connected
 * physical machines form a hierarchical structure.
 * This representation fits the batch processing-based evaluation.
 */
public class ComputingAppliance {

    /**
     * Ensures a common virtual image file with 1 GB of disk size requirement.
     */
    public static VirtualAppliance brokerVa = new VirtualAppliance("brokerVa", 1, 0, false, 1073741824L);
    
    /**
     * Ensures a common resource (1 CPU core, 0.001 processing speed and 1 GB of memory) requirements for the broker VM.
     */
    public static AlterableResourceConstraints brokerArc = new AlterableResourceConstraints(1, 0.001, 1294967296L);

    /**
     * A list containing references to all computing appliances.
     * Each element in the list is an instance of the {@code ComputingAppliance} class.
     */
    public static ArrayList<ComputingAppliance> allComputingAppliances = new ArrayList<>();

    /**
     * The physical position of the node.
     */
    public final GeoLocation geoLocation;
    
    /**
     * This class represents a single IaaS service responsible for maintaining
     * physical machines and scheduling VM requests.
     */
    public IaaSService iaas;

    /**
     * The covered physical neighborhood of the node from where it accepts IoT data (in km).
     */
    public final long range;

    /**
     * The reference to the parent node, which is located higher in the hierarchy.
     * It might have a null value. 
     */
    public ComputingAppliance parent;

    /**
     * A list of neighboring {@code ComputingAppliance} instances. These nodes are
     * considered a cluster, and can be used for various strategies (e.g. load balancing).
     */
    public ArrayList<ComputingAppliance> neighbors; // TODO: it should be a Set, not a List

    /**
     * The name of the node, strongly recommended to be unique during an evaluation.
     */
    public final String name;

    /**
     * List of IoT applications deployed to this physical resources.
     */
    public ArrayList<Application> applications;
    
    /**
     * The reference to the broker VM of this node, which is required to be
     * in RUNNING state to enable the data processing of IoT applications.
     */
    public AppVm broker;

    /**
     * The energy consumed by this physical resource.
     */
    public double energyConsumption;

    /**
     * A helper variable to store every important event of the runtime of 
     * this computing appliance.
     */
    public ArrayList<TimelineEntry> timelineList = new ArrayList<TimelineEntry>();

    public String provider;
    
    public String location;

    /**
     * Constructs a new {@code ComputingAppliance} with the specified parameters.
     * It also starts the energy measurement for this instance.
     *
     * @param file        the file path defining an IaaSService
     * @param name        the name of the computing appliance
     * @param geoLocation the geographical location of the computing appliance
     * @param range       the range of the computing appliance in km (0 or smaller means infinite range)
     */
    public ComputingAppliance(String file, String name, GeoLocation geoLocation, long range) {
        try {
            this.iaas = CloudLoader.loadNodes(file);
        } catch (IOException | SAXException | ParserConfigurationException e) {
            e.printStackTrace();
        }
        this.name = name;
        this.geoLocation = geoLocation;
        this.neighbors = new ArrayList<>();
        this.applications = new ArrayList<>();
        this.range = range <= 0 ? Integer.MAX_VALUE : range;
        this.modifyRepoName(this.iaas.repositories.get(0).getName() + "-" + this.name);
        ComputingAppliance.allComputingAppliances.add(this);
        this.readEnergy(); // TODO: use the global EnergyDataMeter class instead
    }
    
    public ComputingAppliance(IaaSService iaas, GeoLocation geoLocation, String location, String provider) {
        this.iaas = iaas;
        this.name = iaas.repositories.get(0).getName().contains("-") 
                ? iaas.repositories.get(0).getName().substring(0, iaas.repositories.get(0).getName().indexOf('-')) 
                : iaas.repositories.get(0).getName();
        this.geoLocation = geoLocation;
        this.location = location;
        this.provider = provider;
        this.neighbors = new ArrayList<>();
        this.range = Integer.MAX_VALUE;
        ComputingAppliance.allComputingAppliances.add(this);
    }

    /**
     * Calculates and returns the load of CPU resources for this computing appliance.
     * The load is represented as a percentage of the used CPU capacity measured to 
     * the total CPU capacity.
     *
     * @return the load of CPU resources as a percentage
     */
    public double getLoadOfResource() {
        double usedCpu = 0.0;
        for (VirtualMachine vm : this.iaas.listVMs()) {
            if (vm.getResourceAllocation() != null) {
                usedCpu += vm.getResourceAllocation().allocated.getRequiredCPUs();
            }
        }
        double requiredCpus = this.iaas.getRunningCapacities().getRequiredCPUs();
        return requiredCpus > 0 ? usedCpu / requiredCpus * 100 : 0;
    }
    
    public static void setConnection(ComputingAppliance that, int latency) {
        for (ComputingAppliance ca : ComputingAppliance.getAllComputingAppliances()) {
            if (ca != that) {
                ca.neighbors.add(that);
                ca.iaas.repositories.get(0).addLatencies(that.iaas.repositories.get(0).getName(), latency);
            }
        }
    }
    
    /**
     * Reads and monitors energy consumption of the computing appliance.
     * Energy consumption data is collected periodically using an {@code IaaSEnergyMeter}.
     * The time period is set to 1 minute.
     */
    public void readEnergy() {
        final IaaSEnergyMeter iaasEnergyMeter = new IaaSEnergyMeter(this.iaas);
        
        /**
         * A helper class which is able to periodically log the energy consumption.
         */
        class EnergyDataCollector extends Timed {
            
            /**
             * Starts the data collection process.
             */
            public void start() {
                subscribe(1 * 60 * 1000);
            }

            /**
             * Stops the data collection process.
             */
            public void stop() {
                unsubscribe();
            }

            /**
             * It updates the energy consumption info in every minute until the IoT applications
             * are running.
             */
            @Override
            public void tick(final long fires) {
                energyConsumption = iaasEnergyMeter.getTotalConsumption();
                if (checkApplicationStatus() /* TODO: && Timed.getFireCount() > Device.longestRunningDevice*/) {
                    this.stop();
                    iaasEnergyMeter.stopMeter();
                }
            }
        }
        
        final EnergyDataCollector dc = new EnergyDataCollector();
        iaasEnergyMeter.startMeter(1 * 60 * 1000, true);
        dc.start();
    }

    /**
     * Returns true if all applications are stopped.
     */
    private boolean checkApplicationStatus() {
        for (Application a : this.applications) {
            if (a.isSubscribed()) {
                return false;
            }
        }
        return true;
    }

    /**
     * It modifies name of the local storage. It is useful if multiple computing
     *appliances read their resource definition from the same file.
     *
     * @param newName the new name of the repository
     */
    private void modifyRepoName(String newName) {
        String oldName = this.iaas.repositories.get(0).getName();
        this.iaas.repositories.get(0).setName(newName);
        for (Repository r : this.iaas.repositories) {
            if (r.getLatencies().get(oldName) != null) {
                int latency = r.getLatencies().get(oldName);
                r.getLatencies().remove(oldName);
                r.addLatencies(newName, latency);
            }
        }
    }

    /**
     * It adds ('deploys') one or more applications to the computing appliance.
     * If the computing appliance does not have a broker configured, it starts the broker.
     *
     * @param applications one or more applications to add
     */
    public void addApplication(Application... applications) {
        for (Application app : applications) {
            app.setComputingAppliance(this);
            this.applications.add(app);
        }
        if (this.broker == null) {
            this.startBroker(); 
        }
    }

    /**
     * Adds a neighboring computing appliance with the specified latency.
     *
     * @param that    the computing appliance to add as a neighbor
     * @param latency the latency between this computing appliance and the neighbor
     */
    public void addNeighbor(ComputingAppliance that, int latency) {
        if (!this.neighbors.contains(that)) {
            this.neighbors.add(that);
        }
            
        if (!that.neighbors.contains(this)) {
            that.neighbors.add(this);
        }
            
        this.iaas.repositories.get(0).addLatencies(that.iaas.repositories.get(0).getName(), latency);
        that.iaas.repositories.get(0).addLatencies(this.iaas.repositories.get(0).getName(), latency);
    }

    /**
     * Sets the parent computing appliance of this computing appliance with the specified latency.
     *
     * @param parent  the parent computing appliance to set
     * @param latency the latency between this computing appliance and its parent
     */
    public void setParent(ComputingAppliance parent, int latency) {
        this.parent = parent;
        parent.iaas.repositories.get(0).addLatencies(this.iaas.repositories.get(0).getName(), latency);
        this.iaas.repositories.get(0).addLatencies(parent.iaas.repositories.get(0).getName(), latency);
    }

    /**
     * The method requests a virtual machine for the broker, and turns it on.
     */
    private void startBroker() {
        try {
            this.iaas.repositories.get(0).registerObject(ComputingAppliance.brokerVa);
            VirtualMachine vm = this.iaas.requestVM(ComputingAppliance.brokerVa, ComputingAppliance.brokerArc,
                    this.iaas.repositories.get(0), 1)[0];
            this.broker = new AppVm(vm);
            SimLogger.logRun(name + " broker is turned on at: " + Timed.getFireCount());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Returns with the list of all computing appliance instances.
     */
    public static ArrayList<ComputingAppliance> getAllComputingAppliances() {
        return allComputingAppliances;
    }
}