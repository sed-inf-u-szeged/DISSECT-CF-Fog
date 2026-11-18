"""Causal retry tests use explicit model doubles; the runner uses real TensorFlow."""
import copy
import csv
from dataclasses import replace
import io
import json
from pathlib import Path
import tempfile
import unittest

import numpy as np
from build_mobility_windows import read_json, sha256, write_json
from check_mobility_run import motion
from mobility_online_buffer import OnlineSampleBuffer, TrainingPolicy
from mobility_online_buffer_worker import serve
from mobility_online_lstm import ARCHITECTURE, OnlineLstm, training_data, version_name
from mobility_online_retry_worker import RetryObserver
from run_mobility_online_buffer import audit_online, jsonlines
from run_mobility_online_retry import audit_retry
from test_mobility_online_buffer import samples, feed, stream_text


class RetryModelDouble:
    def __init__(self, request, snapshot, accelerate=False):
        self.metadata = dict(status="COMPLETE", reload_check="PASSED", version=version_name(request.request_number),
                             request_number=request.request_number, request_sha256=sha256(snapshot),
                             training_windows=len(request.windows), training_cutoff_sim_ms=request.requested_at_sim_ms)
        self.accelerate, self.calls, self.verifications = accelerate, 0, 0

    def predict_delta(self, x):
        self.calls += 1
        return x[:, -3, :2] - 3 * x[:, -2, :2] if self.accelerate else np.zeros((len(x), 2))

    def verify_frozen(self):
        self.verifications += 1


