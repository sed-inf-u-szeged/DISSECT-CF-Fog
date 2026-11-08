"""Causality and protocol failures use explicit test doubles, never pretend training."""
import copy
import io
import json
from pathlib import Path
import tempfile
import unittest

import numpy as np

from audit_mobility_closed_loop import audit_closed_worker
from build_mobility_windows import read_json, sha256, write_json
from check_mobility_run import motion
from mobility_closed_loop_worker import ClosedLoopObserver, PROTOCOL
from mobility_online_buffer import OnlineSampleBuffer
from mobility_online_buffer_worker import serve
from mobility_online_lstm import training_data
from run_mobility_online_buffer import jsonlines
from test_mobility_online_buffer import samples, raw
from test_mobility_online_retry import RetryModelDouble


def controlled_stream(data, schedule, *, good=False, mutate=None, end=True):
    """Test Java peer; real Java scheduling is covered by integration and JUnit."""
    current, window, cutoff, number = "", 32, -1, 0
    lines = []
    for index, row in enumerate(data):
        final = index == len(data) - 1
        state = "NONE" if not current else "EVALUATING" if index - cutoff < 64 else "ACTIVE" if good else "FAILED"
        command = dict(sequence=index + 1, sim_time_ms=int(row["time_ms"]), run_id=row["run_id"], device_id=row["device_id"],
                       action="HOLD", reason="STREAM_COMPLETE" if final else "WARMUP" if not current else
                       "ACTIVE" if state == "ACTIVE" else "EVALUATING", available_windows=max(0, index - 8),
                       window_size=window, request_number=0, model_version="", previous_version=current, previous_state=state)
        if index in schedule:
            size, reason = schedule[index]
            number += 1
            command.update(action="TRAIN", reason=reason, window_size=size, request_number=number, model_version="v%04d" % number,
                           previous_state="NONE" if reason == "INITIAL" else "DEGRADED" if reason == "DEGRADATION" else "FAILED")
            current, window, cutoff = command["model_version"], size, index
        if mutate is not None:
            mutate(index, command)
        lines.extend(["OBS\t%s\t%s\n" % ("false" if final else "true", raw(row)),
                      "CONTROL\t" + json.dumps(command) + "\n"])
    return "".join(lines) + ("END\n" if end else "")


class DegradingDouble(RetryModelDouble):
    def predict_delta(self, x):
        result = super().predict_delta(x)
        if self.metadata["version"] == "v0001" and self.calls == 65:
            return np.full((len(x), 2), 100000.0)
        return result


