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
import hu.u_szeged.inf.fog.simulator.iot.Device;
import hu.u_szeged.inf.fog.simulator.iot.mobility.GeoLocation;
import hu.u_szeged.inf.fog.simulator.util.SimLogger;
import hu.u_szeged.inf.fog.simulator.util.TimelineVisualiser.TimelineEntry;
import hu.u_szeged.inf.fog.simulator.workflow.WorkflowJob;

import java.util.ArrayList;
import java.util.PriorityQueue;

public class ComputingAppliance {

    public static VirtualAppliance brokerVa = new VirtualAppliance("brokerVa", 1, 0, false, 1073741824L); // 1 GB

    public static AlterableResourceConstraints brokerArc = new AlterableResourceConstraints(1, 0.001, 1294967296L);

    public static ArrayList<ComputingAppliance> allComputingAppliances = new ArrayList<>();

    public GeoLocation geoLocation;

    public IaaSService iaas;

    public long range;

    public ComputingAppliance parent;

    public ArrayList<ComputingAppliance> neighbors;

    public String name;

    public ArrayList<Application> applications;

    public AppVm broker;

    public double energyConsumption;

    public ArrayList<VirtualMachine> workflowVMs = new ArrayList<VirtualMachine>();

    public long vmTime;

    public PriorityQueue<WorkflowJob> workflowQueue;

    public ArrayList<TimelineEntry> timelineList = new ArrayList<TimelineEntry>();

    public ComputingAppliance(String file, String name, GeoLocation geoLocation, long range) throws Exception {
        this.iaas = CloudLoader.loadNodes(file);
        this.name = name;
        this.geoLocation = geoLocation;
        this.range = range;
        this.neighbors = new ArrayList<ComputingAppliance>();
        this.applications = new ArrayList<Application>();
        this.modifyRepoName(this.iaas.repositories.get(0).getName() + "-" + this.name);
        ComputingAppliance.allComputingAppliances.add(this);
        this.readEnergy();
    }

    public double getLoadOfResource() {
        double usedCPU = 0.0;
        for (VirtualMachine vm : this.iaas.listVMs()) {
            if (vm.getResourceAllocation() != null) {
                usedCPU += vm.getResourceAllocation().allocated.getRequiredCPUs();
            }
        }
        double requiredCPUs = this.iaas.getRunningCapacities().getRequiredCPUs();
        return requiredCPUs > 0 ? usedCPU / requiredCPUs * 100 : 0;
    }

    public void readEnergy() {
        final IaaSEnergyMeter iaasEnergyMeter = new IaaSEnergyMeter(this.iaas);
        class DataCollector extends Timed {
            public void start() {
                subscribe(1 * 60 * 1000);
            }

            public void stop() {
                unsubscribe();
            }

            @Override
            public void tick(final long fires) {
                energyConsumption = iaasEnergyMeter.getTotalConsumption();
                if (checkApplicationStatus() /* && Timed.getFireCount() > Device.longestRunningDevice*/) {
                    this.stop();
                    iaasEnergyMeter.stopMeter();
                }
            }
        }
        
        final DataCollector dc = new DataCollector();
        iaasEnergyMeter.startMeter(1 * 60 * 1000, true);
        dc.start();
    }

    private boolean checkApplicationStatus() {
        for (Application a : this.applications) {
            if (a.isSubscribed()) {
                return false;
            }
        }
        return true;
    }

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

    public void addApplication(Application... applications) {
        for (Application app : applications) {
            app.setComputingAppliance(this);
            this.applications.add(app);
        }
        if (this.broker == null) {
            this.startBroker();
        }
    }

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

    public void setParent(ComputingAppliance parent, int latency) {
        this.parent = parent;
        parent.iaas.repositories.get(0).addLatencies(this.iaas.repositories.get(0).getName(), latency);
        this.iaas.repositories.get(0).addLatencies(parent.iaas.repositories.get(0).getName(), latency);
    }

    public static ArrayList<ComputingAppliance> getAllComputingAppliances() {
        return allComputingAppliances;
    }

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

    @Override
    public String toString() {
        return name;
    }

}