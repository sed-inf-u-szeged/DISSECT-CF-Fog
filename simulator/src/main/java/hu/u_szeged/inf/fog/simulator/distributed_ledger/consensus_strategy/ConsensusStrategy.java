package hu.u_szeged.inf.fog.simulator.distributed_ledger.consensus_strategy;

import hu.u_szeged.inf.fog.simulator.distributed_ledger.crypto_strategy.CryptoStrategy;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.digest_strategy.DigestStrategy;

/**
 * The ConsensusStrategy interface defines the methods required for a consensus mechanism in a distributed ledger.
 * Implementing classes must provide the logic for retrieving the digest strategy, cryptographic strategy, and block size.
 */
public interface ConsensusStrategy {
    /**
     * Gets the digest strategy used in the consensus mechanism.
     *
     * @return the digest strategy
     */
    DigestStrategy getDigestStrategy();

    /**
     * Gets the cryptographic strategy used in the consensus mechanism.
     *
     * @return the cryptographic strategy
     */
    CryptoStrategy getCryptoStrategy();

    /**
     * Gets the block size used in the consensus mechanism.
     *
     * @return the block size
     */
    int getBlockSize();
}