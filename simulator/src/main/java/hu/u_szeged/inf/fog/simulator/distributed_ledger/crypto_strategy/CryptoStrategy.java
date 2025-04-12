package hu.u_szeged.inf.fog.simulator.distributed_ledger.crypto_strategy;

/**
 * The `CryptoStrategy` interface defines the methods for cryptographic operations.
 * Implementing classes must provide the logic for signing and verifying data, as well as managing key sizes and algorithm names.
 */
public interface CryptoStrategy {
    /**
     * Computes the signing time for the cryptographic operation.
     *
     * @return the computed signing time
     */
    double sign();

    /**
     * Computes the verification time for the cryptographic operation.
     *
     * @return the computed verification time
     */
    double verify();

    /**
     * Gets the key size used in the cryptographic operation.
     *
     * @return the key size
     */
    int getKeySize();

    /**
     * Sets the key size for the cryptographic operation.
     *
     * @param keySize the key size to set
     */
    void setKeySize(int keySize);

    /**
     * Gets the name of the cryptographic algorithm.
     *
     * @return the name of the algorithm
     */
    String getName();
}