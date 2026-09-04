package hu.u_szeged.inf.fog.simulator.fl.demos;

/**
 * SCENARIO S0 — Baseline.
 *
 * Equivalent to {@link FLHolographicUseCase} with default settings; provided as a
 * separate entry point so the S0..S4 scenario family forms a complete sweep.
 */
public class FLHolographicUseCase_S0 {

    public static void main(String[] args) throws Exception {
        FLHolographicUseCase.Config cfg = new FLHolographicUseCase.Config();
        cfg.label        = "HolographicUseCase_S0";
        cfg.tagPrefix    = "HolographicUseCaseS0";   // existing CSV/PNG convention: no underscore before S0
        cfg.outputSuffix = "";                        // existing convention: same dir prefix as base
        FLHolographicUseCase.run(cfg);
    }
}
