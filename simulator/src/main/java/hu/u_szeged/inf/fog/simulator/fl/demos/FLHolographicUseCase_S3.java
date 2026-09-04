package hu.u_szeged.inf.fog.simulator.fl.demos;

/**
 * SCENARIO S3 — Pacing strategies.
 *
 * Switches the orchestrator from cool-down pacing (baseline) to fixed-cadence
 * pacing: round {@code t+1} starts exactly {@code roundInterval} ticks after
 * round {@code t}, regardless of whether round {@code t} has finished
 * aggregation. This stresses clock-driven 6G control planes and exposes
 * round-overlap behaviour when training/upload runs longer than the heartbeat.
 *
 * All other knobs stay at baseline (minCompletionRate=0.75, timeoutRatio=0.80,
 * 50% sampling, 10% dropout, 5% in-transit loss) so the variant isolates the
 * effect of pacing.
 */
public class FLHolographicUseCase_S3 {

    public static void main(String[] args) throws Exception {
        FLHolographicUseCase.Config cfg = new FLHolographicUseCase.Config();
        cfg.label        = "HolographicUseCase_S3";
        cfg.tagPrefix    = "HolographicUseCase_S3";
        cfg.outputSuffix = "_S3";
        cfg.fixedCadence = true;   // start-to-start heartbeat (instead of cool-down)
        FLHolographicUseCase.run(cfg);
    }
}
