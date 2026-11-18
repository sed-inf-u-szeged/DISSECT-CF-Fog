# W07b: load model migration and early Mobility baselines

Implemented 2026-11-18. This is an engineering/UI update; Windows interactive
acceptance and controlled prediction-quality experiments are separate.

## Requested behavior

| Task | Implementation |
| --- | --- |
| Load baselines | ARIMA(3,0,0) and LR, using the existing standalone predictor classes. |
| Trained load families | LSTM, TCN, GRU, PatchTST; original network builders and existing optimized trainer. |
| Default/fallback load service | LR; use an available ARIMA vector if LR has no usable vector for the target. |
| Persistence | Not produced by new joint runs. Old result files remain readable under their real model name. |
| SVR/RF/HW | Not migrated. Standalone code remains unchanged. |
| Mobility device labels | D0/D1/etc. use observed-path yellow `#ffd174`, matching the dots. |
| Mobility baselines | Independent causal forecasts from the earliest sufficient observations. |
| Mobility readiness | Per-device collection, actual training status and 64-target evaluation progress. |

Model implementations are reused; weights trained on another scenario are not
silently transferred. Each new run/node/version owns its trained model and data.
Queue data means received data waiting for compute, measured in bytes; this is
not CPU utilization. No resource/capacity/connection/offloading controller is
introduced by this change.

## Load timeline and evaluation

ARIMA/LR fit the latest 192 observed minute samples and issue a 64-target vector.
They first issue at sample index 191 (191 minutes on the common node grid),
then at 64-sample intervals and at any intervening neural forecast origin so
same-origin comparisons are available. LR has no separate neural pretraining
stage; it still needs input history. Before this warm-up there is no load
forecast, rather than a manufactured baseline value.

LSTM/TCN/GRU/PatchTST use 192 input points, 64 targets, and initially 32 training
windows: the first fit needs 287 raw samples (index 286). The existing 64-real-
target qualification, retry schedule, window growth and degradation/fallback
remain in effect. Candidate profile requires 80% within the existing tolerance
(20% of actual with a 0.1-byte floor). Baselines are reference/service methods;
their scores are not silently treated as neural qualification.

ARIMA fits that report nonconvergence are recorded as unusable, with no fake
replacement ARIMA curve. LR/ARIMA negative queue predictions are clipped to zero;
raw outputs and clipping/convergence status are retained in the fit log. If no
usable baseline is available after warm-up, execution fails explicitly instead
of renaming another method or inventing forecasts.

Overlapping baseline blocks use the latest already-issued forecast for each
visible target. All original blocks remain in exports. Model rows expose
same-origin ARIMA/LR scores; cumulative diagnostics use each method's own
matured targets and explicitly say their sample sets can differ.

## Mobility timing: no delayed baselines and no invented zero errors

For observed positions P1, P2, P3, one simulated minute apart:

| Observation | New forecasts | Newly measurable errors |
| --- | --- | --- |
| P1 | HoldPosition predicts P2 = P1 | None: P2 has not been observed. |
| P2 | HoldPosition and ConstantVelocity predict P3 | The first HoldPosition error. |
| P3 | Both baselines predict P4 | HoldPosition and the first ConstantVelocity error. |

ConstantVelocity uses the last measured displacement, matching the existing
baseline formula. The initial sample has no valid measured speed/heading, so
it cannot produce a genuine velocity forecast. No future positions or invented
initial velocity are used. Baseline forecasts also work while disconnected.

The learned Mobility pipeline retains its eight-history-point representation
and initial 32 training windows. Its current first fit needs 41 observations;
the readiness card shows observed count and ready training-window count.
During a real live fit the progress indicator is indeterminate (no made-up
epoch percentage). LSTM then shows real targets out of 64; its candidate error
curve starts as soon as predictions mature, without waiting to be promoted.
Additional Mobility comparison models keep their existing shadow-only roles.

The error plot uses early baseline records. A lone first error is drawn as a
point, so it is visible before a second point can form a line. The comparison
table continues to use the candidate's matched target set; early baseline-only
observations do not improve or worsen that paired comparison. Selecting a
version limits the baseline curves to that version's target set.

New files per device: `position_baseline_predictions.csv` and
`position_baseline_evaluations.csv`. Readers check sample/run/device identity,
one-minute timing, causal formulas, every matured target and exact distance.
Legacy results recover earlier errors from actual recorded coverage forecasts;
they do not gain forecasts that were never recorded. A new run is required to
see the very first HoldPosition forecast and all newly migrated load models.

## Verification

- Angular production build passed; existing bundle/style budget warnings remain.
- 86 component/replay logic cases passed, including first-target errors,
  rewind, version filters, fair comparison counts, live training status and
  six-method load display.
- 31 result-reader/IPC cases passed, including new/old result compatibility,
  missing forecast files, altered target errors and model identity checks.
- 11 focused Java cases passed: baseline start/target timing, sample gaps,
  load qualification/rejection/retry/degradation and ARIMA/LR routing.
- Six Python migration cases and 15 existing load/config cases passed.
- Real 480-minute, nine-device, ten-node periodic-demand run:
  `mobility-multi-4716286472209986431`, seed 42, **two load epochs**.
  77 neural versions, 86 replayed neural vectors, 120 replayed baseline fit
  attempts; original standalone ARIMA/LR parity and saved neural-model parity
  passed. Six nonconverged ARIMA fits were correctly skipped. 4,950 live/saved
  chart-state comparisons passed. This run predates the early Mobility recorder
  added later in this update; it verifies the load migration, not early errors.
- Separate real 104-minute/two-device/ten-node run with Mobility LSTM and TCN:
  `mobility-multi-17516816124554093630` (final source snapshot). Each device has 104 HoldPosition and
  103 ConstantVelocity forecasts/errors; all 414 live/export pairs and two
  pre-fit training events agree. Existing lifecycle/target/accounting audits
  and the strict reader passed. Load models were disabled for this short run.

The configured normal load training remains **40 epochs**. The two-epoch run
validates model migration and data flow, not comparative predictive quality.
Java validation used the available JDK 21 targeting Java 11 plus the project's
dependency artifact; a full Windows Maven/UI launch is not claimed here.

## Next acceptance and research steps

1. Install W07b after W07a; rebuild and restart the UI. Reopen an old result to
   check compatibility and yellow labels.
2. Run 104 minutes with two devices to inspect the earliest Mobility frames,
   first single-point error, collection/training/evaluation and rewinding.
3. Run the new `w07b-city-load-models.json` configuration: periodic bursts,
   nine devices, 480 minutes, all four neural load models, normal 40 epochs.
4. Continue W07 controlled repeated-seed experiments, reporting matched-target
   quality, forecast availability, training/inference time and memory before
   tuning. W07.1 mobility-to-load feature fusion remains next, then W08 energy
   and feasible actions, then W09 actual load/capacity/offloading controllers.
