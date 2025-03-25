package hu.u_szeged.inf.fog.simulator.iot.distributed_ledger.digest_strategy;


import static hu.u_szeged.inf.fog.simulator.iot.distributed_ledger.utils.InterpolationUtils.createSplineFunction;
import static hu.u_szeged.inf.fog.simulator.iot.distributed_ledger.utils.InterpolationUtils.interpolate;

public class SHA512Strategy extends DigestStrategy {

    private static final double[] BLOCK_SIZES = {16, 64, 256, 1024, 8192, 16384};
    private static final double[] TIMES = {
            0.0002376162715820919,
            0.0002398602094699209,
            0.00048395870477163926,
            0.0012201361509259613,
            0.008095286923952874,
            0.01595201633486473
    };

    public SHA512Strategy() {
        setName("SHA-512");
        setKeySize(512);
    }

    @Override
    public double hash(long inputSize) {
        if (inputSize < 16 || inputSize > 16384) {
            throw new IllegalArgumentException("Block size out of interpolation range for SHA-512.");
        }
        return interpolate(createSplineFunction(BLOCK_SIZES, TIMES), inputSize, BLOCK_SIZES[0], BLOCK_SIZES[BLOCK_SIZES.length - 1]);
    }
}
