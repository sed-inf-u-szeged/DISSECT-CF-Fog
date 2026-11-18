"""Causal first-fit orchestration tests; the explicit model double needs no TensorFlow."""
import copy
import csv
from dataclasses import replace
import io
import json
from pathlib import Path
import tempfile
import unittest

import numpy as np
from build_mobility_windows import read_json, write_json
from mobility_online_buffer import OnlineSampleBuffer, TrainingPolicy
from mobility_online_buffer_worker import serve
from mobility_online_first_fit_worker import FirstFitObserver
from mobility_online_lstm import OnlineLstm, training_data
from run_mobility_online_buffer import audit_online, jsonlines
from run_mobility_online_first_fit import audit_first_fit
from test_mobility_online_buffer import samples, raw, stream_text, feed


class ModelDouble:
    """Test-only constant displacement; integration uses the real TensorFlow trainer."""
    def __init__(self, request):
        self.metadata = dict(status="COMPLETE", reload_check="PASSED", training_windows=len(request.windows),
                             training_cutoff_sim_ms=request.requested_at_sim_ms)
        self.calls = 0
        self.verified = False

    def predict_delta(self, x):
        self.calls += 1
        return np.zeros((len(x), 2))

    def verify_frozen(self):
        self.verified = True


class MobilityOnlineFirstFitTest(unittest.TestCase):
    def setUp(self):
        self.folder = tempfile.TemporaryDirectory()
        self.addCleanup(self.folder.cleanup)
        self.root = Path(self.folder.name)
        self.output = self.root / "run"
        self.output.mkdir()
        self.policy = self.root / "policy.json"
        write_json(self.policy, {})
        self.requests = []

    def trainer(self, request, snapshot, output):
        training_data(request)
        self.requests.append(request)
        self.assertTrue(snapshot.is_file())
        self.assertFalse(output.exists())
        return ModelDouble(request)

    def run_stream(self, data=None, text=None, trainer=None):
        data = samples() if data is None else data
        observer = FirstFitObserver(self.output, trainer or self.trainer)
        self.addCleanup(observer.close)
        reply = io.StringIO()
        serve(self.policy, self.output, io.StringIO(stream_text(data) if text is None else text), reply, observer)
        observer.close()
        with (self.output / "shadow_receipts.csv").open("w", encoding="utf-8", newline="") as stream:
            writer = csv.writer(stream)
            writer.writerow(["origin_ms", "target_ms", "received_at_sim_ms", "predicted_latitude", "predicted_longitude"])
            for line in reply.getvalue().splitlines():
                if line.startswith("PRED\t"):
                    _, origin, target, lat, lon = line.split("\t")
                    writer.writerow([origin, target, origin, lat, lon])
        return observer, reply.getvalue(), data

    def test_first_fit_at_32_followed_by_80_matured_origins(self):
        observer, reply, data = self.run_stream()
        result = audit_first_fit(self.output, data)
        audit_online(self.output, data, TrainingPolicy(), worker_mode="FIRST_FIT_SHADOW", training="COMPLETE", scaler="FROZEN")
        self.assertEqual(len(self.requests), 1)
        self.assertEqual(len(self.requests[0].windows), 32)
        self.assertEqual(observer.model.calls, 80)
        self.assertTrue(observer.model.verified)
        self.assertEqual(result["deferred_request_sizes"], [96])
        self.assertFalse(result["active"])
        self.assertTrue(reply.endswith("DONE\t121\t112\n"))

    def test_stationary_disconnected_stream_still_trains_and_scores(self):
        _, _, data = self.run_stream(samples(stationary=True))
        result = audit_first_fit(self.output, data)
        self.assertEqual(result["metrics"]["LSTM"]["count"], 80)
        self.assertEqual(result["metrics"]["LSTM"]["rmse_m"], 0)
        self.assertEqual(training_data(self.requests[0])["y"].sum(), 0)

    def test_before_cutoff_no_fit_or_lstm_predictions(self):
        with self.assertRaises(ValueError):
            self.run_stream(samples(40))
        self.assertFalse(self.requests)
        events = jsonlines(self.output / "first_fit_events.jsonl")
        self.assertTrue(all(e["event"] == "OBSERVATION" for e in events))
        self.assertEqual(read_json(self.output / "first-fit.json")["status"], "FAILED")

    def test_training_failure_cannot_report_complete_or_issue_lstm(self):
        def failed(*args):
            raise RuntimeError("test trainer failure")
        with self.assertRaisesRegex(RuntimeError, "test trainer failure"):
            self.run_stream(trainer=failed)
        for name in ("first-fit.json", "online-worker.json"):
            self.assertEqual(read_json(self.output / name)["status"], "FAILED")
        self.assertNotIn("SHADOW_PREDICTION", [e["event"] for e in jsonlines(self.output / "first_fit_events.jsonl")])

    def test_incomplete_model_cannot_enter_shadow(self):
        def invalid(request, *_):
            model = ModelDouble(request)
            model.metadata["reload_check"] = "FAILED"
            return model
        with self.assertRaisesRegex(ValueError, "incomplete or mismatched"):
            self.run_stream(trainer=invalid)
        self.assertEqual(read_json(self.output / "first-fit.json")["lifecycle_state"], "TRAINING")

    def test_missing_end_after_training_cannot_report_complete(self):
        with self.assertRaisesRegex(ValueError, "without END"):
            self.run_stream(text=stream_text(samples())[:-4])
        self.assertEqual(len(self.requests), 1)
        self.assertEqual(read_json(self.output / "first-fit.json")["status"], "FAILED")

    def test_future_label_is_rejected_before_fitting(self):
        request = feed(OnlineSampleBuffer(), samples(41))[-1][3]
        with self.assertRaisesRegex(ValueError, "Unavailable training label"):
            training_data(replace(request, requested_at_sim_ms=request.requested_at_sim_ms - 60000))

    def test_mixed_identity_and_nonfinite_training_features_rejected(self):
        request = feed(OnlineSampleBuffer(), samples(41))[-1][3]
        item = request.windows[-1]
        for window in (replace(item.window, run_id="other"),
                       replace(item.window, x=tuple(tuple(float("nan") for _ in row) for row in item.window.x))):
            changed = replace(request, windows=request.windows[:-1] + (replace(item, window=window),))
            with self.assertRaises(ValueError):
                training_data(changed)

    def test_altered_snapshot_rejected_before_tensorflow_or_output_creation(self):
        request = feed(OnlineSampleBuffer(), samples(41))[-1][3]
        snapshot = self.root / "snapshot.json"
        write_json(snapshot, dict(request_number=1, requested_at_sim_ms=request.requested_at_sim_ms,
                                  training_windows=32, windows=[]))
        directory = self.output / "models/v0001"
        with self.assertRaisesRegex(ValueError, "snapshot differs"):
            OnlineLstm.fit(None, request, snapshot, directory)
        self.assertFalse(directory.exists())

    def test_later_observations_do_not_change_training_snapshot(self):
        buffer = OnlineSampleBuffer()
        data = samples()
        request = feed(buffer, data[:41], final=False)[-1][3]
        before = copy.deepcopy(training_data(request))
        feed(buffer, data[41:])
        after = training_data(request)
        np.testing.assert_array_equal(before["x"], after["x"])
        np.testing.assert_array_equal(before["y"], after["y"])
        self.assertEqual(before["records"], after["records"])

    def test_event_audit_rejects_prediction_before_training_complete(self):
        _, _, data = self.run_stream()
        path = self.output / "first_fit_events.jsonl"
        events = jsonlines(path)
        index = next(i for i, e in enumerate(events) if e["event"] == "TRAINING_COMPLETE")
        events[index], events[index + 1] = events[index + 1], events[index]
        for i, event in enumerate(events, 1):
            event["sequence"] = i
        path.write_text("".join(json.dumps(e) + "\n" for e in events), encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "causal event order"):
            audit_first_fit(self.output, data)

    def test_evaluation_audit_rejects_early_target_and_changed_truth(self):
        _, _, data = self.run_stream()
        path = self.output / "first_fit_evaluations.csv"
        with path.open(encoding="utf-8", newline="") as stream:
            records = list(csv.DictReader(stream))
        for field, value in (("evaluated_at_sim_ms", records[0]["origin_ms"]), ("actual_latitude_deg", "0")):
            modified = copy.deepcopy(records)
            modified[0][field] = value
            with path.open("w", encoding="utf-8", newline="") as stream:
                writer = csv.DictWriter(stream, fieldnames=list(modified[0]))
                writer.writeheader()
                writer.writerows(modified)
            with self.assertRaises(ValueError):
                audit_first_fit(self.output, data)


if __name__ == "__main__":
    unittest.main(verbosity=2)
