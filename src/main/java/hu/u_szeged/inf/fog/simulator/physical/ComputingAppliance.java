package hu.u_szeged.inf.fog.simulator.physical;

import java.util.ArrayList;

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

public class ComputingAppliance {
	
	public static VirtualAppliance gatewayVa = new VirtualAppliance("gatewayVa", 1, 0, false, 1073741824L); // 1 GB
	
	public static AlterableResourceConstraints gatewayArc = new AlterableResourceConstraints(1, 0.001, 1294967296L);

	static ArrayList < ComputingAppliance > allComputingAppliances = new ArrayList<>();
	
	public GeoLocation geoLocation;
	
	public IaaSService iaas;
	
	public long range;
	
	public ComputingAppliance parent;
	
	public ArrayList < ComputingAppliance > neighbors;
	
	public String name;
	
	public ArrayList < Application > applications;
	
	public AppVm gateway;
	
	public double energyConsumption;
			
	public ComputingAppliance(String file, String name, GeoLocation geoLocation, long range) throws Exception {
		 this.iaas = CloudLoader.loadNodes(file);
		 this.name = name;
		 this.geoLocation = geoLocation;
		 this.range = range;
	     this.neighbors = new ArrayList<ComputingAppliance>();
	     this.applications = new ArrayList < Application > ();
	     this.modifyRepoName(this.iaas.repositories.get(0).getName()+"-"+this.name);
	     ComputingAppliance.allComputingAppliances.add(this);
	     this.startGateway();
	     this.readEnergy();
	 }
	
	  public double getLoadOfResource() {
	        double usedCPU = 0.0;
	        for (VirtualMachine vm: this.iaas.listVMs()) {
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
                subscribe(1*60*1000);
            }
            public void stop() {
                unsubscribe();
            }
            @Override
            public void tick(final long fires) {
               energyConsumption = iaasEnergyMeter.getTotalConsumption();
               if (Timed.getFireCount() > Device.longestRunningDevice && Device.totalGeneratedSize == Application.totalProcessedSize) {
                    this.stop();
                    iaasEnergyMeter.stopMeter();
                }
            }
        }
        final DataCollector dc = new DataCollector();
        iaasEnergyMeter.startMeter(1*60*1000, true);
        dc.start();
    }
		
	private void modifyRepoName(String newName) {
		String oldName = this.iaas.repositories.get(0).getName();
		this.iaas.repositories.get(0).setName(newName);
		for(Repository r : this.iaas.repositories) {
			if(r.getLatencies().get(oldName) != null) {
				int latency = r.getLatencies().get(oldName);
				r.getLatencies().remove(oldName);
				r.addLatencies(newName, latency);
			}
		}
	}
	
	public void addApplication(Application... applications) {
        for (Application app: applications) {
            app.setComputingAppliance(this);
            this.applications.add(app);
        }
    }
	
	public void addNeighbor(ComputingAppliance that, int latency) {
		 if(!this.neighbors.contains(that))
     		this.neighbors.add(that);
         if(!that.neighbors.contains(this))
         	that.neighbors.add(this);
         
         this.iaas.repositories.get(0).addLatencies(that.iaas.repositories.get(0).getName(), latency);
         that.iaas.repositories.get(0).addLatencies(this.iaas.repositories.get(0).getName(), latency);
	   }

	public void setParent(ComputingAppliance parent, int latency) {
		this.parent = parent;
		this.iaas.repositories.get(0).addLatencies(parent.iaas.repositories.get(0).getName(), latency);
		parent.iaas.repositories.get(0).addLatencies(this.iaas.repositories.get(0).getName(), latency);
	}

	public static ArrayList<ComputingAppliance> getAllComputingAppliances() {
		return allComputingAppliances;
	}
	
	private void startGateway() {
        try {
            this.iaas.repositories.get(0).registerObject(ComputingAppliance.gatewayVa);
             VirtualMachine vm = this.iaas.requestVM(ComputingAppliance.gatewayVa, ComputingAppliance.gatewayArc, this.iaas.repositories.get(0), 1)[0];
             this.gateway = new AppVm(vm);
             System.out.println(name + " gateway is turned on at: " + Timed.getFireCount());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}