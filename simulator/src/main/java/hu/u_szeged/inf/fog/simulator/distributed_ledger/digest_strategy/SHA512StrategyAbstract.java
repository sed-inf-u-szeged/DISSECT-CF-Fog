package hu.u_szeged.inf.fog.simulator.distributed_ledger.digest_strategy;


import static hu.u_szeged.inf.fog.simulator.distributed_ledger.utils.InterpolationUtils.createSplineFunction;
import static hu.u_szeged.inf.fog.simulator.distributed_ledger.utils.InterpolationUtils.interpolate;

/**
 * The `SHA512Strategy` class implements the `DigestStrategy` interface.
 * It provides a method to compute the hash time for a given input size using the SHA-512 algorithm.
 * The hash time is interpolated based on predefined block sizes and their corresponding times.
 */
public class SHA512StrategyAbstract extends AbstractDigestStrategy {

    private static final double[] BLOCK_SIZES = {16, 64, 256, 1024, 8192, 16384};
    private static final double[] TIMES = {
            0.0002376162715820919,
            0.0002398602094699209,
            0.00048395870477163926,
            0.0012201361509259613,
            0.008095286923952874,
            0.01595201633486473
    };

    /**
     * Constructs a `SHA512Strategy` and sets the name and key size.
     */
    public SHA512StrategyAbstract() {
        setName("SHA-512");
        setKeySize(512);
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
            throw new IllegalArgumentException("Block size out of interpolation range for SHA-512.");
        }
        return interpolate(createSplineFunction(BLOCK_SIZES, TIMES), inputSize, BLOCK_SIZES[0], BLOCK_SIZES[BLOCK_SIZES.length - 1]);
    }
}
