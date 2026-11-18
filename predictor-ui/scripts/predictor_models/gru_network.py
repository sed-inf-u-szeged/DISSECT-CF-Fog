"""GRU forecasting core for DISSECT-CF-Fog (integration step 1).

get_model(settings) follows the existing LSTM trainer's builder contract.
The initial baseline keeps LSTM's depth, width and external dropout:
four forward GRU layers with 50 units each, Dropout(0.2) after each layer,
and a linear Dense(outputSize) forecast head. This matches depth and width,
not parameter count. No bidirectional or stateful recurrent layers are used.

This file defines the network only. Training-data selection, train-only
normalization, the combined loss, registry and nonnegative serving wrapper
belong to the next integration step. Running --self-test uses synthetic data
and temporary files; it does not train on simulation data or register a model.
Only built-in tf.keras layers are used, so loading needs no custom objects.
"""

import argparse
import json
import tempfile
from pathlib import Path

import numpy as np
import tensorflow as tf


UNITS = 50
RECURRENT_LAYERS = 4
DROPOUT = 0.2


def _positive_size(settings, key):
    value = settings[key]
    try:
        size = int(value)
        valid = not isinstance(value, (bool, np.bool_)) and float(value) == size
    except (TypeError, ValueError, OverflowError):
        valid = False
        size = 0
    if not valid or size < 1:
        raise ValueError(f"{key} must be a positive integer.")
    return size


def get_model(settings):
    """Return an uncompiled core: (batch, inputSize, 1) -> (batch, outputSize)."""
    input_size = _positive_size(settings, "inputSize")
    output_size = _positive_size(settings, "outputSize")
    layers = tf.keras.layers

    inputs = tf.keras.Input(shape=(input_size, 1), name="gru_input")
    x = inputs
    for index in range(1, RECURRENT_LAYERS + 1):
        x = layers.GRU(
            units=UNITS,
            return_sequences=index < RECURRENT_LAYERS,
            activation="tanh",
            recurrent_activation="sigmoid",
            reset_after=True,
            use_bias=True,
            stateful=False,
            go_backwards=False,
            dropout=0.0,
            recurrent_dropout=0.0,
            name=f"gru_layer{index}",
        )(x)
        x = layers.Dropout(DROPOUT, name=f"gru_dropout{index}")(x)

    outputs = layers.Dense(output_size, name="gru_forecast")(x)
    return tf.keras.Model(inputs, outputs, name="gru_baseline_core")


def self_test():
    """Check the core with four synthetic windows; not an accuracy experiment."""
    tf.keras.utils.set_random_seed(42)
    rng = np.random.default_rng(42)
    settings = {"inputSize": 192, "outputSize": 64}
    model = get_model(settings)
    if model.input_shape != (None, 192, 1):
        raise AssertionError(f"Unexpected input shape: {model.input_shape}")
    if model.output_shape != (None, 64):
        raise AssertionError(f"Unexpected output shape: {model.output_shape}")

    x = rng.normal(size=(4, 192, 1)).astype(np.float32)
    y = rng.normal(size=(4, 64)).astype(np.float32)
    weights_before = model.get_weights()
    # MAE is used only for this smoke check. The full trainer will use
    # the same combined_mae_mean10 profile as LSTM and TCN.
    model.compile(optimizer=tf.keras.optimizers.Adam(0.001), loss="mae")
    loss = float(np.asarray(model.train_on_batch(x, y)).reshape(-1)[0])
    if not np.isfinite(loss):
        raise AssertionError("Non-finite synthetic training loss.")
    weights_after = model.get_weights()
    if not all(np.isfinite(weight).all() for weight in weights_after):
        raise AssertionError("Non-finite weights after training.")
    if not any(
        not np.array_equal(before, after)
        for before, after in zip(weights_before, weights_after)
    ):
        raise AssertionError("Training did not update any weights.")

    expected = model(x, training=False).numpy()
    if expected.shape != (4, 64) or not np.isfinite(expected).all():
        raise AssertionError("Invalid predictions after training.")

    # Later input values must not alter earlier forward hidden states.
    # This checks recurrence direction, not the eventual dataset split.
    sequence_model = tf.keras.Model(
        model.input, model.get_layer("gru_layer3").output
    )
    changed = x.copy()
    changed[:, 96:, :] += 5.0
    original_features = sequence_model(x, training=False).numpy()
    changed_features = sequence_model(changed, training=False).numpy()
    np.testing.assert_allclose(
        original_features[:, :96, :],
        changed_features[:, :96, :],
        atol=1e-6,
        rtol=1e-6,
    )
    if np.allclose(original_features[:, 96:, :], changed_features[:, 96:, :]):
        raise AssertionError("Forward-sequence check did not respond to input changes.")

    # Predictions for separate nodes/windows must not share recurrent state.
    separate = np.concatenate([
        model(x[index:index + 1], training=False).numpy()
        for index in range(len(x))
    ])
    np.testing.assert_allclose(separate, expected, atol=1e-5, rtol=1e-5)

    # Save a clean uncompiled core, matching the planned serving integration.
    clean = get_model(settings)
    clean.set_weights(weights_after)
    with tempfile.TemporaryDirectory(prefix="dissect_gru_check_") as temporary:
        model_path = Path(temporary) / "gru_core.keras"
        clean.save(model_path)
        loaded = tf.keras.models.load_model(model_path, compile=False)
        actual = loaded(x, training=False).numpy()
        if actual.shape != (4, 64) or not np.isfinite(actual).all():
            raise AssertionError("Invalid predictions from reloaded model.")
        np.testing.assert_allclose(actual, expected, atol=1e-5, rtol=1e-5)

    print(json.dumps({
        "status": "GRU_SELF_TEST_OK",
        "tensorflow_version": tf.__version__,
        "input_shape": list(model.input_shape),
        "output_shape": list(model.output_shape),
        "recurrent_layers": RECURRENT_LAYERS,
        "units_per_layer": UNITS,
        "dropout": DROPOUT,
        "reset_after": True,
        "parameters": model.count_params(),
        "training_step": "passed",
        "forward_sequence_check": "passed",
        "batch_independence": "passed",
        "save_load": "passed",
        "save_load_max_abs_error": float(np.max(np.abs(actual - expected))),
        "note": "Synthetic core check only; GRU is not yet registered in the simulator.",
    }, indent=2, allow_nan=False))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--self-test", action="store_true")
    arguments = parser.parse_args()
    if arguments.self_test:
        self_test()
    else:
        get_model({"inputSize": 192, "outputSize": 64}).summary()
