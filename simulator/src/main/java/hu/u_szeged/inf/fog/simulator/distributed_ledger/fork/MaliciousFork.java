package hu.u_szeged.inf.fog.simulator.distributed_ledger.fork;


import hu.u_szeged.inf.fog.simulator.distributed_ledger.Miner;
import hu.u_szeged.inf.fog.simulator.util.SimLogger;

import java.util.List;

public class MaliciousFork extends ForkScenario {
    public MaliciousFork(boolean recurring, double probability, long baseInterval, long variance) {
        super("Malicious Fork", recurring, probability, baseInterval, variance);
    }

    @Override
    public void executeScenario(List<Miner> miners) {
        SimLogger.logRun("[Fork] Triggered: " + scenarioName);
        // Assign some miners to build an alternative chain
    }
}
