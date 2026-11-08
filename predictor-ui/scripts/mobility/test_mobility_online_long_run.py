"""Long-stream lifecycle boundaries; model doubles do not establish real model quality."""
import csv
import io
import json
from pathlib import Path
import tempfile
import unittest

from build_mobility_windows import read_json, sha256, write_json
from check_mobility_run import motion
from mobility_online_buffer import TrainingPolicy
from mobility_online_buffer_worker import serve
from mobility_online_lstm import training_data
from mobility_online_retry_worker import RetryObserver
from run_mobility_online_buffer import audit_online, jsonlines
from run_mobility_online_retry import audit_retry
from test_mobility_online_buffer import samples, stream_text
from test_mobility_online_retry import RetryModelDouble


class MobilityOnlineLongRunTest(unittest.TestCase):
    def setUp(self):
        folder = tempfile.TemporaryDirectory()
        self.addCleanup(folder.cleanup)
        self.root = Path(folder.name)
        self.output = self.root / "run"
        self.output.mkdir()
        self.policy = self.root / "policy.json"
        write_json(self.policy, {})
        self.requests, self.models = [], []

    def trainer(self, request, snapshot, directory):
        training_data(request)
        self.requests.append(request)
        model = RetryModelDouble(request, snapshot)
        self.models.append(model)
        return model

    def run_stream(self, data=None, expected=233, trainer=None):
        data = samples(expected) if data is None else data
        observer = RetryObserver(self.output, trainer or self.trainer, expected)
        self.addCleanup(observer.close)
        reply = io.StringIO()
        serve(self.policy, self.output, io.StringIO(stream_text(data)), reply, observer, expected)
        observer.close()
        with (self.output / "shadow_receipts.csv").open("w", encoding="utf-8", newline="") as stream:
            writer = csv.writer(stream)
            writer.writerow(["origin_ms", "target_ms", "received_at_sim_ms", "predicted_latitude", "predicted_longitude"])
            for line in reply.getvalue().splitlines():
                if line.startswith("PRED\t"):
                    _, origin, target, lat, lon = line.split("\t")
                    writer.writerow([origin, target, origin, lat, lon])
        return data, reply.getvalue()

    def accelerated_samples(self):
        data = samples(233)
        for i, row in enumerate(data):
            row["longitude_deg"] = str(18.27 + 0.000001 * i * i)
            if i:
                distance, heading = motion(data[i - 1], row)
                row.update(displacement_m=str(distance), observed_speed_m_s=str(distance / 60), heading_deg=str(heading))
        return data

    def test_three_rejected_versions_have_own_64_and_terminal_224_is_not_fitted(self):
        data, reply = self.run_stream()
        result = audit_retry(self.output, data, 233)
        buffer = audit_online(self.output, data, TrainingPolicy(), worker_mode="RETRY_SHADOW",
                              training="COMPLETE", scaler="FROZEN", expected_observations=233)
        self.assertEqual(buffer["request_sizes"], [32, 96, 160, 224])
        self.assertEqual([len(r.windows) for r in self.requests], [32, 96, 160])
        self.assertEqual([m.calls for m in self.models], [64, 64, 64])
        self.assertEqual([r["quality_decision"] for r in result["versions"]], ["REJECT"] * 3)
        self.assertEqual([r["remaining_quality_origins"] for r in result["versions"]], [0] * 3)
        self.assertEqual(result["deferred_request_sizes"], [224])
        self.assertEqual(result["lifecycle_state"], "RETRAIN_PENDING")
        self.assertFalse(result["active"])
        self.assertTrue(reply.endswith("DONE\t233\t224\n"))
        events = jsonlines(self.output / "retry_events.jsonl")
        final = [e for e in events if e["sim_time_ms"] == int(data[-1]["time_ms"])]
        self.assertEqual([e["event"] for e in final],
                         ["OBSERVATION", "EVALUATION", "QUALITY_DECISION", "TRAINING_REQUEST_DEFERRED"])
        self.assertEqual(final[-1]["reason"], "STREAM_COMPLETE")
        for n, request in enumerate(self.requests, 1):
            saved = read_json(self.output / ("training-request-%04d.json" % n))
            self.assertEqual(saved["windows"], json.loads(json.dumps([w.record() for w in request.windows])))
            self.assertTrue(all(w.completed_at_sim_ms <= request.requested_at_sim_ms for w in request.windows))
        self.assertEqual(self.requests[2].windows[:96], self.requests[1].windows)

    def test_second_version_pass_stops_retries_and_freezes_first_64_decision(self):
        def trainer(request, snapshot, directory):
            model = self.trainer(request, snapshot, directory)
            model.accelerate = request.request_number == 2
            return model
        data, _ = self.run_stream(self.accelerated_samples(), trainer=trainer)
        result = audit_retry(self.output, data, 233)
        self.assertEqual([len(r.windows) for r in self.requests], [32, 96])
        self.assertEqual([r["quality_decision"] for r in result["versions"]], ["REJECT", "PASS"])
        self.assertEqual([m.calls for m in self.models], [64, 128])
        self.assertEqual(result["versions"][1]["quality_evaluated_origins"], 64)
        self.assertEqual(result["deferred_request_sizes"], [160, 224])
        self.assertEqual(result["lifecycle_state"], "QUALIFIED_SHADOW")
        self.assertFalse(result["active"])

    def test_first_version_pass_does_not_force_long_run_retraining(self):
        def trainer(request, snapshot, directory):
            model = self.trainer(request, snapshot, directory)
            model.accelerate = True
            return model
        data, _ = self.run_stream(self.accelerated_samples(), trainer=trainer)
        result = audit_retry(self.output, data, 233)
        self.assertEqual(len(self.requests), 1)
        self.assertEqual(result["versions"][0]["matched_origins"], 192)
        self.assertEqual(result["versions"][0]["quality_evaluated_origins"], 64)
        self.assertEqual(result["deferred_request_sizes"], [96, 160, 224])
        self.assertFalse(result["active"])

    def test_final_observation_at_second_decision_does_not_train_third(self):
        data, _ = self.run_stream(expected=169)
        result = audit_retry(self.output, data, 169)
        self.assertEqual([len(r.windows) for r in self.requests], [32, 96])
        self.assertEqual([m.calls for m in self.models], [64, 64])
        self.assertEqual(result["deferred_request_sizes"], [160])
        self.assertEqual(jsonlines(self.output / "retry_events.jsonl")[-1]["reason"], "STREAM_COMPLETE")

    def test_third_training_failure_never_publishes_third_version(self):
        def trainer(request, snapshot, directory):
            if request.request_number == 3:
                raise RuntimeError("third training failed")
            return self.trainer(request, snapshot, directory)
        with self.assertRaisesRegex(RuntimeError, "third training failed"):
            self.run_stream(trainer=trainer)
        self.assertEqual(read_json(self.output / "retry.json")["status"], "FAILED")
        self.assertFalse(any(e["event"] == "SHADOW_PREDICTION" and e["model_version"] == "v0003"
                             for e in jsonlines(self.output / "retry_events.jsonl")))

    def test_one_missing_final_target_cannot_complete_third_evaluation_block(self):
        with self.assertRaisesRegex(ValueError, "Incomplete development simulation stream"):
            self.run_stream(samples(232))
        self.assertEqual([m.calls for m in self.models], [64, 64, 63])
        self.assertFalse((self.output / "v0003-quality-decision.json").exists())
        self.assertEqual(read_json(self.output / "retry.json")["status"], "FAILED")

    def test_changed_third_decision_fails_independent_audit_even_with_changed_hash(self):
        data, _ = self.run_stream()
        path = self.output / "v0003-quality-decision.json"
        decision = read_json(path)
        decision["decision"] = "PASS"
        write_json(path, decision)
        state = read_json(self.output / "retry.json")
        state["versions"][2].update(quality_decision="PASS", decision_sha256=sha256(path))
        write_json(self.output / "retry.json", state)
        with self.assertRaisesRegex(ValueError, "quality decision differs"):
            audit_retry(self.output, data, 233)

    def test_third_training_cannot_precede_second_quality_decision(self):
        data, _ = self.run_stream()
        path = self.output / "retry_events.jsonl"
        events = jsonlines(path)
        index = next(i for i, e in enumerate(events) if e["event"] == "QUALITY_DECISION" and e["model_version"] == "v0002")
        events[index], events[index + 1] = events[index + 1], events[index]
        for i, event in enumerate(events, 1):
            event["sequence"] = i
        path.write_text("".join(json.dumps(e) + "\n" for e in events), encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "causal event order"):
            audit_retry(self.output, data, 233)


if __name__ == "__main__":
    unittest.main(verbosity=2)
