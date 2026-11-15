# Mobility prediction: IMM/Kalman, XGBoost and TCN

The first additional model batch is available through **New mobility run** and
the existing audited CLI. HoldPosition, ConstantVelocity and LSTM remain available.
Additional methods run in shadow comparison. The Java lifecycle still controls
LSTM qualification, promotion and fallback; connections/offloads remain reactive.

## Run and view

Install optional dependencies in the same environment used by the UI:

```powershell
# DISSECT-CF-Fog repository root, activated dissect-fog environment
python -m pip install -r predictor-ui/scripts/mobility/requirements-models.txt
Set-Location predictor-ui/ui
$env:DISSECT_MOBILITY_CONFIGURE = '1'
npm run electron:build_and_run
```

Choose Random walk or Nomadic, one device and 120 minutes, then select the three
additional models. **Run mobility** generates observations, trains and predicts
in causal simulation order, saves results and opens audited replay automatically.
This is actual fitting for XGBoost and TCN, not loading demonstration weights.

During execution choose a model in **Shown forecast**. In completed replay use
**Prediction** to choose a model, then inspect **Candidate comparison** and
**Additional model versions**. Replay does not retrain. At final settlement there
is no new forecast; choose an earlier frame to inspect the prediction and optional
retrospective true target. Training/qualification status in the serving card
continues to describe LSTM, separately from the displayed shadow forecast.

Old results remain readable but do not acquire new model predictions retroactively.
To compare models, create a new run with the desired methods selected.

The equivalent CLI command is:

```powershell
python predictor-ui/scripts/mobility/run_mobility_multi.py --devices 1 --minutes 120 --drain-minutes 1440 --strategy random-walk --repeats 1 --comparison-models 'IMM/Kalman' XGBoost TCN
```

Omitting `--comparison-models` preserves the previous CLI behavior. Models are
trained sequentially in the existing worker, independently for each device.
More devices increase fitting time. This update was exercised with one and two
devices; it does not establish 100-device performance for the three additions.

## Model definitions

| Method | Implementation | Fit policy |
| --- | --- | --- |
| IMM/Kalman | FilterPy IMM with two constant-velocity Kalman modes; lower and higher acceleration noise | Fixed parameters; reconstruct state from the last eight observations at each origin |
| XGBoost | Two independent CPU boosted-tree regressors, one per displacement axis; flattened 48 features; 100 rounds, depth 3, eta 0.05 | Fit the shared mature snapshot; native Booster API and JSON persistence |
| TCN | Input 8×6; width 16; three residual blocks with two causal kernel-3 convolutions each, dilations 1/2/4; final timestep; Dense16→Dense2 | 20 epochs, batch 32, Adam 0.001, clipnorm 1; existing CPU TensorFlow runtime |

IMM uses position observations to estimate velocity. Its smooth/manoeuvring
modes share a constant-velocity transition and have acceleration noise standard
deviations 0.03 and 0.6 m/s². Measurement noise is 5 m. These are initial fixed
settings, not fitted or selected using the evaluation errors. It is an IMM
baseline, not a pretrained neural network or a turn-aware map model.

The features for learned methods are the existing relative east/north positions,
observed speed, sine/cosine of observed heading, and heading-valid mask. Inputs
use eight past 60-second observations. Outputs are east/north displacement at
the next 60-second target, converted back to GPS for scoring and display. Route
waypoints, future headings, future speeds and target positions are not inputs.

## Fair comparison and time boundaries

- XGBoost and TCN reuse the exact immutable training requests issued for LSTM.
  Initial fit uses 32 mature windows. Later fits use the actual LSTM lifecycle's
  snapshot (for example 96 after rejection, or a recent 32 after degradation).
- All new methods start issuing comparisons at the first LSTM fit. This keeps
  their target origins identical to the existing three-method candidate table.
  IMM could run earlier, but its scores intentionally use the same comparison set.
- Scalers use the current training snapshot only. A target must have arrived
  before entering a training request. Evaluation occurs only when the predicted
  target observation arrives. No random train/test mixing is used.
- New versions are updated when the shared schedule requests a fit. This is a
  controlled initial comparison, not an independent qualification controller for
  each new model and not a held-out benchmark for choosing a winner.
- A 120-minute single-device run has 121 observations, 112 mature system-selected
  forecasts and 80 common post-fit targets per candidate method. Warm-up accounts
  for the difference. All methods in the candidate table use those same 80 targets.

The table reports mean geographic position error and RMSE in metres. It does
not fabricate a percentage accuracy threshold. Mean inference milliseconds cover
each new method's per-window preprocessing, inference and GPS conversion. IMM's
window reconstruction is included. Existing methods show `—` for unrecorded
inference time. Version training time includes preprocessing and saving, but
excludes the subsequent reload audit; for IMM it is setup time, not learning.

## Saved artifacts and validation

Each `output/mobility-multi-*/devices/<id>/` contains:

- `comparison.json`: selected methods, role, version provenance and target count.
- `comparison_predictions.csv`: predictions issued at their origins and timings.
- `comparison_evaluations.csv`: actual targets and measured position errors.
- `comparison-models/<method>/vNNNN/`: training metadata, hashes, model artifact,
  learned preprocessing and TCN training history where applicable.

The existing LSTM snapshots and artifacts remain the reference for shared cutoffs.
The existing `scaling.csv` train/predict timing columns still describe LSTM only;
simulation wall time includes the selected extra models. Additional-model timings
are stored in their version metadata and comparison prediction rows.
Every additional model is saved and reloaded. The final Python audit recomputes
each issued forecast from the saved model and independently reconstructed causal
window (position agreement within 1 cm), checks snapshot/scaler provenance and
recomputes errors. The UI reader also rejects incomplete, mixed-device,
wrong-version, duplicate and inconsistent comparison records. New results remain
`FAILED` if required audits fail; cancellation keeps partial data without completion.

## Demonstration versus model selection

The default non-repeating Nomadic route reaches its destination before candidate
evaluation begins. Its near-zero errors mainly test a stationary device. For a
moving demonstration choose Random walk, or Nomadic with a closed route such as
`0,0;1600,0;1600,600;0,0` and **Repeat closed route** enabled.

These are initial integrations, with no parameter search. A successful run means
the training/prediction/persistence/display path passed its checks. It does not
mean the new models outperform HoldPosition or LSTM. Compare multiple trajectories
and seeds before deciding which model suits the mobility task best.

Implementation references: [FilterPy IMM](https://filterpy.readthedocs.io/en/latest/kalman/IMMEstimator.html),
[XGBoost native Python API](https://xgboost.readthedocs.io/en/stable/python/python_api.html),
[Keras Conv1D causal padding](https://keras.io/api/layers/convolution_layers/convolution1d/).
