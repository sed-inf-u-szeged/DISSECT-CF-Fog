package hu.u_szeged.inf.fog.simulator.distributed_ledger.crypto_strategy;

public class ECDSAStrategy extends CryptoStrategy {

    public final EllipticCurve ellipticCurve;
    public ECDSAStrategy(EllipticCurve ellipticCurve) {
        this.ellipticCurve = ellipticCurve;
    }

    @Override
    public double sign() {
        return this.ellipticCurve.getSignInstructions();
    }

    @Override
    public double verify() {
        return this.ellipticCurve.getVerifyInstructions();
    }
}

