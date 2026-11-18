# Mobility prediction result replay

The existing Electron/Angular UI can now display completed single-device
shared-lifecycle runs from both official mobility strategies. It reads the
saved outputs; opening a result does not train a model or modify the run.

## Open a result

From `predictor-ui/ui`:

```powershell
npm run electron:build_and_run
```

Click **Open mobility results** and choose the individual run directory that
contains `run.properties`, not the parent batch directory. For example:

```text
simulator/sim_res/mobility-closed-loop/<batch>/random-walk/output/mobility-<id>
```

The existing `DISSECT_MOBILITY_RUN_DIR` environment variable is also supported.
The existing load prediction view and baseline-only mobility replay remain
available. The folder chooser can switch between completed mobility runs.

To check a directory without opening Electron:

```powershell
node check-mobility-results.js "<absolute-run-directory>"
```

Successful validation prints `MOBILITY_UI_DATA_OK`. It reports each version's
training windows, matched targets, first qualification, final state and selected
prediction count. This display check does not replace the closed-loop auditor.

## Interpretation

- **System selected** shows the prediction actually chosen by the shared Java
  manager. This can be HoldPosition while a candidate is being evaluated.
- **LSTM candidate**, **HoldPosition** and **ConstantVelocity** use the same
  candidate comparison origins from `retry_predictions.csv`. These comparisons
  start after the first fit. The older coverage forecasts are not substituted
  for missing LSTM comparison records.
- The trajectory shows observed positions up to the replay time. The diamond
  shows the issued one-step forecast. The optional target cross is explicitly
  retrospective and is off by default.
- Model state comes from lifecycle events up to the selected replay time.
  Final state is shown separately in the retrospective version table.
- Error curves and running metrics include only targets already observed by
  the replay time. The three candidate methods use matched common targets.
  Version filtering affects these comparisons, not the historical prediction
  that was issued at a different time.
- System-selected metrics cover its own matched stream, including service
  before the first LSTM fit. Its sample count can differ from the candidate
  comparison count.
- A version's first 64 targets determine qualification. The displayed running
  metrics do not rewrite that frozen decision. PASS followed by DEGRADED is
  valid; fewer than 64 targets remain awaiting evaluation.

## Inputs and compatibility

The legacy trajectory, coverage and node-accounting files are still validated.
RandomWalk uses its speed range instead of an invented fixed speed. When a
device only connects to one node, missing forecast-origin geometry is recovered
from the saved start, node radius and node separation metadata and checked.

The shared-lifecycle view additionally requires:

```text
mobility_samples.csv
retry_predictions.csv
retry_evaluations.csv
shared_selected_predictions.csv
shared_selected_evaluations.csv
shared_lifecycle_events.jsonl
shared-lifecycle.json
```

The reader checks run/device identity, prediction-target matching, position
errors, three-method comparison groups, selected ownership, qualification
timing, per-version counts, and lifecycle event ordering. Partial model bundles
produce an explicit error. They are not silently displayed as a baseline-only
run. The input limit remains 50 MiB for the combined files.

This increment supports one device. Multi-device dispatch, training policy,
simulation connection control, movement configuration, and model architecture
are unchanged. New simulation launching and strategy parameter editing from
inside the UI are separate tasks; this page opens completed results.

## Checks

```powershell
node --test test/mobility-ipc.test.cjs test/mobility-results.test.cjs
npm run build
npm test -- --watch=false --browsers=ChromeHeadless --include="src/app/shared/mobility-display.spec.ts" --include="src/app/components/mobility-view/mobility-view.component.spec.ts" --include="src/app/pages/scenario/scenario.component.spec.ts" --include="src/app/services/electron/electron.service.spec.ts"
```

The Node fixture is synthetic and is not experimental evidence of prediction
quality. Browser replay checks likewise use explicitly labelled synthetic data.
Actual saved Nomadic and RandomWalk results should also be opened on the target
Windows installation. Build size warnings are non-blocking: configured error
budgets remain unchanged.
