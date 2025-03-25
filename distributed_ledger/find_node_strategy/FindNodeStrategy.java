package hu.u_szeged.inf.fog.simulator.iot.distributed_ledger.find_node_strategy;

import hu.u_szeged.inf.fog.simulator.iot.distributed_ledger.BlockValidator;

public interface FindNodeStrategy {
    BlockValidator findNode();
}
