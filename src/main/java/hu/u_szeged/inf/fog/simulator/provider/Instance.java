package hu.u_szeged.inf.fog.simulator.provider;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.AlterableResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;

public class Instance {

	public VirtualAppliance va;
	
	public AlterableResourceConstraints arc;
	
	public double pricePerTick;
	 
	public Instance(VirtualAppliance va, AlterableResourceConstraints arc, double pricePerTick) {
	        this.va = va;
	        this.arc = arc;
	        this.pricePerTick = pricePerTick;
	}
	
	public double calculateCloudCost(long time) {
        return time * pricePerTick;
    }
}