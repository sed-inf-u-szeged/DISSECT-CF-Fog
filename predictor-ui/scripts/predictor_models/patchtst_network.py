"""Small PatchTST forecasting core for DISSECT-CF-Fog, integration step 1.

Reference: Nie et al., A Time Series is Worth 64 Words (ICLR 2023),
https://arxiv.org/abs/2211.14730
https://github.com/yuqinie98/PatchTST

This is a small TensorFlow adaptation, not the paper's benchmark configuration
or pretrained weights. A univariate sample becomes overlapping length-16
patches with stride 8, a shared linear embedding, learned position embeddings,
two post-LayerNorm attention blocks, and a flattened linear forecast head.
Settings: width 16, two heads, feed-forward width 32, GELU, dropout 0.1.
No end padding, RevIN, decomposition, or residual attention-score accumulation.
Residual feature connections ARE present. Shared channel-independent training
will pool node windows as separate samples, as in the current LSTM/TCN/GRU.

get_model(settings) returns an uncompiled (batch, inputSize, 1) ->
(batch, outputSize) core. Train-only normalization, the common training loss,
nonnegative serving output, model registry, and simulator routing are separate
integration steps. Only built-in Keras layers/operations are serialized; no
Lambda layers or custom_objects are needed when loading a .keras artifact.

The encoder attends to ALL patches of the supplied historical window. This is
intentional: every patch is known at the forecast origin. It must never receive
the held-out target/future window. No autoregressive causal mask is required.
The self-test uses synthetic data and temporary files, not simulation results.
"""

import argparse
import json
import subprocess
import sys
import tempfile
from pathlib import Path

import numpy as np
import tensorflow as tf


PATCH_LENGTH = 16
PATCH_STRIDE = 8
MODEL_DIM = 16
ATTENTION_HEADS = 2
ENCODER_LAYERS = 2
FEED_FORWARD_DIM = 32
DROPOUT = 0.1


def _positive_size(settings, key):
    value = settings[key]
    try:
        size = int(value)
        valid = not isinstance(value, (bool, np.bool_)) and float(value) == size
    except (TypeError, ValueError, OverflowError):
        valid, size = False, 0
    if not valid or size < 1:
        raise ValueError(f"{key} must be a positive integer.")
    return size


def get_model(settings):
    """Build the small univariate core without compiling or training it."""
    input_size = _positive_size(settings, "inputSize")
    output_size = _positive_size(settings, "outputSize")
    if input_size < PATCH_LENGTH or (input_size - PATCH_LENGTH) % PATCH_STRIDE:
        raise ValueError(
            f"inputSize must be >= {PATCH_LENGTH} and inputSize - "
            f"{PATCH_LENGTH} must be divisible by {PATCH_STRIDE}; "
            "this avoids silently dropping the newest observations."
        )
    patch_count = 1 + (input_size - PATCH_LENGTH) // PATCH_STRIDE
    layers, ops = tf.keras.layers, tf.keras.ops
    inputs = tf.keras.Input(shape=(input_size, 1), name="patchtst_input")

    # For ONE input channel this valid strided convolution is exactly a shared
    # Dense projection on length-16 patches, with no nonlinear convolution stack.
    x = layers.Conv1D(
        MODEL_DIM, PATCH_LENGTH, strides=PATCH_STRIDE, padding="valid",
        activation=None, name="patch_projection",
    )(inputs)
    # Symbolic indices keep the Embedding layer inside the serialized graph.
    positions = ops.zeros_like(x[:, :, 0], dtype="int32") + ops.arange(patch_count)
    positional = layers.Embedding(
        patch_count, MODEL_DIM,
        embeddings_initializer=tf.keras.initializers.RandomUniform(-0.02, 0.02),
        name="position_embedding",
    )(positions)
    x = layers.Add(name="patch_position_add")([x, positional])
    x = layers.Dropout(DROPOUT, name="embedding_dropout")(x)

    for index in range(1, ENCODER_LAYERS + 1):
        attention = layers.MultiHeadAttention(
            num_heads=ATTENTION_HEADS, key_dim=MODEL_DIM // ATTENTION_HEADS,
            attention_axes=(1,), dropout=0.0, name=f"encoder{index}_attention",
        )(x, x)
        attention = layers.Dropout(DROPOUT, name=f"encoder{index}_attn_dropout")(
            attention
        )
        x = layers.Add(name=f"encoder{index}_attn_add")([x, attention])
        x = layers.LayerNormalization(
            epsilon=1e-5, name=f"encoder{index}_attn_norm"
        )(x)
        feed_forward = layers.Dense(
            FEED_FORWARD_DIM, activation="gelu", name=f"encoder{index}_ff_expand"
        )(x)
        feed_forward = layers.Dropout(
            DROPOUT, name=f"encoder{index}_ff_inner_dropout"
        )(feed_forward)
        feed_forward = layers.Dense(
            MODEL_DIM, name=f"encoder{index}_ff_project"
        )(feed_forward)
        feed_forward = layers.Dropout(
            DROPOUT, name=f"encoder{index}_ff_dropout"
        )(feed_forward)
        x = layers.Add(name=f"encoder{index}_ff_add")([x, feed_forward])
        x = layers.LayerNormalization(
            epsilon=1e-5, name=f"encoder{index}_ff_norm"
        )(x)

    x = layers.Flatten(name="flatten_patches")(x)
    outputs = layers.Dense(output_size, name="patchtst_forecast")(x)
    return tf.keras.Model(inputs, outputs, name="patchtst_small_core")


