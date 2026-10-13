# Prediction and Model Lifecycle

This module contains the Python prediction and training code, together
with the Electron UI for the DISSECT-CF-Fog simulator.

The Java simulator collects feature data, manages model lifecycle states
and requests predictions. Python performs prediction and model training.
The UI displays saved results.

## Setup and entry points

The prediction workflow requires the Java simulator, a Python environment
with the predictor dependencies, and Node.js dependencies for the UI.

See [simulator setup](../simulator/README.md) for Java and Maven setup.

Install UI dependencies from `predictor-ui/ui`:

```sh
npm install
```

Use a Python environment compatible with the project's dependency file.
Install its dependencies with the same interpreter used by the simulator:

```sh
python -m pip install -r predictor-ui/scripts/requirements.txt
```

Replace the placeholder with the actual dependency-file path.
Python is required for this prediction workflow, even when it is optional
for other simulator examples.

The current Java demo entry point is:

```text
simulator/src/main/java/hu/u_szeged/inf/fog/simulator/demo/PredictionSimulation.java
```

Its configuration must select the intended Python environment and
prediction settings.

For viewing existing results, see the [UI guide](ui/README.md).
Starting the UI alone does not start a simulation or train a model.

## Data-driven training

LSTM training is triggered by data readiness rather than a fixed fifth
simulation hour.

Each feature is checked separately. Features can therefore start
training at different simulation times.

For a sequence model with input length I, output length O and a minimum
of W training windows, stride-one window construction requires:

```text
minimum data points = I + O + W - 1
```

With the current LSTM settings:

```text
input length = 192
output length = 64
minimum training windows = 32

minimum data points = 192 + 64 + 32 - 1 = 287
```

This is a data requirement, not a guarantee of good prediction quality.
The selected training data must also satisfy the controller's validity
checks.

Training readiness and evaluation readiness are separate requirements.
Having enough data to train does not immediately make a model ACTIVE.

## Training and prediction windows

Training uses a snapshot of the data available at the training attempt.

The current LSTM uses 192 input values to predict 64 target values.

For a 256-point historical prediction frame:

- The first 192 points provide the test input.
- The following 64 actual points provide the test targets.
- Future prediction uses the latest 192 available points to forecast
  the next 64 points.

Input preprocessing must remain consistent between training and
prediction. The updated pipeline fits scaling on input data and keeps
evaluation targets in their original units.

Historical test errors and future online evaluation are different
measurements.

## Shadow evaluation

After training, the candidate produces forecasts for future target
timestamps while the serving model continues to operate.

Each forecast records its target timestamp. It can be evaluated only
after the actual value for that timestamp becomes available.

This wait is required because the target is in the simulated future.
It is not an additional fixed training delay.

The current policy requires 64 matched target samples before making
a complete-window quality decision.

For each model version and feature, the updated coordinator retains
the first registered forecast for a target timestamp. Later forecasts
for the same target are not counted again.

The evaluator uses the most recent resolved targets up to its configured
window size. Forecasts can have different lead times; this rule does
not establish a fixed-horizon comparison between models.

## Metrics and acceptance

The evaluator computes all metrics from matched predictions and actual
values in the evaluation window.

```text
error = prediction - actual

MAE = mean(|error|)
MSE = mean(error²)
RMSE = sqrt(MSE)
SMAPE = mean(200 × |error| / (|prediction| + |actual|))
```

A SMAPE term is zero when both prediction and actual are zero.

Within-tolerance accuracy is:

```text
allowed error = max(absolute tolerance, relative tolerance × |actual|)
hit = |error| <= allowed error
accuracy = 100 × number of hits / matched samples
```

Accuracy is not calculated by converting MAE or RMSE into a percentage.

A candidate must have enough matched samples and satisfy all configured
quality checks:

- Accuracy at or above the minimum.
- MAE, MSE, RMSE and SMAPE at or below their respective maxima.

