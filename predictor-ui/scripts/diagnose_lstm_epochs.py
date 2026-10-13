import os
os.environ.setdefault("TF_DETERMINISTIC_OPS", "1")

import argparse
import json
import sqlite3
from pathlib import Path

import numpy as np
import tensorflow as tf

from app_utils.preprocessor import Preprocessor
from predictor_models.lstm_trainer import (
    get_model, load_dataframes, make_data, prepare_dataframes,
)


def metrics(actual, predicted):
    error = np.abs(predicted - actual)
    denominator = np.abs(actual) + np.abs(predicted)
    smape = np.divide(
        200 * error,
        denominator,
        out=np.zeros_like(error),
        where=denominator > 0,
    )
    return {
        "MAE": float(error.mean()),
        "RMSE": float(np.sqrt(np.mean(error ** 2))),
        "SMAPE": float(smape.mean()),
        "accuracy_pct": float(100 * np.mean(
            error <= np.maximum(0.1, 0.1 * np.abs(actual))
        )),
        "negative_pct": float(100 * np.mean(predicted < 0)),
        "predicted_mean": float(predicted.mean()),
        "actual_mean": float(actual.mean()),
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--run-dir", type=Path, required=True)
    parser.add_argument("--no-smoothing", action="store_true")
    args = parser.parse_args()

    directory = args.run_dir.resolve()
    config_files = list(directory.glob("simulation_settings_*.json"))
    if len(config_files) != 1:
        raise ValueError("Expected exactly one simulation settings file")

    config = json.loads(
        config_files[0].read_text(encoding="utf-8-sig")
    )
    settings = config["trainSettings"]
    input_size = int(settings["inputSize"])
    output_size = int(settings["outputSize"])
    smoothing = (
        None if args.no_smoothing else settings.get("smoothing")
    )
    scale = settings["scale"]

    snapshot = directory / "online-training-data" / "dataset.csv"
    frames = load_dataframes([snapshot])
    prepared = prepare_dataframes(settings, frames)
    x_train, y_train = make_data(
        prepared,
        input_size,
        output_size,
        smoothing=smoothing,
        scale=scale,
        shuffle_datasets=False,
    )
    if len(x_train) == 0:
        raise ValueError("No training windows")

    # Use the same two future blocks per feature as the previous run.
    inputs, targets, scalers, labels = [], [], [], []
    database_uri = (directory / "database.db").as_uri() + "?mode=ro"

    with sqlite3.connect(database_uri, uri=True) as connection:
        for feature in frames[0].columns:
            values = frames[0][feature].astype(str).str.replace(
                ",", ".", regex=False
            ).astype(float).to_numpy()

            if not np.all(np.isfinite(values)):
                raise ValueError(f"Invalid training values: {feature}")

            table = feature.replace("::", "____") + "_feature_raw"
            quoted_table = '"' + table.replace('"', '""') + '"'
            raw = np.array([
                row[0] for row in connection.execute(
                    f"SELECT raw_value FROM {quoted_table} ORDER BY id"
                )
            ], dtype=float)

            if not np.array_equal(raw[:len(values)], values):
                raise ValueError(
                    f"Snapshot does not match raw prefix: {feature}"
                )

            for block in range(2):
                start = len(values) + block * output_size
                end = start + output_size
                if end > len(raw):
                    raise ValueError(
                        f"Insufficient validation data: {feature}"
                    )

                processed, scaler = Preprocessor.process(
                    raw[start-input_size:start], smoothing, scale
                )
                inputs.append(processed["data"].to_numpy())
                targets.append(raw[start:end])
                scalers.append(scaler)
                labels.append(f"{feature}: targets [{start}, {end})")

    x_validation = np.asarray(inputs, dtype=np.float32)[..., None]
    actual = np.asarray(targets, dtype=float)

    tf.keras.utils.set_random_seed(42)
    tf.config.experimental.enable_op_determinism()

    model = get_model(settings)
    model.compile(
        optimizer=settings["optimizer"],
        loss=settings["lossFunction"],
    )

    print(f"Smoothing: {smoothing}", flush=True)
    print(f"Training windows: {len(x_train)}", flush=True)
    print("Validation blocks:", *labels, sep="\n", flush=True)

    class Checkpoints(tf.keras.callbacks.Callback):
        def on_epoch_end(self, epoch, logs=None):
            completed = epoch + 1
            if completed not in (3, 20, 50):
                return

            normalized = np.asarray(
                self.model(x_validation, training=False)
            )
            predicted = np.array([
                Preprocessor.get_data_inverse(row, scaler)
                for row, scaler in zip(normalized, scalers)
            ])

            if not np.all(np.isfinite(predicted)):
                raise ValueError("Non-finite validation predictions")

            result = {
                "smoothing_enabled": smoothing is not None,
                "epoch": completed,
                "training_loss": float(logs["loss"]),
                **metrics(actual, predicted),
            }
            print("\nEPOCH_CHECK " + json.dumps(result), flush=True)

    model.fit(
        x_train,
        y_train,
        epochs=50,
        batch_size=32,
        shuffle=True,
        callbacks=[Checkpoints()],
        verbose=2,
    )


if __name__ == "__main__":
    main()