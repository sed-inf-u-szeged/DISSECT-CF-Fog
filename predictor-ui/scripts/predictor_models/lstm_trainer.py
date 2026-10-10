import json
import os
import sys
from pathlib import Path

import numpy as np
import pandas as pd
import tensorflow as tf
from sklearn.utils import shuffle

SCRIPTS_DIRECTORY = Path(__file__).resolve().parents[1]
if str(SCRIPTS_DIRECTORY) not in sys.path:
    sys.path.insert(0, str(SCRIPTS_DIRECTORY))

from app_utils.preprocessor import Preprocessor
from model_registry import ModelRegistry


def send_to_pyshell(data):
    print(json.dumps({"pyshell_data": data}))


def get_from_args():
    return json.loads(sys.argv[1])


def load_file_paths(datasets_directory):
    directory = Path(datasets_directory).expanduser()
    return sorted(directory.glob("*.csv"))


def load_dataframes(file_paths):
    result = []
    for path in file_paths:
        dataframe = pd.read_csv(path, sep=";")

        # Remove only empty unnamed columns created by trailing separators.
        empty_columns = [
            column for column in dataframe.columns
            if str(column).startswith("Unnamed:")
            and dataframe[column].isna().all()
        ]
        result.append(dataframe.drop(columns=empty_columns))

    return result


def prepare_dataframes(settings, dataframes):
    result = []

    for dataframe in dataframes:
        for column in dataframe.columns:
            series = pd.to_numeric(
                dataframe[column].astype(str).str.replace(
                    ",", ".", regex=False
                ),
                errors="coerce"
            )
            values = series.to_numpy(dtype=float)
            finite_positions = np.flatnonzero(np.isfinite(values))

            if len(finite_positions) == 0:
                continue

            # Trim empty padding, but keep internal gaps at their positions.
            start = finite_positions[0]
            end = finite_positions[-1] + 1
            result.append(pd.DataFrame({"data": values[start:end]}))

    return result


def make_data(
        dataframes,
        input_shape,
        output_shape,
        smoothing=None,
        scale=True,
        shuffle_datasets=True):
    if input_shape < 1 or output_shape < 1:
        raise ValueError("Input and output sizes must be positive")

    x, y = [], []
    skipped = 0
    total_size = input_shape + output_shape

    for dataframe in dataframes:
        values = dataframe["data"].to_numpy(dtype=float)

        for start in range(len(values) - total_size + 1):
            window = values[start:start + total_size]

            # Do not join observations across missing or invalid values.
            if not np.all(np.isfinite(window)):
                skipped += 1
                continue

            raw_input = window[:input_shape]
            raw_target = window[input_shape:]

            processed_input, scaler = Preprocessor.process(
                data=raw_input,
                smoothing=smoothing,
                scale=scale
            )
            processed_target = Preprocessor.transform_targets(
                data=raw_target,
                scaler=scaler
            )

            x.append(processed_input["data"].to_numpy(dtype=float))
            y.append(processed_target)

    if skipped:
        sys.stderr.write(
            f"Skipped {skipped} windows containing invalid values\n"
        )

    x = np.asarray(x, dtype=np.float32).reshape(
        (-1, input_shape, 1)
    )
    y = np.asarray(y, dtype=np.float32).reshape(
        (-1, output_shape)
    )

    if not np.all(np.isfinite(x)) or not np.all(np.isfinite(y)):
        raise ValueError("Preprocessing produced non-finite training values")

    if shuffle_datasets and len(x):
        x, y = shuffle(x, y, random_state=0)

    return x, y


def get_model(settings):
    model = tf.keras.models.Sequential([
        tf.keras.layers.Input(
            shape=(int(settings["inputSize"]), 1)
        ),
        tf.keras.layers.LSTM(50, return_sequences=True),
        tf.keras.layers.Dropout(0.2),
        tf.keras.layers.LSTM(50, return_sequences=True),
        tf.keras.layers.Dropout(0.2),
        tf.keras.layers.LSTM(50, return_sequences=True),
        tf.keras.layers.Dropout(0.2),
        tf.keras.layers.LSTM(50),
        tf.keras.layers.Dropout(0.2),
        tf.keras.layers.Dense(int(settings["outputSize"]))
    ])
    model.summary()
    return model


