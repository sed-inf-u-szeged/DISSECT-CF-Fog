package hu.u_szeged.inf.fog.simulator.provider;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.AlterableResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;
import java.util.HashMap;

/**
 * The class represents a VM type with pricing, and image and flavor characteristics.
 */
public class Instance {

    /**
     * A map storing all instances and identified by their name.
     */
    public static HashMap<String, Instance> allInstances = new HashMap<String, Instance>();

    /**
     * Name of the instance, it should be unique.
     */
    public String name;

    /**
     * The VM image file associated to this instance type.
     */
    public VirtualAppliance va;

    /**
     * The resource requirement associated to this instance type.
     */
    public AlterableResourceConstraints arc;

    /**
     * The unit price of this instance type (in ms).
     */
    public double pricePerTick;
    
    /**
     * The byte/instruction ratio, which is only used for workflow-based evaluation.
     */
    public double processingRatio;

    /**
     * Constructs an instance type with the specified name, virtual appliance, resource constraints, and cost per tick.
     *
     * @param name         the name of the instance
     * @param va           the VM image file associated with the instance
     * @param arc          the resource requirement of the instance
     * @param pricePerTick the cost of the instance per tick (in ms)
     */
    public Instance(String name, VirtualAppliance va, AlterableResourceConstraints arc, double pricePerTick) {
        this.va = va;
        this.name = name;
        this.arc = arc;
        this.pricePerTick = pricePerTick;
        Instance.allInstances.put(this.name, this);
    }

    /**
     * Constructs an instance type with the specified name, virtual appliance, resource constraints, cost per tick,
     * and processing ratio.
     * This constructor should be used for workflow-based evaluations.
     *
     * @param name            the name of the instance
     * @param va              the VM image file associated with the instance
     * @param arc             the resource requirement of the instance
     * @param pricePerTick    the cost of the instance per tick
     * @param processingRatio the byte/instruction ratio of this instance
     */
    public Instance(String name, VirtualAppliance va, AlterableResourceConstraints arc, double pricePerTick,
            double processingRatio) {
        this.name = name;
        this.va = va;
        this.arc = arc;
        this.pricePerTick = pricePerTick;
        this.processingRatio = processingRatio;
        Instance.allInstances.put(this.name, this);
    }
    
    /**
     * Calculates the cost of using this instance over the given time.
     *
     * @param time the duration for which the cost is to be calculated
     */
    public double calculateCloudCost(long time) {
        return time * pricePerTick;
    }
}