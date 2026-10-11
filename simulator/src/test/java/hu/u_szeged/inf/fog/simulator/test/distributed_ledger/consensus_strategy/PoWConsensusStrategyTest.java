package hu.u_szeged.inf.fog.simulator.test.distributed_ledger.consensus_strategy;

import hu.u_szeged.inf.fog.simulator.distributed_ledger.Block;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.consensus_strategy.PoWConsensusStrategy;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.crypto_strategy.RSAStrategy;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.digest_strategy.SHA256Strategy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PoWConsensusStrategyTest {

    private final PoWConsensusStrategy consensus =
            new PoWConsensusStrategy(
                    5, 100, 25_000, 1_000,
                    new RSAStrategy(4096),
                    new SHA256Strategy()
            );

    @Test
    void rejectsNullBlock() {
        assertFalse(consensus.isConsensusValid(null));
    }

    @Test
    void rejectsBlockWithoutFoundNonce() {
        Block block = new Block(consensus, 5);

        assertFalse(consensus.isConsensusValid(block));
    }

    @Test
    void acceptsValidSimulatedPoWBlock() {
        Block block = new Block(consensus, 5);
        block.setNonceFound(true);

        assertTrue(consensus.isConsensusValid(block));
    }

    @Test
    void rejectsBlockWithInsufficientLeadingZeros() {
        Block block = new Block(consensus, 5);
        block.setNonceFound(true);

        String id = block.getId();
        int leadingZeros = 0;

        while (leadingZeros < id.length()
                && id.charAt(leadingZeros) == '0') {
            leadingZeros++;
        }

        block.setDifficulty(leadingZeros + 1);

        assertFalse(consensus.isConsensusValid(block));
    }
}