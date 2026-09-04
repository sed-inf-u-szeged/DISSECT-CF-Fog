package hu.u_szeged.inf.fog.simulator.fl.demos;

/**
 * SCENARIO S2 — Policies.
 *
 * Aggressive timeout (50% of interval) combined with a strict completion rate
 * (100% of expected participants must arrive before aggregation).
 */
public class FLHolographicUseCase_S2 {

    public static void main(String[] args) throws Exception {
        FLHolographicUseCase.Config cfg = new FLHolographicUseCase.Config();
        cfg.label             = "HolographicUseCase_S2";
        cfg.tagPrefix         = "HolographicUseCase_S2";
        cfg.outputSuffix      = "_S2";
        cfg.timeoutRatio      = 0.50;   // aggressive (30s of 60s interval)
        cfg.minCompletionRate = 1.0;    // strict: wait for 100% of participants
        FLHolographicUseCase.run(cfg);
    }
}
