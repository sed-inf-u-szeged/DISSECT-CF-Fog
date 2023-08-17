# Prediction settings in DISSECT-CF-Fog


## Installation and settings
1. Install
    * Node.js <small>(>= 18.16.1)</small>
    * Angular <small>(>= 16.1.3)</small>
    * Python <small>(>= 3.11.4)</small>
2. Install <b>[requirements.txt](https://github.com/andrasmarkus/dissect-cf/blob/prediction/src/main/resources/prediction/scripts/requirements.txt)</b>.
3. In <b>[application files](https://github.com/andrasmarkus/dissect-cf/tree/prediction/src/main/java/hu/u_szeged/inf/fog/simulator/prediction/communication/applications)</b> update start commands if needed:

```java
@Override
public Process openWindows() throws Exception {
    return Runtime.getRuntime().exec(String.format("C:/Windows/System32/cmd.exe /c cd %s & start npm.cmd run electron:build_and_run", getProjectLocation()));
}

@Override
public Process openLinux() throws Exception {
    return Runtime.getRuntime().exec("<Start command for Linux>");
}

@Override
public Process openMac() throws Exception {
    return Runtime.getRuntime().exec("<Start command for MacOS>");
}
```

## Prediction without UI
Take the example in <b>[PredictionSim.java](https://github.com/andrasmarkus/dissect-cf/blob/prediction/src/main/java/hu/u_szeged/inf/fog/simulator/demo/PredictionSim.java)</b>

* Create SimulationSettings object which is responsible for prediction settings.

```java
SimulationSettings simulationSettings = new SimulationSettings(
    new ExportSettings(true, "D:/", true, false, true),
    new PredictionSettings(
        64,
        64,
        256,
        new PredictionSettings.SmoothingSettings(
                48,
                5
        ),
        true
    ),
    new PredictorSettings(
        PredictorSettings.PredictorEnum.ARIMA,
        Map.of(
                "p_value", 3,
                "d_value", 0,
                "q_value", 0
        ),
        null
    )
);
```
* Predictor model's <b>hyperparameters and options</b> <b>must match</b> their template in <b>[PredictorTemplate.getAll()](https://github.com/andrasmarkus/dissect-cf/blob/prediction/src/main/java/hu/u_szeged/inf/fog/simulator/prediction/settings/predictor/PredictorTemplate.java)</b>
```java
// PredictorTemplate
Arrays.asList(
    new Parameter("P value").required().setType(Parameter.ParameterType.TEXT).setDefaultValue("3"),
    new Parameter("D value").required().setType(Parameter.ParameterType.TEXT).setDefaultValue("0"),
    new Parameter("Q value").required().setType(Parameter.ParameterType.TEXT).setDefaultValue("0")
)
```

```java
// SimulationSettings
Map.of(
    "p_value", 3,
    "d_value", 0,
    "q_value", 0
),
```
* If the Parameter ID is not provided, it is calculated from the label as follows - ```label.toLowerCase().replaceAll(" ", "_");```
* Add <b>PredictorApplication</b> to the applications:
```java
predictionSimulation.addApplications(
    new PredictorApplication()
);
```

After you start the simulation the predictor application will show up in a terminal window.

## Prediction with UI
Take the example in <b>[PredictionSimWithEUI.java](https://github.com/andrasmarkus/dissect-cf/blob/prediction/src/main/java/hu/u_szeged/inf/fog/simulator/demo/PredictionSimWithEUI.java)</b>

No need for SimulationSettings. All settings come from the UI.

* Add <b>PredictorApplication</b> and <b>ElectronApplication</b> to the applications:
```java
predictionSimulation.addApplications(
    new PredictorApplication(),
    new ElectronApplication()
);
```

After you start the simulation the predictor and the UI application will show up in a terminal window.

## LSTM model
Each LSTM model can be used only with specific simulations. The model's <b>parameters must match the simulation parameters</b>. 
For example: 
* Future model

| Settings | Simulation | LSTM model |
|:----------:|:----------:|:----------:|
| Batch size | 256 | input_size:256 |
| Prediction length | 64 | output_size:64 |

* Test model

| Settings | Simulation | LSTM model |
|:----------:|:----------:|:----------:|
| Batch size | 256 | input_size:192 <small><i>(Batch size - Test length)</i></small> |
| Test length | 64 | output_size:64 |

Use <b>[lstm_trainer.py](https://github.com/andrasmarkus/dissect-cf/blob/prediction/src/main/resources/prediction/scripts/predictor_models/lstm_trainer.py)</b> script to train LSTM models.

* The followings need to be set:

```python
if __name__ == "__main__":
    main({
        "model_name": "model_i256_o64.h5",
        "datasets_directory": "D:/train_datasets/",  # Directory containing *.csv files.
        "model_output_location": "D:/train_datasets/",
        "training_feature_index": 3,  # Column index in .csv files that trainer uses for training.
        "scale": True,
        "smoothing": {
            "window_size": 48,
            "polynomial_degree": 5
        },
        "input_size": 256,
        "output_size": 64,
        "loss_function": "mean_squared_error",
        "optimizer": "Adam",
        "epochs": 3
    })
```

After running the script, the model will be generated in the output  directory.

## Prediction result
Currently prediction result can be found in <b>[PliantApplicationStrategy.java](https://github.com/andrasmarkus/dissect-cf/blob/prediction/src/main/java/hu/u_szeged/inf/fog/simulator/application/strategy/PliantApplicationStrategy.java)</b>

```java
PredictionResult result = PredictionResult.get();
if (result != null) {
    // Handle prediction result
}
```

## Easier testing
For testing purposes use <b>disableApplications()</b> which prevents applications from opening. 

```java
PredictionSimulation predictionSimulation = new PredictionSimulation(new PredictionSim());
predictionSimulation.addSimulationSettings(simulationSettings);
predictionSimulation.addApplications(
    new PredictorApplication()
);
predictionSimulation.disableApplications(); // <--- Disable opening.
predictionSimulation.simulate();
```