class MobilityOnlineRetryTest(unittest.TestCase):
    def setUp(self):
        self.folder = tempfile.TemporaryDirectory()
        self.addCleanup(self.folder.cleanup)
        self.root = Path(self.folder.name)
        self.output = self.root / "run"
        self.output.mkdir()
        self.policy = self.root / "policy.json"
        write_json(self.policy, {})
        self.requests, self.models = [], []

    def trainer(self, request, snapshot, directory):
        training_data(request)
        self.assertFalse(directory.exists())
        self.assertEqual(directory.name, version_name(request.request_number))
        self.requests.append(request)
        model = RetryModelDouble(request, snapshot)
        self.models.append(model)
        return model

    def run_stream(self, data=None, text=None, trainer=None):
        data = samples() if data is None else data
        self.observer = RetryObserver(self.output, trainer or self.trainer)
        self.addCleanup(self.observer.close)
        reply = io.StringIO()
        serve(self.policy, self.output, io.StringIO(stream_text(data) if text is None else text), reply, self.observer)
        self.observer.close()
        with (self.output / "shadow_receipts.csv").open("w", encoding="utf-8", newline="") as stream:
            writer = csv.writer(stream)
            writer.writerow(["origin_ms", "target_ms", "received_at_sim_ms", "predicted_latitude", "predicted_longitude"])
            for line in reply.getvalue().splitlines():
                if line.startswith("PRED\t"):
                    _, origin, target, lat, lon = line.split("\t")
                    writer.writerow([origin, target, origin, lat, lon])
        return data, reply.getvalue()

    def test_reject_then_real_request_96_has_own_16_evaluations(self):
        data, reply = self.run_stream()
        result = audit_retry(self.output, data)
        audit_online(self.output, data, TrainingPolicy(), worker_mode="RETRY_SHADOW", training="COMPLETE", scaler="FROZEN")
        self.assertEqual([len(r.windows) for r in self.requests], [32, 96])
        self.assertEqual([m.calls for m in self.models], [64, 16])
        self.assertTrue(all(m.verifications >= 2 for m in self.models))
        self.assertEqual(result["versions"][1]["quality_decision"], "WAITING_FOR_EVALUATION")
        self.assertEqual(result["versions"][1]["remaining_quality_origins"], 48)
        self.assertFalse(result["active"])
        self.assertTrue(reply.endswith("DONE\t121\t112\n"))
        tick = int(data[104]["time_ms"])
        events = [e["event"] for e in jsonlines(self.output / "retry_events.jsonl") if e["sim_time_ms"] == tick]
        self.assertEqual(events, ["OBSERVATION", "EVALUATION", "QUALITY_DECISION", "TRAINING_STARTED",
                                  "TRAINING_COMPLETE", "SHADOW_PREDICTION"])

    def test_stationary_disconnected_zero_error_does_not_qualify_second_version_early(self):
        data, _ = self.run_stream(samples(stationary=True))
        result = audit_retry(self.output, data)
        self.assertEqual(result["versions"][0]["quality_decision"], "REJECT")
        second = result["versions"][1]
        self.assertEqual(second["metrics"]["LSTM"]["rmse_m"], 0)
        self.assertEqual(second["quality_evaluated_origins"], 16)
        self.assertEqual(second["quality_decision"], "WAITING_FOR_EVALUATION")
        self.assertFalse((self.output / "v0002-quality-decision.json").exists())

    def test_second_training_failure_cannot_complete_or_issue_new_predictions(self):
        def failed(request, snapshot, directory):
            if request.request_number == 2:
                raise RuntimeError("second training failed")
            return self.trainer(request, snapshot, directory)
        with self.assertRaisesRegex(RuntimeError, "second training failed"):
            self.run_stream(trainer=failed)
        for name in ("retry.json", "online-worker.json"):
            self.assertEqual(read_json(self.output / name)["status"], "FAILED")
        events = jsonlines(self.output / "retry_events.jsonl")
        self.assertFalse(any(e["event"] == "SHADOW_PREDICTION" and e["model_version"] == "v0002" for e in events))
        self.assertEqual(read_json(self.output / "v0001-quality-decision.json")["decision"], "REJECT")

    def test_wrong_second_model_version_is_rejected(self):
        def invalid(request, snapshot, directory):
            model = self.trainer(request, snapshot, directory)
            model.metadata["version"] = "v0001"
            return model
        with self.assertRaisesRegex(ValueError, "incomplete or mismatched version"):
            self.run_stream(trainer=invalid)
        self.assertEqual(read_json(self.output / "retry.json")["status"], "FAILED")

    def test_missing_end_after_second_fit_is_failed(self):
        with self.assertRaisesRegex(ValueError, "without END"):
            self.run_stream(text=stream_text(samples())[:-4])
        self.assertEqual(len(self.requests), 2)
        self.assertEqual(read_json(self.output / "retry.json")["status"], "FAILED")

    def test_truncation_before_64_matured_origins_does_not_retry(self):
        with self.assertRaises(ValueError):
            self.run_stream(samples(104))
        self.assertEqual(len(self.requests), 1)
        self.assertFalse((self.output / "v0001-quality-decision.json").exists())
        self.assertEqual(read_json(self.output / "retry.json")["status"], "FAILED")

    def test_later_labels_do_not_change_first_or_second_training_snapshot(self):
        data, _ = self.run_stream()
        buffer = OnlineSampleBuffer()
        replies = feed(buffer, data)
        requests = [r[3] for r in replies if r[3] is not None]
        for number, request in enumerate(requests, 1):
            saved = read_json(self.output / ("training-request-%04d.json" % number))
            self.assertEqual(saved["windows"], json.loads(json.dumps([w.record() for w in request.windows])))
            self.assertTrue(all(w.completed_at_sim_ms <= request.requested_at_sim_ms for w in request.windows))
        self.assertEqual(requests[1].windows[:32], requests[0].windows)

    def test_quality_pass_does_not_force_retry_or_claim_activation(self):
        data = samples()
        for i, row in enumerate(data):
            row["longitude_deg"] = str(18.27 + 0.000001 * i * i)
            if i:
                distance, heading = motion(data[i - 1], row)
                row.update(displacement_m=str(distance), observed_speed_m_s=str(distance / 60), heading_deg=str(heading))
        def good(request, snapshot, directory):
            model = self.trainer(request, snapshot, directory)
            model.accelerate = True
            return model
        self.run_stream(data, trainer=good)
        result = audit_retry(self.output, data)
        self.assertEqual(len(self.requests), 1)
        self.assertEqual(result["versions"][0]["quality_decision"], "PASS")
        self.assertEqual(result["versions"][0]["matched_origins"], 80)
        self.assertFalse(result["active"])

    def test_changed_version_tag_cannot_borrow_previous_64_samples(self):
        data, _ = self.run_stream()
        path = self.output / "retry_evaluations.csv"
        with path.open(encoding="utf-8", newline="") as stream:
            records = list(csv.DictReader(stream))
        records[-1]["model_version"] = "v0001"
        with path.open("w", encoding="utf-8", newline="") as stream:
            writer = csv.DictWriter(stream, fieldnames=list(records[0]))
            writer.writeheader()
            writer.writerows(records)
        with self.assertRaisesRegex(ValueError, "mixed-version"):
            audit_retry(self.output, data)

    def test_audit_rejects_training_before_quality_decision(self):
        data, _ = self.run_stream()
        path = self.output / "retry_events.jsonl"
        events = jsonlines(path)
        index = next(i for i, e in enumerate(events) if e["event"] == "QUALITY_DECISION")
        events[index], events[index + 1] = events[index + 1], events[index]
        for i, event in enumerate(events, 1):
            event["sequence"] = i
        path.write_text("".join(json.dumps(e) + "\n" for e in events), encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "causal event order"):
            audit_retry(self.output, data)

    def test_audit_rejects_false_complete_second_quality_window(self):
        data, _ = self.run_stream()
        path = self.output / "retry.json"
        state = read_json(path)
        state["versions"][1]["quality_evaluated_origins"] = 64
        state["versions"][1]["quality_decision"] = "PASS"
        write_json(path, state)
        with self.assertRaisesRegex(ValueError, "Per-version"):
            audit_retry(self.output, data)

    def test_later_training_cannot_rewrite_first_quality_decision(self):
        def altered(request, snapshot, directory):
            model = self.trainer(request, snapshot, directory)
            if request.request_number == 2:
                path = self.output / "v0001-quality-decision.json"
                value = read_json(path)
                value["decision"] = "PASS"
                write_json(path, value)
            return model
        with self.assertRaisesRegex(ValueError, "Frozen version quality decision changed"):
            self.run_stream(trainer=altered)

    def test_future_or_altered_retry_labels_are_rejected_before_trainer(self):
        buffer = OnlineSampleBuffer()
        data = samples(105)
        request = feed(buffer, data)[-1][3]
        observer = RetryObserver(self.output, self.trainer)
        self.addCleanup(observer.close)
        # The actual tensor validator is shared by the two training paths.
        altered = replace(request, requested_at_sim_ms=request.requested_at_sim_ms - 60000)
        with self.assertRaisesRegex(ValueError, "Unavailable training label"):
            training_data(altered)
        self.assertFalse(self.requests)

    def test_saved_model_cannot_alias_a_different_request_version(self):
        directory = self.output / "models/v0002"
        directory.mkdir(parents=True)
        write_json(directory / "training.json", dict(status="COMPLETE", model="MobilityOnlineLSTM-v1",
                                                     version="v0001", request_number=2, architecture=ARCHITECTURE))
        with self.assertRaisesRegex(ValueError, "complete online LSTM version"):
            OnlineLstm.load(None, directory)


if __name__ == "__main__":
    unittest.main(verbosity=2)
