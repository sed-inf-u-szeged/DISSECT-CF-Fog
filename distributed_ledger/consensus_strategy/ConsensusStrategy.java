package hu.u_szeged.inf.fog.simulator.iot.distributed_ledger.consensus_strategy;

import hu.u_szeged.inf.fog.simulator.iot.distributed_ledger.crypto_strategy.CryptoStrategy;
import hu.u_szeged.inf.fog.simulator.iot.distributed_ledger.digest_strategy.DigestStrategy;

public interface ConsensusStrategy {
    DigestStrategy getDigestStrategy();

    CryptoStrategy getCryptoStrategy();

    int getBlockSize();

    int getDifficultyAdjustmentBlock();

    int getStartDifficulty();
}
