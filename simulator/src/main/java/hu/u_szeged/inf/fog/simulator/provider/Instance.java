package hu.u_szeged.inf.fog.simulator.provider;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.AlterableResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;
import java.util.HashMap;

public class Instance {

    public static HashMap<String, Instance> instances = new HashMap<String, Instance>();

    public String name;

    public double processingRatio;

    public VirtualAppliance va;

    public AlterableResourceConstraints arc;

    public double pricePerTick;

    public Instance(String name, VirtualAppliance va, AlterableResourceConstraints arc, double pricePerTick) {
        this.va = va;
        this.name = name;
        this.arc = arc;
        this.pricePerTick = pricePerTick;
        Instance.instances.put(this.name, this);
    }

    public double calculateCloudCost(long time) {
        return time * pricePerTick;
    }

    public Instance(String name, VirtualAppliance va, AlterableResourceConstraints arc, double pricePerTick,
            double processingRatio) {
        this.name = name;
        this.va = va;
        this.arc = arc;
        this.pricePerTick = pricePerTick;
        this.processingRatio = processingRatio;
        Instance.instances.put(this.name, this);
    }
}