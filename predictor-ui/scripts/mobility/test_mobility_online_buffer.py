"""Causality, maturity, request growth and stream failure checks; no model training."""
import copy
import csv
from dataclasses import FrozenInstanceError
import io
import json
from pathlib import Path
import tempfile
import unittest

from build_mobility_windows import read_json, sha256, windows_for_run, write_json
from check_mobility_run import motion
from mobility_online_buffer import OnlineSampleBuffer, TrainingPolicy
from mobility_online_buffer_worker import serve
from mobility_shadow_worker import HEADER
from run_mobility_online_buffer import audit_online, jsonlines


def samples(count=121, stationary=False, run_id="run"):
    result = []
    for i in range(count):
        row = dict(zip(HEADER, [run_id, "0", str(193000 + i * 60000), "47.73",
            str(18.27 if stationary else 18.27 + i * .0001), "", "", "", "", "false", "false", "NONE"]))
        if i:
            distance, heading = motion(result[-1], row)
            row.update(delta_time_s="60", displacement_m=str(distance), observed_speed_m_s=str(distance / 60),
                       heading_deg=str(heading) if distance > 1e-6 else "", motion_valid="true",
                       heading_valid="true" if distance > 1e-6 else "false")
        result.append(row)
    return result


def raw(row):
    text = io.StringIO()
    csv.writer(text).writerow([row[k] for k in HEADER])
    return text.getvalue().rstrip("\r\n")


def feed(buffer, data, final=True):
    return [buffer.observe(raw(row), not (final and i == len(data) - 1)) for i, row in enumerate(data)]


def state(buffer):
    return copy.deepcopy((buffer.history.__dict__, buffer.pending, buffer.completed_count,
                          buffer.request_count, buffer.next_request_windows, buffer._training_prefix))


def stream_text(data):
    return "".join("OBS\t%s\t%s\n" % ("false" if i == len(data) - 1 else "true", raw(row))
                   for i, row in enumerate(data)) + "END\n"