def start_training(settings, model, x_train, y_train):
    model.compile(
        optimizer=settings["optimizer"],
        loss=settings["lossFunction"]
    )
    model.fit(
        x_train,
        y_train,
        epochs=int(settings["epochs"]),
        verbose=1
    )


def save_model(settings, model):
    output_directory = Path(
        settings["modelOutputLocation"]
    ).expanduser().resolve()
    output_directory.mkdir(parents=True, exist_ok=True)

    model_name = str(settings["modelName"]).strip()
    if not model_name:
        raise ValueError("modelName must not be empty")
    if not model_name.lower().endswith((".h5", ".keras")):
        model_name += ".h5"

    model_path = output_directory / model_name
    model.save(str(model_path))
    return model_path


def register_candidate(settings, model_path):
    registry = ModelRegistry()
    registered_model = registry.register_artifact(
        source_path=model_path,
        feature_name="shared",
        model_type="LSTM",
        metadata={
            "input_size": int(settings["inputSize"]),
            "output_size": int(settings["outputSize"]),
            "loss_function": settings["lossFunction"],
            "optimizer": settings["optimizer"],
            "epochs": int(settings["epochs"])
        },
        preprocessing={
            "scale": settings["scale"],
            "smoothing": settings.get("smoothing"),
            "strategy": "input_window_v1",
            "target_smoothing": False
        },
        activate=False
    )

    artifact_path = (
        registry.store_directory / registered_model["artifact_path"]
    ).resolve()

    result = {
        "feature_name": registered_model["feature_name"],
        "model_type": registered_model["model_type"],
        "model_version": registered_model["model_version"],
        "artifact_path": str(artifact_path)
    }

    result_path = (
        Path(settings["modelOutputLocation"]).expanduser().resolve()
        / "lstm_training_result.json"
    )
    temporary_path = result_path.with_suffix(
        result_path.suffix + ".tmp"
    )

    with temporary_path.open(
            "w", encoding="utf-8", newline="\n") as result_file:
        json.dump(
            result, result_file,
            indent=2, ensure_ascii=False, sort_keys=True
        )
        result_file.write("\n")
        result_file.flush()
        os.fsync(result_file.fileno())

    os.replace(temporary_path, result_path)
    return result


def main(settings):
    send_to_pyshell({
        "progress": 1, "message": "Loading file paths"
    })
    file_paths = load_file_paths(settings["datasetsDirectory"])
    if not file_paths:
        sys.exit(1225)

    send_to_pyshell({
        "progress": 2, "message": "Loading dataframes"
    })
    dataframes = load_dataframes(file_paths)

    send_to_pyshell({
        "progress": 3, "message": "Preparing dataframes"
    })
    dataframes = prepare_dataframes(settings, dataframes)

    send_to_pyshell({
        "progress": 4, "message": "Formatting training windows"
    })
    x_train, y_train = make_data(
        dataframes=dataframes,
        input_shape=int(settings["inputSize"]),
        output_shape=int(settings["outputSize"]),
        smoothing=settings.get("smoothing"),
        scale=settings["scale"],
        shuffle_datasets=True
    )
    if len(x_train) == 0:
        sys.stderr.write("No valid training windows were found\n")
        sys.exit(1226)

    model = get_model(settings)

    send_to_pyshell({
        "progress": 5, "message": "Start training"
    })
    start_training(settings, model, x_train, y_train)

    send_to_pyshell({
        "progress": 6, "message": "Saving model"
    })
    model_path = save_model(settings, model)

    send_to_pyshell({
        "progress": 7, "message": "Registering candidate model"
    })
    registered_model = register_candidate(settings, model_path)

    send_to_pyshell({
        "progress": 8,
        "message": "Done",
        "model": registered_model
    })


if __name__ == "__main__":
    main(get_from_args())