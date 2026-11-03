"""PatchTST prediction adapter and read-only check of the Predictor request path."""

import argparse
import hashlib
import json
import sys
from pathlib import Path

import numpy as np
import pandas as pd
import tensorflow as tf

SCRIPTS_DIRECTORY = Path(__file__).resolve().parents[1]
if str(SCRIPTS_DIRECTORY) not in sys.path:
    sys.path.insert(0, str(SCRIPTS_DIRECTORY))

from model_registry import ModelRegistry
from predictor_models.predictor_model import PredictorModel


class PatchTSTModel(PredictorModel):
    def __init__(self, predictor_settings):
        super().__init__("PATCHTST", predictor_settings)
        prediction = predictor_settings["prediction"]
        if prediction.get("scale") is not False or prediction.get("smoothing") is not None:
            raise ValueError(
                "PATCHTST serving models contain normalization; "
                "prediction.scale must be False and smoothing must be None."
            )
        parameters = predictor_settings["predictor"].get("hyperparameters", {})
        future_path = self._configured_path(parameters.get("future_model_location"))
        if future_path is None:
            registered = ModelRegistry().get_active("shared", "PATCHTST")
            if registered is None:
                raise FileNotFoundError(
                    "No active PATCHTST model; configure future_model_location "
                    "with the candidate artifact path."
                )
            future_path = Path(registered["artifact_path"]).resolve()

        self._future_model = tf.keras.models.load_model(future_path, compile=False, safe_mode=True)
        test_path = self._configured_path(parameters.get("test_model_location"))
        if test_path is None or test_path == future_path:
            self._test_model = self._future_model
        else:
            self._test_model = tf.keras.models.load_model(test_path, compile=False, safe_mode=True)
        self._dimensions(False)
        self._dimensions(True)

    @staticmethod
    def _configured_path(value):
        text = "" if value is None else str(value).strip()
        if not text or text in ("<path_to_keras>", "<path_to_h5>"):
            return None
        path = Path(text).expanduser().resolve()
        if not path.is_file():
            raise FileNotFoundError(f"PATCHTST model artifact does not exist: {path}")
        return path

    def _get_model(self, is_test_data):
        return self._test_model if is_test_data else self._future_model

    def _dimensions(self, is_test_data):
        model = self._get_model(is_test_data)
        input_shape, output_shape = model.input_shape, model.output_shape
        if (
            isinstance(input_shape, list) or len(input_shape) != 3
            or input_shape[1] is None or input_shape[2] != 1
            or int(input_shape[1]) < 1
        ):
            raise ValueError("PATCHTST requires input shape (batch, fixed length, 1).")
        if (
            isinstance(output_shape, list) or len(output_shape) != 2
            or output_shape[1] is None or int(output_shape[1]) < 1
        ):
            raise ValueError("PATCHTST requires output shape (batch, fixed horizon).")
        return int(input_shape[1]), int(output_shape[1])

    def get_input_size(self, is_test_data=False):
        return self._dimensions(is_test_data)[0]

    def predict(self, feature_name, dataframe, prediction_length, is_test_data):
        input_size, output_size = self._dimensions(is_test_data)
        if (
            isinstance(prediction_length, bool)
            or not isinstance(prediction_length, (int, np.integer))
            or prediction_length != output_size
        ):
            raise ValueError(
                f"PATCHTST output size is {output_size}, requested {prediction_length}."
            )
        values = np.asarray(dataframe["data"].values, dtype=np.float32)
        if len(values) < input_size:
            raise ValueError(f"PATCHTST requires {input_size} values; got {len(values)}.")
        values = values[-input_size:]
        if not np.isfinite(values).all():
            raise ValueError("PATCHTST input contains non-finite values.")
        result = np.asarray(self._get_model(is_test_data)(
            values.reshape(1, input_size, 1), training=False
        ))
        if result.shape != (1, output_size) or not np.isfinite(result).all():
            raise ValueError("PATCHTST returned an invalid prediction.")
        return result[0].tolist()


