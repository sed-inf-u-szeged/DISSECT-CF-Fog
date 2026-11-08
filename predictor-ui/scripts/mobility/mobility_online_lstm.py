"""Train one mobility LSTM on an immutable, already-mature training request."""
import csv
import importlib.metadata
import json
import sys
import time

import numpy as np
from build_mobility_windows import FEATURES, RADIUS, project, read_json, require, sha256, write_json
from mobility_learning import fit_scaler, transform_x
from mobility_online_buffer import REFERENCE
from train_mobility_lstm import make_model

ARCHITECTURE = ["Input(8,6)", "LSTM(32)", "Dense(16,relu)", "Dense(2,linear)"]


def version_name(request_number):
    require(type(request_number) is int and request_number > 0, "Invalid training request number")
    return "v%04d" % request_number


def tensorflow_runtime():
    import tensorflow as tf
    tf.config.threading.set_inter_op_parallelism_threads(1)
    tf.config.threading.set_intra_op_parallelism_threads(2)
    tf.keras.utils.set_random_seed(42)
    tf.config.experimental.enable_op_determinism()
    return tf


def training_data(request):
    """Validate time/identity boundaries before a scaler or model can see data."""
    windows = request.windows
    require(len(windows) >= 1 and request.request_number >= 1, "Empty or invalid request")
    identity = (windows[0].window.run_id, windows[0].window.device_id)
    previous = None
    records = []
    for item in windows:
        w = item.window
        require((w.run_id, w.device_id) == identity and identity[0] and identity[1] == "0", "Mixed training identity")
        require(w.target_ms == w.origin_ms + 60000 and item.completed_at_sim_ms == w.target_ms
                and w.target_ms <= request.requested_at_sim_ms, "Unavailable training label")
        require(w.history_time_ms == tuple(range(w.origin_ms - 420000, w.origin_ms + 1, 60000)),
                "Invalid training history times")
        require(previous is None or w.origin_ms == previous + 60000, "Duplicate or gapped training windows")
        previous = w.origin_ms
        record = item.record()
        record["split"] = "train"
        records.append(record)
    require(windows[-1].completed_at_sim_ms == request.requested_at_sim_ms, "Training cutoff differs")
    x = np.asarray([w.window.x for w in windows], dtype=np.float64)
    y = np.asarray([w.y for w in windows], dtype=np.float64)
    require(x.shape == (len(windows), 8, 6) and y.shape == (len(windows), 2)
            and np.isfinite(x).all() and np.isfinite(y).all(), "Invalid training tensors")
    require(np.all(x[:, -1, :2] == 0) and np.all(x[:, :, 2] >= 0)
            and np.isin(x[:, :, 5], [0, 1]).all(), "Invalid training features")
    valid = x[:, :, 5] == 1
    require(np.allclose(np.sum(x[:, :, 3:5][valid] ** 2, axis=-1), 1, atol=1e-8)
            and np.all(x[:, :, 3:5][~valid] == 0), "Invalid heading encoding")
    for item in windows:
        points = [(item.window.anchor_latitude_deg, item.window.anchor_longitude_deg),
                  (item.target_latitude_deg, item.target_longitude_deg)]
        require(all(np.isfinite([lat, lon]).all() and abs(lat) <= 90 and abs(lon) <= 180 for lat, lon in points),
                "Invalid training GPS")
        anchor, target = [project(*p, REFERENCE) for p in points]
        require(np.array_equal(item.y, np.subtract(target, anchor)), "Training label/GPS mismatch")
    return dict(x=x, y=y, records=records)


