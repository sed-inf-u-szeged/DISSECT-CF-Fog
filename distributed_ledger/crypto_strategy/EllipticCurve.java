package hu.u_szeged.inf.fog.simulator.iot.distributed_ledger.crypto_strategy;

// TODO: in the future, these measurements should come from an aggregated configuration file, e.g. json
public enum EllipticCurve {
    SECP160R1("secp160r1", 0.1, 0.1),
    NISTP192("nistp192", 0.2, 0.2),
    NISTP224("nistp224", 0.04, 0.1),
    NISTP256("nistp256", 0.02, 0.1),
    NISTP384("nistp384", 0.6, 0.5),
    NISTP521("nistp521", 0.2, 0.4),
    NISTK163("nistk163", 0.1, 0.3),
    NISTK233("nistk233", 0.2, 0.4),
    NISTK283("nistk283", 0.3, 0.7),
    NISTK409("nistk409", 0.6, 1.1),
    NISTK571("nistk571", 1.3, 2.6),
    NISTB163("nistb163", 0.1, 0.3),
    NISTB233("nistb233", 0.2, 0.4),
    NISTB283("nistb283", 0.3, 0.7),
    NISTB409("nistb409", 0.6, 1.2),
    NISTB571("nistb571", 1.3, 2.6),
    BRAINPOOLP256R1("brainpoolP256r1", 0.3, 0.3),
    BRAINPOOLP256T1("brainpoolP256t1", 0.3, 0.3),
    BRAINPOOLP384R1("brainpoolP384r1", 0.8, 0.6),
    BRAINPOOLP384T1("brainpoolP384t1", 0.6, 0.5),
    BRAINPOOLP512R1("brainpoolP512r1", 0.9, 0.8);

    private final String name;
    private final double signInstr;
    private final double verifyInstr;

    EllipticCurve(String name, double signInstr, double verifyInstr) {
        this.name = name;
        this.signInstr = signInstr;
        this.verifyInstr = verifyInstr;
    }

    public String getName() {
        return name;
    }

    public double getSignInstructions() {
        return signInstr;
    }

    public double getVerifyInstructions() {
        return verifyInstr;
    }
}