Passing evaluation makes a candidate READY. Lifecycle selection then
determines whether it becomes ACTIVE.

The configured trusted baseline has special fallback behavior and is
not subject to the same degradation rule as an ordinary candidate.

## Monitoring and retraining

An ACTIVE non-trusted model continues to receive online evaluation.

When a complete evaluation window fails the policy, the model becomes
DEGRADED and the lifecycle manager can fall back to the trusted baseline.

Retraining uses a new snapshot of the available data. It can contain
both earlier observations and newly collected observations within the
selected training window.

Retraining is not simply repeated evaluation of the original snapshot.

Current training defaults include:

| Setting | Value |
| --- | ---: |
| Minimum training windows | 32 |
| Minimum new data points between attempts | 64 |
| Training-window growth after shadow failure | 64 |
| Maximum training data points | 4096 |

The new-data condition is measured from the previous training attempt,
not from the moment evaluation fails. An eligible retry may therefore
start without another full waiting period after failure.

After shadow failure, the requested training window grows, subject to
its limit. Degradation triggers a retry but does not itself apply that
shadow-failure growth rule.

## Model versions

Names such as `LSTM256-v1`, `LSTM256-v2` and `LSTM256-v3` identify
successive candidate versions.

A higher version number does not prove that the previous version was
ACTIVE. A previous candidate may have failed shadow evaluation.

Each new version must be evaluated before it can serve as an active
model.

## Configuration and implementation

Java prediction sources are under:

```text
simulator/src/main/java/hu/u_szeged/inf/fog/simulator/prediction/
```

Key files include:

| File | Responsibility |
| --- | --- |
| `settings/OnlineTrainingSettings.java` | Training readiness and retry data requirements |
| `settings/PairPredictionSettings.java` | Prediction and sequence settings |
| `settings/LifecycleSettings.java` | Evaluation thresholds and baseline settings |
| `LstmOnlineLifecycleController.java` | LSTM training attempts and candidate lifecycle |
| `OnlineModelAdapter.java` | Model adapter contract |
| `LstmModelAdapter.java` | LSTM-specific adapter |
| `lifecycle/ShadowEvaluator.java` | Matching predictions and calculating metrics |
| `lifecycle/LifecyclePolicy.java` | Quality acceptance checks |
| `lifecycle/LifecycleCoordinator.java` | Per-model evaluation and state transitions |
| `lifecycle/LifecycleManager.java` | Activation and fallback coordination |

The demo configuration can override settings. Inspect the selected run's
exported settings instead of assuming every run uses the same thresholds.

Permissive settings, such as minimum accuracy 0%, are useful for observing
the workflow. They do not demonstrate acceptable model quality.

## Tests

From the repository root, run the focused lifecycle checks:

```sh
mvn -f simulator/pom.xml test "-Dtest=LifecycleUniqueTargetTest,LifecycleCoordinatorTest,LifecyclePolicyTest,LifecycleToleranceConfigurationTest,ShadowEvaluatorToleranceTest"
```

This focused set passed 31 tests in the Windows development environment.

The [UI guide](ui/README.md) contains the separate six-test refresh command.

Unit tests verify specific behavior using controlled inputs. They do not
train and validate every model or establish real-world prediction quality.

## Current scope and remaining work

The workflow has been exercised with LSTM, ARIMA and Linear Regression.
Saved-run chart display and replay have also been checked on Windows.

Remaining work includes:

- Calibrating quality thresholds against representative data and
  application requirements.
- Verifying the latest changes in an integrated live run.
- Completing Linux runtime verification.
- Extending and testing model-specific training and prediction adapters.

The adapter interface is a foundation for adding models. GRU and
Transformer support is not automatic: each model still needs an
implementation that declares its data requirements, trains, saves,
loads and predicts correctly.

Existing result databases retain the metrics generated by the code
and settings used for those runs. UI updates do not recalculate them.