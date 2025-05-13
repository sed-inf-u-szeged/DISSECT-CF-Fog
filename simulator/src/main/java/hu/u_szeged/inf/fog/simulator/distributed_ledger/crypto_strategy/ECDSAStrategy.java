package hu.u_szeged.inf.fog.simulator.distributed_ledger.crypto_strategy;

/**
 * The ECDSAStrategy class extends the AbstractCryptoStrategy class and implements the CryptoStrategy interface.
 * It provides methods to compute the signing and verification times for a given elliptic curve using the ECDSA algorithm.
 * The times are based on the predefined signing and verification instructions for each elliptic curve.
 */
public class ECDSAStrategy extends AbstractCryptoStrategy {

    public final EllipticCurve ellipticCurve;

    /**
     * Constructs an ECDSAStrategy with the specified elliptic curve.
     *
     * @param ellipticCurve the elliptic curve to use
     */
    public ECDSAStrategy(EllipticCurve ellipticCurve) {
        this.ellipticCurve = ellipticCurve;
    }

    /**
     * Computes the signing time for the given elliptic curve.
     *
     * @return the computed signing time
     */
    @Override
    public double sign() {
        return this.ellipticCurve.getSignInstructions();
    }

    /**
     * Computes the verification time for the given elliptic curve.
     *
     * @return the computed verification time
     */
    @Override
    public double verify() {
        return this.ellipticCurve.getVerifyInstructions();
    }
}