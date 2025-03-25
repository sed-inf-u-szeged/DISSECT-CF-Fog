package hu.u_szeged.inf.fog.simulator.distributed_ledger.fork;

import hu.u_szeged.inf.fog.simulator.distributed_ledger.BlockValidator;

import java.util.List;

public class NetworkLatencyFork extends ForkScenario {
    public NetworkLatencyFork(boolean recurring, double probability, long baseInterval, long variance) {
        super("Network Latency Fork", recurring, probability, baseInterval, variance);
    }

    @Override
    public void executeScenario(List<BlockValidator> validators) {
        System.out.println("[Fork] Triggered: " + scenarioName);
        // Simulate a temporary network partition between validators
        // Example: Split validators into 2 groups, delay/block propagation temporarily
    }
}