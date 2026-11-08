"""Small causal TCN core for DISSECT-CF-Fog (integration step 1).

get_model(settings) follows the existing LSTM trainer's builder signature.
This module defines the network; it does not register a predictor or change
the simulator. Normalization, training policy and nonnegative serving output
belong to the training/serving integration, not this linear-output core.

Architecture: six residual blocks, two causal convolutions per block,
16 channels, kernel size 3, dilations 1/2/4/8/16/32, spatial dropout 0.2.
This is a small TCN adaptation, without the original paper's weight norm.
Only built-in tf.keras layers are used so saved files need no custom layers.
"""

import argparse
import json
import tempfile
from pathlib import Path

import numpy as np
import tensorflow as tf


CHANNELS = 16
KERNEL_SIZE = 3
DILATIONS = (1, 2, 4, 8, 16, 32)
DROPOUT = 0.2
RECEPTIVE_FIELD = 1 + 2 * (KERNEL_SIZE - 1) * sum(DILATIONS)


def get_model(settings):
    """Return an uncompiled core: (batch, inputSize, 1) -> (batch, outputSize)."""
    input_size = int(settings["inputSize"])
    output_size = int(settings["outputSize"])
    if input_size < 1 or output_size < 1:
        raise ValueError("Input and output sizes must be positive.")

    layers = tf.keras.layers
    inputs = tf.keras.Input(shape=(input_size, 1), name="tcn_input")
    x = inputs

    for block, dilation in enumerate(DILATIONS, start=1):
        prefix = f"tcn_block{block}"
        residual = x
        for convolution in (1, 2):
            x = layers.Conv1D(
                filters=CHANNELS,
                kernel_size=KERNEL_SIZE,
                padding="causal",
                dilation_rate=dilation,
                data_format="channels_last",
                kernel_initializer="he_normal",
                name=f"{prefix}_conv{convolution}",
            )(x)
            x = layers.ReLU(name=f"{prefix}_relu{convolution}")(x)
            x = layers.SpatialDropout1D(
                DROPOUT,
                name=f"{prefix}_dropout{convolution}",
            )(x)

        if int(residual.shape[-1]) != CHANNELS:
            residual = layers.Conv1D(
                CHANNELS,
                kernel_size=1,
                padding="same",
                data_format="channels_last",
                name=f"{prefix}_projection",
            )(residual)

        x = layers.Add(name=f"{prefix}_add")([residual, x])
        x = layers.ReLU(name=f"{prefix}_out")(x)

    # Keep the last time step using serializable built-in layers.
    x = layers.Cropping1D(
        cropping=(input_size - 1, 0), name="tcn_last_step"
    )(x)
    x = layers.Flatten(data_format="channels_last", name="tcn_flatten")(x)
    outputs = layers.Dense(output_size, name="tcn_forecast")(x)
    return tf.keras.Model(inputs, outputs, name="tcn_small_core")


def self_test():
    """Synthetic integration checks only; this is not an accuracy experiment."""
    tf.keras.utils.set_random_seed(42)
    rng = np.random.default_rng(42)
    settings = {"inputSize": 192, "outputSize": 64}
    model = get_model(settings)
    if model.input_shape != (None, 192, 1):
        raise AssertionError(f"Unexpected input shape: {model.input_shape}")
    if model.output_shape != (None, 64):
        raise AssertionError(f"Unexpected output shape: {model.output_shape}")
    if model.count_params() != 9808:
        raise AssertionError(f"Unexpected parameter count: {model.count_params()}")

    x = rng.normal(size=(4, 192, 1)).astype(np.float32)
    y = rng.normal(size=(4, 64)).astype(np.float32)
    weights_before = model.get_weights()
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

    # Changing later inputs must not change earlier backbone activations.
    backbone = tf.keras.Model(
        model.input, model.get_layer("tcn_block6_out").output
    )
    changed = x.copy()
    changed[:, 96:, :] += 5.0
    original_features = backbone(x, training=False).numpy()
    changed_features = backbone(changed, training=False).numpy()
    np.testing.assert_allclose(
        original_features[:, :96, :],
        changed_features[:, :96, :],
        atol=1e-6,
        rtol=1e-6,
    )
    if np.allclose(original_features[:, 96:, :], changed_features[:, 96:, :]):
        raise AssertionError("Causality check had no response to changed inputs.")

    # Save an uncompiled core, as the serving integration will do.
    clean = get_model(settings)
    clean.set_weights(weights_after)
    expected = model(x, training=False).numpy()
    with tempfile.TemporaryDirectory(prefix="dissect_tcn_check_") as temporary:
        model_path = Path(temporary) / "tcn_core.keras"
        clean.save(model_path)
        loaded = tf.keras.models.load_model(model_path, compile=False)
        actual = loaded(x, training=False).numpy()
        if actual.shape != (4, 64) or not np.isfinite(actual).all():
            raise AssertionError("Invalid predictions from reloaded model.")
        np.testing.assert_allclose(actual, expected, atol=1e-5, rtol=1e-5)

    print(json.dumps({
        "status": "TCN_SELF_TEST_OK",
        "tensorflow_version": tf.__version__,
        "input_shape": list(model.input_shape),
        "output_shape": list(model.output_shape),
        "parameters": model.count_params(),
        "receptive_field": RECEPTIVE_FIELD,
        "training_step": "passed",
        "causality": "passed",
        "save_load": "passed",
        "save_load_max_abs_error": float(np.max(np.abs(actual - expected))),
    }, indent=2, allow_nan=False))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--self-test", action="store_true")
    arguments = parser.parse_args()
    if arguments.self_test:
        self_test()
    else:
        get_model({"inputSize": 192, "outputSize": 64}).summary()
