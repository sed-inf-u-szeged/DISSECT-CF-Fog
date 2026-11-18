# Multi-device mobility forecasting

This mode creates multiple real `ObservedDevice` instances in one simulator JVM.
They share the simulation clock, two fog nodes, applications and transfer ledger.
Each device retains the official SmartDevice random 0-3 minute start delay.
Observations are 60 seconds apart within each device, with different offsets
across devices. The UI preserves global simulation time when switching devices,
shows the last available observation and still-valid forecast, and explicitly
marks a device that has not produced its first observation yet.
Each device has a separate movement strategy instance, causal history buffer,
training windows, scaler, LSTM versions, qualification block and Java lifecycle.

One Python process serves all devices sequentially. Training and inference block
the bridge in wall-clock time. They are not modelled as fog-node compute tasks.
Position predictions are recorded and selected by the existing lifecycle rules;
device connections and offloading still use the existing reactive policies.

## First check on Windows

Use the existing environment and compiled simulator dependencies. No changes to
the original Nomadic or RandomWalk properties files are required.

```powershell
Set-Location D:\Github\DISSECT-CF-Fog
conda activate dissect-fog
python -m pip install -r predictor-ui\scripts\mobility\requirements-multi.txt
python predictor-ui\scripts\mobility\run_mobility_multi.py --devices 1 10 --minutes 120
```

The runner compiles the changed Java sources into an isolated runtime, runs the
1-device and 10-device cases, and checks all devices. It prints
`MOBILITY_MULTI_DEVICE_OK` only after both simulation and post-run audits pass.
The `Open mobility results:` line gives the exact directory to open in the UI.
Select the directory containing `multi-device.json`, not its `devices/0` child.

```powershell
Set-Location D:\Github\DISSECT-CF-Fog\predictor-ui\ui
node --test test/mobility-ipc.test.cjs test/mobility-results.test.cjs test/mobility-multi-results.test.cjs
npm run electron:build_and_run
```

Use the Device dropdown to choose a trajectory, forecast, error curve and lifecycle.
Switching devices preserves the selected simulation time and resets the version
filter and retrospective target toggle. Node load, offloads and accounting always
describe the entire simulation; they do not change to a per-device attribution.
Previous single-device folders and the original default Run entry remain supported.

## Runs and audits

Every invocation creates a new batch under `simulator/sim_res/mobility-multi`.
Each case contains one `mobility-multi-*` output directory with:

| File or directory | Scope |
| --- | --- |
| `multi-device.json`, `run.properties` | Run identity, complete device roster and global counts |
| `drain-state.json` | Final or capped simulation time, next event, remaining queues/tasks and byte counters |
| `node_load.csv`, `offload_events.csv` | Shared infrastructure snapshots and transfers |
| `devices/<id>/mobility_samples.csv` | That device's simulator observations |
| `devices/<id>/retry_*.csv` | Same-target comparisons for that device's three methods |
| `devices/<id>/shared_*`, `shared-lifecycle.json` | That device's Java selection, qualification and fallback |
| `devices/<id>/models/vNNNN` | That device's saved model, scaler and training provenance |
| `devices/<id>/timings.csv` | Measured fit and inference calls |
| `bridge-timings.csv` | Java wall time spent in each device's synchronous bridge |
| `audit.json`, `ui-validation.log` | Post-run feature, model, lifecycle and accounting checks |

All histories are keyed by `(run_id, device_id)`. A version name such as `v0001`
belongs to one device only. Identity mismatches, gaps within a device's stream,
duplicate records, incomplete streams and missing output files cause failure.
A failed run remains available for diagnosis and is not accepted by the UI.

The audit independently reconstructs features and labels, compares Java and Python
observations, verifies each training cutoff, recomputes first-64 qualification
and subsequent fallback, reloads each saved model, replays predictions, and checks
global byte conservation and transfer counts. Validation time is outside the
simulation timing. Test doubles in automated tests are explicitly synthetic.

At 120 simulated minutes, each device has 121 observations, 112 selected forecast
targets and 80 candidate comparison targets, provided the run completes. The
first fit still needs 32 mature windows, and every new version still needs its
own 64 subsequent targets. An unfinished final version is a valid Evaluating
state, not a failed experiment or an automatic promotion.

