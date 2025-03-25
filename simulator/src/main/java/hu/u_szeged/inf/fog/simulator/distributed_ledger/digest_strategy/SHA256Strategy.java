package hu.u_szeged.inf.fog.simulator.distributed_ledger.digest_strategy;

import static hu.u_szeged.inf.fog.simulator.distributed_ledger.utils.InterpolationUtils.createSplineFunction;
import static hu.u_szeged.inf.fog.simulator.distributed_ledger.utils.InterpolationUtils.interpolate;

public class SHA256Strategy extends DigestStrategy {

    private static final double[] BLOCK_SIZES = {16, 64, 256, 1024, 8192, 16384};
    private static final double[] TIMES = {
            0.00010125457455511018,
            0.00013283740701381508,
            0.00022078863344788827,
            0.0005724139957511617,
            0.0038499297387822673,
            0.007598149090881461
    };

    public SHA256Strategy() {
        setName("SHA-256");
        setKeySize(256);
    }

    @Override
    public double hash(long inputSize) {
        if (inputSize < 16 || inputSize > 16384) {
            throw new IllegalArgumentException("Block size out of interpolation range for SHA-256.");
        }
        return interpolate(createSplineFunction(BLOCK_SIZES, TIMES), inputSize, BLOCK_SIZES[0], BLOCK_SIZES[BLOCK_SIZES.length - 1]);
    }
}