class MobilityOnlineBufferTest(unittest.TestCase):
    def test_first_request_needs_41_actual_observations(self):
        buffer = OnlineSampleBuffer()
        data = samples(41)
        for i, row in enumerate(data):
            _, pending, labelled, request = buffer.observe(raw(row))
            self.assertEqual(pending is not None, i >= 8)
            self.assertEqual(labelled is not None, i >= 9)
            self.assertEqual(request is not None, i == 40)
        self.assertEqual(len(request.windows), 32)
        self.assertEqual(request.requested_at_sim_ms, int(data[40]["time_ms"]))

    def test_growth_32_96_160_and_immutable_old_snapshot(self):
        buffer = OnlineSampleBuffer()
        results = feed(buffer, samples(180))
        requests = [(i + 1, r[3]) for i, r in enumerate(results) if r[3] is not None]
        self.assertEqual([n for n, _ in requests], [41, 105, 169])
        self.assertEqual([len(r.windows) for _, r in requests], [32, 96, 160])
        first = requests[0][1]
        self.assertEqual(first.windows, requests[1][1].windows[:32])
        with self.assertRaises(FrozenInstanceError):
            first.windows[0].window.origin_ms = 0
        with self.assertRaises(TypeError):
            first.windows[0].window.x[0][0] = 999
        record = first.windows[0].record()
        record["y"] = [999, 999]
        self.assertNotEqual(first.windows[0].y, (999, 999))

    def test_all_112_windows_match_independent_offline_builder(self):
        data = samples()
        expected, skipped = windows_for_run(dict(case_id="case", route_id="route", split="development", run_id="run"),
                                             data, [47.73, 18.27])
        completed = [r[2] for r in feed(OnlineSampleBuffer(), data) if r[2] is not None]
        self.assertEqual(skipped, 1)
        self.assertEqual(len(completed), 112)
        for actual, wanted in zip(completed, expected):
            actual = json.loads(json.dumps(actual.record()))
            self.assertEqual(actual.pop("completed_at_sim_ms"), wanted["target_ms"])
            self.assertEqual(actual, {k: wanted[k] for k in actual})

    def test_future_truth_changes_only_label_of_existing_input(self):
        data = samples(10)
        left, right = OnlineSampleBuffer(), OnlineSampleBuffer()
        for row in data[:9]:
            left.observe(raw(row))
            right.observe(raw(row))
        old = left.pending
        changed = data[-1].copy()
        changed["latitude_deg"] = "47.7301"
        distance, heading = motion(data[-2], changed)
        changed.update(displacement_m=str(distance), observed_speed_m_s=str(distance / 60), heading_deg=str(heading))
        a = left.observe(raw(data[-1]))[2]
        b = right.observe(raw(changed))[2]
        self.assertEqual(a.window, b.window)
        self.assertEqual(a.window, old)
        self.assertNotEqual(a.y, b.y)

    def test_invalid_observations_are_atomic_even_before_warmup(self):
        data = samples(3)
        for field, value in (("time_ms", data[0]["time_ms"]), ("time_ms", data[2]["time_ms"]),
                ("run_id", "different"), ("device_id", "1"), ("latitude_deg", "nan"),
                ("longitude_deg", "181"), ("observed_speed_m_s", "nan"), ("displacement_m", "999"),
                ("heading_deg", "nan"), ("heading_deg", "0"), ("heading_valid", "false"),
                ("current_node", "unknown"), ("motion_valid", "false")):
            with self.subTest(field=field, value=value):
                buffer = OnlineSampleBuffer()
                buffer.observe(raw(data[0]))
                before = state(buffer)
                broken = data[1].copy()
                broken[field] = value
                with self.assertRaises(ValueError):
                    buffer.observe(raw(broken))
                self.assertEqual(state(buffer), before)
                buffer.observe(raw(data[1]))
                self.assertEqual(buffer.history.count, 2)

    def test_duplicate_gap_and_identity_do_not_consume_pending(self):
        data = samples(11)
        for changed in (data[8], data[10], dict(data[9], run_id="other")):
            buffer = OnlineSampleBuffer()
            feed(buffer, data[:9], final=False)
            before = state(buffer)
            with self.assertRaises(ValueError):
                buffer.observe(raw(changed))
            self.assertEqual(state(buffer), before)
            self.assertIsNotNone(buffer.observe(raw(data[9]))[2])

    def test_stationary_and_disconnected_samples_are_training_data(self):
        buffer = OnlineSampleBuffer()
        results = feed(buffer, samples(41, stationary=True))
        request = results[-1][3]
        self.assertEqual(len(request.windows), 32)
        for w in request.windows:
            self.assertEqual(w.window.origin_node, "NONE")
            self.assertEqual(w.y, (0, 0))
            self.assertTrue(all(all(v == 0 for v in row) for row in w.window.x))

    def test_final_observation_settles_without_new_pending_input(self):
        buffer = OnlineSampleBuffer()
        result = feed(buffer, samples())[-1]
        self.assertIsNone(result[1])
        self.assertIsNotNone(result[2])
        summary = buffer.finish()
        self.assertEqual((summary["completed_windows"], summary["pending_windows"], summary["training_requests"]),
                         (112, 0, 2))
        self.assertEqual(summary["next_request_windows"], 160)

    def test_unfinished_stream_and_append_after_final_are_rejected(self):
        buffer = OnlineSampleBuffer()
        data = samples(10)
        feed(buffer, data[:9], final=False)
        with self.assertRaises(ValueError):
            buffer.finish()
        buffer.observe(raw(data[9]), False)
        with self.assertRaises(ValueError):
            buffer.observe(raw(samples(11)[10]))
        buffer.finish()
        with self.assertRaises(ValueError):
            buffer.finish()

    def test_policy_cap_bounds_retained_prefix_and_requests(self):
        buffer = OnlineSampleBuffer(TrainingPolicy(4, 3, 10))
        requests = [r[3] for r in feed(buffer, samples(40)) if r[3] is not None]
        self.assertEqual([len(r.windows) for r in requests], [4, 7, 10])
        summary = buffer.finish()
        self.assertEqual(summary["completed_windows"], 31)
        self.assertEqual(summary["retained_windows"], 10)
        self.assertIsNone(summary["next_request_windows"])

    def test_invalid_policy_and_forecast_flag_are_rejected(self):
        for values in ((0, 64, 4096), (32, -1, 4096), (32, 64, 10), (True, 64, 4096), (32, 1.5, 4096)):
            with self.assertRaises(ValueError):
                TrainingPolicy(*values)
        buffer = OnlineSampleBuffer()
        for flag in (None, 1, "true", False):
            with self.assertRaises(ValueError):
                buffer.observe(raw(samples(1)[0]), flag)
            self.assertEqual(buffer.history.count, 0)

    def test_runs_have_independent_warmup_and_request_counts(self):
        a, b = OnlineSampleBuffer(), OnlineSampleBuffer()
        feed(a, samples(41, run_id="a"))
        feed(b, samples(10, run_id="b"))
        self.assertEqual(a.request_count, 1)
        self.assertEqual(b.request_count, 0)
        self.assertEqual(b.completed_count, 1)


