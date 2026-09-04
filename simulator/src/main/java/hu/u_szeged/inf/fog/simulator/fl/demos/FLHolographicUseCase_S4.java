package hu.u_szeged.inf.fog.simulator.fl.demos;

/**
 * SCENARIO S4 — Privacy & Compression (scaffolding).
 *
 * NOTE: The original variant under this name only changed the labels and output
 * paths — the privacy/compression knobs ({@code secureAgg}, {@code dlCompressionFactor},
 * {@code ulCompressionFactor}, {@code serverDpNoiseStd}, client DP) were left at
 * baseline defaults. To exercise the intended privacy/compression sweep, set the
 * relevant {@code cfg.*} fields below before calling {@code run(cfg)}.
 */
public class FLHolographicUseCase_S4 {

    public static void main(String[] args) throws Exception {
        FLHolographicUseCase.Config cfg = new FLHolographicUseCase.Config();
        cfg.label        = "HolographicUseCase_S4";
        cfg.tagPrefix    = "HolographicUseCase_S4";
        cfg.outputSuffix = "_S4";

        // Wire-in points for the intended scenario — currently no-ops to preserve
        // bit-for-bit parity with the existing _S4 outputs:
           cfg.secureAgg                 = true;
           cfg.secureExtraBytesPerClient = 1024L;
           cfg.dlCompressionFactor       = 0.2;
           cfg.ulCompressionFactor       = 0.2;
           cfg.serverDpNoiseStd          = 0.01;
           cfg.clientClipNorm            = 1.0;
           cfg.clientDpSigma             = 0.01;
        FLHolographicUseCase.run(cfg);
    }
}
