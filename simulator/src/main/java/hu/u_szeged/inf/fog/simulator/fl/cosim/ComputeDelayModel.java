package hu.u_szeged.inf.fog.simulator.fl.cosim;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The analytic local-training delay of the Pass-3 replay (§8.4 Track B
 * calibration).
 *
 * <h2>What it replaces</h2>
 * Without a calibration the replay took the harness's measured wall-clock
 * {@code train_time_ms} and used it directly as simulated ticks. That has two
 * defects: the simulated duration is then a property of the <i>host CPU</i>
 * rather than of the modelled fog device, so the device tiers have no effect on
 * round duration or barrier idle time at all; and the quantity being validated
 * is not the quantity the campaign uses.
 *
 * <h2>The model</h2>
 * Local training cost is dominated by the amount of local data, so the delay is
 * affine in the node's shard size and scaled to the device tier:
 *
 * <pre>
 *   delay_ticks(tier, nSamples) = round( (intercept_ms + msPerSample · nSamples) · deviceScale(tier) )
 * </pre>
 *
 * {@code intercept_ms} and {@code msPerSample} are fitted per tier from Track-B
 * measurements (least squares, warmup rounds discarded) and validated on
 * held-out rounds — this is criterion <i>F4</i>. {@code deviceScale} maps the
 * calibration host onto the simulated device tier; it is {@code 1.0} for the
 * tier the measurements were taken on and {@code >1} for a slower tier.
 *
 * <p>A constant-per-tier delay (the earlier form) ignores {@code nSamples},
 * which under a skewed Dirichlet partition is the largest single driver of
 * training time — that is precisely why it failed its held-out tolerance.</p>
 */
public final class ComputeDelayModel {

    /** Per-tier affine fit plus the host→device scale. */
    public static final class TierFit {
        public final double interceptMs;
        public final double msPerSample;
        public final double deviceScale;

        /**
         * @param interceptMs fixed per-round cost (ms).
         * @param msPerSample marginal cost per local sample (ms).
         * @param deviceScale host→device multiplier ({@code > 0}).
         */
        public TierFit(double interceptMs, double msPerSample, double deviceScale) {
            if (deviceScale <= 0.0) {
                throw new IllegalArgumentException("deviceScale must be > 0: " + deviceScale);
            }
            this.interceptMs = interceptMs;
            this.msPerSample = msPerSample;
            this.deviceScale = deviceScale;
        }

        /** Predicted delay in ticks for a shard of {@code nSamples}. */
        public long ticks(int nSamples) {
            double ms = (interceptMs + msPerSample * nSamples) * deviceScale;
            return Math.max(1L, Math.round(ms));
        }
    }

    private final Map<String, TierFit> byTier;
    private final String instrument;

    private ComputeDelayModel(Map<String, TierFit> byTier, String instrument) {
        this.byTier = byTier;
        this.instrument = instrument;
    }

    /**
     * Loads a model from the {@code calibration.json} written by
     * {@code analysis/calibration.py}.
     *
     * @param path the calibration file.
     * @return the parsed model.
     * @throws IOException if the file is unreadable or has no tiers.
     */
    public static ComputeDelayModel load(Path path) throws IOException {
        JsonNode root = new ObjectMapper().readTree(path.toFile());
        JsonNode tiers = root.path("tiers");
        if (tiers.isMissingNode() || !tiers.fieldNames().hasNext()) {
            throw new IOException("calibration.json has no tiers: " + path);
        }
        Map<String, TierFit> map = new LinkedHashMap<>();
        tiers.fieldNames().forEachRemaining(tier -> {
            JsonNode t = tiers.get(tier);
            map.put(tier, new TierFit(
                    t.path("interceptMs").asDouble(0.0),
                    t.path("msPerSample").asDouble(0.0),
                    t.path("deviceScale").asDouble(1.0)));
        });
        return new ComputeDelayModel(map, root.path("instrument").asText("UNKNOWN"));
    }

    /**
     * An identity model: the delay is the measured host time, unscaled. This is
     * the pre-calibration behaviour and is kept only so a run without a
     * calibration file still executes; it must not be presented as a device
     * model, because it has none.
     *
     * @return an identity model.
     */
    public static ComputeDelayModel identity() {
        return new ComputeDelayModel(new LinkedHashMap<>(), "IDENTITY");
    }

    /** Whether a per-tier fit is available (false ⇒ {@link #identity()}). */
    public boolean isCalibrated() {
        return !byTier.isEmpty();
    }

    /** The instrument label recorded by the calibration ({@code PLACEHOLDER} etc.). */
    public String instrument() {
        return instrument;
    }

    /**
     * Simulated local-training delay in ticks.
     *
     * @param tier the node's device tier (e.g. {@code near_edge}).
     * @param nSamples the node's local shard size.
     * @param measuredTrainTimeMs the harness's measured wall-clock time, used
     *                            only by the identity fallback.
     * @return delay in ticks ({@code >= 1}).
     */
    public long ticks(String tier, int nSamples, double measuredTrainTimeMs) {
        TierFit fit = byTier.get(tier);
        if (fit == null) {
            // Unknown tier: fall back to any single fit if there is exactly one,
            // else to the measured time.
            if (byTier.size() == 1) {
                return byTier.values().iterator().next().ticks(nSamples);
            }
            return Math.max(1L, (long) Math.ceil(measuredTrainTimeMs));
        }
        return fit.ticks(nSamples);
    }
}
