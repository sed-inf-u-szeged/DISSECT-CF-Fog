package hu.u_szeged.inf.fog.simulator.iot.distributed_ledger;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.u_szeged.inf.fog.simulator.iot.distributed_ledger.consensus_strategy.ConsensusStrategy;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

//TODO: track newly created blocks and track how many node has already validated it, if it is more than 51%, than it can be handled as a valid block
//if 51% of the miners has the new block in their localledger, then it can be considered valid
public class DistributedLedger extends Timed {
    private int numberOfBlocks;
    private int previousDifficulty;
    private int difficulty;

    private final Map<Block, Set<BlockValidator>> blockValidations = new HashMap<>();

    public final ConsensusStrategy consensusStrategy;

    public int getDifficulty() {
        return difficulty;
    }

    public DistributedLedger(ConsensusStrategy consensusStrategy) {
        //subscribe(1); //should be adjusted only if it reaches the adjustment block size, and should not check each ms
        this.consensusStrategy = consensusStrategy;
        this.numberOfBlocks = 0;
        this.difficulty = this.getConsensusStrategy().getStartDifficulty();
    }

    public ConsensusStrategy getConsensusStrategy() {
        return consensusStrategy;
    }

    private void adjustDifficulty() {
        this.previousDifficulty = this.difficulty;
        this.difficulty *= 1; //TODO: This needs some logic. We need to adjust the difficulty depending on how long the last block took to validate
    }

    /**
     * Add the validator to the set of the block in key after it approved the block
     * @param block
     * @param validator
     */
    public void addValidation(Block block, BlockValidator validator) {
        blockValidations.computeIfAbsent(block, k -> new HashSet<>()).add(validator);
    }

    @Override
    public void tick(long fires) {
        if (this.numberOfBlocks % this.getConsensusStrategy().getDifficultyAdjustmentBlock() == 0) {
            adjustDifficulty();
            System.out.println("Difficulty has been adjusted from: " + previousDifficulty + " to: " + difficulty);
        }

    }
}