class OnlineLstm:
    def __init__(self, model, scaler, directory, metadata):
        self.model, self.scaler, self.directory, self.metadata = model, scaler, directory, metadata

    @classmethod
    def fit(cls, tf, request, snapshot_path, directory):
        data = training_data(request)
        version = version_name(request.request_number)
        require(not directory.exists(), "Refusing existing model version")
        snapshot = read_json(snapshot_path)
        require(snapshot["request_number"] == request.request_number
                and snapshot["requested_at_sim_ms"] == request.requested_at_sim_ms
                and snapshot["training_windows"] == len(request.windows)
                and snapshot["windows"] == json.loads(json.dumps([w.record() for w in request.windows])),
                "Training snapshot differs from the live request")
        directory.mkdir(parents=True, exist_ok=False)
        metadata = dict(schema_version=1, status="TRAINING", model="MobilityOnlineLSTM-v1", version=version,
                        mode="FIRST_FIT_SHADOW" if request.request_number == 1 else "RETRY_SHADOW", architecture=ARCHITECTURE,
                        training_windows=len(request.windows), training_cutoff_sim_ms=request.requested_at_sim_ms,
                        request_number=request.request_number, request_sha256=sha256(snapshot_path),
                        epochs=20, batch_size=32, seed=42, learning_rate=0.001, gradient_clipnorm=1.0,
                        validation="NONE_FIXED_EPOCHS", selection="FINAL_EPOCH", device="CPU",
                        prediction_horizon_ms=60000,
                        scaler_fit=("LATEST_MATURE_REQUEST_ONLY" if snapshot.get("window_selection") == "LATEST_MATURE_WINDOWS"
                                    else "REQUEST_PREFIX_ONLY"), wall_time_limit_s=120,
                        quality_gate="NOT_APPLIED", active=False,
                        versions=dict(python=sys.version.split()[0], numpy=np.__version__, tensorflow=tf.__version__,
                                      keras=importlib.metadata.version("keras")))
        write_json(directory / "training.json", metadata)
        started = time.monotonic()
        try:
            tf.keras.utils.set_random_seed(42)
            scaler = fit_scaler(data)
            scaler.update(feature_names=FEATURES, target_names=["delta_east_m", "delta_north_m"],
                          fit_cutoff_sim_ms=request.requested_at_sim_ms, request_sha256=metadata["request_sha256"],
                          projection=dict(name="fixed_reference_equirectangular", earth_radius_m=RADIUS,
                                          reference_latitude_deg=REFERENCE[0], reference_longitude_deg=REFERENCE[1]))
            x = transform_x(data["x"], scaler)
            y = (data["y"] / scaler["y_scale_m"]).astype(np.float32)
            model = make_model(tf)
            require(model.count_params() == 5554, "Unexpected LSTM architecture")
            rng = np.random.default_rng(42)
            with (directory / "history.csv").open("x", encoding="utf-8", newline="") as stream:
                writer = csv.writer(stream)
                writer.writerow(["epoch", "train_scaled_mse"])
                for epoch in range(1, 21):
                    total = 0.0
                    order = rng.permutation(len(x))
                    for offset in range(0, len(x), 32):
                        require(time.monotonic() - started <= 120, "Online training time limit exceeded")
                        indices = order[offset:offset + 32]
                        model.reset_metrics()
                        loss = float(model.train_on_batch(x[indices], y[indices], return_dict=True)["loss"])
                        require(np.isfinite(loss), "Non-finite training loss")
                        total += loss * len(indices)
                    writer.writerow([epoch, total / len(x)])
                    stream.flush()
            write_json(directory / "preprocessing.json", scaler)
            model.save(directory / "model.keras")
            metadata.update(status="COMPLETE", parameters=5554, elapsed_seconds=time.monotonic() - started,
                            artifact_sha256={name: sha256(directory / name)
                                             for name in ("model.keras", "preprocessing.json", "history.csv")})
            write_json(directory / "training.json", metadata)
            saved = cls.load(tf, directory)
            before = np.asarray(model(x, training=False)) * scaler["y_scale_m"]
            after = saved.predict_delta(data["x"])
            require(np.allclose(before, after, rtol=1e-6, atol=1e-6), "Reloaded model/scaler predictions differ")
            metadata.update(reload_check="PASSED")
            write_json(directory / "training.json", metadata)
            saved.metadata = metadata
            return saved
        except BaseException as error:
            metadata.update(status="FAILED", error=str(error), elapsed_seconds=time.monotonic() - started)
            write_json(directory / "training.json", metadata)
            raise

    @classmethod
    def load(cls, tf, directory):
        metadata = read_json(directory / "training.json")
        require(metadata["status"] == "COMPLETE" and metadata["model"] == "MobilityOnlineLSTM-v1"
                and metadata["version"] == version_name(metadata["request_number"])
                and metadata["architecture"] == ARCHITECTURE,
                "Expected a complete online LSTM version")
        for name in ("model.keras", "preprocessing.json", "history.csv"):
            require(sha256(directory / name) == metadata["artifact_sha256"][name], "Frozen artifact changed: " + name)
        scaler = read_json(directory / "preprocessing.json")
        require(scaler["fit_windows"] == metadata["training_windows"]
                and scaler["fit_cutoff_sim_ms"] == metadata["training_cutoff_sim_ms"]
                and scaler["request_sha256"] == metadata["request_sha256"] and scaler["feature_names"] == FEATURES,
                "Scaler provenance differs")
        model = tf.keras.models.load_model(directory / "model.keras", compile=False, safe_mode=True)
        require(model.input_shape == (None, 8, 6) and model.output_shape == (None, 2)
                and model.count_params() == 5554, "Loaded model shape differs")
        return cls(model, scaler, directory, metadata)

    def predict_delta(self, x):
        result = np.asarray(self.model(transform_x(x, self.scaler), training=False)) * self.scaler["y_scale_m"]
        require(result.shape == (len(x), 2) and np.isfinite(result).all(), "Invalid LSTM prediction")
        return result

    def verify_frozen(self):
        for name in ("model.keras", "preprocessing.json", "history.csv"):
            require(sha256(self.directory / name) == self.metadata["artifact_sha256"][name], "Frozen bundle changed")
