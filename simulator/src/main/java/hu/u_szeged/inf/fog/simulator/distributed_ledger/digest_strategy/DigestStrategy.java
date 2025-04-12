package hu.u_szeged.inf.fog.simulator.distributed_ledger.digest_strategy;

/**
 * The `DigestStrategy` abstract class defines the structure for cryptographic hashing algorithms.
 * Subclasses must implement the `hash` method to provide the specific hashing logic.
 */
public abstract class DigestStrategy {
    private String name;
    private int keySize;

    /**
     * Computes the hash time for the given input size.
     *
     * @param inputSize the size of the input to hash
     * @return a normalized value of the needed number of instructions for a hash operation
     */
    public abstract double hash(long inputSize);

    /**
     * Gets the key size of the hashing algorithm.
     *
     * @return the key size
     */
    public int getKeySize() {
        return this.keySize;
    }

    /**
     * Sets the key size of the hashing algorithm.
     *
     * @param keySize the key size to set
     */
    public void setKeySize(int keySize) {
        this.keySize = keySize;
    }

    /**
     * Gets the name of the hashing algorithm.
     *
     * @return the name of the algorithm
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the name of the hashing algorithm.
     *
     * @param name the name to set
     */
    public void setName(String name) {
        this.name = name;
    }
}