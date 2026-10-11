package hu.u_szeged.inf.fog.simulator.distributed_ledger.consensus_strategy;

import hu.u_szeged.inf.fog.simulator.distributed_ledger.Block;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.LocalLedger;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.Miner;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.crypto_strategy.CryptoStrategy;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.digest_strategy.DigestStrategy;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.task.MinerTask;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.task.block.FindNonceTask;
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
     * Creates a new block using the Proof of Work difficulty adjustment mechanism.
     *
     * @param miner the miner creating the block
     * @return the newly created block
     */
    @Override
    public Block createBlock(Miner miner) {
        long nextDifficulty = computeNextDifficulty(miner.getLocalLedger());
        return new Block(this, nextDifficulty);
    }

    /**
     * Creates the block finalization task used by the Proof of Work consensus mechanism.
     *
     * @return a new nonce finding task
     */
    @Override
    public MinerTask createBlockFinalizationTask() {
        return new FindNonceTask();
    }

    /**
     * Allows every miner to attempt block creation in the Proof of Work consensus mechanism.
     *
     * @param miner the miner attempting to create a block
     * @return always true
     */
    @Override
    public boolean canCreateBlock(Miner miner) {
        return true;
    }

    /**
     * Computes the next mining difficulty based on the recent block confirmation times
     * in the given {@link LocalLedger}.
     *
     * <p>
     * The difficulty is adjusted every {@code difficultyAdjustmentBlock} blocks
     * by comparing the actual mining timespan with the expected timespan.
     * </p>
     *
     * <p><b>Adjustment Formula:</b><br>
     * {@code difficultyChange = log(expectedTimespan / actualTimespan) / log(16)}
     * </p>
     *
     * <p>
     * Since each difficulty level represents a 16-fold increase in expected
     * computational work, the adjustment is performed logarithmically rather
     * than by directly multiplying the difficulty level.
     * </p>
     *
     * <p>
     * The expected timespan is calculated using N - 1 block intervals,
     * where N is the number of blocks in the adjustment window.
     * The adjustment is limited to one difficulty level per interval,
     * with a minimum difficulty of 1 and a maximum of 64.
     * </p>
     *
     * @param localLedger the miner's local blockchain used to retrieve block timestamps
     * @return the difficulty level to be used for the next block
     */
    @Override
    public long computeNextDifficulty(LocalLedger localLedger) {

        int chainSize = localLedger.size();
        if (chainSize == 0) {
            return startDifficulty;
        }

        Block lastBlock = localLedger.getChain().get(chainSize - 1);
        long currentDifficulty = lastBlock.getDifficulty();

        // Difficulty adjustment requires at least two blocks per window.
        if (difficultyAdjustmentBlock < 2) {
            return currentDifficulty;
        }

        // Keep the current difficulty between adjustment boundaries.
        if (chainSize % difficultyAdjustmentBlock != 0) {
            return currentDifficulty;
        }

        Block firstInWindow = localLedger.getChain().get(
                chainSize - difficultyAdjustmentBlock
        );

        long actualTimespan = Math.max(1L,
                lastBlock.getTimestamp() - firstInWindow.getTimestamp());

        // N blocks contain N - 1 intervals between their timestamps.
        double expectedTimespan =
                (difficultyAdjustmentBlock - 1.0) * targetBlockTimespan;

        // Calculate the logarithmic difficulty adjustment.
        double ratio = expectedTimespan / actualTimespan;
        double change = Math.log(ratio) / Math.log(16.0);

        // Limit the adjustment to one difficulty level per interval.
        double clampedChange = Math.max(-1.0, Math.min(1.0, change));

        // Enforce the supported difficulty range.
        long newDifficulty = Math.max(1L, Math.min(64L,
                Math.round(currentDifficulty + clampedChange)));

        SimLogger.logRun("[PoWConsensusStrategy] Difficulty adjustment: " +
                "currentDifficulty=" + currentDifficulty +
                ", expectedTimespan=" + expectedTimespan +
                ", actualTimespan=" + actualTimespan +
                ", newDifficulty=" + newDifficulty);

        return newDifficulty;
    }


    /**
     * Validates a simulated Proof of Work result.
     *
     * <p>
     * The block must have a completed nonce search and a valid
     * 64-character hexadecimal identifier containing at least
     * the required number of leading zeros.
     * </p>
     *
     * <p>
     * This checks the simulator's synthetic block identifier.
     * It does not perform cryptographic nonce verification.
     * </p>
     *
     * @param block the block to validate
     * @return true if the simulated Proof of Work requirements are met
     */
    @Override
    public boolean isConsensusValid(Block block) {
        if (block == null || !block.isNonceFound()) {
            return false;
        }

        long difficulty = block.getDifficulty();

        // SHA-256 hashes contain 64 hexadecimal characters.
        if (difficulty < 1 || difficulty > 64) {
            return false;
        }

        String blockId = block.getId();

        if (blockId == null || !blockId.matches("[0-9a-f]{64}")) {
            return false;
        }

        // Check whether the block ID satisfies the difficulty target.
        return blockId.startsWith("0".repeat((int) difficulty));
    }

    /**
     * Returns the computational cost of validating the Proof of Work nonce.
     *
     * @param block the block to validate
     * @return the nonce validation cost
     */
    @Override
    public double getConsensusValidationCost(Block block) {
        return digestStrategy.hash(80L);
    }
}