class MobilityOnlineWorkerTest(unittest.TestCase):
    def make_run(self, root):
        output = root / "run"
        output.mkdir()
        policy = root / "policy.json"
        write_json(policy, dict(first_windows=32, growth_windows=64, max_training_windows=4096))
        data = samples()
        reply = io.StringIO()
        serve(policy, output, io.StringIO(stream_text(data)), reply)
        with (output / "shadow_receipts.csv").open("w", encoding="utf-8", newline="") as stream:
            writer = csv.writer(stream)
            writer.writerow(["origin_ms", "target_ms", "received_at_sim_ms", "predicted_latitude", "predicted_longitude"])
            for line in reply.getvalue().splitlines():
                if line.startswith("PRED\t"):
                    _, origin, target, lat, lon = line.split("\t")
                    writer.writerow([origin, target, origin, lat, lon])
        return output, data, reply.getvalue()

    def test_live_protocol_publishes_requests_without_training(self):
        with tempfile.TemporaryDirectory() as folder:
            output, data, reply = self.make_run(Path(folder))
            self.assertTrue(reply.startswith("READY\n"))
            self.assertTrue(reply.endswith("DONE\t121\t112\n"))
            result = audit_online(output, data, TrainingPolicy())
            self.assertEqual(result["request_sizes"], [32, 96])
            self.assertEqual(read_json(output / "online-worker.json")["training"], "NOT_STARTED")
            self.assertFalse(list(output.glob("*.keras")))
            self.assertFalse(list(output.glob("*preprocessing*")))

    def test_aborted_or_truncated_stream_cannot_report_complete(self):
        for text in (stream_text(samples())[:-4], stream_text(samples(20)), "OBS\tbad\tx\nEND\n"):
            with self.subTest(prefix=text[:20]), tempfile.TemporaryDirectory() as folder:
                root = Path(folder)
                output = root / "run"
                output.mkdir()
                policy = root / "policy.json"
                write_json(policy, {})
                reply = io.StringIO()
                with self.assertRaises(ValueError):
                    serve(policy, output, io.StringIO(text), reply)
                self.assertEqual(read_json(output / "online-worker.json")["status"], "FAILED")
                self.assertNotIn("DONE", reply.getvalue())

    def test_audit_rejects_early_labels_and_future_training_snapshot(self):
        with tempfile.TemporaryDirectory() as folder:
            output, data, _ = self.make_run(Path(folder))
            path = output / "online_events.jsonl"
            original = path.read_bytes()
            events = jsonlines(path)
            event = next(e for e in events if e["event"] == "LABEL_COMPLETED")
            event["sim_time_ms"] -= 60000
            path.write_text("".join(json.dumps(e) + "\n" for e in events), encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "event order"):
                audit_online(output, data, TrainingPolicy())
            path.write_bytes(original)
            requests = jsonlines(output / "online_training_requests.jsonl")
            snapshot = output / requests[0]["snapshot_file"]
            value = read_json(snapshot)
            value["windows"][-1] = jsonlines(output / "online_samples.jsonl")[32]
            write_json(snapshot, value)
            requests[0]["snapshot_sha256"] = sha256(snapshot)
            (output / "online_training_requests.jsonl").write_text(
                "".join(json.dumps(r) + "\n" for r in requests), encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "future or altered"):
                audit_online(output, data, TrainingPolicy())


if __name__ == "__main__":
    unittest.main(verbosity=2)
