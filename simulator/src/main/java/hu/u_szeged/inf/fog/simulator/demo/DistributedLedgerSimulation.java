package hu.u_szeged.inf.fog.simulator.demo;


import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.DistributedLedger;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.Miner;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.consensus_strategy.ConsensusStrategy;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.consensus_strategy.PoWConsensusStrategy;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.crypto_strategy.RSAStrategy;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.digest_strategy.SHA256Strategy;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.fork.ForkManager;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.fork.ForkScenario;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.fork.NetworkLatencyFork;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.transaction_selection_strategy.FiLoStrategy;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.validation_strategy.RandomizedValidation;
import hu.u_szeged.inf.fog.simulator.iot.mobility.GeoLocation;
import hu.u_szeged.inf.fog.simulator.node.ComputingAppliance;
import hu.u_szeged.inf.fog.simulator.util.SimLogger;

import java.util.ArrayList;

public class DistributedLedgerSimulation {
    public static void main(String[] args) throws Exception {
        SimLogger.setLogging(1, true);

        String cloudfile = ScenarioBase.resourcePath + "LPDS_original.xml";

        ConsensusStrategy consensus = new PoWConsensusStrategy(10, 20, 10_000, 1000, new RSAStrategy(4096), new SHA256Strategy()); //TODO
        DistributedLedger distributedLedger = new DistributedLedger(consensus);

        for (int i = 0; i < 2; i++) {
            ComputingAppliance ca = new ComputingAppliance(cloudfile, "fog1", new GeoLocation(47.6, 17.9), 50);
            new Miner(distributedLedger, new FiLoStrategy(), ca, new RandomizedValidation(0.95, 0.90, SeedSyncer.centralRnd));
        }
        ForkManager forkManager = new ForkManager();
        ForkScenario forkScenario = new NetworkLatencyFork(true, 0.01, 10_000L, 0);
        forkScenario.setTargets(new ArrayList<>(Miner.miners.values()));
        forkManager.registerScenario(forkScenario, 10_000L);
    }
}
