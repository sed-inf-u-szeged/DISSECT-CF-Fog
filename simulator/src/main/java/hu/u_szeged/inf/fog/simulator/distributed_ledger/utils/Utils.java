package hu.u_szeged.inf.fog.simulator.distributed_ledger.utils;

import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.Block;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.Miner;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.Transaction;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.consensus_strategy.ConsensusStrategy;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.digest_strategy.DigestStrategy;
import hu.u_szeged.inf.fog.simulator.node.ComputingAppliance;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * Utility class for various helper methods used in the distributed ledger simulation.
 */
public class Utils {

    private static final Random RANDOM = SeedSyncer.centralRnd;
    private static final double VARIANCE_PROBABILITY = 0.05; // 5% chance to reduce effort
    private static final double VARIANCE_FACTOR = 0.1; // Reduce to 10% of original
    private static final int FAKE_TRANSACTION_SIZE = 512; // Size of fake transactions

    private static final int BASE = 16;

    private Utils() {
    }

    /**
     * Calculates the number of instructions required for proof of work based on the given difficulty.
     *
     * @param difficulty the difficulty level of the proof of work
     * @return the number of instructions required
     */
    public static long instructionsPoW(long difficulty) {
        double result = Math.pow(BASE, difficulty);

        // Simulate variance: 5% chance to reduce effort (early nonce discovery)
        if (RANDOM.nextDouble() < VARIANCE_PROBABILITY) {
            result = (long) (result * VARIANCE_FACTOR);
        }

        return (long) result;
    }

    /**
     * Calculates the number of nodes in a Merkle tree given the number of leaf nodes.
     *
     * @param numberOfLeaves the number of leaf nodes
     * @return the total number of nodes in the Merkle tree
     */
    private static int merkleRootNodes(int numberOfLeaves) {
        return 2 * numberOfLeaves - 1;
    }

    /**
     * Calculates the number of instructions required to compute the Merkle root of a block.
     *
     * @param nextBlock      the block for which the Merkle root is being calculated
     * @param digestStrategy the strategy used to compute the hash
     * @return the number of instructions required
     */
    public static double merkleRoot(Block nextBlock, DigestStrategy digestStrategy) {
        int leafNodes = nextBlock.getTransactions().size();
        double instructions = nextBlock.getTransactions().stream().mapToDouble(tx -> digestStrategy.hash(tx.getSize())).sum();
        int nonLeafNodes = Utils.merkleRootNodes(leafNodes) - leafNodes;
        int hashKeySize = digestStrategy.getKeySize();
        for (int i = 0; i < nonLeafNodes; i++) {
            instructions += digestStrategy.hash(hashKeySize * 2L);
        }
        return instructions;
    }

    /**
     * Calculates the cost of reorganizing the local ledger.
     *
     * @param consensusStrategy the consensus strategy used for validation
     * @param blocks            the list of blocks to be validated
     * @return the total cost of reorganization
     */
    public static double reorgCost(ConsensusStrategy consensusStrategy, List<Block> blocks) {
        double blockValidationCosts = blocks.stream().mapToDouble(block -> validateBlockCost(consensusStrategy, block)).sum();

        int numberOfTransactions = blocks.stream().mapToInt(block -> block.getTransactions().size()).sum();
        double transactionValidationCosts = numberOfTransactions * consensusStrategy.getCryptoStrategy().verify();
        return blockValidationCosts + transactionValidationCosts;
    }

    /**
     * Generates a fake hash string with a specified number of leading zeros.
     *
     * @param zerosCount the number of leading zeros in the hash
     * @return the generated fake hash string
     */
    public static String generateFakeHash(int zerosCount) {
        final String HEX_CHARS = "0123456789abcdef";
        final int HASH_LENGTH = 64;

        if (zerosCount < 0) {
            zerosCount = 0;
        } else if (zerosCount > HASH_LENGTH) {
            zerosCount = HASH_LENGTH;
        }

        StringBuilder sb = new StringBuilder(HASH_LENGTH);

        sb.append("0".repeat(zerosCount));

        for (int i = zerosCount; i < HASH_LENGTH; i++) {
            int index = RANDOM.nextInt(HEX_CHARS.length());
            sb.append(HEX_CHARS.charAt(index));
        }

        return sb.toString();
    }

    /**
     * Generates a list of fake blocks for the distributed ledger.
     *
     * @param consensusStrategy the consensus strategy used for block generation
     * @param numberOfBlocks    the number of blocks to generate
     * @param difficulty        the difficulty level for the blocks
     * @return the list of generated fake blocks
     */
    public static List<Block> generateFakeBlocks(ConsensusStrategy consensusStrategy, int numberOfBlocks, long difficulty) {
        List<Block> fakeBlocks = new ArrayList<>(numberOfBlocks);
        for (int i = 0; i < numberOfBlocks; i++) {
            Block block = new Block(consensusStrategy, difficulty);
            while (!block.isFull()) {
                Transaction tx = new Transaction("fake-data", FAKE_TRANSACTION_SIZE);
                if (block.size() + tx.getSize() <= consensusStrategy.getBlockSize()) {
                    block.add(tx);
                }
            }
            fakeBlocks.add(block);
        }
        return fakeBlocks;
    }

    /**
     * Calculates the cost of validating a block in the distributed ledger.
     *
     * @param consensusStrategy the consensus strategy used for validation
     * @param block             the block to be validated
     * @return the cost of validating the block
     */
    public static double validateBlockCost(ConsensusStrategy consensusStrategy, Block block) {
        int numberOfTx = block.getTransactions().size();
        double verifySign = consensusStrategy.getCryptoStrategy().verify();
        double verifyNonce = consensusStrategy.getDigestStrategy().hash(80L);
        double verifyMerkle = Utils.merkleRoot(block, consensusStrategy.getDigestStrategy());
        return verifySign * numberOfTx + verifyNonce + verifyMerkle;
    }

    /**
     * Converts a list of computing appliances to a list of miners.
     *
     * @param cas the list of computing appliances
     * @return the list of miners
     */
    public static List<Miner> computingAppliances2Miners(List<ComputingAppliance> cas) {
        return cas.stream().map(Utils::ca2Miner).collect(Collectors.toList());
    }

    /**
     * Converts a list of miners to a list of computing appliances.
     *
     * @param miners the list of miners
     * @return the list of computing appliances
     */
    public static List<ComputingAppliance> miners2ComputingAppliances(List<Miner> miners) {
        return miners.stream().map(Utils::miner2Ca).collect(Collectors.toList());
    }

    /**
     * Converts a computing appliance to a miner.
     *
     * @param ca the computing appliance
     * @return the corresponding miner
     */
    public static Miner ca2Miner(ComputingAppliance ca) {
        return Miner.miners.get(ca);
    }

    /**
     * Converts a miner to a computing appliance.
     *
     * @param m the miner
     * @return the corresponding computing appliance
     */
    public static ComputingAppliance miner2Ca(Miner m) {
        return Miner.miners.inverse().get(m);
    }
}