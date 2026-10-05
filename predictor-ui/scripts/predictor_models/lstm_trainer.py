import json
import os
import sys
from os import listdir
from os.path import isfile, join
from pathlib import Path

import numpy as np
import tensorflow as tf
import pandas as pd
from sklearn.utils import shuffle
from sklearn import preprocessing
from scipy.signal import savgol_filter

SCRIPTS_DIRECTORY = Path(__file__).resolve().parents[1]

if str(SCRIPTS_DIRECTORY) not in sys.path:
    sys.path.insert(0, str(SCRIPTS_DIRECTORY))

from model_registry import ModelRegistry


class Preprocessor:

    @staticmethod
    def process(data, smoothing, scale):
        timestamp = [i for i in range(0, len(data))]
        dataframe = pd.DataFrame({"data": data, "timestamp": timestamp})

        if smoothing is not None:
            dataframe["data"] = savgol_filter(
                dataframe["data"].values,
                smoothing["windowSize"],
                smoothing["polynomialDegree"]
            )

        scaler = None
        if scale:
            scaler = preprocessing.MinMaxScaler(feature_range=(0, 1))
            normed = scaler.fit_transform(
                np.array(dataframe["data"]).reshape(-1, 1)
            )
            dataframe["data"] = normed.flatten().tolist()

        dataframe.reset_index(drop=True, inplace=True)
        return dataframe, scaler

    @staticmethod
    def create_test_data(data, test_size):
        return data.iloc[:len(data) - test_size], data.iloc[-test_size:]

    @staticmethod
    def get_data_inverse(data, scaler):
        return scaler.inverse_transform(
            np.array(data).reshape(-1, 1)
        ).flatten().tolist()


def get_path(path):
    if path[-1] == "/" or path[-1] == "\\":
        return path
    return path + "/"


def replace_commas(cell):
    return str(cell).replace(",", ".")


def send_to_pyshell(data):
    print(json.dumps({"pyshell_data": data}))


def get_from_pyshell():
    return json.loads(input())


def get_from_args():
    return json.loads(sys.argv[1])


def make_data(
        dataframes,
        input_shape,
        output_shape,
        shuffle_datasets=True
):
    x, y = [], []

    for dataframe in dataframes:
        data = dataframe["data"].values
        to = len(data) - (input_shape + output_shape) + 1

        for i in range(0, to):
            x.append(data[i:i + input_shape])
            y.append(
                data[
                    i + input_shape:
                    i + input_shape + output_shape
                ]
            )

    if shuffle_datasets:
        return shuffle(
            np.array(x),
            np.array(y),
            random_state=0
        )

    return np.array(x), np.array(y)


def load_file_paths(datasets_directory):
    return [
        join(datasets_directory, file_name).replace("\\", "/")
        for file_name in listdir(datasets_directory)
        if (
            isfile(join(datasets_directory, file_name))
            and file_name.endswith(".csv")
        )
    ]


def load_dataframes(file_paths):
    result = []

    for path in file_paths:
        dataframe = pd.read_csv(path, sep=";")

        # The exported CSV contains one trailing empty column.
        dataframe = dataframe.drop(
            dataframe.columns[-1],
            axis=1
        )

        result.append(dataframe)

    return result


def prepare_dataframes(settings, dataframes):
    result = []

    for dataframe in dataframes:
        df = dataframe.copy()

        for column in df.columns:
            # Process every feature independently.
            series = (
                df[column]
                .apply(replace_commas)
                .astype(float)
            )
            series = series[series.notna()]

            try:
                dataframe_result, scaler = Preprocessor.process(
                    data=series.tolist(),
                    smoothing={
                        "windowSize": int(
                            settings["smoothing"]["windowSize"]
                        ),
                        "polynomialDegree": int(
                            settings["smoothing"]["polynomialDegree"]
                        )
                    },
                    scale=settings["scale"],
                )
            except ValueError:
                sys.stderr.write(
                    f"Skipped {column}, "
                    f"not enough data for window\n"
                )
                continue

            result.append(dataframe_result)

    return result


