"""Quality boundaries, common-origin comparison and live decision timing."""
import copy
import csv
import io
import json
import math
from pathlib import Path
import tempfile
import unittest

from build_mobility_windows import RADIUS, read_json, sha256, write_json
from check_mobility_run import motion
from mobility_online_buffer_worker import serve
from mobility_online_first_fit_worker import FirstFitObserver
from mobility_quality_gate import QualityPolicy, RelativeQualityGate
from mobility_shadow_evaluation import MODELS, distance_m
from run_mobility_online_buffer import jsonlines
from run_mobility_online_first_fit import audit_first_fit
from test_mobility_online_buffer import samples, stream_text
from test_mobility_online_first_fit import ModelDouble

CUTOFF = 2593000


def batch(index, errors=(10, 12, 5), run_id="run", cutoff=CUTOFF):
    result = []
    for model, error in zip(MODELS, errors):
        latitude = math.degrees(error / RADIUS)
        result.append(dict(run_id=run_id, device_id="0", model=model, origin_ms=cutoff + index * 60000,
                           target_ms=cutoff + (index + 1) * 60000, evaluated_at_sim_ms=cutoff + (index + 1) * 60000,
                           actual_latitude_deg=0.0, actual_longitude_deg=0.0,
                           predicted_latitude_deg=latitude, predicted_longitude_deg=0.0,
                           position_error_m=distance_m((latitude, 0), (0, 0))))
    return result


def feed_gate(gate, errors=None, count=64):
    for index in range(count):
        group = batch(index, errors[index] if errors is not None else (10, 12, 5), cutoff=gate.cutoff)
        gate.observe(gate.version, group, group[0]["target_ms"])
    return gate.decision


class MobilityQualityGateTest(unittest.TestCase):
    def gate(self, policy=None):
        return RelativeQualityGate(policy or QualityPolicy(), "v0001", CUTOFF)

    def test_decision_requires_64_unique_mature_origins(self):
        gate = self.gate()
        self.assertIsNone(feed_gate(gate, count=63))
        group = batch(63)
        result = gate.observe("v0001", group, group[0]["target_ms"])
        self.assertEqual(result["evaluated_origins"], 64)
        self.assertEqual(result["decision"], "PASS")
        self.assertEqual(result["decided_at_sim_ms"], CUTOFF + 64 * 60000)
        self.assertFalse(result["active"])
        self.assertEqual(result["absolute_quality"], "NOT_SPECIFIED")

    def test_zero_baseline_and_equal_rmse_do_not_promote(self):
        for errors in ((0, 0, 0), (10, 12, 10), (0, 0, .014)):
            with self.subTest(errors=errors):
                decision = feed_gate(self.gate(), [errors] * 64)
                self.assertEqual(decision["decision"], "REJECT")
                self.assertIn("FAILED_RMSE_STRICTLY_BETTER", decision["reason_codes"])
                if errors[0] == 0:
                    self.assertIsNone(decision["relative_rmse_improvement"])

    def test_best_baseline_is_selected_from_the_same_block(self):
        decision = feed_gate(self.gate(), [(10, 8, 9)] * 64)
        self.assertEqual(decision["reference_baseline"], "ConstantVelocity")
        self.assertEqual(decision["decision"], "REJECT")

    def test_lower_mean_cannot_hide_large_squared_errors(self):
        errors = [(10, 12, 0)] * 63 + [(10, 12, 100)]
        decision = feed_gate(self.gate(), errors)
        self.assertTrue(decision["checks"]["mean_error_not_worse"])
        self.assertFalse(decision["checks"]["rmse_strictly_better"])
        self.assertEqual(decision["decision"], "REJECT")

    def test_lower_rmse_cannot_hide_worse_mean(self):
        decision = feed_gate(self.gate(), [(0, 20, 11)] * 32 + [(20, 20, 11)] * 32)
        self.assertTrue(decision["checks"]["rmse_strictly_better"])
        self.assertFalse(decision["checks"]["mean_error_not_worse"])
        self.assertEqual(decision["decision"], "REJECT")

    def test_required_improvement_and_larger_block_are_configurable(self):
        gate = self.gate(QualityPolicy(80, .2))
        self.assertIsNone(feed_gate(gate, [(10, 12, 9)] * 79, count=79))
        group = batch(79, (10, 12, 9))
        decision = gate.observe("v0001", group, group[0]["target_ms"])
        self.assertEqual(decision["evaluated_origins"], 80)
        self.assertIn("FAILED_REQUIRED_RMSE_IMPROVEMENT", decision["reason_codes"])

    def test_mixed_version_run_duplicate_and_early_truth_are_atomic(self):
        gate = self.gate()
        first = batch(0)
        gate.observe("v0001", first, first[0]["target_ms"])
        next_group = batch(1)
        early = copy.deepcopy(next_group)
        early[0]["evaluated_at_sim_ms"] -= 60000
        cases = [("v0002", next_group), ("v0001", batch(1, run_id="other")),
                 ("v0001", first), ("v0001", batch(2)), ("v0001", early)]
        before = copy.deepcopy(gate.__dict__)
        for version, group in cases:
            with self.subTest(version=version, target=group[0]["target_ms"]), self.assertRaises(ValueError):
                gate.observe(version, group, group[0]["target_ms"])
            self.assertEqual(gate.__dict__, before)
        gate.observe("v0001", next_group, next_group[0]["target_ms"])
        self.assertEqual(gate.count, 2)

    def test_unequal_origins_missing_models_and_bad_errors_are_rejected(self):
        valid = batch(0)
        missing = valid[:-1]
        wrong_origin = copy.deepcopy(valid)
        wrong_origin[1]["origin_ms"] += 60000
        cases = [missing, [valid[0], valid[0], valid[2]], wrong_origin]
        for field, value in (("position_error_m", float("nan")), ("position_error_m", -1),
                             ("position_error_m", 999), ("actual_latitude_deg", 1)):
            changed = copy.deepcopy(valid)
            changed[-1][field] = value
            cases.append(changed)
        for group in cases:
            gate = self.gate()
            with self.assertRaises(ValueError):
                gate.observe("v0001", group, valid[0]["target_ms"])
            self.assertEqual(gate.count, 0)
            self.assertIsNone(gate.identity)

    def test_decision_snapshot_is_immutable_and_versions_are_independent(self):
        gate = self.gate()
        decision = feed_gate(gate)
        original = copy.deepcopy(decision)
        decision["decision"] = "REJECT"
        decision["metrics"]["LSTM"]["rmse_m"] = 999
        self.assertEqual(gate.decision, original)
        with self.assertRaises(ValueError):
            gate.observe("v0001", batch(64), CUTOFF + 65 * 60000)
        newer = RelativeQualityGate(QualityPolicy(), "v0002", CUTOFF + 64 * 60000)
        self.assertEqual(newer.count, 0)
        self.assertIsNone(newer.decision)

    def test_invalid_policy_cannot_silently_relax_minimum(self):
        for count, improvement in ((63, 0), (True, 0), (64.0, 0), (64, -.1), (64, 1),
                                   (64, float("nan")), (64, True)):
            with self.assertRaises(ValueError):
                QualityPolicy(count, improvement)


class MobilityQualityIntegrationTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)

    def run_stream(self, data, name="run"):
        output = self.root / name
        output.mkdir()
        policy = self.root / (name + "-policy.json")
        write_json(policy, {})
        observer = FirstFitObserver(output, lambda request, *_: ModelDouble(request), QualityPolicy())
        self.addCleanup(observer.close)
        serve(policy, output, io.StringIO(stream_text(data)), io.StringIO(), observer)
        observer.close()
        return output

    def test_live_gate_runs_after_target_before_next_training_request(self):
        data = samples()
        output = self.run_stream(data)
        result = audit_first_fit(output, data, QualityPolicy())
        self.assertEqual(result["quality_result"]["decision"], "REJECT")
        self.assertEqual(read_json(output / "first-fit.json")["status"], "COMPLETE")
        events = jsonlines(output / "first_fit_events.jsonl")
        now = int(data[104]["time_ms"])
        self.assertEqual([e["event"] for e in events if e["sim_time_ms"] == now],
                         ["OBSERVATION", "EVALUATION", "QUALITY_DECISION", "TRAINING_REQUEST_DEFERRED", "SHADOW_PREDICTION"])

    def test_last_16_results_do_not_change_first_64_decision(self):
        data = samples()
        initial = self.run_stream(data, "initial")
        changed = copy.deepcopy(data)
        for i in range(105, 121):
            changed[i]["longitude_deg"] = str(float(data[i]["longitude_deg"]) + .1)
            distance, heading = motion(changed[i - 1], changed[i])
            changed[i].update(displacement_m=str(distance), observed_speed_m_s=str(distance / 60), heading_deg=str(heading))
        later = self.run_stream(changed, "later")
        audit_first_fit(later, changed, QualityPolicy())
        self.assertEqual(read_json(initial / "quality-decision.json"), read_json(later / "quality-decision.json"))
        self.assertNotEqual(read_json(initial / "first-fit.json")["metrics"], read_json(later / "first-fit.json")["metrics"])

    def test_truncated_stream_does_not_produce_a_decision(self):
        with self.assertRaises(ValueError):
            self.run_stream(samples(104))
        self.assertFalse((self.root / "run/quality-decision.json").exists())
        self.assertEqual(read_json(self.root / "run/first-fit.json")["status"], "FAILED")

    def test_audit_rejects_changed_decision_even_with_updated_hash(self):
        data = samples()
        output = self.run_stream(data)
        path = output / "quality-decision.json"
        value = read_json(path)
        value["metrics"]["LSTM"]["rmse_m"] += 1
        write_json(path, value)
        summary = read_json(output / "first-fit.json")
        summary["quality_decision_sha256"] = sha256(path)
        write_json(output / "first-fit.json", summary)
        with self.assertRaisesRegex(ValueError, "first matured block"):
            audit_first_fit(output, data, QualityPolicy())


if __name__ == "__main__":
    unittest.main(verbosity=2)
