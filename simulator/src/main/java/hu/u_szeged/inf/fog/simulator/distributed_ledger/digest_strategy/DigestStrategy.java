package hu.u_szeged.inf.fog.simulator.distributed_ledger.digest_strategy;

/**
 * The `DigestStrategy` interface defines the structure for cryptographic hashing algorithms.
 */
public interface DigestStrategy {

    double hash(long inputSize);

    int getKeySize();

    void setKeySize(int keySize);

    String getName();

    void setName(String name);
}
