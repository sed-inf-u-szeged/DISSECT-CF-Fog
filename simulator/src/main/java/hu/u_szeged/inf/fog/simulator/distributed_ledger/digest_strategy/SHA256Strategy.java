package hu.u_szeged.inf.fog.simulator.distributed_ledger.digest_strategy;

import static hu.u_szeged.inf.fog.simulator.distributed_ledger.utils.InterpolationUtils.createSplineFunction;
import static hu.u_szeged.inf.fog.simulator.distributed_ledger.utils.InterpolationUtils.interpolate;

/**
 * The SHA256Strategy class implements the DigestStrategy interface.
 * It provides a method to compute the hash time for a given input size using the SHA-256 algorithm.
 * The hash time is interpolated based on predefined block sizes and their corresponding times.
 */
public class SHA256Strategy extends AbstractDigestStrategy {

    private static final double[] BLOCK_SIZES = {16, 64, 256, 1024, 8192, 16384};
    private static final double[] TIMES = {
            0.00010125457455511018,
            0.00013283740701381508,
            0.00022078863344788827,
            0.0005724139957511617,
            0.0038499297387822673,
            0.007598149090881461
    };


    /**
     * Default reference throughput measured on a desktop CPU.
     * The benchmark processed 7,068,143 SHA-256 hashes
     * of 80-byte inputs in 3 seconds using a single thread.
     */
    private static final double DEFAULT_REFERENCE_HASHES_PER_SECOND =
            7_068_143.0 / 3.0;

    private final double referenceHashesPerSecond;

    /**
     * Creates the SHA-256 strategy using the configured reference throughput.
     * The JVM property overrides the default benchmark value when provided.
     */
    public SHA256Strategy() {
        this(Double.parseDouble(System.getProperty(
                "dissect.sha256.hashesPerSecond",
                Double.toString(DEFAULT_REFERENCE_HASHES_PER_SECOND)
        )));
    }

    /**
     * Creates the SHA-256 strategy with a custom reference throughput.
     *
     * @param referenceHashesPerSecond the measured single-threaded
     *                                 SHA-256 throughput in hashes per second
     *                                 for 80-byte inputs
     * @throws IllegalArgumentException if the throughput is invalid
     */
    public SHA256Strategy(double referenceHashesPerSecond) {
        if (!Double.isFinite(referenceHashesPerSecond)
                || referenceHashesPerSecond <= 0.0) {
            throw new IllegalArgumentException(
                    "SHA-256 reference hash rate must be positive and finite."
            );
        }

        this.referenceHashesPerSecond = referenceHashesPerSecond;
        setName("SHA-256");
        setKeySize(256);
    }


    /**
     * Estimates the normalized computational cost of SHA-256 hashing.
     * Preserves the relative benchmark costs for different input sizes
     * and calibrates them using the configured single-threaded
     * reference throughput for 80-byte inputs.
     *
     * @param inputSize the input size in bytes
     * @return the estimated computational cost in normalized work units
     * @throws IllegalArgumentException if the input size is out of range
     */
    @Override
    public double hash(long inputSize) {
        if (inputSize < 16 || inputSize > 16384) {
            throw new IllegalArgumentException(
                    "Block size out of interpolation range for SHA-256."
            );
        }

        var spline = createSplineFunction(BLOCK_SIZES, TIMES);

        double benchmarkCost = interpolate(
                spline, inputSize,
                BLOCK_SIZES[0], BLOCK_SIZES[BLOCK_SIZES.length - 1]
        );

        double benchmarkCost80 = interpolate(
                spline, 80L,
                BLOCK_SIZES[0], BLOCK_SIZES[BLOCK_SIZES.length - 1]
        );

        double referenceTime80Seconds =
                1.0 / referenceHashesPerSecond;

        return (benchmarkCost / benchmarkCost80)
                * referenceTime80Seconds;
    }


}