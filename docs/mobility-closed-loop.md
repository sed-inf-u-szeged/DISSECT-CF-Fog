# Mobility closed-loop training

This bounded development probe connects rejection and active degradation to new
training requests. It uses the original Nomadic and RandomWalk strategies without
changing their route generation, speed rules, seed, or connection policy.

## Run

From the repository root, with the verified `dissect-fog` Python environment:

```powershell
python predictor-ui/scripts/mobility/run_mobility_closed_loop.py
```

The probe runs 360 simulated minutes (361 observations) for each strategy. Each
run has 352 mature input/target examples. Training starts after 32 examples,
which requires 41 observations because of motion-feature warmup and target delay.
All outputs go to a new directory under `simulator/sim_res/mobility-closed-loop`.
Existing runs are never resumed or overwritten. Successful output ends with
`MOBILITY_CLOSED_LOOP_OK`; retain the entire run directory on failure.

## Shared behavior and domain adapters

`OnlineTrainingSchedule` contains the same eligibility and window-growth rules
used by `LstmOnlineLifecycleController` and the mobility adapter:

- Do not fit a replacement while a model is usable or its qualification is pending.
- A retry requires at least 64 new data items since the later of the last attempt
  and that feature's evaluation start.
- A rejected qualification grows the window by 64, capped at 4096.
- Active degradation does not itself enlarge the window. After fallback, fit the
  latest mature data using the current window size once the new-data rule permits.

Load prediction counts raw points and retains its original input/output sequence
requirements, feature joins, model adapters and controller. Mobility counts mature
labelled examples; each example still has 8 history feature vectors and one
60-second-ahead position target. These are different counting units. The shared
rules do not change the load input length to 8, or equate 32 examples to 32 raw
observations. For example, load settings with 192 input points, 64 output points
and 32 training examples require 287 raw points.

The real Java `LifecycleCoordinator` and `LifecycleManager` own qualification,
activation, selected prediction ownership, rolling monitoring and fallback.
`MobilityLifecycleAdapter` issues the training command after processing the real
current target. Python freezes and validates the requested latest window, then
fits the existing LSTM and returns completion facts and frozen forecasts.
The Python first-64 qualification result cannot suppress a later Java degradation
request. Python never commands activation.

This shares the lifecycle core and training decision rules. It does not replace
the load controller with the mobility adapter or route mobility fitting through
the load `FeatureManager`, model registry, or model-specific training launcher.
Training execution still uses the mobility Python adapter. Fits block the live
bridge at the current simulation tick; their CPU time is not a simulated fog job.

## Causal event order

For every observation, Java first settles the previous selected prediction and
candidate forecasts with the actual position. It then applies qualification or
rolling monitoring, performs promotion or fallback, and sends exactly one
`CONTROL` message. Python can fit only the matching authorized request, before
issuing the current origin's new forecast. Java then selects the active owner.

Each new version starts with zero matched evaluation targets. Its first 64 future
targets determine its frozen qualification decision. An active version is then
checked on a rolling block of 64 paired targets. The reference is the better
HoldPosition/ConstantVelocity baseline on that same block, ranked by RMSE and
then mean position error. Qualification requires strictly lower RMSE and mean
error no worse than the reference. All distances are metres. Thresholds have not
been relaxed to force promotion.

After rejection or fallback, HoldPosition remains the selected predictor while
the next version is being evaluated. A saved model or a prior PASS does not make
that new version active. A final observation settles pending targets but cannot
start another training attempt. A version with fewer than 64 targets remains
`WAITING_FOR_EVALUATION`; a successfully ended stream can contain such a version.
Stream completion is distinct from model qualification.

## Artifacts and independent verification

The batch `closed-loop.json` links both isolated runs. Within each run:

| Artifact | Meaning |
| --- | --- |
| `shared-lifecycle.json` | Authoritative Java owner, states, promotions, fallbacks and degradation-triggered fits |
| `shared_lifecycle_events.jsonl` | Actual targets, quality decisions, control commands, training facts and selected predictions |
| `shared_selected_predictions.csv` | Predictions actually selected by the shared lifecycle owner |
| `shared_selected_evaluations.csv` | Those selected predictions scored only after their target arrived |
| `training_controls.jsonl` | Java commands received by the Python executor |
| `scheduled-request-NNNN.json` | Immutable latest mature training window and its authorizing command |
| `models/vNNNN` | Saved model, frozen scaler, training history and provenance |
| `retry.json` | Python training/qualification accounting; `active=false` means it has no activation authority |

Legacy `training-request-NNNN.json` files remain buffer milestone snapshots for
compatibility and causality checks. They do not authorize training in this mode.
Bridge `shadow_receipts.csv` remains a transport receipt, not the active-owner
selection log.

The runner independently reconstructs input tensors, labels, training windows,
all forecast errors, the first-64 decisions, rolling degradation, Java commands,
version boundaries and selected ownership. It reloads every model, recomputes the
scaler from only that version's requested data and replays its saved predictions.
Source, runtime, configuration and artifact hashes are checked. Missing controls,
premature training, altered targets, mixed versions, incomplete models and missing
END cannot be accepted as a successful closed loop.

## Scope

Only the LSTM is trained in this probe, alongside the two fixed baselines. The
existing load-family adapters are covered by regression tests; that does not mean
TCN, GRU or other models have been trained on mobility data. Fair comparison of
those models remains a separate step. Connection control stays disabled: selected
predictions are recorded, but do not alter device associations or migration.

Repository files, messages and this document are English-only. Installer backups
are outside the repository; no installation step stages, commits or pushes files.
