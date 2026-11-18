"""Reject future leakage, target mixups and invented tail scores in baseline exports."""
import csv
import json
from pathlib import Path
import tempfile
import unittest

from audit_node_load_predictions import audit, PREDICTION_HEADER, EVALUATION_HEADER
from audit_node_load_samples import HEADER, PHASE


class NodeLoadPredictionsTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        self.meta = dict(schema_version=1, sample_phase=PHASE, run_id="run-1", node_ids=["west", "east"],
                         sample_count_per_node=2, interval_ms=60000, start_ms=0, last_sample_ms=60000,
                         sample_until_ms=60000, simulation_end_ms=120001, rows=4)
        self.samples, self.predictions, self.evaluations = [], [], []
        for index, values in enumerate(((15, 100), (4, 105))):
            for node, value in zip(self.meta["node_ids"], values):
                sample = dict.fromkeys(HEADER[5:], 0)
                sample.update(run_id="run-1", node_id=node, sample_index=index, time_ms=index * 60000,
                              sample_phase=PHASE, waiting_assignment_bytes=value)
                self.samples.append(sample)
                prediction = dict(run_id="run-1", node_id=node, task="node_load", model="Persistence",
                                  model_version="fixed-v1", target_metric="waiting_assignment_bytes", unit="B",
                                  origin_sample_index=index, last_input_time_ms=index * 60000,
                                  origin_time_ms=index * 60000, target_time_ms=(index + 1) * 60000,
                                  horizon_ms=60000, predicted_waiting_assignment_bytes=value, inference_duration_ns=100)
                self.predictions.append(prediction)
        for previous, sample in zip(self.predictions[:2], self.samples[2:]):
            error = previous["predicted_waiting_assignment_bytes"] - sample["waiting_assignment_bytes"]
            self.evaluations.append(dict(previous, actual_sample_index=1, actual_time_ms=60000,
                                         actual_waiting_assignment_bytes=sample["waiting_assignment_bytes"],
                                         error_bytes=error, absolute_error_bytes=abs(error)))
        self.summary = dict(schema_version=1, run_id="run-1", task="node_load", model="Persistence",
                            model_version="fixed-v1", target_metric="waiting_assignment_bytes", unit="B",
                            horizon_ms=60000, sample_phase=PHASE, role="BASELINE_COMPARISON",
                            controls_simulation=False, qualification_required=False, execution_mode="SIMULATION_CALLBACK",
                            evaluation_scope="ALL_MATCHED_GRID_TARGETS_INCLUDING_STARTUP",
                            inference_timing_scope="PREDICT_CALL_AND_CLOCK_OVERHEAD_EXCLUDING_IO",
                            aggregate=dict(generated=4, matched=2, unmatched=2, mae_bytes=8, rmse_bytes=73 ** .5,
                                           mean_error_bytes=3, mean_inference_ms=.0001), nodes=[])
        for node, error in (("west", 11), ("east", -5)):
            self.summary["nodes"].append(dict(node_id=node, generated=2, matched=1, unmatched=1,
                                              mae_bytes=abs(error), rmse_bytes=abs(error), mean_error_bytes=error,
                                              mean_inference_ms=.0001, last_origin_time_ms=60000,
                                              pending_target_time_ms=120000))

    def save(self):
        for name, data in (("node_load_sampling.json", self.meta), ("node_load_prediction_summary.json", self.summary)):
            (self.root / name).write_text(json.dumps(data), encoding="utf-8")
        for name, header, data in (("node_load_samples.csv", HEADER, self.samples),
                                   ("node_load_predictions.csv", PREDICTION_HEADER, self.predictions),
                                   ("node_load_evaluations.csv", EVALUATION_HEADER, self.evaluations)):
            with (self.root / name).open("w", encoding="utf-8", newline="") as stream:
                writer = csv.DictWriter(stream, fieldnames=header)
                writer.writeheader()
                writer.writerows(data)

    def test_accepts_known_errors_and_unmatched_last_predictions(self):
        self.save()
        self.assertEqual(audit(self.root)["matched"], 2)
        self.assertEqual(audit(self.root)["mae_bytes"], 8)

    def test_rejects_forecasts_using_future_actual_or_other_node(self):
        for key, value in (("predicted_waiting_assignment_bytes", 4), ("last_input_time_ms", 60000),
                           ("node_id", "east"), ("target_time_ms", 0)):
            with self.subTest(key=key):
                old = self.predictions[0][key]
                self.predictions[0][key] = value
                self.save()
                with self.assertRaisesRegex(ValueError, "causal time identity"):
                    audit(self.root)
                self.predictions[0][key] = old

    def test_rejects_missing_or_fabricated_evaluations(self):
        for data in (self.evaluations[:1], self.evaluations + self.evaluations[:1]):
            old = self.evaluations
            self.evaluations = data
            self.save()
            with self.assertRaisesRegex(ValueError, "invented target"):
                audit(self.root)
            self.evaluations = old

    def test_rejects_same_length_but_wrong_targets_and_summary(self):
        self.evaluations[0]["actual_time_ms"] = 120000
        self.save()
        with self.assertRaisesRegex(ValueError, "Evaluation target"):
            audit(self.root)
        self.evaluations[0]["actual_time_ms"] = 60000
        self.summary["aggregate"]["rmse_bytes"] = 8
        self.save()
        with self.assertRaisesRegex(ValueError, "Incorrect summary"):
            audit(self.root)

    def test_rejects_duplicate_source_sample(self):
        self.samples[1]["node_id"] = "west"
        self.save()
        with self.assertRaisesRegex(ValueError, "duplicate source"):
            audit(self.root)


if __name__ == "__main__":
    unittest.main()
