# DISSECT-CF-Fog Predictor UI

This Angular and Electron application displays prediction results saved by
the DISSECT-CF-Fog simulator.

## Setup

Run the following command in `predictor-ui/ui`:

```sh
npm install
```

Use the dependency versions declared in `package.json` and
`package-lock.json`. The current project uses Angular 16 and TypeScript 5.1.

The simulator and Python predictor have separate setup requirements.
See the parent predictor README and the simulator README.

## Start the UI

Build the application and open Electron:

```sh
npm run electron:build_and_run
```

If the UI has already been built and its Angular source has not changed:

```sh
npm run electron:run
```

Starting the UI reads existing results. It does not start model training
or a new simulation.

The browser development server can be started with `npm start`, but
database access requires the Electron API. Use Electron to inspect
simulation results.

## Select a result database

By default, the application selects a valid `database.db` under
`simulator/sim_res`, using the database file modification time.

To inspect a specific run, set `DISSECT_DATABASE_PATH` before starting
Electron.

### Windows PowerShell

Replace the example path with an existing database path:

```powershell
$env:DISSECT_DATABASE_PATH = "C:\path\to\run\database.db"
try {
    npm run electron:build_and_run
}
finally {
    Remove-Item Env:\DISSECT_DATABASE_PATH -ErrorAction SilentlyContinue
}
```

### Linux shell

Replace the example path with an existing database path:

```sh
DISSECT_DATABASE_PATH="/path/to/run/database.db" npm run electron:build_and_run
```

The database is opened in read-only mode.

## Charts and replay

The main page groups predictions by prediction number and allows
navigation between available groups.

Use **View simulation** to replay the saved predictions for a selected
feature and predictor.

Chart filters control the visibility of original data, preprocessed data,
test predictions and future predictions.

The replay window uses a snapshot captured when it is opened.
Close and reopen it to load refreshed data from the main page.

A model without saved prediction rows will not appear in the prediction
charts. This can happen when a candidate has not passed shadow evaluation;
its absence alone does not indicate a chart error.

## Evaluation panels

### Latest online evaluation

This panel shows the latest lifecycle state and metrics saved for the
selected feature and predictor.

It does not show the lifecycle state at the historical replay frame.
Its values may therefore remain unchanged while replay advances.

- **Matched samples**: number of resolved samples in the evaluation window.
- **Within tolerance (%)**: percentage of evaluated predictions within
  the configured error tolerance.
- **MAE**: mean absolute error.
- **MSE**: mean squared error.
- **RMSE**: square root of MSE.
- **SMAPE (%)**: symmetric mean absolute percentage error.

Within-tolerance accuracy is calculated using:

```text
allowed error = max(absolute tolerance, relative tolerance × |actual|)
hit = |prediction - actual| <= allowed error
accuracy = 100 × hits / matched samples
```

For runs generated with the updated lifecycle coordinator, repeated
forecasts for the same target timestamp are counted once per model
version and feature. The first registered forecast is retained.

Existing databases are not recalculated by opening them in the UI.

### This frame: test errors

This panel shows the saved test errors for the current prediction frame.

These errors compare the frame's test predictions with its actual test
data. They use a different evaluation window from the latest online
metrics, so the values need not match.

### Run settings

This panel displays the run identifier and lifecycle thresholds read
from a `simulation_settings*.json` file beside the selected database.

- **Not recorded** means the corresponding information was not saved.
- **No practical limit** represents an error limit set to the maximum
  Java double value.
- For legacy settings without an explicit absolute tolerance, the UI
  reports the legacy fallback value and displays an explanatory note.
- If no settings file is available, or multiple matching files exist,
  the UI displays a note instead of selecting a configuration arbitrarily.

A minimum accuracy of 0% allows activation without a positive hit rate,
provided the other activation conditions are satisfied.

An **ACTIVE** state under permissive thresholds does not demonstrate
acceptable prediction quality.

## Refresh behavior

Electron checks the selected database every five seconds and sends
updated data when its contents change.

The main page replaces its prediction snapshot so that existing records
can receive updated lifecycle metrics and run settings.

Within the same run, a manually selected prediction is retained when
still available. When the run changes, the previous snapshot is replaced.
Nonconsecutive prediction numbers are sorted into navigable pages.

## Tests

Run the focused refresh tests from `predictor-ui/ui`:

```sh
npm test -- --watch=false --browsers=ChromeHeadless --include=src/app/pages/scenario/scenario.component.spec.ts
```

This requires a Chrome installation available to the test launcher.

The six tests cover:

1. Updating metrics and state for an existing prediction.
2. Removing old predictions when the run changes.
3. Navigating nonconsecutive prediction numbers.
4. Retaining the selected prediction during refresh.
5. Clearing the page for an empty snapshot.
6. Unsubscribing when the component is destroyed.

Run the configured UI test suite with:

```sh
npm test
```

## Validation scope

The focused six-test refresh suite passed on Windows with ChromeHeadless.
The main page, LSTM chart replay and run-settings display were also
checked against a saved simulation database.

These checks do not establish model prediction quality or verify the
complete live database-to-UI workflow.

Linux runtime verification and formal quality-threshold calibration
remain pending.