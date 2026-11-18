# Mobility online lifecycle: first fit and frozen shadow evaluation

## Scope

This step connects the causal online sample buffer to real TensorFlow training.
During each independent simulator run, an LSTM is trained once on the first 32
completed windows, then evaluated against subsequently observed positions.
No offline model or held-out pilot test data is loaded.

This is the first-fit portion of the planned online lifecycle. Quality-gated
promotion, growth retraining, fallback, and additional model families remain
pending. A successful run verifies the mechanism; it does not approve a model.

The implementation reuses the existing feature builder, LSTM architecture,
scaling functions, Java/Python observation bridge, and delayed evaluator. It
aligns with the load task's past-only synchronous training principle, but does
not yet call the load task's `OnlineModelLifecycleManager` or reuse its complete
controller. A common lifecycle integration is still required.

## Live event order

1. Java sends the current simulator observation and waits for a reply.
2. The buffer freezes past-only inputs and completes the previous window's label
   only when its actual target observation arrives.
3. On the 41st observation, 32 labelled windows are available. The worker saves
   that immutable request, fits a scaler only on those 32 windows, and trains
   a new LSTM from seeded initial weights.
4. The worker saves the model and scaler and verifies their reload predictions.
5. At the same simulation tick, the frozen model predicts the next observation.
6. Subsequent observations first settle the previous forecast, then issue the
   next forecast. No score is recorded before the target observation arrives.
7. A request for 96 windows is recorded but deferred in this first-fit-only step.
8. The final observation settles the last forecast without creating another one.

Training takes wall-clock time while Java's simulation clock waits. This step
does not model training as CPU/network work within the simulated infrastructure.
The bridge reply timeout is 300 seconds for this runner; other callers retain
the existing 30-second default. The trainer checks a 120-second elapsed limit
between batches, and the owning runner enforces a 300-second limit per JVM run.

## Distinct window sizes

| Quantity | Current mobility meaning |
| --- | --- |
| 8 historical points | Input sequence for one prediction; six features per point |
| 1 future point | Position 60 seconds after the prediction origin |
| 32 completed windows | Minimum training set for the first fit |
| 64 additional windows | Next request grows from 32 to 96; execution is deferred here |
| 64 evaluated origins | Minimum count being exercised, not a quality approval rule |
| 80 evaluated origins | Available after the first fit in this 121-observation fixture |
| 4096 completed windows | Buffer prefix cap; different units from the load task's raw-point cap |

The load task's 192 input points and 64 output points are separate modelling
parameters. They are not equivalent to the 32 training windows or to the LSTM's
32 hidden units. This step preserves the already-verified 8-to-1 mobility task.

## Training protocol

- CPU TensorFlow/Keras; seed 42; deterministic operations enabled.
- Input `(8, 6)`; `LSTM(32) -> Dense(16, relu) -> Dense(2)`, 5,554 parameters.
- Adam, learning rate 0.001, gradient clip norm 1.0, mean squared error.
- Exactly 20 epochs, batch size 32, using only the first completed request.
- No validation-driven epoch selection and no future labels used for fitting.
- Input scaling and shared target scale are fitted once, saved, then frozen.
- The saved model is reloaded before shadow prediction starts.

Twenty epochs are a fixed integration setting, not an optimized model choice.
Metrics obtained by this runner must not be presented as an independent test-set
result or as evidence that online LSTM is the best model.

The request snapshot retains `training=NOT_STARTED`, `scaler=NOT_FITTED`, and
`mode=REQUEST_ONLY`: these describe the moment the immutable request was emitted.
Actual execution status is in `models/v0001/training.json`, `first-fit.json`, and
`online-worker.json`. Request files are never rewritten after fitting.

## Official strategy fixtures

The runner uses the previously verified `official-nomadic.properties` and
`official-random-walk.properties` without changing either movement algorithm.
Each run has its own model, scaler, buffer, and output directory.

- Nomadic movement finishes its short route before the first fit. All 80
  post-fit targets in this fixture are stationary. This is a useful lifecycle
  check, not a meaningful moving-trajectory model comparison.
- Random walk draws fresh directions and speeds under the official strategy.
  A recurrent model cannot infer independent future random draws. Static and
  constant-velocity baselines must be retained in the comparison.

Both fixtures use one device, two fog nodes, 60-second sampling, and 121
observations. These are verification fixture settings, not simulator limits.

## Run and expected result

From the repository root, with the existing `dissect-fog` environment active:

```powershell
python -m unittest discover -s predictor-ui/scripts/mobility -p 'test_mobility*.py' -v
python predictor-ui/scripts/mobility/run_mobility_online_first_fit.py
```

The second command needs the existing JDK, compiled classes, dependency JAR,
NumPy, TensorFlow, and Keras. It compiles the reviewed Java entry point into a
new isolated runtime and runs both official strategy fixtures. Previous outputs
and models are not overwritten. It opens no UI and performs no Git operations.

Per strategy, expect:

```text
observations=121 completed_windows=112 request_sizes=32,96
training_windows=32 trained_versions=1
shadow_origins=80 evaluation_rows=240 pending_forecasts=0
training=COMPLETE scaler=FROZEN lifecycle_state=SHADOW active=false
quality_gate=NOT_APPLIED
```

After both strategies pass, expect `MOBILITY_ONLINE_FIRST_FIT_OK`. A separate
version `v0001` is trained in each run; this is not two versions in one lifecycle.
The original request-only `run_mobility_online_buffer.py` remains available.

## Evidence and verification

The manifest is `simulator/sim_res/mobility-online-first-fit/<session>/online-first-fit.json`.
Each run includes:

- `training-request-0001.json`: the 32-window prefix actually fitted.
- `training-request-0002.json`: the deferred 96-window request.
- `models/v0001/model.keras`, `preprocessing.json`, `history.csv`, `training.json`.
- `first_fit_events.jsonl`: observations, training, predictions, and delayed scores.
- `first_fit_predictions.csv` and `first_fit_evaluations.csv`: all three methods
  scored on exactly the same 80 prediction origins.
- `first-fit.json`: state, counters, and mean/median/P90/RMSE position errors in metres.
- Existing buffer journals, simulator sample exports, accounting checks, and
  same-tick bridge receipts.

The auditor reconstructs all windows from actual simulator observations, checks
request cutoffs and event order, recomputes scaling from the first 32 windows,
reloads and replays model predictions, recomputes all errors, and verifies
recursive artifact hashes. No audit data is fed back into the live training run.

Python unit tests use an explicitly named model double for orchestration and
failure checks. The integration runner always uses actual TensorFlow training.
Verification before packaging: 67 Python tests and 24 Java tests passed; both
official strategies completed real first-fit/shadow runs and artifact replay.

The bridge continues returning `HoldPosition` receipts, with connection control
disabled. LSTM predictions are recorded and scored in shadow only. The full
model-adoption policy and predictive handover control are separate concerns.
