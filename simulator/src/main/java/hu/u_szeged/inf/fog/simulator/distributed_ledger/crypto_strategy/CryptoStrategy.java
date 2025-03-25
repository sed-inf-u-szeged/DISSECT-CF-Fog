package hu.u_szeged.inf.fog.simulator.distributed_ledger.crypto_strategy;

/**
 * The cryptographic algorithm for the Public Key Infrastructure
 */
public abstract class CryptoStrategy {
    private String name;
    private int keySize;

    public abstract double sign();

    public abstract double verify();

    public int getKeySize() {
        return this.keySize;
    }

    public void setKeySize(int keySize) {
        this.keySize = keySize;
    }

    public String getName() {
        return name;
    }
}