def _sha256(path):
    digest = hashlib.sha256()
    with Path(path).open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def check_prediction(result_path, dataset_path):
    """Check plumbing with an existing snapshot; do not report performance."""
    from predictor import Predictor

    result = json.loads(Path(result_path).read_text(encoding="utf-8-sig"))
    if result.get("model_type") != "PATCHTST" or result.get("feature_name") != "shared":
        raise ValueError("The training result must refer to a shared PATCHTST model.")
    artifact = Path(result["artifact_path"]).expanduser().resolve()
    metadata = json.loads((artifact.parent / "metadata.json").read_text(encoding="utf-8"))
    preprocessing = json.loads(
        (artifact.parent / "preprocessing.json").read_text(encoding="utf-8")
    )
    if (
        metadata.get("model_type") != "PATCHTST"
        or metadata.get("feature_name") != "shared"
        or metadata.get("model_version") != result["model_version"]
        or metadata.get("artifact_sha256") != _sha256(artifact)
    ):
        raise ValueError("PATCHTST artifact identity or checksum verification failed.")
    if (
        preprocessing.get("scale") is not False
        or preprocessing.get("smoothing") is not None
        or preprocessing.get("strategy") != "embedded_train_standardization_v1"
    ):
        raise ValueError("Unexpected PATCHTST preprocessing metadata.")

    settings = {
        "predictor_name": "PATCHTST256-check",
        "prediction": {
            "length": 64, "testSize": 64, "batchSize": 256,
            "scale": False, "smoothing": None, "minPredictionTime": 0,
        },
        "predictor": {
            "predictor": "PATCHTST",
            "hyperparameters": {
                "future_model_location": str(artifact),
                "test_model_location": str(artifact),
            },
        },
    }
    predictor = Predictor(settings)
    adapter = predictor._predictor_model
    if adapter.__class__.__name__ != "PatchTSTModel" or adapter._name != "PATCHTST":
        raise AssertionError("Predictor did not route the request to PatchTSTModel.")
    if adapter.get_input_size(False) != 192 or adapter.get_input_size(True) != 192:
        raise AssertionError("Expected a PATCHTST with input length 192.")

    frame = pd.read_csv(dataset_path, sep=";")
    selected = None
    for column in frame.columns:
        name = str(column)
        if not name.endswith("::notYetProcessedData") or "_cloud::" in name:
            continue
        values = pd.to_numeric(
            frame[column].astype(str).str.replace(",", ".", regex=False), errors="coerce"
        ).to_numpy(dtype=float)
        for start in range(len(values) - 255):
            window = values[start:start + 256]
            if np.isfinite(window).all():
                selected = (name, start, window)
                break
        if selected is not None:
            break
    if selected is None:
        raise ValueError("No fog-node column has 256 consecutive finite observations.")

    name, start, window = selected
    response = predictor.compute({"name": name, "values": window.tolist()})
    # Independent slicing here verifies the request's historical/future alignment.
    loaded = tf.keras.models.load_model(artifact, compile=False, safe_mode=True)
    direct_future = np.asarray(loaded(
        window[-192:].astype(np.float32).reshape(1, 192, 1), training=False
    ))[0]
    direct_test = np.asarray(loaded(
        window[:192].astype(np.float32).reshape(1, 192, 1), training=False
    ))[0]
    errors = {}
    for phase, expected, first_timestamp in (
        ("future", direct_future, 256), ("test", direct_test, 192)
    ):
        prediction = response[f"prediction_{phase}"]
        actual = np.asarray(prediction["data"], dtype=float)
        if actual.shape != (64,) or not np.isfinite(actual).all() or (actual < 0).any():
            raise AssertionError(f"Invalid {phase} prediction.")
        if prediction["timestamp"] != list(range(first_timestamp, first_timestamp + 64)):
            raise AssertionError(f"Incorrect {phase} timestamp alignment.")
        np.testing.assert_allclose(actual, expected, atol=0.1, rtol=1e-5)
        errors[phase] = float(np.max(np.abs(actual - expected)))
    # Confirm the supplied historical target cannot enter the backtest input.
    changed_targets = window.copy()
    changed_targets[192:] += 1000.0
    changed_response = predictor.compute({"name": name, "values": changed_targets.tolist()})
    np.testing.assert_allclose(
        response["prediction_test"]["data"],
        changed_response["prediction_test"]["data"], atol=0.1, rtol=1e-5,
    )
    # The future request must use only the latest 192 of the 256 observations.
    changed_prefix = window.copy()
    changed_prefix[:64] += 1000.0
    prefix_response = predictor.compute({"name": name, "values": changed_prefix.tolist()})
    np.testing.assert_allclose(
        response["prediction_future"]["data"],
        prefix_response["prediction_future"]["data"], atol=0.1, rtol=1e-5,
    )
    for key, expected in (
        ("test_data_beginning", window[:192]),
        ("test_data_end", window[192:]),
        ("preprocessed_data", window[-192:]),
    ):
        np.testing.assert_allclose(response[key]["data"], expected, atol=1e-5, rtol=1e-6)

    summary = {
        "status": "PATCHTST_PREDICTION_CHECK_OK",
        "model_type": "PATCHTST",
        "model_version": result["model_version"],
        "feature_name": name,
        "snapshot_start_row": start,
        "input_size": 192,
        "future_points": 64,
        "test_points": 64,
        "future_timestamp_range": [256, 319],
        "test_timestamp_range": [192, 255],
        "artifact_sha256": "verified",
        "routing": "PatchTSTModel",
        "direct_call_max_abs_error": errors,
        "backtest_input_boundary": "passed",
        "future_input_boundary": "passed",
        "input_preprocessing": "embedded model normalization only",
        "note": "Interface check using a training snapshot; not an accuracy evaluation.",
    }
    print(json.dumps(summary, indent=2, ensure_ascii=False, allow_nan=False))
    return summary


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check-result", required=True, help="patchtst_training_result.json")
    parser.add_argument("--dataset", required=True, help="Training snapshot CSV for the check")
    args = parser.parse_args()
    check_prediction(args.check_result, args.dataset)
