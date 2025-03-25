package hu.u_szeged.inf.fog.simulator.distributed_ledger.fork;

import hu.u_szeged.inf.fog.simulator.distributed_ledger.BlockValidator;

import java.util.List;

public class FiftyOnePercentAttackFork extends ForkScenario {
    private final List<BlockValidator> maliciousGroup;
    private final List<BlockValidator> honestGroup;

    public FiftyOnePercentAttackFork(List<BlockValidator> maliciousGroup, List<BlockValidator> honestGroup) {
        super("51% Attack", false, 1.0, 0, 0);
        this.maliciousGroup = maliciousGroup;
        this.honestGroup = honestGroup;
    }

    @Override
    public void executeScenario(List<BlockValidator> validators) {
//        for (BlockValidator bv : maliciousGroup) {
//            // Override their normal block propagation logic
//            bv.setSelfishMiningMode(true);
//        }
//
//        new DeferredEvent(5000) {
//            @Override
//            protected void eventAction() {
//                triggerReorg();
//            }
//        };
    }

    private void triggerReorg() {
//        for (BlockValidator bv : maliciousGroup) {
//            bv.revealPrivateChain();
//        }
//        for (BlockValidator bv : honestGroup) {
//            bv.reorgTo(bv.receiveNewChainFromMaliciousGroup());
//        }
        System.out.println("[51% Attack] Malicious chain revealed and accepted by honest validators.");
    }
}

