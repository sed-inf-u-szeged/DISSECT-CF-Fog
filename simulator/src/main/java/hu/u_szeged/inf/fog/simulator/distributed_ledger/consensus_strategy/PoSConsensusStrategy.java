package hu.u_szeged.inf.fog.simulator.distributed_ledger.consensus_strategy;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.Block;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.Miner;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.crypto_strategy.CryptoStrategy;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.digest_strategy.DigestStrategy;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.task.MinerTask;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.task.block.FinalizePoSBlockTask;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.validator.ValidatorRegistry;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.validator.selection.ValidatorSelectionStrategy;
import hu.u_szeged.inf.fog.simulator.util.SimLogger;

import java.util.HashSet;
import java.util.Set;

/**
 * Implements a simplified Proof of Stake consensus mechanism.
 */
public class PoSConsensusStrategy implements ConsensusStrategy {

    private final int blockSize;
    private final long slotDuration;
    private final CryptoStrategy cryptoStrategy;
    private final DigestStrategy digestStrategy;
    private final ValidatorRegistry validatorRegistry;
    private final ValidatorSelectionStrategy selectionStrategy;

    private final Set<Long> producedSlots = new HashSet<>();

    /**
     * Creates a new Proof of Stake consensus strategy.
     *
     * @param slotDuration      the duration of a slot in simulation ticks
     * @param blockSize         the maximum block size
     * @param cryptoStrategy    the cryptographic strategy
     * @param digestStrategy    the digest strategy
     * @param validatorRegistry the registered validators and their stakes
     * @param selectionStrategy the strategy used to select validators
     */
    public PoSConsensusStrategy(long slotDuration,
                                int blockSize,
                                CryptoStrategy cryptoStrategy,
                                DigestStrategy digestStrategy,
                                ValidatorRegistry validatorRegistry,
                                ValidatorSelectionStrategy selectionStrategy) {
        if (slotDuration <= 0) {
            throw new IllegalArgumentException("Slot duration must be positive");
        }

        this.slotDuration = slotDuration;
        this.blockSize = blockSize;
        this.cryptoStrategy = cryptoStrategy;
        this.digestStrategy = digestStrategy;
        this.validatorRegistry = validatorRegistry;
        this.selectionStrategy = selectionStrategy;
    }

    /**
     * Returns the current Proof of Stake slot.
     *
     * @return the current slot
     */
    public long getCurrentSlot() {
        return Timed.getFireCount() / slotDuration;
    }

    @Override
    public DigestStrategy getDigestStrategy() {
        return digestStrategy;
    }

    @Override
    public CryptoStrategy getCryptoStrategy() {
        return cryptoStrategy;
    }

    @Override
    public int getBlockSize() {
        return blockSize;
    }

    /**
     * Determines whether the given miner is selected to create a block in the current slot.
     *
     * @param miner the miner attempting to create a block
     * @return true if the miner is the selected validator
     */
    @Override
    public boolean canCreateBlock(Miner miner) {
        if (validatorRegistry.getValidators().isEmpty()) {
            return false;
        }

        long slot = getCurrentSlot();

        if (producedSlots.contains(slot)) {
            return false;
        }

        Miner selected = selectionStrategy.selectValidator(
                validatorRegistry.getValidators(),
                slot
        );

        return selected == miner;
    }

    /**
     * Creates a new Proof of Stake block for the selected validator.
     *
     * @param miner the miner creating the block
     * @return the newly created block
     */
    @Override
    public Block createBlock(Miner miner) {
        long slot = getCurrentSlot();

        Miner selected = selectionStrategy.selectValidator(
                validatorRegistry.getValidators(),
                slot
        );

        if (selected != miner) {
            throw new IllegalStateException("Miner is not selected for the current slot");
        }

        SimLogger.logRun(
                "[PoSConsensusStrategy] Slot " + slot
                        + " selected validator: " + miner.getName()
                        + ", stake: " + validatorRegistry.getStake(miner)
        );

        Block block = new Block(this, 0);
        block.setProposerId(miner.getName());
        block.setSlot(slot);

        producedSlots.add(slot);

        return block;
    }

    /**
     * Creates the Proof of Stake block finalization task.
     *
     * @return a new Proof of Stake block finalization task
     */
    @Override
    public MinerTask createBlockFinalizationTask() {
        return new FinalizePoSBlockTask();
    }

    /**
     * Checks whether the block was proposed by the validator selected for its slot.
     *
     * @param block the block to validate
     * @return true if the block has a valid proposer for its slot
     */
    @Override
    public boolean isConsensusValid(Block block) {
        if (block == null
                || block.getSlot() < 0
                || block.getProposerId() == null
                || validatorRegistry.getValidators().isEmpty()) {
            return false;
        }

        Miner selected = selectionStrategy.selectValidator(
                validatorRegistry.getValidators(),
                block.getSlot()
        );

        return selected.getName().equals(block.getProposerId());
    }

    /**
     * Returns the computational cost of validating the Proof of Stake proof.
     *
     * @param block the block to validate
     * @return the Proof of Stake validation cost
     */
    @Override
    public double getConsensusValidationCost(Block block) {
        return cryptoStrategy.verify();
    }
}