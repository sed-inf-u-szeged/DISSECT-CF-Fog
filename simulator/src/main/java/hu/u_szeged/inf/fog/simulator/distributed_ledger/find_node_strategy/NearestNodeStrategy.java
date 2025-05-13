package hu.u_szeged.inf.fog.simulator.distributed_ledger.find_node_strategy;

import hu.u_szeged.inf.fog.simulator.distributed_ledger.Miner;

import hu.u_szeged.inf.fog.simulator.iot.Device;
import hu.u_szeged.inf.fog.simulator.iot.mobility.GeoLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * The NearestNodeStrategy class implements the FindNodeStrategy interface.
 * It defines a strategy for finding the nearest node to a given miner based on the mobility strategy.
 * This is a placeholder implementation and should be replaced with actual logic.
 */
public class NearestNodeStrategy implements FindNodeStrategy {

    private Device device;

    /**
     * Constructor for the NearestNodeStrategy class.
     * Initializes the strategy with the given device.
     *
     * @param device The device to be used in this strategy.
     */
    public NearestNodeStrategy(Device device) {
        this.device = device;
    }

    /**
     * Returns the device associated with this strategy.
     *
     * @return The device associated with this strategy.
     */
    public Device getDevice() {
        return device;
    }

    /**
     * Sets the device for this strategy.
     *
     * @param device The device to set.
     */
    public void setDevice(Device device) {
        this.device = device;
    }

    /**
     * Finds the nearest node to the given miner based on the mobility strategy.
     * This method iterates through all registered miners and calculates the distance to each one.
     * The miner with the smallest distance is returned.
     *
     * @return The nearest miner to the given device.
     */
    @Override
    public Miner findNode() {
        List<Miner> nodes = new ArrayList<>(Miner.miners.values());
        if (nodes.isEmpty()) throw new IllegalStateException("No nodes registered!");
        if (this.device.geoLocation == null) return nodes.get(0);

        double minDistance = Double.MAX_VALUE;
        Miner nearestNode = null;
        for (Miner miner : nodes) {
            GeoLocation geoLocation = miner.computingAppliance.geoLocation;
            if (geoLocation == null) continue;
            double distance = geoLocation.calculateDistance(this.device.geoLocation);
            if (distance < minDistance) {
                minDistance = distance;
                nearestNode = miner;
            }
        }
        return nearestNode;
    }
}