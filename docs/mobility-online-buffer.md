# Mobility online sample buffer and training requests

This development step connects the official Nomadic and RandomWalk simulations
to a causal sample buffer. It generates training requests during simulation, but
does not execute them, fit a scaler, load an offline model, promote a model, or
change connection decisions. The full online model lifecycle is not complete.

## Data and timing

The existing Java bridge sends only the current observation to a persistent
Python worker. Java waits for the reply at the same simulation tick. The worker
does not read the simulator's output CSV or future route points. Post-run audits
read those files only after the simulation has exited.

The feature contract is unchanged: eight history points with six features per
point, predicting a two-dimensional displacement 60 simulated seconds ahead.
Features are relative east/north offsets, observed speed, heading sine/cosine,
and the heading-valid mask. The fixed projection reference is 47.73, 18.27.
Speed is endpoint displacement over elapsed time, not instantaneous speed.
Run/device identity, timestamps, and node identity are metadata, not model inputs.

The first observation has no preceding motion interval. It cannot be part of a
valid input window. The ninth observation creates the first input, and the tenth
observation supplies its real target. A completed sample therefore contains an
input created at an earlier tick and a label available at the current tick.

For each accepted observation, the journal records this order:

1. Accept the actual observation.
2. Complete the pending sample whose target time matches this observation.
3. Publish a training request if the completed-sample threshold is reached.
4. Create the next immutable input and baseline forecasts, unless this is the
   final observation.

Duplicate timestamps, gaps, mixed run/device identities, non-finite positions or
motion, and inconsistent derived speed/heading are rejected without advancing
buffer state. Valid stationary and disconnected observations remain usable.
The final real observation settles the last pending label without creating a
new input. An aborted stream cannot report successful completion.

## Request policy

| Setting | Default | Unit |
| --- | ---: | --- |
| History length | 8 | Observations per input |
| Forecast horizon | 60 | Simulated seconds |
| First request | 32 | Completed labelled windows |
| Growth | 64 | Additional completed labelled windows |
| Retained training prefix cap | 4096 | Completed labelled windows |

The request sizes are 32, 96, 160, and so on, capped at 4096. The last increment
may be smaller when it reaches the cap. No further requests are emitted after
the cap, and retained training data remains bounded. The cap counts completed
windows; it is not the load workflow's raw-observation cap.

With continuous 60-second observations and the initial missing-motion row:

| Request size | Observations needed, including the initial observation |
| ---: | ---: |
| 32 | 41 |
| 96 | 105 |
| 160 | 169 |

Eight is the length of one input sequence, not the number of training samples.
The load workflow's 192 input points and 64 output points describe its own model
shape. This step adopts the 32-then-plus-64 growth rule for completed mobility
samples without changing the agreed eight-point, one-step mobility task.

Each request stores an immutable snapshot of the completed training prefix and
its time cutoff. Scaler fitting and model training are explicitly marked
`NOT_FITTED` and `NOT_STARTED`. Requests are a readiness probe, not an active
training controller. In the next step, the controller must decide whether to
execute a request after considering its current training/shadow/active state.

## Run from the repository root

Use the existing Python environment with NumPy and a JDK. No new dependency is
required. The previous official-strategy setup and compiled simulator classes
must be present.

```text
python predictor-ui/scripts/mobility/test_mobility_online_buffer.py
python predictor-ui/scripts/mobility/run_mobility_online_buffer.py
```

The runner starts two fresh JVM simulations, one per official strategy. It
copies the Java runtime and Python sources into a fresh result directory,
records hashes, and saves simulation logs. It verifies 121 observations, 112
completed windows, request sizes 32 and 96, and zero pending labels per run.
The third request size, 160, is covered by the longer unit-test stream.

It checks exact parity with the independent offline window builder, reconstructs
request snapshots from the actual observed prefix, verifies event order and
same-tick Java receipts, and checks simulator byte conservation. HoldPosition
and ConstantVelocity forecasts are logged; the bridge receives HoldPosition.
No trained model is used and no model quality result is claimed.

Expected final output:

```text
strategies=2 completed_windows=224 training_requests=4 training=NOT_STARTED scaler=NOT_FITTED
MOBILITY_ONLINE_BUFFER_OK
```

## Results

Results are written under `simulator/sim_res/mobility-online-buffer/<session>/`.

| File | Purpose |
| --- | --- |
| `online-buffer.json` | Session status, case outputs, checks and artifact hashes |
| `source-sha256.json`, `runtime-sha256.json` | Frozen source/runtime provenance |
| `training-policy.json` | Request counts for one simulation |
| `online-worker.json` | Worker status and final counters |
| `online_observations.csv` | Actual samples received from Java |
| `online_inputs.jsonl` | Inputs frozen at their origin times |
| `online_samples.jsonl` | Labelled samples completed at their target times |
| `online_training_requests.jsonl` | Request sizes, cutoffs and snapshot hashes |
| `training-request-0001.json`, etc. | Raw training prefixes, not trained models |
| `online_events.jsonl` | Ordered observation/label/request/input events |
| `online_predictions.csv` | Two baseline forecasts per valid origin |
| `shadow_receipts.csv` | Existing Java bridge receipts for HoldPosition |

The existing bridge passes `--training` and uses `MOBILITY_SHADOW_MODEL`. For
this worker only, that argument is an alias for `--policy` and contains the path
to `training-policy.json`. No trained model directory is required. Environment
settings are applied only to the child process. Existing frozen LSTM shadow
commands retain their original behavior.

## Scope and next step

The 121-observation runs are transport and causality checks. The short Nomadic
fixture reaches its final waypoint and remains stationary, so its samples are
not evidence of useful model quality. RandomWalk's unpredictable direction is
also not evidence that a deep model will beat a baseline.

The next step needs longer simulations and actual training execution, using
only the request's completed prefix. Each trained version must retain its own
scaler and enter shadow evaluation on predictions issued after training. A
quality gate, promotion, retraining and fallback are still pending. Later model
adapters can consume the same raw input/target contract. This request probe does
not yet call the existing load lifecycle manager or train LSTM/TCN/GRU.

All repository documentation and messages in this implementation are English.
