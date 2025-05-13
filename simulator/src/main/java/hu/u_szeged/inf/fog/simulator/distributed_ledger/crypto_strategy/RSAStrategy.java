package hu.u_szeged.inf.fog.simulator.distributed_ledger.crypto_strategy;

import static hu.u_szeged.inf.fog.simulator.distributed_ledger.utils.InterpolationUtils.createSplineFunction;
import static hu.u_szeged.inf.fog.simulator.distributed_ledger.utils.InterpolationUtils.interpolate;

/**
 * The RSAStrategy class implements the AbstractCryptoStrategy interface.
 * It provides methods to compute the signing and verification times for a given key size using the RSA algorithm.
 * The times are interpolated based on predefined key sizes and their corresponding times.
 */
public class RSAStrategy extends AbstractCryptoStrategy {

    // TODO: in the future, these measurements should come from an aggregated configuration file, e.g. json
    private static final double[] KEY_SIZES = {512, 1024, 2048, 3072, 4096, 7680, 15360};
    private static final double[] SIGN_TIMES = {0.028, 0.07, 0.48, 1.464, 3.313, 29.326, 156.719};
    private static final double[] VERIFY_TIMES = {0.002, 0.004, 0.013, 0.029, 0.05, 0.171, 0.673};

    public final double signInstr;
    public final double verifyInstr;

    /**
     * Constructs an RSAStrategy and sets the key size.
     *
     * @param keySize the size of the key
     * @throws IllegalArgumentException if the key size is out of range
     */
    public RSAStrategy(int keySize) {
        if (keySize < KEY_SIZES[0] || keySize > KEY_SIZES[KEY_SIZES.length - 1]) {
            throw new IllegalArgumentException("Key size out of range. Must be between " + KEY_SIZES[0] + " and " + KEY_SIZES[KEY_SIZES.length - 1] + "."); // TODO: extend this with extrapolation too?
        }
        this.setKeySize(keySize);
        this.signInstr = interpolate(createSplineFunction(KEY_SIZES, SIGN_TIMES), keySize, KEY_SIZES[0], KEY_SIZES[KEY_SIZES.length - 1]);
        this.verifyInstr = interpolate(createSplineFunction(KEY_SIZES, VERIFY_TIMES), keySize, KEY_SIZES[0], KEY_SIZES[KEY_SIZES.length - 1]);
    }

    /**
     * Computes the signing time for the given key size.
     *
     * @return the computed signing time
     */
    @Override
    public double sign() {
        return this.signInstr;
    }

    /**
     * Computes the verification time for the given key size.
     *
     * @return the computed verification time
     */
    @Override
    public double verify() {
        return this.verifyInstr;
    }
}