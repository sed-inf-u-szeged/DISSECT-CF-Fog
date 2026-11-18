import os
os.environ.setdefault("TF_DETERMINISTIC_OPS", "1")

import argparse
import json
from datetime import datetime
from pathlib import Path
from time import perf_counter

import numpy as np
import pandas as pd
import tensorflow as tf

from app_utils.preprocessor import Preprocessor
from predictor_models.lstm_trainer import make_data
from diagnose_lstm_multi import score


def build_model():
    return tf.keras.Sequential([
        tf.keras.Input(shape=(192, 1)),
        tf.keras.layers.LSTM(32),
        tf.keras.layers.Dense(64),
    ])


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-dir", type=Path, required=True)
    args = parser.parse_args()
    source = args.source_dir.resolve()

    metadata = json.loads(
        (source / "metadata.json").read_text(encoding="utf-8-sig")
    )
    settings = metadata["trainSettings"]
    policy = metadata["lifecycleSettings"]
    cutoff = int(metadata["cutoff"])
    seed = int(metadata["seed"])
    epochs = int(metadata["epochs"])

    if (int(settings["inputSize"]), int(settings["outputSize"])) != (192, 64):
        raise ValueError("Expected input=192 and output=64")
    if settings.get("smoothing") is None:
        raise ValueError("Source configuration already disables smoothing")

    files = sorted(source.glob("node-*.npz"))
    if not files:
        raise ValueError("No saved node data found")

    tf.config.experimental.enable_op_determinism()
    output = source.parent / (
        "quality-smoothing-"
        + datetime.now().strftime("%Y%m%d-%H%M%S-%f")
    )
    output.mkdir()

    experiment = {
        "source_dir": str(source),
        "cutoff": cutoff,
        "seed": seed,
        "epochs": epochs,
        "batch_size": 32,
        "shuffle": False,
        "model": "Per-node LSTM(32) + Dense(64)",
        "loss": "mean_squared_error",
        "source_metadata": metadata,
        "tensorflow_version": tf.__version__,
        "note": (
            "Exploratory preprocessing comparison on previously examined data. "
            "Identical initial weights and sample order in both arms. "
            "Scaling is fitted after optional smoothing, on input only. "
            "All64 forecast windows overlap. No model is registered."
        ),
    }
    (output / "metadata.json").write_text(
        json.dumps(experiment, indent=2), encoding="utf-8"
    )

    results = []
    for index, path in enumerate(files, 1):
        with np.load(path, allow_pickle=False) as data:
            feature = str(data["feature"].item())
            raw = data["raw"].copy()
            starts = data["starts"].copy()
            actual = data["actual"].copy()
            predictions = {
                "LR": data["LR"].copy(),
                "LastValue": data["LastValue"].copy(),
            }

        if not np.all(np.isfinite(raw)):
            raise ValueError(f"{feature}: non-finite raw data")
        if cutoff < 256:
            raise ValueError("Training prefix is too short")
        if np.any(starts < cutoff) or np.any(starts + 64 > len(raw)):
            raise ValueError(f"{feature}: invalid evaluation origins")

        expected = np.asarray([raw[s:s + 64] for s in starts])
        if not np.array_equal(actual, expected):
            raise ValueError(f"{feature}: saved targets differ from raw data")

        predictions["PastMean10"] = np.repeat(
            np.asarray([raw[s - 10:s].mean() for s in starts])[:, None],
            64, axis=1,
        )

        initial_weights = None
        training_times = {}
        for name, smoothing in [
            ("Smooth", settings["smoothing"]),
            ("NoSmooth", None),
        ]:
            x_train, y_train = make_data(
                [pd.DataFrame({"data": raw[:cutoff]})],
                192, 64,
                smoothing=smoothing,
                scale=settings["scale"],
                shuffle_datasets=False,
            )
            if len(x_train) != cutoff - 192 - 64 + 1:
                raise ValueError(
                    f"{feature}: unexpected training window count"
                )

            inputs, scalers = [], []
            for start in starts:
                processed, scaler = Preprocessor.process(
                    raw[start - 192:start],
                    smoothing, settings["scale"],
                )
                inputs.append(processed["data"].to_numpy())
                scalers.append(scaler)
            x_eval = np.asarray(inputs, dtype=np.float32)[..., None]

            tf.keras.backend.clear_session()
            tf.keras.utils.set_random_seed(seed)
            model = build_model()

            if initial_weights is None:
                initial_weights = [
                    value.copy() for value in model.get_weights()
                ]
            model.set_weights(initial_weights)
            model.compile(
                optimizer=settings["optimizer"],
                loss="mean_squared_error",
            )

            print(
                f"TRAIN {index}/{len(files)} {feature} {name}",
                flush=True,
            )
            started = perf_counter()
            history = model.fit(
                x_train, y_train,
                epochs=epochs, batch_size=32,
                shuffle=False, verbose=0,
                callbacks=[tf.keras.callbacks.TerminateOnNaN()],
            )
            training_times[name] = perf_counter() - started

            losses = np.asarray(history.history["loss"])
            if len(losses) != epochs or not np.all(np.isfinite(losses)):
                raise ValueError(f"{feature} {name}: training failed")

            normalized = np.asarray(model(x_eval, training=False))
            if normalized.shape != actual.shape:
                raise ValueError("Unexpected prediction shape")
            predictions[name] = np.asarray([
                Preprocessor.get_data_inverse(row, scaler)
                for row, scaler in zip(normalized, scalers)
            ], dtype=float)

        node_results = []
        for name, predicted in predictions.items():
            node_results.append({
                "feature": feature,
                "model": name,
                **score(actual, predicted, policy),
                "mean10_bias": float(
                    (predicted[:, :10] - actual[:, :10]).mean()
                ),
                "training_seconds": training_times.get(name, 0.0),
                "prediction_file": path.name,
            })

        np.savez_compressed(
            output / path.name,
            feature=np.asarray(feature),
            starts=starts, actual=actual, raw=raw,
            **predictions,
        )
        results.extend(node_results)
        pd.DataFrame(results).to_csv(output / "summary.csv", index=False)

        for row in node_results:
            print(
                f"  {row['model']:<12} "
                f"mean10_MAE={row['mean10_MAE']:.2f}  "
                f"MAE64={row['MAE_all64']:.2f}  "
                f"accuracy64={row['accuracy_all64_pct']:.2f}%",
                flush=True,
            )

    columns = [
        "mean10_MAE", "MAE_first10", "MAE_all64",
        "accuracy_all64_pct", "mean10_bias",
    ]
    print("\nEqual-weight averages across nodes:")
    print(
        pd.DataFrame(results).groupby("model")[columns]
        .mean().round(2).to_string()
    )
    print(f"\nRESULT_DIR: {output}")
    print("SMOOTHING_COMPARISON_DONE")


if __name__ == "__main__":
    main()