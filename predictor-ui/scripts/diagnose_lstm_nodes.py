import os
os.environ.setdefault("TF_DETERMINISTIC_OPS", "1")

import argparse
import json
import sqlite3
from pathlib import Path
from time import perf_counter

import numpy as np
import tensorflow as tf
from sklearn.linear_model import LinearRegression

from app_utils.preprocessor import Preprocessor
from predictor_models.lstm_trainer import (
    load_dataframes, make_data, prepare_dataframes,
)


def metrics(actual, predicted, policy):
    actual = np.asarray(actual, dtype=float)
    predicted = np.asarray(predicted, dtype=float)
    if actual.shape != predicted.shape or actual.size == 0:
        raise ValueError("Invalid prediction or target shape")
    if not np.all(np.isfinite(actual)) or not np.all(np.isfinite(predicted)):
        raise ValueError("Non-finite evaluation values")

    error = np.abs(predicted - actual)
    denominator = np.abs(actual) + np.abs(predicted)
    smape = np.divide(
        200 * error, denominator,
        out=np.zeros_like(error), where=denominator > 0,
    )
    tolerance = np.maximum(
        float(policy["absoluteTolerance"]),
        float(policy["accuracyTolerance"]) * np.abs(actual),
    )
    mse = float(np.mean(error ** 2))
    return {
        "points": int(actual.size),
        "MAE": float(error.mean()),
        "MSE": mse,
        "RMSE": float(np.sqrt(mse)),
        "SMAPE": float(smape.mean()),
        "accuracy_pct": float(100 * np.mean(error <= tolerance)),
        "actual_mean": float(actual.mean()),
        "predicted_mean": float(predicted.mean()),
        "negative_pct": float(100 * np.mean(predicted < 0)),
    }


