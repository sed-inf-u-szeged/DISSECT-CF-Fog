package hu.u_szeged.inf.fog.simulator.distributed_ledger.fork.partition;

import hu.u_szeged.inf.fog.simulator.node.ComputingAppliance;

import java.util.List;

/**
 * Interface for partition strategies.
 * <p>
 * This interface defines the method for selecting nodes based on a partition strategy.
 * Implementations of this interface can provide different strategies for selecting nodes.
 */
public interface PartitionStrategy {

    /**
     * Selects nodes based on the partition strategy.
     *
     * This method takes a list of {@code ComputingAppliance} nodes and returns a list of selected nodes
     * based on the partition strategy implemented by the class.
     * @param nodes The list of {@code ComputingAppliance} to select from.
     * @return A list of selected nodes based on the partition strategy.
     */
    List<ComputingAppliance> selectNodes(List<ComputingAppliance> nodes);
}
