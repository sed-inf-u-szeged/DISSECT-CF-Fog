package hu.u_szeged.inf.fog.simulator.iot.distributed_ledger.digest_strategy;

/**
 * The cryptographic algorithm to hash
 */
public abstract class DigestStrategy {
    private String name;

    public abstract void hash();

    public String getName() {
        return name;
    }
}
