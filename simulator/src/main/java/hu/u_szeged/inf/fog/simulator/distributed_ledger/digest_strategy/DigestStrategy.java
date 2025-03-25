package hu.u_szeged.inf.fog.simulator.distributed_ledger.digest_strategy;

/**
 * The cryptographic algorithm to hash
 */
public abstract class DigestStrategy {
    private String name;
    private int keySize;

    /**
     *
     * @return a normalized value of the needed number of instructions for a hash operation
     */
    public abstract double hash(long inputSize);

    public int getKeySize() {
        return this.keySize;
    }

    public void setKeySize(int keySize) {
        this.keySize = keySize;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
