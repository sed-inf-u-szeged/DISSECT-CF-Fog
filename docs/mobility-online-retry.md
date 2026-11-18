# Mobility online retry: 32 to 96 labelled windows

This integration step executes an actual second LSTM training job when the first
version fails the existing relative quality gate. Both runs use the unchanged
official NomadicMobilityStrategy and RandomWalkMobilityStrategy fixtures. No UI
or movement strategy implementation is changed.

## Scope and lifecycle boundary

The previous quality-gate probe recorded a decision and deferred the 96-window
request. The new runner executes that request after a first-version REJECT.
It preserves the first bundle, creates a new bundle, resets the new version's
evaluation count, and scores only predictions issued after that version finishes
training. Java simulation time stays paused while the Python worker trains.

This is a bounded retry integration probe. It does not yet connect the mobility
worker to the load task's OnlineModelLifecycleManager or LifecycleCoordinator.
Promotion, active prediction selection, degradation fallback, and connection
control remain disabled. A training exception makes the verification run fail;
graceful recovery from such exceptions is not implemented in this step.

The ordering follows the existing load workflow: collect data, train, wait for
real shadow targets, decide quality, and retry with more available data after
rejection. The domain-specific sizes and quality rules are distinct:

| Concept | Current mobility retry probe |
| --- | --- |
| Input history | 8 valid motion observations per input |
| Prediction horizon | One point, 60 seconds ahead |
| First fit | 32 completed labelled windows |
| Retry | 96 completed labelled windows, including the previous 32 |
| Quality count | At least 64 future origins per model version |
| Error | Great-circle position error in metres |
| Quality rule | RMSE strictly below the best same-block baseline, mean error no worse |
| Scaler | Fitted separately to each version's available training prefix, then frozen |

The scalar load task's percentage tolerance is not applied to latitude or
longitude. The current mobility rule is relative to baselines; no absolute
application-specific accuracy threshold has been approved. The buffer cap is
expressed in labelled windows, whereas the load controller uses raw point limits.
These differences must be explicit when the common controller is integrated.

## Timeline for the current 121-observation fixture

Observation numbers below are one-based. The initial observation has no valid
motion estimate, so the first 32 completed windows require 41 observations.

| Observation | Action |
| --- | --- |
| 1-40 | Accumulate observations and labels; the Java bridge receives HoldPosition predictions once history is ready |
| 41 | Train v0001 using 32 completed windows, save and reload it, then issue its first shadow prediction |
| 42-104 | Evaluate v0001 predictions as their actual targets arrive |
| 105 | Settle v0001's 64th origin and freeze its quality decision |
| 105, after REJECT | Train v0002 on the 96 completed windows, then issue v0002's first shadow prediction |
| 106-121 | Settle 16 v0002 origins; do not create a v0002 quality decision |

At observation 105 the event order is strictly:

1. OBSERVATION
2. EVALUATION for v0001
3. QUALITY_DECISION for v0001
4. TRAINING_STARTED for v0002
5. TRAINING_COMPLETE for v0002
6. SHADOW_PREDICTION from v0002

The second version needs 48 additional future origins to reach its own 64-origin
minimum. It must report WAITING_FOR_EVALUATION even if its first 16 errors are zero.
The current fixture therefore verifies the retry transition, not the second
version's eventual quality or the complete recurring lifecycle.

If v0001 passes, the 96-window request is recorded as deferred with reason
QUALITY_PASSED_PROMOTION_PENDING. The qualified version continues shadow scoring;
this probe does not activate it or force a rejection to exercise retraining.

## Run from the repository root

```powershell
python -m unittest discover -s predictor-ui/scripts/mobility -p 'test_mobility*.py' -v
python predictor-ui/scripts/mobility/run_mobility_online_retry.py
```

The runner needs the same JDK, built simulator dependencies, Python environment,
and TensorFlow setup as the first-fit and quality-gate steps. Each successful
model fit uses a fresh LSTM32-Dense16-Dense2 network, 20 fixed epochs, batch size
32, and seed 42. The second fit is a fresh fit on a larger prefix, not extra
epochs on v0001. No future validation/test data is supplied to either fit.

The existing first-fit and quality-gate commands remain available. The shared
OnlineLstm trainer now derives version names from the training request number;
request 1 retains its previous v0001 behavior.

## Outputs and verification

Results are stored under simulator/sim_res/mobility-online-retry/<session>/.
The top-level online-retry.json links both official-strategy runs and their hashes.
Each simulator output includes:

- training-request-0001.json and training-request-0002.json: immutable causal snapshots.
- models/v0001/ and, after a rejection, models/v0002/: separate model.keras,
  preprocessing.json, history.csv, and training.json bundles.
- retry_events.jsonl: observation, training, evaluation, and quality event order.
- retry_predictions.csv and retry_evaluations.csv: explicit model_version on every row.
- v0001-quality-decision.json: the frozen first 64-origin decision.
- retry.json: per-version counts, metrics, and remaining evaluation origins.

After the run, the auditor reconstructs features and training prefixes from the
simulator's actual observations. It validates target-time errors, version boundaries,
event order, quality decisions, and immutable snapshots. It independently reloads
each saved model/scaler and replays that version's own prediction origins. None
of this post-run audit data is sent back into the live training worker.

MOBILITY_ONLINE_RETRY_OK means the workflow checks passed. It does not mean a
model passed its quality gate. Per-version metrics describe different periods
and counts, so a v0001-versus-v0002 accuracy claim cannot be made from them alone.

## Verification for this delivery

Local Linux validation completed 95 Python tests and real Java/Python runs for
both official fixtures using TensorFlow 2.21.0 on CPU. Both first versions were
rejected, both second fits used 96 windows, and both second versions ended with
16 evaluated origins and 48 still required. Four independently saved model
versions passed reload, scaling-prefix, frozen-artifact, and replay checks.

The unit tests use explicitly named model doubles for deterministic state and
failure tests. The real strategy runs use TensorFlow LSTMs. These local results
do not substitute for the user's Windows verification.

Remaining work includes enough simulation duration for subsequent quality
blocks and the 160-window retry; integration with the shared lifecycle manager;
promotion and degradation fallback; and additional model adapters. No full
mobility lifecycle completion is claimed by this package.
