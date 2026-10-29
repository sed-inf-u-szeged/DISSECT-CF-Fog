"""Optimized training branch for the existing LSTM lifecycle."""
import json
from pathlib import Path

import numpy as np
import tensorflow as tf


def combined_loss(y_true, y_pred):
    full = tf.reduce_mean(tf.abs(y_pred - y_true), axis=-1)
    short = tf.abs(
        tf.reduce_mean(y_pred[:, :10], axis=-1)
        - tf.reduce_mean(y_true[:, :10], axis=-1)
    )
    return full + short


def build_serving_model(core, mean, std, input_size):
    inputs = tf.keras.Input(shape=(input_size, 1))
    x = tf.keras.layers.Rescaling(
        scale=1.0, offset=-mean
    )(inputs)
    x = tf.keras.layers.Rescaling(scale=1.0 / std)(x)
    x = core(x, training=False)
    x = tf.keras.layers.Rescaling(
        scale=std, offset=mean
    )(x)
    outputs = tf.keras.layers.ReLU()(x)
    return tf.keras.Model(inputs, outputs)


def run(settings, helpers):
    load_paths, load_frames, prepare, make_data, get_model, save_model, register = helpers

    if bool(settings["scale"]) or settings.get("smoothing") is not None:
        raise ValueError(
            "combined_mae_mean10 requires external scale=False "
            "and smoothing=None."
        )

    input_size = int(settings["inputSize"])
    output_size = int(settings["outputSize"])
    if input_size != 192 or output_size != 64:
        raise ValueError("This training profile requires input192/output64.")

    paths = load_paths(settings["datasetsDirectory"])
    if not paths:
        raise ValueError("No training CSV files were found.")

    frames = load_frames(paths)
    selected = []
    selected_columns = []
    for frame in frames:
        columns = [
            column for column in frame.columns
            if str(column).endswith("::notYetProcessedData")
            and "_cloud::" not in str(column)
        ]
        if columns:
            selected.append(frame[columns])
            selected_columns.extend(map(str, columns))

    if not selected:
        raise ValueError(
            "No fog-node ::notYetProcessedData columns were found. "
            "Refusing to train on an unidentified target."
        )

    frames = prepare(settings, selected)
    x_raw, y_raw = make_data(
        frames,
        input_size,
        output_size,
        smoothing=None,
        scale=False,
        shuffle_datasets=False,
    )
    if len(x_raw) == 0:
        raise ValueError("No complete training windows.")

    # Statistics use training inputs only, never future shadow observations.
    mean = float(np.mean(x_raw, dtype=np.float64))
    std = float(np.std(x_raw, dtype=np.float64))
    if not np.isfinite(mean) or not np.isfinite(std):
        raise ValueError("Invalid normalization statistics.")
    if std < 1e-8:
        std = 1.0

    x = ((x_raw.astype(np.float64) - mean) / std).astype(np.float32)
    y = ((y_raw.astype(np.float64) - mean) / std).astype(np.float32)
    if not np.isfinite(x).all() or not np.isfinite(y).all():
        raise ValueError("Non-finite normalized training data.")

    tf.keras.utils.set_random_seed(42)
    model = get_model(settings)
    model.compile(
        optimizer=tf.keras.optimizers.Adam(learning_rate=0.001),
        loss=combined_loss,
    )
    history = model.fit(
        x, y,
        epochs=int(settings["epochs"]),
        batch_size=64,
        shuffle=True,
        verbose=2,
    )
    if not all(np.isfinite(history.history["loss"])):
        raise ValueError("Training loss became non-finite.")

    # Use a fresh, uncompiled copy so the serving file does not
    # require a custom loss function when loaded.
    clean_core = get_model(settings)
    clean_core.set_weights(model.get_weights())
    clean_core.trainable = False
    serving = build_serving_model(
        clean_core, mean, std, input_size
    )

    output = Path(settings["modelOutputLocation"]).resolve()
    output.mkdir(parents=True, exist_ok=True)
    save_settings = dict(settings)
    save_settings["modelName"] = (
        Path(str(settings["modelName"])).stem + ".keras"
    )
    model_path = save_model(save_settings, serving)

    # Verify the file actually consumed by the predictor.
    loaded = tf.keras.models.load_model(model_path, compile=False)
    sample = x_raw[:min(8, len(x_raw))]
    normalized = (
        (sample.astype(np.float64) - mean) / std
    ).astype(np.float32)
    expected = np.maximum(
        model.predict(normalized, verbose=0).astype(np.float64)
        * std + mean,
        0.0,
    )
    actual = loaded.predict(sample, verbose=0)
    if not np.isfinite(actual).all():
        raise ValueError("Saved model returned non-finite predictions.")
    np.testing.assert_allclose(
        actual, expected, atol=0.1, rtol=1e-5
    )

    report = {
        "profile": "combined_mae_mean10",
        "training_windows": len(x_raw),
        "training_columns": sorted(set(selected_columns)),
        "normalization_mean": mean,
        "normalization_std": std,
        "normalization_source": "current training input windows",
        "external_scale": False,
        "external_smoothing": None,
        "batch_size": 64,
        "epochs": int(settings["epochs"]),
        "training_seed": 42,
        "checkpoint_rule": "final epoch; no validation-based selection",
        "parity_max_abs_error": float(
            np.max(np.abs(actual - expected))
        ),
        "training_loss": [
            float(value) for value in history.history["loss"]
        ],
    }
    (output / "optimized_training_report.json").write_text(
        json.dumps(report, indent=2, allow_nan=False),
        encoding="utf-8",
    )

    # Reuse the existing registry and lifecycle result-file contract.
    return register(save_settings, model_path)
