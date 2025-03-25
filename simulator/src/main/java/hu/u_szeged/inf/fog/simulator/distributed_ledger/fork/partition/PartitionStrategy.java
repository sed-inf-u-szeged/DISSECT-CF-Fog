package hu.u_szeged.inf.fog.simulator.distributed_ledger.fork.partition;

import hu.u_szeged.inf.fog.simulator.node.ComputingAppliance;

import java.util.List;

public interface PartitionStrategy {

    List<ComputingAppliance> selectValidators(List<ComputingAppliance> blocks);
}