def get_model(settings):
    model = tf.keras.models.Sequential()

    model.add(
        tf.keras.layers.LSTM(
            units=50,
            return_sequences=True,
            input_shape=(int(settings["inputSize"]), 1)
        )
    )
    model.add(tf.keras.layers.Dropout(0.2))

    model.add(
        tf.keras.layers.LSTM(
            units=50,
            return_sequences=True
        )
    )
    model.add(tf.keras.layers.Dropout(0.2))

    model.add(
        tf.keras.layers.LSTM(
            units=50,
            return_sequences=True
        )
    )
    model.add(tf.keras.layers.Dropout(0.2))

    model.add(
        tf.keras.layers.LSTM(units=50)
    )
    model.add(tf.keras.layers.Dropout(0.2))

    model.add(
        tf.keras.layers.Dense(
            units=int(settings["outputSize"])
        )
    )

    model.summary()
    return model


def start_training(
        settings,
        model,
        x_train,
        y_train
):
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

    output_directory.mkdir(
        parents=True,
        exist_ok=True
    )

    model_name = str(
        settings["modelName"]
    ).strip()

    if not model_name:
        raise ValueError(
            "modelName must not be empty"
        )

    if not model_name.lower().endswith(
        (".h5", ".keras")
    ):
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
            "epochs": int(settings["epochs"]),
        },
        preprocessing={
            "scale": settings["scale"],
            "smoothing": settings["smoothing"],
        },
        activate=False,
    )

    artifact_path = (
        registry.store_directory
        / registered_model["artifact_path"]
    ).resolve()

    result = {
        "feature_name": registered_model["feature_name"],
        "model_type": registered_model["model_type"],
        "model_version": registered_model["model_version"],
        "artifact_path": str(artifact_path),
    }

    result_path = (
        Path(settings["modelOutputLocation"])
        / "lstm_training_result.json"
    )
    temporary_result_path = result_path.with_suffix(
        result_path.suffix + ".tmp"
    )

    with temporary_result_path.open(
        "w",
        encoding="utf-8",
        newline="\n",
    ) as result_file:
        json.dump(
            result,
            result_file,
            indent=2,
            ensure_ascii=False,
            sort_keys=True,
        )
        result_file.write("\n")
        result_file.flush()
        os.fsync(result_file.fileno())

    os.replace(
        temporary_result_path,
        result_path
    )

    return result


def main(settings):
    send_to_pyshell({
        "progress": 1,
        "message": "Loading file paths"
    })

    file_paths = load_file_paths(
        get_path(settings["datasetsDirectory"])
    )

    if len(file_paths) < 1:
        sys.exit(1225)

    send_to_pyshell({
        "progress": 2,
        "message": "Loading dataframes"
    })

    dataframes = load_dataframes(file_paths)

    send_to_pyshell({
        "progress": 3,
        "message": "Preparing dataframes"
    })

    dataframes = prepare_dataframes(
        settings,
        dataframes
    )

    model = get_model(settings)

    send_to_pyshell({
        "progress": 4,
        "message": "Formatting dataframes"
    })

    x_train, y_train = make_data(
        dataframes=dataframes,
        input_shape=int(settings["inputSize"]),
        output_shape=int(settings["outputSize"]),
        shuffle_datasets=True
    )

    if len(x_train) < 1 or len(y_train) < 1:
        sys.exit(1226)

    send_to_pyshell({
        "progress": 5,
        "message": "Start training"
    })

    start_training(
        settings,
        model,
        x_train,
        y_train
    )

    send_to_pyshell({
        "progress": 6,
        "message": "Saving model"
    })

    model_path = save_model(
        settings,
        model
    )

    send_to_pyshell({
        "progress": 7,
        "message": "Registering candidate model"
    })

    registered_model = register_candidate(
        settings,
        model_path
    )

    send_to_pyshell({
        "progress": 8,
        "message": "Done",
        "model": registered_model
    })


if __name__ == "__main__":
    main(get_from_args())