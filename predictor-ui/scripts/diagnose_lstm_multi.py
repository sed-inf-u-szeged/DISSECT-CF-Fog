import os
os.environ.setdefault("TF_DETERMINISTIC_OPS", "1")

import argparse
import json
import sqlite3
from datetime import datetime
from pathlib import Path
from time import perf_counter

import numpy as np
import pandas as pd
import tensorflow as tf
from sklearn.linear_model import LinearRegression

from app_utils.preprocessor import Preprocessor
from predictor_models.lstm_trainer import make_data


def score(actual, predicted, policy):
    actual = np.asarray(actual, dtype=float)
    predicted = np.asarray(predicted, dtype=float)
    if actual.shape != predicted.shape:
        raise ValueError("Prediction and target shapes differ")
    if not np.all(np.isfinite(predicted)):
        raise ValueError("Non-finite predictions")

    error = np.abs(predicted - actual)
    tolerance = np.maximum(
        float(policy["absoluteTolerance"]),
        float(policy["accuracyTolerance"]) * np.abs(actual),
    )

    actual_mean = actual[:, :10].mean(axis=1)
    predicted_mean = predicted[:, :10].mean(axis=1)
    mean_error = np.abs(predicted_mean - actual_mean)
    mean_tolerance = np.maximum(
        float(policy["absoluteTolerance"]),
        float(policy["accuracyTolerance"]) * np.abs(actual_mean),
    )

    return {
        "origins": int(len(actual)),
        "MAE_first1": float(error[:, 0].mean()),
        "MAE_first10": float(error[:, :10].mean()),
        "MAE_all64": float(error.mean()),
        "RMSE_all64": float(np.sqrt(np.mean(error ** 2))),
        "accuracy_first10_pct": float(
            100 * np.mean(error[:, :10] <= tolerance[:, :10])
        ),
        "accuracy_all64_pct": float(100 * np.mean(error <= tolerance)),
        "mean10_MAE": float(mean_error.mean()),
        "mean10_within_tolerance_pct": float(
            100 * np.mean(mean_error <= mean_tolerance)
        ),
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--run-dir", type=Path, required=True)
    args = parser.parse_args()
    directory = args.run_dir.resolve()

    configs = list(directory.glob("simulation_settings_*.json"))
    if len(configs) != 1:
        raise ValueError("Expected one simulation settings file")
    config = json.loads(configs[0].read_text(encoding="utf-8-sig"))
    settings = config["trainSettings"]
    policy = config["lifecycleSettings"]

    input_size = int(settings["inputSize"])
    output_size = int(settings["outputSize"])
    if (input_size, output_size) != (192, 64):
        raise ValueError("Expected input=192 and output=64")

    cutoff, epochs, stride, seed = 415, 100, 10, 42
    lr_settings = next(
        item["prediction"] for item in config["predictionSettings"]
        if item["predictor"]["predictor"] == "LINEAR_REGRESSION"
    )
    lr_size = int(lr_settings["batchSize"])
    if not 0 < lr_size <= cutoff:
        raise ValueError("Invalid LR history length")

    tf.config.experimental.enable_op_determinism()

    output = directory / (
        "quality-multi-" + datetime.now().strftime("%Y%m%d-%H%M%S-%f")
    )
    output.mkdir()
    metadata = {
        "run_dir": str(directory),
        "cutoff": cutoff,
        "epochs": epochs,
        "stride": stride,
        "seed": seed,
        "model": "Per-node LSTM(32) + Dense(64)",
        "trainSettings": settings,
        "lifecycleSettings": policy,
        "lrSettings": lr_settings,
        "tensorflow_version": tf.__version__,
        "note": (
            "Offline diagnostic; overlapping forecast windows; "
            "indices are local to each node, not synchronized timestamps."
        ),
    }
    (output / "metadata.json").write_text(
        json.dumps(metadata, indent=2), encoding="utf-8"
    )

    database_uri = (directory / "database.db").as_uri() + "?mode=ro"
    results, skipped = [], []

    with sqlite3.connect(database_uri, uri=True) as connection:
        tables = sorted(
            row[0] for row in connection.execute(
                "SELECT name FROM sqlite_master WHERE type='table'"
            )
            if row[0].endswith("____notYetProcessedData_feature_raw")
        )
        if not tables:
            raise ValueError("No backlog tables found")

        for index, table in enumerate(tables, 1):
            quoted = '"' + table.replace('"', '""') + '"'
            raw = np.asarray([
                row[0] for row in connection.execute(
                    f"SELECT raw_value FROM {quoted} ORDER BY id"
                )
            ], dtype=float)
            feature = table.removesuffix("_feature_raw").replace("____", "::")

            if len(raw) < cutoff + output_size:
                reason = f"Only {len(raw)} points; need at least 479"
                skipped.append({"feature": feature, "reason": reason})
                print(f"SKIP {feature}: {reason}", flush=True)
                continue
            if not np.all(np.isfinite(raw)):
                reason = "Non-finite raw data; no gaps removed"
                skipped.append({"feature": feature, "reason": reason})
                print(f"SKIP {feature}: {reason}", flush=True)
                continue

            starts = np.arange(
                cutoff, len(raw) - output_size + 1, stride, dtype=int
            )
            actual = np.asarray([
                raw[start:start + output_size] for start in starts
            ])

            inputs, scalers = [], []
            for start in starts:
                processed, scaler = Preprocessor.process(
                    raw[start - input_size:start],
                    settings.get("smoothing"), settings["scale"],
                )
                inputs.append(processed["data"].to_numpy())
                scalers.append(scaler)
            x_eval = np.asarray(inputs, dtype=np.float32)[..., None]

            predictions = {
                "LastValue": np.asarray([
                    np.full(output_size, raw[start - 1])
                    for start in starts
                ])
            }

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
                forecast = regression.predict(future.reshape(-1, 1))
                lr_predictions.append(
                    Preprocessor.get_data_inverse(forecast, scaler)
                )
            predictions["LR"] = np.asarray(lr_predictions, dtype=float)

            x_train, y_train = make_data(
                [pd.DataFrame({"data": raw[:cutoff]})],
                input_size, output_size,
                smoothing=settings.get("smoothing"),
                scale=settings["scale"],
                shuffle_datasets=False,
            )
            if len(x_train) != 160:
                raise ValueError(f"{feature}: expected 160 training windows")

            tf.keras.backend.clear_session()
            tf.keras.utils.set_random_seed(seed)
            model = tf.keras.Sequential([
                tf.keras.Input(shape=(input_size, 1)),
                tf.keras.layers.LSTM(32),
                tf.keras.layers.Dense(output_size),
            ])
            model.compile(
                optimizer=settings["optimizer"],
                loss=settings["lossFunction"],
            )

            print(
                f"TRAIN {index}/{len(tables)} {feature}: "
                f"160 training windows, {len(starts)} forecast origins",
                flush=True,
            )
            started = perf_counter()
            model.fit(
                x_train, y_train, epochs=epochs,
                batch_size=32, shuffle=True, verbose=0,
            )
            training_seconds = perf_counter() - started

            normalized = np.asarray(model(x_eval, training=False))
            if normalized.shape != actual.shape:
                raise ValueError("Unexpected LSTM output shape")
            predictions["LSTM_small"] = np.asarray([
                Preprocessor.get_data_inverse(row, scaler)
                for row, scaler in zip(normalized, scalers)
            ], dtype=float)

            node_results = []
            for name, predicted in predictions.items():
                result = {
                    "feature": feature,
                    "model": name,
                    **score(actual, predicted, policy),
                }
                node_results.append(result)

            # Save raw predictions so later analysis needs no retraining.
            filename = f"node-{index:02d}.npz"
            np.savez_compressed(
                output / filename,
                feature=np.asarray(feature),
                starts=starts,
                actual=actual,
                raw=raw,
                **predictions,
            )
            for result in node_results:
                result["prediction_file"] = filename
                result["lstm_training_seconds"] = training_seconds
            results.extend(node_results)

            pd.DataFrame(results).to_csv(
                output / "summary.csv", index=False
            )
            print(
                f"  Training: {training_seconds:.2f}s; "
                "MAE first10 / MAE mean10:",
                flush=True,
            )
            for result in node_results:
                print(
                    f"  {result['model']:<12} "
                    f"{result['MAE_first10']:.2f} / "
                    f"{result['mean10_MAE']:.2f} bytes",
                    flush=True,
                )

    (output / "skipped.json").write_text(
        json.dumps(skipped, indent=2), encoding="utf-8"
    )
    if not results:
        raise ValueError("No eligible nodes")

    print("\nEqual-weight averages across evaluated nodes:", flush=True)
    summary = pd.DataFrame(results)
    columns = [
        "MAE_first10", "MAE_all64",
        "accuracy_all64_pct", "mean10_MAE",
        "mean10_within_tolerance_pct",
    ]
    print(
        summary.groupby("model")[columns].mean().round(2).to_string(),
        flush=True,
    )
    print(f"\nRESULT_DIR: {output}", flush=True)
    print("MULTI_NODE_DONE", flush=True)


if __name__ == "__main__":
    main()