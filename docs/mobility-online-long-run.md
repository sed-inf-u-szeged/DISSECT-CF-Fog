# Long online mobility retry verification

This step extends the existing retry probe so that later model versions can complete their own future evaluation blocks. It uses the official `NomadicMobilityStrategy` and `RandomWalkMobilityStrategy`; their implementations, starting positions, destination settings, speed settings and seed are unchanged.

## Scope and current integration status

The Java simulator sends each actual observation to a Python worker. The worker trains a fresh LSTM from the available labelled prefix, evaluates frozen predictions when their real targets arrive, and retries only after a quality rejection. Training blocks the bridge while the simulator stays at the same logical tick. Training wall-clock time is not charged as simulated compute work.

This is still a mobility integration probe. The shared Java load lifecycle controller is **not connected**. Model promotion, active-model selection, degradation fallback and prediction-driven connection control are **disabled**. A quality `PASS` means `QUALIFIED_SHADOW`, not activation. A completed verification run does not mean that the full lifecycle is implemented.

## Duration and compatibility

`duration_minutes` is an optional integer in the scenario configuration, between 10 and 1440. Its default remains 120 minutes, producing the original 121 observations. The sampling interval remains 60 seconds. The simulation limit retains the previous 120-minute allowance beyond the configured duration so data can drain.

The retry runner accepts `--long`, which copies each official scenario into an isolated result directory and sets its duration to 232 minutes. This produces 233 observations and 224 completed supervised windows. The original resource configurations are not edited. The existing offline pilot builder still requires 121 observations by default; long-run audits must explicitly supply the expected observation count.

```powershell
Set-Location -LiteralPath 'D:\Github\DISSECT-CF-Fog'
python predictor-ui/scripts/mobility/run_mobility_online_retry.py --long
```

Without `--long`, the runner retains the original 121-observation retry check.

## Causal timeline

Observation numbers below are one-based. Each input contains eight valid historical motion samples; the first motion sample needs an earlier position. Its target is the actual position one sampling interval later. The 32/96/160 counts refer to labelled training windows, not the length of one model input.

| Actual observation | Available labelled windows | Action when previous candidates were rejected |
| --- | ---: | --- |
| 41 | 32 | Fit `v0001`; freeze its model and scaler; issue its first shadow prediction. |
| 105 | 96 | Score the 64th actual target for `v0001`; decide quality; if rejected, fit `v0002` on the 96-window prefix. |
| 169 | 160 | Score the 64th actual target for `v0002`; decide quality; if rejected, fit `v0003` on the 160-window prefix. |
| 233, final | 224 | Score the 64th actual target for `v0003`; record the 224-window request as deferred with reason `STREAM_COMPLETE`; do not fit a model that has no future observations to evaluate. |

A later training prefix may include data that previously evaluated an older version. It is available at the new training cutoff. The new version must then collect its own future evaluation block; it cannot reuse its predecessor's 64 evaluations.

If a version passes before the final observation, it remains frozen in qualified shadow mode. Later requests are deferred with reason `QUALITY_PASSED_PROMOTION_PENDING`. Its quality decision stays based on its first 64 mature origins even if further shadow errors are recorded. The runner never forces a rejection merely to obtain more versions.

At the final tick, observation and evaluation precede the quality decision and request deferral. There is no new forecast. Ending one observation early cannot report successful completion or invent the missing evaluation.

## Quality policy

The existing `RELATIVE_BASELINE_V1` policy is unchanged. Within the same version's first 64 mature origins, it chooses the better baseline (`HoldPosition` or `ConstantVelocity`) and requires:

- LSTM RMSE strictly below the selected baseline RMSE.
- The configured relative RMSE improvement, currently 0.0.
- Mean positional error no worse than the selected baseline.

This policy has no absolute accuracy threshold. A zero-error stationary baseline cannot be beaten under the strict RMSE rule. This rejection is expected, not a failed execution. The short nomadic fixture reaches its last destination early and then stays stationary; extending its duration does not turn it into a representative moving-trajectory benchmark.

## Outputs and verification

Long results are written under `simulator/sim_res/mobility-online-long-run/<session>/online-retry.json`. Each run retains the actual observations, immutable training requests, version-specific model/scaler bundles, prediction and target-time evaluation records, quality decisions and ordered lifecycle events. The terminal request snapshot remains available even when no corresponding model is fitted.

Successful execution ends with `MOBILITY_ONLINE_LONG_RUN_OK`. The final report continues to state:

```text
promotion=DISABLED connection_control=DISABLED controller_integration=NOT_CONNECTED
```

The independent audits reconstruct features and labels from observations, verify each training prefix and cutoff, replay every saved model, recompute errors and quality decisions, and check event ordering, exact version identity, immutable artifacts and final byte conservation.

The long probe has 192 post-fit prediction origins per strategy and three methods per origin, giving 576 evaluation rows per strategy. Two strategies therefore produce 384 origins and 1152 evaluation rows. A version's 64 origins correspond to 192 method rows; these must not be counted as 192 independent evaluations for that version.

## Local validation evidence

Validated on 2026-11-08 with Java 17, Python 3.12.14, TensorFlow CPU 2.21.0 and Keras 3.15.1:

- All 103 mobility Python tests passed, including long-stream rejection, early qualification, terminal deferral, missing targets, third-fit failure and tampered-event/decision rejection.
- All 28 focused Java tests passed, including original reconnection regressions, official strategy semantics, duration round-trips and actual 233-observation simulation/accounting checks.
- The unchanged short runner completed `20261108T054743Z-c6531879c61f`: each strategy fitted 32 and 96 windows, with 64 and 16 future evaluations respectively. Saved models, scalers and replay audits passed.
- Real TensorFlow long run `20261108T054651Z-6cce6dac5d95` completed both strategies, with three trained versions each, 64 future evaluations per version, zero pending forecasts, frozen bundles and successful saved-model replay.

| Strategy | 32-window version | 96-window version | 160-window version | Final state |
| --- | --- | --- | --- | --- |
| Nomadic | REJECT | REJECT | REJECT | RETRAIN_PENDING; terminal request deferred |
| Random walk | REJECT | REJECT | PASS | QUALIFIED_SHADOW; active=false |

For the random-walk third version's own 64-origin block, HoldPosition RMSE was 242.309 m and LSTM RMSE was 241.035 m (about 0.53% improvement). Mean errors were 232.520 m and 229.664 m. This is a small relative improvement in one development run. It does not establish an absolute accuracy standard or show that LSTM is the best model. Versions evaluated on different time blocks are not directly comparable as a controlled training-size experiment. The user's Windows run must be checked independently.

## Remaining workflow work

Connect mobility to the shared lifecycle manager, verify qualification-to-activation and degradation fallback, and then run TCN and the agreed additional models through the same data and lifecycle interface. Fair cross-model comparisons must use identical observation streams and evaluation origins. Keep repository documentation in English and preserve experiment outputs outside source control.
