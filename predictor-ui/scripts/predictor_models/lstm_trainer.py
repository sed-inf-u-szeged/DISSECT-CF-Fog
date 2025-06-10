import json
import os
import sys
from os import listdir
from os.path import isfile, join

import numpy as np
import tensorflow as tf
import pandas as pd
from sklearn.utils import shuffle
from sklearn import preprocessing
from scipy.signal import savgol_filter


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
            normed = scaler.fit_transform(np.array(dataframe["data"]).reshape(-1, 1))
            dataframe["data"] = normed.flatten().tolist()

        dataframe.reset_index(drop=True, inplace=True)
        return dataframe, scaler

    @staticmethod
    def create_test_data(data, test_size):
        return data.iloc[:len(data) - test_size], data.iloc[-test_size:]

    @staticmethod
    def get_data_inverse(data, scaler):  # TODO 50 - 49.99999999999999, 3147 - 3147.0000000000005
        return scaler.inverse_transform(np.array(data).reshape(-1, 1)).flatten().tolist()


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


def make_data(dataframes, input_shape, output_shape, shuffle_datasets=True):

    x, y = [], []
    for dataframe in dataframes:
        data = dataframe["data"].values
        to = len(data) - (input_shape + output_shape) + 1

        for i in range(0, to):
            x.append(data[i:i + input_shape])
            y.append(data[i + input_shape:i + input_shape + output_shape])

    if shuffle_datasets:
        return shuffle(np.array(x), np.array(y), random_state=0)
    return np.array(x), np.array(y)


def load_file_paths(datasets_directory):
    return [join(datasets_directory, f).replace("\\", "/") for f in listdir(datasets_directory)
            if isfile(join(datasets_directory, f)) and f.endswith(".csv")]


def load_dataframes(file_paths):
    result = []
    for path in file_paths:
        df = pd.read_csv(path, sep=";")
        df = df.drop(df.columns[-1], axis=1)  # TODO Why Unnamed: 10
        result.append(df)
    return result


def prepare_dataframes(settings, dataframes):
    result = []
    for dataframe in dataframes:
        df = dataframe.copy()

        for column in df.columns:
            df[column] = df[column].apply(replace_commas).astype(float).tolist()
            df = df[df[column].notna()]
            try:
                df_result, scaler = Preprocessor.process(
                    data=df[column].tolist(),
                    smoothing={
                    "windowSize": int(settings["smoothing"]["windowSize"]),
                    "polynomialDegree": int(settings["smoothing"]["polynomialDegree"])
                    },
                    scale = settings["scale"],
                )
            except ValueError:
                sys.stderr.write(f"Skipped {column}, not enough data for window\n")
                continue
            result.append(df_result)
    return result


def get_model(settings):
    model = tf.keras.models.Sequential()
    model.add(tf.keras.layers.LSTM(units=50, return_sequences=True, input_shape=(int(settings["inputSize"]), 1)))
    model.add(tf.keras.layers.Dropout(0.2))
    model.add(tf.keras.layers.LSTM(units=50, return_sequences=True))
    model.add(tf.keras.layers.Dropout(0.2))
    model.add(tf.keras.layers.LSTM(units=50, return_sequences=True))
    model.add(tf.keras.layers.Dropout(0.2))
    model.add(tf.keras.layers.LSTM(units=50))
    model.add(tf.keras.layers.Dropout(0.2))
    model.add(tf.keras.layers.Dense(units=int(settings["outputSize"])))
    model.summary()
    return model


def start_training(settings, model, x_train, y_train):
    model.compile(optimizer=settings["optimizer"], loss=settings["lossFunction"])
    model.fit(x_train, y_train, epochs=int(settings["epochs"]), verbose=1)


def save_model(settings, model):
    if not os.path.exists(get_path(settings["modelOutputLocation"])):
        os.makedirs(get_path(settings["modelOutputLocation"]))
    model.save(get_path(settings["modelOutputLocation"]) + settings["modelName"] + ".h5")


def main(settings):
    send_to_pyshell({"progress": 1, "message": "Loading file paths"})
    file_paths = load_file_paths(get_path(settings["datasetsDirectory"]))
    if len(file_paths) < 1:
        sys.exit(1225)

    send_to_pyshell({"progress": 2, "message": "Loading dataframes"})
    dataframes = load_dataframes(file_paths)
    send_to_pyshell({"progress": 3, "message": "Preparing dataframes"})
    dataframes = prepare_dataframes(settings, dataframes)
    model = get_model(settings)

    send_to_pyshell({"progress": 4, "message": "Formatting dataframes"})
    x_train, y_train = make_data(
        dataframes=dataframes,
        input_shape=int(settings["inputSize"]),
        output_shape=int(settings["outputSize"]),
        shuffle_datasets=True
    )

    if len(x_train) < 1 or len(y_train) < 1:
        sys.exit(1226)

    send_to_pyshell({"progress": 5, "message": "Start training"})
    start_training(settings, model, x_train, y_train)
    send_to_pyshell({"progress": 6, "message": "Saving model"})
    save_model(settings, model)
    send_to_pyshell({"progress": 7, "message": "Done"})


if __name__ == "__main__":
    main(get_from_args())