def self_test():
    """Exercise patch indexing, gradients, training and independent loading."""
    tf.keras.utils.set_random_seed(42)
    rng = np.random.default_rng(42)
    model = get_model({"inputSize": 192, "outputSize": 64})
    if model.input_shape != (None, 192, 1) or model.output_shape != (None, 64):
        raise AssertionError("Unexpected input/output contract.")
    x = rng.normal(size=(4, 192, 1)).astype(np.float32)
    y = rng.normal(size=(4, 64)).astype(np.float32)

    # Independently calculate projected patches, including the final observation.
    starts = list(range(0, 192 - PATCH_LENGTH + 1, PATCH_STRIDE))
    patches = np.stack([x[:, start:start + PATCH_LENGTH, 0] for start in starts], axis=1)
    projection = model.get_layer("patch_projection")
    kernel, bias = projection.get_weights()
    reference = patches @ kernel[:, 0, :] + bias
    np.testing.assert_allclose(projection(x).numpy(), reference, atol=2e-6, rtol=2e-6)
    covered = {point for start in starts for point in range(start, start + PATCH_LENGTH)}
    if covered != set(range(192)) or len(starts) != 23:
        raise AssertionError("Patches must cover exactly the entire historical input.")

    # Extra future/target points must not be accepted as model input.
    try:
        model(np.zeros((1, 256, 1), dtype=np.float32), training=False)
    except (ValueError, tf.errors.InvalidArgumentError):
        pass
    else:
        raise AssertionError("Model accepted a window containing extra target points.")

    # Check every trainable tensor, including learned positions, is connected.
    with tf.GradientTape() as tape:
        loss = tf.reduce_mean(tf.abs(model(x, training=False) - y))
    gradients = tape.gradient(loss, model.trainable_weights)
    for weight, gradient in zip(model.trainable_weights, gradients):
        if gradient is None or not np.isfinite(tf.convert_to_tensor(gradient).numpy()).all():
            raise AssertionError(f"Missing or non-finite gradient: {weight.name}")

    before = model.get_weights()
    # MAE is only for this synthetic smoke check; real training uses the common
    # training profile in the next integration step.
    model.compile(optimizer=tf.keras.optimizers.Adam(0.001), loss="mae")
    training_loss = float(np.asarray(model.train_on_batch(x, y)).reshape(-1)[0])
    after = model.get_weights()
    if not np.isfinite(training_loss) or not all(np.isfinite(w).all() for w in after):
        raise AssertionError("Non-finite training result.")
    if not any(not np.array_equal(a, b) for a, b in zip(before, after)):
        raise AssertionError("Training did not update model weights.")

    expected = model(x, training=False).numpy()
    separate = np.concatenate([model(row[None], training=False).numpy() for row in x])
    np.testing.assert_allclose(separate, expected, atol=2e-5, rtol=2e-5)
    if expected.shape != (4, 64) or not np.isfinite(expected).all():
        raise AssertionError("Invalid prediction output.")
    for value in (0.0, 1.0):
        constant = np.full((1, 192, 1), value, dtype=np.float32)
        if not np.isfinite(model(constant, training=False).numpy()).all():
            raise AssertionError("Constant inputs produced non-finite predictions.")

    clean = get_model({"inputSize": 192, "outputSize": 64})
    clean.set_weights(after)
    with tempfile.TemporaryDirectory(prefix="dissect_patchtst_check_") as temporary:
        directory = Path(temporary)
        model_path = directory / "patchtst_core.keras"
        clean.save(model_path)
        loaded = tf.keras.models.load_model(model_path, compile=False, safe_mode=True)
        actual = loaded(x, training=False).numpy()
        np.testing.assert_allclose(actual, expected, atol=2e-5, rtol=2e-5)
        np.save(directory / "inputs.npy", x)
        # Deliberately do not import patchtst_network in the fresh interpreter.
        loader = (
            "import sys, numpy as np, tensorflow as tf; "
            "m=tf.keras.models.load_model(sys.argv[1],compile=False,safe_mode=True); "
            "np.save(sys.argv[3],m(np.load(sys.argv[2]),training=False).numpy())"
        )
        completed = subprocess.run(
            [sys.executable, "-c", loader, str(model_path),
             str(directory / "inputs.npy"), str(directory / "predictions.npy")],
            capture_output=True, text=True, timeout=120,
        )
        if completed.returncode:
            raise RuntimeError("Fresh-process load failed:\n" + completed.stderr[-6000:])
        fresh = np.load(directory / "predictions.npy")
        np.testing.assert_allclose(fresh, expected, atol=2e-5, rtol=2e-5)

    print(json.dumps({
        "status": "PATCHTST_SELF_TEST_OK",
        "tensorflow_version": tf.__version__,
        "implementation": "patchtst_small_tf_v1",
        "input_shape": list(model.input_shape),
        "output_shape": list(model.output_shape),
        "patch_length": PATCH_LENGTH, "patch_stride": PATCH_STRIDE,
        "patch_count": len(starts), "model_dim": MODEL_DIM,
        "attention_heads": ATTENTION_HEADS, "encoder_layers": ENCODER_LAYERS,
        "feed_forward_dim": FEED_FORWARD_DIM, "dropout": DROPOUT,
        "parameters": model.count_params(),
        "patch_projection_and_coverage": "passed", "input_boundary": "passed",
        "finite_gradients": "passed", "training_step": "passed",
        "batch_independence": "passed", "constant_inputs": "passed",
        "save_load": "passed", "fresh_process_load": "passed",
        "save_load_max_abs_error": float(np.max(np.abs(actual - expected))),
        "fresh_process_max_abs_error": float(np.max(np.abs(fresh - expected))),
        "note": "Synthetic core check only; PatchTST is not yet registered in the simulator.",
    }, indent=2, allow_nan=False))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--self-test", action="store_true")
    arguments = parser.parse_args()
    if arguments.self_test:
        self_test()
    else:
        get_model({"inputSize": 192, "outputSize": 64}).summary()
