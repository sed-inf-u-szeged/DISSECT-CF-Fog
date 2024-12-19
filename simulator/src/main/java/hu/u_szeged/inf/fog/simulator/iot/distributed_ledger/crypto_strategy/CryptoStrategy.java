package hu.u_szeged.inf.fog.simulator.iot.distributed_ledger.crypto_strategy;

import hu.u_szeged.inf.fog.simulator.iot.distributed_ledger.digest_strategy.DigestStrategy;

/**
 * The cryptographic algorithm for the Public Key Infrastructure
 */
public abstract class CryptoStrategy {
    private String name;
    private int keySize;

    private DigestStrategy digest;
    public abstract void sign();

    public abstract void validateSignature();

    public int getKeySize() {
        return this.keySize;
    }

    public String getName() {
        return name;
    }
}