class MobilityClosedLoopTest(unittest.TestCase):
    def setUp(self):
        folder = tempfile.TemporaryDirectory()
        self.addCleanup(folder.cleanup)
        self.root = Path(folder.name)
        self.output = self.root / "run"
        self.output.mkdir()
        self.policy = self.root / "policy.json"
        write_json(self.policy, {})
        self.requests = []
        self.reply = io.StringIO()

    def trainer(self, request, snapshot, directory):
        training_data(request)
        self.requests.append(request)
        return RetryModelDouble(request, snapshot)

    def run_stream(self, data, schedule, trainer=None, **options):
        observer = ClosedLoopObserver(self.output, trainer or self.trainer, self.reply, len(data))
        self.addCleanup(observer.close)
        try:
            serve(self.policy, self.output, io.StringIO(controlled_stream(data, schedule, **options)),
                  self.reply, observer, len(data), PROTOCOL)
        finally:
            observer.close()
        return observer

    def test_refit_after_pass_uses_latest_mature_window_and_new_future_block(self):
        data = samples(174)
        for i, row in enumerate(data):
            row["longitude_deg"] = str(18.27 + 0.000001 * i * i)
            if i:
                distance, heading = motion(data[i - 1], row)
                row.update(displacement_m=str(distance), observed_speed_m_s=str(distance / 60), heading_deg=str(heading))
        def trainer(request, snapshot, directory):
            self.trainer(request, snapshot, directory)
            return DegradingDouble(request, snapshot, accelerate=True)
        self.run_stream(data, {40: (32, "INITIAL"), 105: (32, "DEGRADATION")}, trainer, good=True)
        result = audit_closed_worker(self.output, data)
        first, second = self.requests
        self.assertEqual([len(r.windows) for r in self.requests], [32, 32])
        self.assertGreater(second.windows[0].window.origin_ms, first.windows[-1].window.origin_ms)
        self.assertEqual(second.windows[-1].completed_at_sim_ms, int(data[105]["time_ms"]))
        self.assertEqual([r["matched_origins"] for r in result["versions"]], [65, 68])
        self.assertEqual([r["quality_decision"] for r in result["versions"]], ["PASS", "PASS"])
        self.assertTrue(self.reply.getvalue().endswith("DONE\t174\t165\n"))

    def test_rejected_candidates_follow_java_commands_and_remain_independent(self):
        data = samples(174, stationary=True)
        self.run_stream(data, {40: (32, "INITIAL"), 104: (96, "REJECTION"), 168: (160, "REJECTION")})
        result = audit_closed_worker(self.output, data)
        self.assertEqual([r["matched_origins"] for r in result["versions"]], [64, 64, 5])
        self.assertEqual(result["versions"][-1]["remaining_quality_origins"], 59)

    def test_buffer_snapshot_is_frozen_and_rejects_future_or_terminal_request(self):
        buffer = OnlineSampleBuffer()
        data = samples(106)
        for row in data[:105]:
            buffer.observe(raw(row))
        first = buffer.scheduled_request(2, 32, int(data[104]["time_ms"]))
        frozen = copy.deepcopy(first)
        with self.assertRaises(ValueError):
            buffer.scheduled_request(2, 32, int(data[105]["time_ms"]))
        buffer.observe(raw(data[105]), False)
        self.assertEqual(first, frozen)
        with self.assertRaises(ValueError):
            buffer.scheduled_request(3, 32, int(data[105]["time_ms"]))

    def test_stale_command_is_rejected_before_trainer(self):
        def corrupt(index, command):
            if index == 40:
                command["sim_time_ms"] += 60000
        with self.assertRaisesRegex(ValueError, "Stale or mismatched"):
            self.run_stream(samples(121), {40: (32, "INITIAL")}, mutate=corrupt)
        self.assertEqual(self.requests, [])
        self.assertNotIn("TRAINING_COMPLETE", self.reply.getvalue())

    def test_refit_cannot_borrow_previous_version_targets(self):
        with self.assertRaisesRegex(ValueError, "before independent qualification"):
            self.run_stream(samples(121), {40: (32, "INITIAL"), 60: (96, "REJECTION")})
        self.assertEqual(len(self.requests), 1)
        self.assertNotIn("DONE\t", self.reply.getvalue())

    def test_second_training_failure_cannot_publish_new_completion_or_done(self):
        def trainer(request, snapshot, directory):
            if request.request_number == 2:
                raise RuntimeError("refit failed")
            return self.trainer(request, snapshot, directory)
        with self.assertRaisesRegex(RuntimeError, "refit failed"):
            self.run_stream(samples(121), {40: (32, "INITIAL"), 104: (96, "REJECTION")}, trainer)
        frames = [json.loads(r.split("\t", 1)[1]) for r in self.reply.getvalue().splitlines() if r.startswith("LIFECYCLE\t")]
        self.assertEqual(len([r for r in frames if r["event"] == "TRAINING_COMPLETE"]), 1)
        self.assertEqual(read_json(self.output / "retry.json")["status"], "FAILED")
        self.assertNotIn("DONE\t", self.reply.getvalue())

    def test_missing_end_cannot_report_complete(self):
        with self.assertRaisesRegex(ValueError, "without END"):
            self.run_stream(samples(121), {40: (32, "INITIAL"), 104: (96, "REJECTION")}, end=False)
        self.assertEqual(read_json(self.output / "retry.json")["status"], "FAILED")

    def test_audit_rejects_changed_snapshot_even_with_updated_hash(self):
        data = samples(121)
        self.run_stream(data, {40: (32, "INITIAL"), 104: (96, "REJECTION")})
        path = self.output / "scheduled-request-0002.json"
        snapshot = read_json(path)
        snapshot["windows"][0]["y"][0] += 100
        write_json(path, snapshot)
        state = read_json(self.output / "retry.json")
        state["versions"][1]["snapshot_sha256"] = sha256(path)
        write_json(self.output / "retry.json", state)
        with self.assertRaisesRegex(ValueError, "exact latest mature"):
            audit_closed_worker(self.output, data)


if __name__ == "__main__":
    unittest.main(verbosity=2)
