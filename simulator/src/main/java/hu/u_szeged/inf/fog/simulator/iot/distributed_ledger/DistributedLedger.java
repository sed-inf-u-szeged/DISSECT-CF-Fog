package hu.u_szeged.inf.fog.simulator.iot.distributed_ledger;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.u_szeged.inf.fog.simulator.iot.distributed_ledger.crypto_strategy.CryptoStrategy;
import hu.u_szeged.inf.fog.simulator.iot.distributed_ledger.digest_strategy.DigestStrategy;

public class DistributedLedger extends Timed {
    private int numberOfBlocks;
    private int previousDifficulty;
    private int difficulty;
    private final int difficultyAdjustmentBlock;
    private final int blockSize;

    final public CryptoStrategy cryptoStrategy;
    final public DigestStrategy digestStrategy;

    public int getBlockSize() {
        return blockSize;
    }

    public int getDifficulty() {
        return difficulty;
    }

    public DistributedLedger(int difficultyAdjustmentBlock, int blockSize, int difficulty,
                             CryptoStrategy cryptoStrategy, DigestStrategy digestStrategy) {
        subscribe(1);
        this.cryptoStrategy = cryptoStrategy;
        this.digestStrategy = digestStrategy;
        this.numberOfBlocks = 0;
        this.blockSize = blockSize; //in bytes
        this.difficultyAdjustmentBlock = difficultyAdjustmentBlock;
        this.difficulty = difficulty;
    }

    private void adjustDifficulty(){
        this.previousDifficulty = this.difficulty;
        this.difficulty *= 1; //TODO: This needs some logic. We need to adjust the difficulty depending on how long the last block took to validate
    }

    @Override
    public void tick(long fires) {
        if(this.numberOfBlocks % this.difficultyAdjustmentBlock == 0){
            adjustDifficulty();
            System.out.println("Difficulty has been adjusted from: " + previousDifficulty + " to: " + difficulty);
        }

    }
}
