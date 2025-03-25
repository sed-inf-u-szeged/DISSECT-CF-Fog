package hu.u_szeged.inf.fog.simulator.iot.distributed_ledger.utils;

import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;
import hu.u_szeged.inf.fog.simulator.iot.distributed_ledger.Block;
import hu.u_szeged.inf.fog.simulator.iot.distributed_ledger.DistributedLedger;
import hu.u_szeged.inf.fog.simulator.iot.distributed_ledger.digest_strategy.DigestStrategy;

import java.util.Random;

public class Utils {

    private static final Random RANDOM = SeedSyncer.centralRnd;
    private static final double VARIANCE_PROBABILITY = 0.05; // 5% chance to reduce effort
    private static final double VARIANCE_FACTOR = 0.1; // Reduce to 10% of original

    private static final int BASE = 16;

    private Utils() {
    }

    public static long instructionsPoW(DistributedLedger distributedLedger) {
        int difficulty = distributedLedger.getDifficulty();
        double result = Math.pow(BASE, difficulty);

        // Simulate variance: 5% chance to reduce effort (early nonce discovery)
        if (RANDOM.nextDouble() < VARIANCE_PROBABILITY) {
            result = (long) (result * VARIANCE_FACTOR);
        }

        return (long) result;
    }

    private static int merkleRootNodes(int numberOfLeaves) {
        return 2 * numberOfLeaves - 1;
    }

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


    public static String generateHash(int zerosCount) {
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

}
