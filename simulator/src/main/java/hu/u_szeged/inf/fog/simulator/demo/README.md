## Prediction module

DISSECT-CF-Fog provides a prediction module for forecasting simulation metrics and using the predicted values during resource-management decisions.

The prediction framework is implemented on the Java side of the simulator, while the actual time-series forecasting algorithms are implemented in Python.
A prediction-enabled simulation can run multiple predictors in parallel. 
The Python predictor and visualization environment are documented separately in `predictor-ui/README.md`.

### Running a prediction-enabled simulation

`PredictionSimulation` provides an example entry point for running the simulator with prediction support.

A simulation configures one or more predictors using `PairPredictionSettings`.

For example:

```java
predictionSettings.add(new PairPredictionSettings(
        "Arima128",
        new PredictionSettings(
                64,
                64,
                128,
                new PredictionSettings.SmoothingSettings(48, 5),
                true,
                0
        ),
        PredictorSettings.getPredictorSettings(
                PredictorSettings.PredictorEnum.ARIMA
        )
));
```

Each configured predictor is executed in a separate Python process.

`PredictionSettings` controls the amount of historical data used by a prediction and the preprocessing and output configuration.

For example, the following sample configures the prediction length, test size, batch size, smoothing and related prediction options.
The batch size determines how many observations must be available before a feature is eligible for prediction.

```java
new PredictionSettings(
        64,
        64,
        128,
        new PredictionSettings.SmoothingSettings(48, 5),
        true,
        0
)
```

Collecting the required number of observations does not by itself guarantee that a prediction will be executed. Prediction requests are initiated by the simulation strategy when predictions are required for a decision.

### Java-Python communication

Communication is performed through the process standard streams:

- The Java side sends feature data through the standard input of the Python process.
- For each prediction request, the Python process returns a JSON response through standard output.
- The Java prediction module parses this response and converts it into the internal prediction representation.
- Standard output is therefore used as a machine-readable communication channel. 
- Diagnostic Python output should not be mixed with the JSON responses written to this stream.

### Prediction results

A generated prediction contains the data required both for simulation decisions and for later evaluation:

- original time-series data
- preprocessed data
- test data
- predictions over the test interval
- future predicted values
- predictor configuration
- prediction execution time
- error metrics

The currently calculated prediction error metrics include:

- RMSE – Root Mean Squared Error
- MSE – Mean Squared Error
- MAE – Mean Absolute Error

When multiple predictors are configured, their predictions are evaluated independently.

### Prediction output

Prediction-enabled simulations create their results under the simulator result directory:

```text
sim_res/
└── <simulation_timestamp>/
```

Depending on the configuration, a prediction-enabled run can produce files such as:

```text
database.db
dataset_<timestamp>.csv
predictions_<timestamp>.csv
error_metrics_<timestamp>.csv
simulation_settings_<timestamp>.json
log.txt
prediction_error_logs.txt
electron.log
```

#### SQLite database

When SQLite output is enabled, the prediction module creates a database for the simulation.

For each monitored feature, two main types of tables are created:

```text
<feature>_feature_raw
<feature>_feature_prediction
```

The raw table stores the feature observations collected during the simulation.

The prediction table stores generated prediction results and related information such as the predictor configuration, prediction data, error metrics and execution time.

Different predictors can generate records for the same feature. The predictor information stored with each prediction identifies which model produced the result.

The SQLite database also serves as the data source of the optional Predictor UI.

#### CSV and JSON output

The prediction module can additionally export simulation and prediction data to files.

`dataset_<timestamp>.csv` contains the feature values collected during the simulation.

`predictions_<timestamp>.csv` contains exported prediction results.

`error_metrics_<timestamp>.csv` contains the calculated prediction error metrics.

`simulation_settings_<timestamp>.json` stores the configuration associated with the simulation and its predictors. This file is useful for identifying and reproducing prediction experiments.

#### Logs

Prediction-enabled simulations can generate separate logs for the involved components.

`log.txt` contains the general simulation log.

`prediction_error_logs.txt` contains output associated with the Python predictor processes.

When the Predictor UI is enabled, `electron.log` contains output from the Electron process.

### Predictor UI

Prediction results can optionally be visualized using the Electron/Angular Predictor UI.

When enabled, the simulator launches the UI together with the prediction components. The UI reads the SQLite database generated during the simulation and displays the available time-series and prediction results. 