package hu.u_szeged.inf.fog.simulator.distributed_ledger.consensus_strategy;

import hu.u_szeged.inf.fog.simulator.distributed_ledger.Block;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.LocalLedger;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.crypto_strategy.CryptoStrategy;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.digest_strategy.DigestStrategy;
import hu.u_szeged.inf.fog.simulator.util.SimLogger;

/**
 * The PoWConsensusStrategy class implements the ConsensusStrategy and DifficultyAdjustmentStrategy interfaces.
 * It provides methods to manage the Proof of Work (PoW) consensus mechanism and adjust the mining difficulty.
 */
public class PoWConsensusStrategy implements ConsensusStrategy, DifficultyAdjustmentStrategy {

    /**
     * The initial difficulty level for mining.
     */
    private final int startDifficulty;

    /**
     * The number of blocks after which the difficulty is adjusted.
     */
    private final int difficultyAdjustmentBlock;

    /**
     * The max size of each block in the blockchain.
     */
    private final int blockSize;

    /**
     * The target block timespan in ticks.
     * This is used to calculate the expected time between blocks.
     */
    private final long targetBlockTimespan;

    /**
     * The cryptographic strategy used for hashing and signing blocks.
     */
    public final CryptoStrategy cryptoStrategy;

    /**
     * The digest strategy used for hashing the block data.
     */
    public final DigestStrategy digestStrategy;

    /**
     * Constructor for the PoWConsensusStrategy.
     *
     * @param startDifficulty           the initial difficulty level
     * @param difficultyAdjustmentBlock the number of blocks after which to adjust the difficulty
     * @param targetBlockTimespan       the target time between blocks in ticks
     * @param blockSize                 the size of each block in the blockchain
     * @param cryptoStrategy            the cryptographic strategy used for hashing and signing blocks
     * @param digestStrategy            the digest strategy used for hashing the block data
     */
    public PoWConsensusStrategy(int startDifficulty, int difficultyAdjustmentBlock, long targetBlockTimespan, int blockSize, CryptoStrategy cryptoStrategy, DigestStrategy digestStrategy) {
        this.startDifficulty = startDifficulty;
        this.difficultyAdjustmentBlock = difficultyAdjustmentBlock;
        this.targetBlockTimespan = targetBlockTimespan;
        this.blockSize = blockSize;
        this.cryptoStrategy = cryptoStrategy;
        this.digestStrategy = digestStrategy;
    }

    /**
     * Gets the target block timespan.
     *
     * @return the target block timespan
     */
    public long getTargetBlockTimespan() {
        return targetBlockTimespan;
    }


    /**
     * Gets the digest strategy used in the consensus mechanism.
     *
     * @return the digest strategy
     */
    public DigestStrategy getDigestStrategy() {
        return digestStrategy;
    }

    /**
     * Gets the cryptographic strategy used in the consensus mechanism.
     *
     * @return the cryptographic strategy
     */
    public CryptoStrategy getCryptoStrategy() {
        return cryptoStrategy;
    }

    /**
     * Gets the block size used in the consensus mechanism.
     *
     * @return the block size
     */
    public int getBlockSize() {
        return blockSize;
    }

    /**
     * Gets the initial difficulty level.
     *
     * @return the initial difficulty level
     */
    public int getStartDifficulty() {
        return startDifficulty;
    }

    /**
     * Computes the next mining difficulty based on the recent block confirmation times
     * in the given {@link LocalLedger}.
     * <p>
     * This method follows a basic difficulty adjustment mechanism similar to Bitcoin's,
     * adjusting the difficulty every {@code difficultyAdjustmentBlock} blocks based on
     * how long the last N blocks took to mine, compared to a target timespan.
     * </p>
     *
     * <p><b>Adjustment Formula:</b><br>
     * {@code newDifficulty = currentDifficulty * (expectedTimespan / actualTimespan)}</p>
     *
     * <p>If blocks were mined faster than the expected rate (i.e., {@code actualTimespan < expectedTimespan}),
     * the difficulty increases. If they were slower, the difficulty decreases. A minimum
     * difficulty of 1 is enforced.</p>
     *
     * @param localLedger the miner’s local copy of the blockchain used to retrieve past block timestamps
     * @return the new difficulty level to be used for the next block
     */
    @Override
    public long computeNextDifficulty(LocalLedger localLedger) {

        int chainSize = localLedger.size();
        if (chainSize == 0) {
            // No blocks yet => use startDifficulty
            return startDifficulty;
        }

        Block lastBlock = localLedger.getChain().get(chainSize - 1);
        long currentDifficulty = lastBlock.getDifficulty();

        // Check if we are at a retarget boundary:
        if (chainSize % difficultyAdjustmentBlock != 0) {
            // Not time to recalc => use the same difficulty as the last block
            return currentDifficulty;
        }

        if (chainSize < difficultyAdjustmentBlock) {
            return currentDifficulty;
        }

        Block firstInWindow = localLedger.getChain().get(chainSize - difficultyAdjustmentBlock);
        Block lastInWindow = localLedger.getChain().get(chainSize - 1);

        long firstTime = firstInWindow.getTimestamp();
        long lastTime = lastInWindow.getTimestamp();
        long actualTimespan = lastTime - firstTime;

        // e.g. each block is targeted at 10_000 ticks =>
        // expectedTimespan = difficultyAdjustmentBlock * 10_000
        long expectedTimespan = difficultyAdjustmentBlock * targetBlockTimespan;

        boolean clampingUsed = false;
        if (actualTimespan < expectedTimespan / 4){
            actualTimespan = expectedTimespan / 4;
            clampingUsed = true;
        }

        if (actualTimespan > expectedTimespan * 4){
            actualTimespan = expectedTimespan * 4;
            clampingUsed = true;
        }

        double ratio = (double) expectedTimespan / (double) actualTimespan;
        long newDifficulty = Math.round(currentDifficulty * ratio);

        if (newDifficulty < 1) newDifficulty = 1;

        SimLogger.logRun("[PoWConsensusStrategy] Difficulty adjustment: " +
                "currentDifficulty=" + currentDifficulty +
                ", expectedTimespan=" + expectedTimespan +
                ", actualTimespan=" + actualTimespan +
                ", newDifficulty=" + newDifficulty +
                ", clampingUsed=" + clampingUsed);

        return newDifficulty;
    }
}