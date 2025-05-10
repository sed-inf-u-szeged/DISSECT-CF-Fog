package hu.u_szeged.inf.fog.simulator.distributed_ledger.digest_strategy;

import static hu.u_szeged.inf.fog.simulator.distributed_ledger.utils.InterpolationUtils.createSplineFunction;
import static hu.u_szeged.inf.fog.simulator.distributed_ledger.utils.InterpolationUtils.interpolate;

/**
 * The `SHA384Strategy` class implements the `DigestStrategy` interface.
 * It provides a method to compute the hash time for a given input size using the SHA-384 algorithm.
 * The hash time is interpolated based on predefined block sizes and their corresponding times.
 */
public class SHA384StrategyAbstract extends AbstractDigestStrategy {

    private static final double[] BLOCK_SIZES = {16, 64, 256, 1024, 8192, 16384};
    private static final double[] TIMES = {
            0.00024137726002534298,
            0.00024112392684782306,
            0.0004922307124727817,
            0.001228048128024836,
            0.00810385905768327,
            0.015952779771875248
    };

    /**
     * Constructs a `SHA384Strategy` and sets the name and key size.
     */
    public SHA384StrategyAbstract() {
        setName("SHA-384");
        setKeySize(384);
    }

    /**
     * Computes the hash time for the given input size using interpolation.
     *
     * @param inputSize the size of the input to hash
     * @return the computed hash time
     * @throws IllegalArgumentException if the input size is out of the interpolation range
     */
    @Override
    public double hash(long inputSize) {
        if (inputSize < 16 || inputSize > 16384) {
            throw new IllegalArgumentException("Block size out of interpolation range for SHA-384.");
        }
        return interpolate(createSplineFunction(BLOCK_SIZES, TIMES), inputSize, BLOCK_SIZES[0], BLOCK_SIZES[BLOCK_SIZES.length - 1]);
    }
}