def tolerance_report(actual, predicted, policy):
    actual = np.asarray(actual, dtype=float)
    predicted = np.asarray(predicted, dtype=float)
    error = np.abs(predicted - actual)
    zero = actual == 0
    relative = float(policy["accuracyTolerance"])
    absolute_values = sorted({
        float(policy["absoluteTolerance"]), 0.1, 128.0, 512.0, 1024.0,
    })

    def percentage(correct, mask):
        if not np.any(mask):
            return "N/A"
        return f"{100 * np.mean(correct[mask]):.2f}"

    print(
        f"  Zero targets: {int(zero.sum())}/{actual.size}; "
        f"relative tolerance: {relative:.0%}",
        flush=True,
    )
    print(
        "  Abs(bytes) | All Acc% | Zero Acc% | Nonzero Acc%",
        flush=True,
    )
    for absolute in absolute_values:
        correct = error <= np.maximum(absolute, relative * np.abs(actual))
        print(
            f"  {absolute:10.1f} | {100 * correct.mean():8.2f} | "
            f"{percentage(correct, zero):>9} | "
            f"{percentage(correct, ~zero):>12}",
            flush=True,
        )


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--run-dir", type=Path, required=True)
    parser.add_argument("--epochs", type=int, default=100)
    args = parser.parse_args()
    if args.epochs < 1:
        raise ValueError("Epochs must be positive")

    directory = args.run_dir.resolve()
    configs = list(directory.glob("simulation_settings_*.json"))
    if len(configs) != 1:
        raise ValueError("Expected exactly one simulation settings file")

    config = json.loads(configs[0].read_text(encoding="utf-8-sig"))
    settings = config["trainSettings"]
    policy = config["lifecycleSettings"]
    input_size = int(settings["inputSize"])
    output_size = int(settings["outputSize"])
    feature = "Sopron_fog2::notYetProcessedData"

    if (input_size, output_size) != (192, 64):
        raise ValueError("This experiment expects input=192, output=64")

    cutoff = 415
    training_lengths = (287, 415)
    starts = [cutoff, cutoff + output_size]

    frames = load_dataframes([
        directory / "online-training-data" / "dataset.csv"
    ])
    frame = frames[0]
    if feature not in frame.columns:
        raise ValueError(f"Missing snapshot feature: {feature}")

    snapshot = (
        frame[feature].astype(str).str.replace(",", ".", regex=False)
        .astype(float).to_numpy()
    )
    table = feature.replace("::", "____") + "_feature_raw"
    quoted = '"' + table.replace('"', '""') + '"'
    uri = (directory / "database.db").as_uri() + "?mode=ro"
    with sqlite3.connect(uri, uri=True) as connection:
        raw = np.array([
            row[0] for row in connection.execute(
                f"SELECT raw_value FROM {quoted} ORDER BY id"
            )
        ], dtype=float)

    if not np.array_equal(raw[:len(snapshot)], snapshot):
        raise ValueError("Snapshot is not the expected raw prefix")
    if len(snapshot) < cutoff or len(raw) < cutoff + 2 * output_size:
        raise ValueError("Insufficient training or future data")
    if not np.all(np.isfinite(raw[:cutoff + 2 * output_size])):
        raise ValueError("Non-finite data")

    actual = np.asarray([raw[s:s + output_size] for s in starts])
    inputs, scalers = [], []
    for start in starts:
        processed, scaler = Preprocessor.process(
            raw[start - input_size:start],
            settings.get("smoothing"), settings["scale"],
        )
        inputs.append(processed["data"].to_numpy())
        scalers.append(scaler)
    x_validation = np.asarray(inputs, dtype=np.float32)[..., None]

    print(f"Feature: {feature}", flush=True)
    print(f"Shared training cutoff: {cutoff}", flush=True)
    print(f"Shared validation starts: {starts}", flush=True)
    print(
        f"Current tolerance: relative={policy['accuracyTolerance']}, "
        f"absolute={policy['absoluteTolerance']} bytes",
        flush=True,
    )
    print(
        "Diagnostic tolerance comparison only; no policy changes.",
        flush=True,
    )
    print(
        "Second block uses newly observed input history. "
        "Neither LSTM retrains between validation blocks.",
        flush=True,
    )

    def report(name, predictions):
        predictions = np.asarray(predictions, dtype=float)
        if predictions.shape != actual.shape:
            raise ValueError(f"Unexpected prediction shape: {name}")
        for block, start in enumerate(starts):
            result = {
                "model": name,
                "block": block + 1,
                "target_start": start,
                "target_end_exclusive": start + output_size,
                **metrics(actual[block], predictions[block], policy),
            }
            print("QUALITY " + json.dumps(result), flush=True)
            tolerance_report(actual[block], predictions[block], policy)

    report("LastValue", [
        np.full(output_size, raw[start - 1]) for start in starts
    ])

    lr_settings = next(
        item["prediction"] for item in config["predictionSettings"]
        if item["predictor"]["predictor"] == "LINEAR_REGRESSION"
    )
    lr_size = int(lr_settings["batchSize"])
    if not 0 < lr_size <= cutoff:
        raise ValueError("Invalid LR history length")

    lr_predictions = []
    for start in starts:
        processed, scaler = Preprocessor.process(
            raw[start - lr_size:start],
            lr_settings.get("smoothing"), lr_settings["scale"],
        )
        regression = LinearRegression().fit(
            processed["timestamp"].to_numpy().reshape(-1, 1),
            processed["data"].to_numpy(),
        )
        future = np.arange(lr_size, lr_size + output_size)
        normalized = regression.predict(future.reshape(-1, 1))
        lr_predictions.append(
            Preprocessor.get_data_inverse(normalized, scaler)
        )
    report("LR", lr_predictions)

    tf.config.experimental.enable_op_determinism()
    print(
        "TensorFlow GPUs:",
        [device.name for device in tf.config.list_physical_devices("GPU")],
        flush=True,
    )

    for training_length in training_lengths:
        training_start = cutoff - training_length
        training_frame = (
            frame[[feature]].iloc[training_start:cutoff].copy()
            .reset_index(drop=True)
        )
        prepared = prepare_dataframes(settings, [training_frame])
        x_train, y_train = make_data(
            prepared, input_size, output_size,
            smoothing=settings.get("smoothing"),
            scale=settings["scale"],
            shuffle_datasets=False,
        )
        expected = training_length - input_size - output_size + 1
        if len(x_train) != expected:
            raise ValueError(
                f"Expected {expected} windows, got {len(x_train)}"
            )

        tf.keras.backend.clear_session()
        tf.keras.utils.set_random_seed(42)
        model = tf.keras.Sequential([
            tf.keras.Input(shape=(input_size, 1)),
            tf.keras.layers.LSTM(32),
            tf.keras.layers.Dense(output_size),
        ])
        model.compile(
            optimizer=settings["optimizer"],
            loss=settings["lossFunction"],
        )

        name = f"LSTM_small_data{training_length}"
        print(
            f"TRAIN {name}: raw [{training_start}, {cutoff}), "
            f"windows={len(x_train)}, epochs={args.epochs}",
            flush=True,
        )

        class Progress(tf.keras.callbacks.Callback):
            def on_epoch_end(self, epoch, logs=None):
                completed = epoch + 1
                if completed == 1 or completed % 20 == 0:
                    print(
                        f"{name}: epoch {completed}/{args.epochs}, "
                        f"loss={logs['loss']:.6f}",
                        flush=True,
                    )

        started = perf_counter()
        history = model.fit(
            x_train, y_train,
            epochs=args.epochs, batch_size=32,
            shuffle=True, verbose=0, callbacks=[Progress()],
        )
        training_seconds = perf_counter() - started

        started = perf_counter()
        normalized = np.asarray(model(x_validation, training=False))
        if normalized.shape != actual.shape:
            raise ValueError(f"Unexpected model output: {normalized.shape}")
        predictions = [
            Preprocessor.get_data_inverse(row, scaler)
            for row, scaler in zip(normalized, scalers)
        ]
        prediction_seconds = perf_counter() - started

        report(name, predictions)
        print("TIMING " + json.dumps({
            "model": name,
            "training_points": training_length,
            "training_windows": len(x_train),
            "epochs": args.epochs,
            "optimizer_updates": int(model.optimizer.iterations.numpy()),
            "training_seconds": training_seconds,
            "prediction_seconds_two_blocks": prediction_seconds,
            "final_training_loss": float(history.history["loss"][-1]),
        }), flush=True)

    print("TOLERANCE_COMPARISON_DONE", flush=True)


if __name__ == "__main__":
    main()