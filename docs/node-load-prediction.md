# Node-load persistence baseline

The common node sampler now feeds a one-step load forecast during the same
mobility simulation. At each node boundary `t`, Persistence predicts
`waiting_assignment_bytes(t + 60000) = waiting_assignment_bytes(t)`.
Only the current observed value is supplied to the predictor. When the next
sample arrives, the previous forecast is evaluated before a new one is issued.

This is a fixed baseline comparison. It requires no fitting or qualification,
and does not control connections, offloads, capacity or energy. Mobility's
HoldPosition/LSTM lifecycle and its additional shadow models retain their own
roles. Sharing a simulation clock is the first integration step; device
position forecasts are not yet inputs to the load predictor.

## Outputs and identity

New runs write these files at the run root, beside `node_load_samples.csv`:

| File | Contents |
| --- | --- |
| `node_load_predictions.csv` | Every issued forecast, including the final unmatched one per node. |
| `node_load_evaluations.csv` | Only forecasts whose exact target sample has arrived. |
| `node_load_prediction_summary.json` | Per-node and pooled counts, MAE, RMSE, bias and timing scope. |

Each forecast carries run and node identity, task, model/version, target metric,
unit, origin sample index, last input time, origin time, target time and horizon.
The model is `Persistence`, version `fixed-v1`; this fixed rule is unrelated to
the trained mobility versions `v0001`, `v0002`. All simulation times are in
milliseconds. The target is waiting/unassigned bytes, not CPU utilisation,
running task count, energy or newly arriving bytes.

For example, if west has 80 B waiting at minute 10, its forecast for minute 11 is
80 B. If minute 11 reveals 35 B, the signed error is `80 - 35 = +45 B` and the
absolute error is 45 B. No error is available at minute 10 for this forecast.

## Evaluation

- `error_bytes = prediction - actual`; positive values overpredict waiting work.
- MAE is the mean absolute error in B. RMSE is the root mean squared error in B.
  Lower is better; neither is a percentage accuracy or a pass/fail threshold.
- Every matched grid target is included, including startup and idle samples.
  Pooled metrics are calculated over all errors, not by averaging per-node RMSE.
- A node with N observations has N forecasts, N-1 matched targets, and one
  unmatched tail forecast. Missing target observations are never invented from
  the final-settlement state. A single sample has null error metrics, not zero.
- `inference_duration_ns` times the small prediction call and clock overhead,
  excluding output writing and simulation waiting. It is wall time, not
  simulation time; it is too small to treat as a meaningful model benchmark.
- This baseline does not claim an improvement, energy saving or qualified
  learned model. It establishes a reproducible reference for later models.

## Run and verify

Use the existing UI to start a new mobility run. No additional dependency is
required. The multi-device runner automatically audits the new exports when
present; old runs without them remain supported. The current UI still displays
its existing observed node-load charts; a predicted-load chart is a later step.

```powershell
python predictor-ui/scripts/mobility/audit_node_load_predictions.py 'PATH_TO_RUN_DIRECTORY'
```

Success prints `NODE_LOAD_BASELINE_OK`. This audit independently reconstructs
origin values, exact future target matches, pending tail targets and summary
metrics. The existing sampling audit separately checks offload accounting.

```powershell
mvn -f simulator/pom.xml -Dtest=NodeLoadPersistenceTest,FixedIntervalSamplerTest test
python -m unittest discover -s predictor-ui/scripts/mobility -p 'test_node_load*.py'
```

## Following steps

W05 now connects the existing LSTM and TCN load predictors through an adapter
with explicit training and qualification settings; see `node-load-learning.md`.
Their current 192-input / 64-output /
32-training-window configuration needs at least 287 raw samples for the first
fit; additional observations are then needed for qualification. A 120-minute
mobility run is sufficient for this baseline, but not for that first fit.
Predictions with different horizons must be compared on matching targets.