## Separate sampling and workload drain limits

`--minutes` controls data generation and the fixed prediction sample window.
`--drain-minutes` controls the additional simulation-time allowance for existing
uploads, offloads and compute tasks to finish. Its default is 120, preserving
earlier runs; accepted values are integers from 1 to 1440. The absolute event
deadline is `(minutes + drain_minutes) * 60000` milliseconds. Both values are
written into the case configuration, batch manifest and result metadata.
Original scenario files are never edited.

The original 100-device, 120-minute experiment reached the 240-minute simulation
deadline while its shared fog nodes still had unfinished tasks. Use a larger
bounded allowance for this workload:

```powershell
python predictor-ui\scripts\mobility\run_mobility_multi.py --devices 100 --minutes 120 --drain-minutes 1440
```

1440 is an upper bound in simulated minutes, not a wall-clock wait. Simulation
ends as soon as all events finish. It still records exactly 121 prediction
observations per device at a 120-minute sampling duration. A longer drain does
not extend training, relax quality gates, increase fog capacity or enable
prediction-based connection control. Node-load charts retain the existing blank
gap where no intermediate load observations were recorded before final settlement.

If the cap is still reached, the run remains FAILED. `drain-state.json` reports
each node's waiting bytes, running tasks and incoming transfers, together with
subscribed devices, unmatched forecasts and global upload/offload counters.
These counters are also printed into `simulation.log` on an incomplete drain.
The independent wall-clock timeout remains a separate guard.

## Scaling measurements

After the first 1/10-device check succeeds, collect repeated scaling runs:

```powershell
python predictor-ui\scripts\mobility\run_mobility_multi.py --devices 1 10 100 --minutes 120 --drain-minutes 1440 --repeats 3
```

`scaling.csv` contains one row per case/repeat:

| Metric | Definition |
| --- | --- |
| `minutes`, `drain_minutes` | Sampling duration and additional workload-drain limit, both in simulated minutes |
| `final_settlement_sim_minutes` | Actual simulation clock at natural completion; not wall-clock duration |
| `simulation_wall_seconds` | JVM launch through simulation/worker completion, including worker startup, fits, inference, I/O and draining; excludes compilation and later audit |
| `train_total_seconds`, `train_mean_ms`, `train_p95_ms` | Full fit calls, including preprocessing, model saving and reload validation |
| `predict_total_seconds`, `predict_mean_ms`, `predict_p95_ms` | LSTM inference calls, including input transformation and conversion of the output; excludes fitting, IPC and audit replay |
| `peak_rss_sampled_mib` | Largest observed sum of JVM and worker process-tree RSS, sampled every 0.25 seconds; short peaks may be missed |

Fit counts depend on each device's qualification and fallback history, so report
the counts alongside time. Means and percentiles include the first inference
call; no warm-up calls are silently discarded. This is sequential inference,
not batched or parallel inference. The memory-sampling dependency is isolated in
`requirements-multi.txt`; installing it does not upgrade TensorFlow.

The official RandomWalk strategy uses `SeedSyncer.centralRnd`. A fixed seed and
device count are repeatable, but changing the count also changes the random
draw order and therefore per-device trajectories. These runs measure system
scaling under the official generator, not identical-trajectory algorithm
comparisons. The runner records this as `SHARED_SEED_SYNCER`.

The runner accepts counts from 1 to 100. Keep the sampling duration, drain budget,
scenario, infrastructure, environment and repeat count explicit when reporting
scaling. Larger or longer workloads can require a different drain budget; reaching
it still fails the run, with logs preserved. Do not report an incomplete run as a
completed timing sample. The delivered fix's validation record distinguishes
model-double drain checks from real TensorFlow runs.

## Review boundaries

The update does not change LSTM architecture, training policy, qualification
criteria, saved single-device experiments, or the user's scenario files.
It does not stage, commit or push repository changes. The drain-fix installer
verifies the installed multi-device baseline and backs up changed files outside
the repository.
