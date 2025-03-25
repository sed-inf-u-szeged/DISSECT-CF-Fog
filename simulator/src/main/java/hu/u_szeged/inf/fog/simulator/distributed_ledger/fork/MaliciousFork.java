package hu.u_szeged.inf.fog.simulator.distributed_ledger.fork;

import hu.u_szeged.inf.fog.simulator.distributed_ledger.BlockValidator;

import java.util.List;

public class MaliciousFork extends ForkScenario {
    public MaliciousFork(boolean recurring, double probability, long baseInterval, long variance) {
        super("Malicious Fork", recurring, probability, baseInterval, variance);
    }

    @Override
    public void executeScenario(List<BlockValidator> validators) {
        System.out.println("[Fork] Triggered: " + scenarioName);
        // Assign some validators to build an alternative chain
    }
}
