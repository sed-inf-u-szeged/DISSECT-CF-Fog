package hu.u_szeged.inf.fog.simulator.iot.distributed_ledger.consensus_strategy;

import hu.u_szeged.inf.fog.simulator.iot.distributed_ledger.crypto_strategy.CryptoStrategy;
import hu.u_szeged.inf.fog.simulator.iot.distributed_ledger.digest_strategy.DigestStrategy;

public class PoWConsensusStrategy implements ConsensusStrategy{

    private final int startDifficulty;
    private final int difficultyAdjustmentBlock;
    private final int blockSize;

    public final CryptoStrategy cryptoStrategy;
    public final DigestStrategy digestStrategy;

    public PoWConsensusStrategy(int startDifficulty, int difficultyAdjustmentBlock, int blockSize, CryptoStrategy cryptoStrategy, DigestStrategy digestStrategy) {
        this.startDifficulty = startDifficulty;
        this.difficultyAdjustmentBlock = difficultyAdjustmentBlock;
        this.blockSize = blockSize;
        this.cryptoStrategy = cryptoStrategy;
        this.digestStrategy = digestStrategy;
    }

    public DigestStrategy getDigestStrategy() {
        return digestStrategy;
    }

    public CryptoStrategy getCryptoStrategy() {
        return cryptoStrategy;
    }

    public int getBlockSize() {
        return blockSize;
    }

    public int getDifficultyAdjustmentBlock() {
        return difficultyAdjustmentBlock;
    }

    public int getStartDifficulty() {
        return startDifficulty;
    }
}
