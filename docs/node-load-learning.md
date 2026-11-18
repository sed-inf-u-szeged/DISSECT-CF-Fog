# W05: trained load models in the Mobility simulation

W05 adapts the existing LSTM and TCN load trainers to the uniform node samples
added in W03. It retains W04's separate one-minute Persistence baseline. Device
position predictions, node-load predictions and resource-control decisions are
different outputs; W05 does not enable capacity, energy, connection or offload
control.

## Start a run

In **New mobility run**, select the movement strategy and configure:

- Devices: `1` for the first integration check.
- Movement duration: `480` simulated minutes.
- Node-load prediction: enable `LSTM` and `TCN`.
- Quality profile: `Candidate · 80% within tolerance`.
- The three additional **mobility** comparison models can be unchecked for this
  check. They predict device positions, not waiting bytes.

The UI uses 40 training epochs, as in the existing optimized load profile.
Restart the UI after installing. `MobilityPredictorUi`'s Run entry rebuilds the
Angular frontend when its sources are newer than the built UI.

CLI equivalent from the project root in `dissect-fog`:

```powershell
python predictor-ui/scripts/mobility/run_mobility_multi.py --devices 1 --minutes 480 --drain-minutes 1440 --load-models LSTM TCN --load-profile candidate --load-epochs 40
```

Without `--load-models`, existing runs retain their previous behavior. Short
runs are allowed and honestly report untrained/unqualified models. Drain time
finishes outstanding work and does not supply extra load-training observations.

## Data and model contract

| Item | Meaning |
| --- | --- |
| Target | `waiting_assignment_bytes`, bytes received but not yet assigned to compute |
| Clock | One sample every 60,000 simulation milliseconds, after boundary events |
| Inputs | Last 192 observed samples for that node |
| Forecast | 64 values for the next 64 one-minute targets |
| Forecast schedule | Consecutive, nonoverlapping blocks of 64 targets |
| First training | 287 raw points produce 32 supervised windows of 192 inputs + 64 targets |
| Training scope | Separate run, node, model family and version; no cross-node sequences |
| Models | Existing `lstm_trainer.get_model` and `tcn_network.get_model` |
| Training | Existing optimized trainer, Adam, combined MAE + first-10-mean loss, 40 epochs |
| Scaling | Mean/std from that training snapshot's input windows only; embedded in saved model |
| Serving | Reload the saved uncompiled `.keras` model; nonnegative byte outputs |
| Registry | Run-local artifacts, without writing the legacy global registry |

The initial zero-load samples are legitimate observations on the grid, not
missing values. Time index 0 is simulation time 0. First training is at index
286 (minute 286); the first evaluation targets are indices 287 through 350.
Actual start/stop of device movement is separate from this common node clock.

## Qualification and selection

The adapter reuses `LifecycleCoordinator`, `LifecycleManager`,
`LifecyclePolicy` and `OnlineTrainingSchedule`. It registers a distinct
`node-load/<run>/<node>` feature and never resets the shared lifecycle manager.
Mobility and load maintain separate evaluation windows and model versions.

Two explicit profiles reproduce the existing load-profile tolerance settings:

| Profile | Minimum targets | Within-tolerance requirement | Relative / absolute tolerance |
| --- | --- | --- | --- |
| candidate | 64 | At least 80% | 20% of actual magnitude, with a 0.1 B minimum |
| observe | 64 | 0%, for workflow checks only | 10% of actual magnitude, with a 0.1 B minimum |

Both use a maximum SMAPE of 200% and unbounded MAE/MSE/RMSE limits, as the
existing experimental profiles do. Error metrics are still calculated. These
are **uncalibrated experimental criteria**, not an established quality or
service guarantee. In particular, observe must not be described as useful
prediction quality. A zero-byte actual uses the 0.1 B absolute tolerance.

1. Persistence supplies the selected forecast while history is collected or
   the first candidate is being evaluated.
2. At first qualification, the first 64 future targets must all be observed.
   Failure records rejection, grows the raw training window by 64, and waits
   for at least 64 new observations after the decision before retraining.
   The first-qualification field says `REJECTED`; the shared coordinator's
   terminal state is `FAILED`. This rejection does not mean the simulation
   crashed: the run can complete successfully with Persistence selected.
3. A passed model becomes eligible. The shared manager ranks eligible families
   by within-tolerance percentage, then RMSE, MAE, SMAPE and MSE, only when the
   latest 64 target times align. Equal scores retain the current model. A first
   passing model can replace the trusted baseline without proving improvement
   over it; paired baseline metrics are reported separately.
4. Active and ready models continue receiving actual targets. The most recent
   64 matched targets monitor quality. Degradation removes eligibility and
   immediately falls back to Persistence if the model was selected. A later fit
   waits for 64 new points; degradation alone does not enlarge the window.

For example, a rejected first fit at index 350 can retrain at index 414 using
351 raw points (96 supervised windows). Its qualification targets are 415–478.
This is why 480 movement minutes are useful for an integration check. Passing
models are monitored rather than automatically retrained on every block.

`node_load_model_selection.csv` records the next-target value available at each
minute. For an active trained model, that value may come from a vector issued
earlier: `forecast_origin_index` and `lead_steps` preserve its true age. This
is not a claim that every trained value was freshly forecast one minute ahead.
Selections are recorded for analysis; no simulator policy consumes them yet.

## Result files

All files are under the same `mobility-multi-*` run directory as the trajectories.

| File | Contents |
| --- | --- |
| `node_load_model_summary.json` | Explicit policy, version counts, metrics, final states and selected models |
| `node_load_model_events.jsonl` | Training, qualification, rejection, promotion, degradation and fallback events |
| `node_load_model_predictions.csv` | All 64-point forecasts, original timestamps, paired Persistence and vector inference time |
| `node_load_model_evaluations.csv` | Exact future-target matches and model/baseline errors |
| `node_load_model_selection.csv` | Available next-target prediction selected by the lifecycle at each sample |
| `node_load_model_audit.json` | Independent checks and saved-model replay status |
| `node_load_models/<node>/<family>/<version>/` | Past-only training CSV, `.keras` model, existing trainer report and provenance |
| `node_load_worker.log` | Training output and worker diagnostics |

The paired Persistence value repeats the observed value at the **same forecast
origin** across the same 64 target times. W04's one-minute baseline is a
different horizon and is not substituted into this comparison. Inference time
is per 64-value vector, including worker-side input conversion and model call;
it excludes Java/Python transport and file writing. Training wall time includes
snapshot preparation, imports on the first call, model saving and parity checks.

Only observed future targets are scored. Pending tails remain unmatched; the
final zero after task draining is never fabricated as a missing target.

## Audit and next step

The runner checks node sampling, W04 baseline, existing Mobility lifecycle and
saved Mobility models, then independently audits the W05 files and replays the
saved load models. It checks exact training snapshots against the node stream,
normalization, forecast origins, future target matches, all qualification
metrics, selected-model eligibility and summary counts. Run-local model files
and frozen runtime sources are included in the batch hash manifest.

```powershell
python predictor-ui/scripts/mobility/audit_node_load_models.py '<run directory>' --replay-models
```

W06 adds node selection and actual/predicted load curves to the same replay
clock; see [node-load-ui.md](node-load-ui.md) for controls, interpretation and
acceptance checks. Existing W05 exports can be opened without retraining.
Scale and quality conclusions require W07's repeated multi-scenario experiments.
Energy measurements and controller research follow in W08/W09.
