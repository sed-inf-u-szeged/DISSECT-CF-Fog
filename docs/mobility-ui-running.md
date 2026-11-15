# Mobility: configure, run, preview and replay

The mobility configuration page uses the official simulator's `random_walk`
and `nomadic` strategies. It invokes the same audited multi-device Python
runner as the command line, with one repeat per UI run.

## Open the UI before running a simulation

After the normal project dependencies have been installed, run
`hu.u_szeged.inf.fog.simulator.demo.simple.MobilityPredictorUi.main` in the IDE.
This entry opens configuration first. It does not require an existing result
path. The original `MobilityPredictionSimulation` entry keeps its existing
behavior.

Alternatively, in PowerShell from `predictor-ui/ui`:

```powershell
$env:DISSECT_MOBILITY_CONFIGURE = '1'
npm run electron:build_and_run
```

Use the activated `dissect-fog` environment. When starting from an IDE that
does not inherit that environment, select its Python executable under
**Advanced settings**. Python, TensorFlow, psutil, Node/npm, a JDK, and the
existing simulator build are required, as for the existing multi-device runner.
For IMM/Kalman and XGBoost, install `predictor-ui/scripts/mobility/requirements-models.txt`
in that same Python environment. TCN uses the existing TensorFlow runtime. No npm
dependency is added. See [the model comparison guide](mobility-model-comparison.md).

## Workflow

1. Select **New mobility run**. Choose a movement strategy explicitly.
2. Set devices (1–100) and movement duration (104–1440 minutes).
3. For Random walk, choose minimum/maximum speed and movement radius. The
   radius must exceed maximum speed multiplied by the 60-second sample period.
4. For Nomadic, enter east,north waypoint offsets in metres, one pair per line,
   starting at 0,0. The default route ends at its last waypoint. To repeat, use
   a closed route ending at 0,0 and enable **Repeat closed route**.
5. Select the additional prediction models: **IMM/Kalman**, **XGBoost**, **TCN**.
   All three are selected initially; uncheck all to use only the existing models.
   Start with one device for a first demonstration. Optional settings include
   seed, start location, task drain allowance and Python path.
6. Click **Run mobility**. Observations are saved while the live preview shows
   the selected device's actual path, selected forecast, model lifecycle and
   global node loads. Switch devices or the shown shadow model without rerunning
   the simulation. New candidates become available after the first training snapshot.
7. After simulation, saved-model replay and existing audits must pass. Only
   then does the UI automatically open the completed result in historical replay.

The first preview can take a few seconds while Java compilation and the Python
worker start. Wall-clock preview updates are batched; fast simulation events
can appear together. This is a live observation preview, not a real-time-paced
animation. Historical replay retains frame stepping and playback controls.

**Cancel run** terminates this run's process tree. Cancelled/failed runs retain
their logs and partial files but are never presented as verified completion.
Closing the UI while a job is active asks whether to stop it. Renderer reloads
restore the current preview while the Electron main process is still alive;
closing the whole application does not resume a run later.

## Data and models

The selected strategy generates observations during simulation. Users do not
need to supply a result folder to start a new run. Run configuration and the
normal mobility CSV/JSON/model artifacts are saved automatically. This UI
does not create a separate train/validation/test benchmark split.

This version retains HoldPosition, ConstantVelocity and the existing independent
per-device LSTM lifecycle. IMM/Kalman, XGBoost and TCN run as shadow comparisons.
XGBoost and TCN are fitted during this run from each device's mature training
snapshot; IMM/Kalman estimates state from observed positions using fixed parameters.
The three additions do not participate in serving-model promotion or fallback.
Connection changes and offloads continue using the existing simulator strategy;
displaying a forecast does not make it control those decisions.

The minimum duration comes from the existing lifecycle audit. The drain
allowance is a separate limit for remaining workload, not additional movement
sampling or training. Increasing it does not fabricate observations.

## Saved files and implementation

- Session config, `effective-config.json`, `progress.json`, `live.jsonl`,
  `runner.log` and `launcher.log`: `simulator/sim_res/mobility-ui/<session-id>/`.
- Full audited results and `scaling.csv`: the existing
  `simulator/sim_res/mobility-multi/<batch-id>/` tree. The UI shows both paths.
- The append-only live journal is read incrementally, in bounded chunks.
  The renderer keeps one device path on screen and bounded observation history.
- Final result parsing uses a worker thread. Final status requires successful
  child exit plus the unchanged completed-result reader's validation.
- **Open mobility results** continues to open previously saved completed runs.

## Checks

```powershell
# Repository root
python -m unittest discover -s predictor-ui/scripts/mobility -p test_mobility_ui.py
# predictor-ui/ui
node --test test/mobility-job.test.cjs test/mobility-ipc.test.cjs
npm run build
```

Manual acceptance on the user's platform: select each strategy and run one
device; watch live observations; verify automatic replay; cancel another run;
reopen an older 100-device result and verify that device switches/frame stepping
remain responsive. No new 100-device experiment is needed for this first UI check.
