"""Live protocol tests use explicit trainer doubles; integration uses real LSTM models."""
import csv
import io
import json
from pathlib import Path
import tempfile
import unittest

from build_mobility_windows import read_json, write_json
from mobility_online_buffer_worker import serve
from mobility_shared_lifecycle_worker import PROTOCOL, SharedLifecycleObserver
from test_mobility_online_buffer import samples, stream_text
from test_mobility_online_retry import RetryModelDouble


class MobilitySharedLifecycleProtocolTest(unittest.TestCase):
    def setUp(self):
        folder = tempfile.TemporaryDirectory()
        self.addCleanup(folder.cleanup)
        self.root = Path(folder.name)
        self.output = self.root / "run"
        self.output.mkdir()
        self.policy = self.root / "policy.json"
        write_json(self.policy, {})
        self.reply = io.StringIO()

    def run_stream(self, trainer=None, protocol=PROTOCOL):
        data = samples(297, stationary=True)
        observer = SharedLifecycleObserver(self.output, trainer or (lambda request, snapshot, _: RetryModelDouble(request, snapshot)),
                                           self.reply, 297)
        self.addCleanup(observer.close)
        try:
            serve(self.policy, self.output, io.StringIO(stream_text(data)), self.reply, observer, 297, protocol)
        finally:
            observer.close()
        return data

    def frames(self):
        return [json.loads(line.split("\t", 1)[1]) for line in self.reply.getvalue().splitlines()
                if line.startswith("LIFECYCLE\t")]

    def test_protocol_publishes_frozen_versions_and_forecasts_in_live_order(self):
        data = self.run_stream()
        lines = self.reply.getvalue().splitlines()
        self.assertEqual(lines[0], "READY\t" + PROTOCOL)
        self.assertEqual(lines[-1], "DONE\t297\t288")
        frames = self.frames()
        self.assertEqual([r["sequence"] for r in frames], list(range(1, len(frames) + 1)))
        started = [r for r in frames if r["event"] == "TRAINING_STARTED"]
        completed = [r for r in frames if r["event"] == "TRAINING_COMPLETE"]
        forecasts = [r for r in frames if r["event"] == "FORECAST"]
        self.assertEqual([r["training_windows"] for r in started], [32, 96, 160, 224])
        self.assertEqual(len(completed), 4)
        self.assertEqual(len(forecasts), 256)
        self.assertEqual(started[0]["sim_time_ms"], int(data[40]["time_ms"]))
        self.assertEqual([r["event"] for r in frames[:3]], ["TRAINING_STARTED", "TRAINING_COMPLETE", "FORECAST"])
        with (self.output / "retry_predictions.csv").open(newline="", encoding="utf-8") as stream:
            logged = list(csv.DictReader(stream))
        for index, frame in enumerate(forecasts):
            self.assertEqual(frame["sim_time_ms"], int(data[index + 40]["time_ms"]))
            self.assertEqual(frame["target_ms"], frame["sim_time_ms"] + 60000)
            self.assertEqual(frame["predictions"], [dict(model=row["model"], latitude_deg=float(row["predicted_latitude_deg"]),
                                                        longitude_deg=float(row["predicted_longitude_deg"]))
                                                    for row in logged[3 * index:3 * index + 3]])
        # Python cannot command activation: Java must recompute actual-target quality.
        self.assertTrue(all(r["event"] in ("TRAINING_STARTED", "TRAINING_COMPLETE", "FORECAST") for r in frames))
        self.assertFalse((self.output / "shared-lifecycle.json").exists())

    def test_trainer_failure_cannot_publish_completion_forecast_or_done(self):
        def failed(*_):
            raise RuntimeError("trainer failure")
        with self.assertRaisesRegex(RuntimeError, "trainer failure"):
            self.run_stream(failed)
        self.assertEqual([r["event"] for r in self.frames()], ["TRAINING_STARTED"])
        self.assertNotIn("DONE\t", self.reply.getvalue())
        self.assertEqual(read_json(self.output / "retry.json")["status"], "FAILED")

    def test_mismatched_model_version_cannot_publish_completion(self):
        def wrong(request, snapshot, _):
            model = RetryModelDouble(request, snapshot)
            model.metadata["version"] = "v0002"
            return model
        with self.assertRaisesRegex(ValueError, "incomplete or mismatched version"):
            self.run_stream(wrong)
        self.assertEqual([r["event"] for r in self.frames()], ["TRAINING_STARTED"])
        self.assertNotIn("DONE\t", self.reply.getvalue())

    def test_unknown_protocol_fails_before_ready(self):
        with self.assertRaisesRegex(ValueError, "Unknown live protocol"):
            self.run_stream(protocol="unsupported")
        self.assertEqual(self.reply.getvalue(), "")


if __name__ == "__main__":
    unittest.main(verbosity=2)
