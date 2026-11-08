# Mobility online lifecycle: relative quality gate

## What this step adds

The existing live first-fit worker can now make a quality decision when one
frozen model version has accumulated its first 64 completed shadow predictions.
All three methods are compared at exactly the same origins and target times.
The decision is made inside the live observation loop and saved at that tick.
It is not inferred later from the final 80-origin summary.

This is an initial, relative-to-baseline rule for workflow integration. It is
not a teacher-approved absolute position accuracy requirement. Absolute error
tolerance in metres remains unspecified; the load task's percentage tolerance
is not applied to latitude or longitude values.

Actual promotion, growth retraining, active-model monitoring, fallback, and
additional model families remain pending. The full shared lifecycle controller
has not yet been connected. A `PASS` here records that the relative rule passed;
it does not activate the LSTM or change connection control.

## Fixed comparison rule

The default policy is:

```json
{
  "minimum_evaluated_origins": 64,
  "minimum_relative_rmse_improvement": 0.0
}
```

For the first completed evaluation block of one model version:

1. Select the baseline with the lower RMSE from `HoldPosition` and
   `ConstantVelocity`, using the same block. Ties use lower mean error, then
   `HoldPosition` for a remaining tie.
2. Require LSTM RMSE to be strictly lower than the selected baseline's RMSE.
3. Require the configured minimum relative RMSE improvement.
4. Require LSTM mean position error to be no greater than that same baseline's
   mean position error.

All conditions must pass. P90 and median error are recorded but are not gates.
The default improvement value is zero, but the separate strict-improvement
condition still rejects ties. A zero-error baseline cannot be strictly improved
upon; the result is `REJECT`, without dividing by zero or relaxing the rule.

Relative RMSE improvement means `1 - LSTM_RMSE / baseline_RMSE`. It is a comparison
between models, not the load task's pointwise tolerance percentage.

Each version gets an independent gate. Duplicate origins, gaps, mixed runs or
versions, unmatched methods, early targets, inconsistent position errors, and
non-finite values are rejected before consuming the batch. Returned decision
snapshots are copies; later observations cannot change the original decision.

## Timing in the current fixture

| Event | One-based observation number |
| --- | --- |
| First training request: 32 completed windows | 41 |
| First future result for the frozen model | 42 |
| 64th future result and quality decision | 105 |
| Request for 96 training windows, still deferred | 105 |
| Final result: 80 evaluated origins in total | 121 |

At observation 105, the journal order is:

1. `OBSERVATION`
2. `EVALUATION`
3. `QUALITY_DECISION`
4. `TRAINING_REQUEST_DEFERRED`
5. `SHADOW_PREDICTION`

The final 16 results are retained in the full 80-origin metrics. They do not
change the already-issued 64-origin decision. This is a one-decision-per-version
probe; continuous monitoring after promotion belongs to a subsequent step.

## Run

From the repository root, with `dissect-fog` active:

```powershell
python -m unittest discover -s predictor-ui/scripts/mobility -p 'test_mobility*.py' -v
python predictor-ui/scripts/mobility/run_mobility_online_first_fit.py --quality-gate
```

The optional flag keeps the previous first-fit command compatible. The gate
runner starts fresh simulations and actual TensorFlow training for both official
strategies. It retains the movement strategies, 32-window first fit, frozen
scaler/model, fixed 20 epochs, and target-time evaluation. It does not load the
earlier offline trained model or alter previous output directories.

To use an explicit policy, pass `--quality-policy path/to/policy.json` instead
of `--quality-gate`. The file must have the two fields shown above. The minimum
evaluation count cannot be less than 64. This short fixture supports at most 80
evaluated origins; a longer simulation is needed for larger blocks or repeated
training/evaluation cycles. Policies are captured before each simulation and
checked against the saved results.

## Output and interpretation

The manifest is:

`simulator/sim_res/mobility-online-quality-gate/<session>/quality-gate.json`

Each simulation output adds `quality-decision.json`, containing the policy,
model version, exact evaluation interval, decision tick, selected baseline,
64-origin metrics, individual checks, and reason codes. It records
`absolute_quality=NOT_SPECIFIED`, `promotion=DISABLED`, and `active=false`.

The unchanged model bundle's `training.json` still records
`quality_gate=NOT_APPLIED` at training completion. The later quality decision is
a separate artifact; trained-model metadata and the scaler are not rewritten.

Expected operational success is `MOBILITY_ONLINE_QUALITY_GATE_OK`, including when
a candidate receives `quality_decision=REJECT`. Rejection is a valid quality
outcome, not a simulator or training failure. In that case the current probe
continues its baseline bridge responses and records further shadow predictions.
Execution of the deferred 96-window training request is the next lifecycle step.

## Verification

Before packaging, 81 Python tests passed, including 14 new quality tests. They
cover pass/reject cases, equal and zero-error baselines, RMSE/mean disagreements,
configurable margins, version isolation, causal boundaries, frozen decisions,
stream truncation, and independent checks against recorded evaluations.

Actual TensorFlow/Java integration passed on both official strategies. The
auditor checked the live event order, recomputed all position errors, rebuilt
the decision from only the first 64 mature evaluations, replayed the saved model,
verified train-prefix-only scaling, and checked artifact hashes.

In the local Linux verification, both candidates were rejected. For random
walk, the first-64 RMSE values were about 255.398 m for `HoldPosition` and
257.239 m for LSTM. These are local verification results, not a replacement for
the user's Windows results and not held-out generalization measurements.

The short nomadic fixture is stationary by the first fit, so its zero-error
baselines do not establish moving-trajectory performance. Random walk samples
fresh movements under the official strategy. Neither fixture guarantees that
a learned model will outperform a baseline.
