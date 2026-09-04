package hu.u_szeged.inf.fog.simulator.fl.demos;

/**
 * SCENARIO S1 — Scalability.
 *
 * Replicates the 10-device fleet 5 times (50 devices total), doubles the model size,
 * lowers the sampling fraction, and uses a cheap constant-fill initialization to keep
 * model bootstrap fast under the larger dimension.
 */
public class FLHolographicUseCase_S1 {

    public static void main(String[] args) throws Exception {
        FLHolographicUseCase.Config cfg = new FLHolographicUseCase.Config();
        cfg.label                   = "HolographicUseCase_S1";
        cfg.tagPrefix               = "HolographicUseCaseS1_Scalability";
        cfg.outputSuffix            = "_S1";
        cfg.rounds                  = 10;
        cfg.modelDimensionW         = 2_000_000;
        cfg.samplingFraction        = 0.2;
        cfg.deviceReplicationFactor = 5;             // 5 * 10 = 50 devices
        cfg.initScheme              = FLHolographicUseCase.InitScheme.FILL_0_001;
        FLHolographicUseCase.run(cfg);
    }
}
