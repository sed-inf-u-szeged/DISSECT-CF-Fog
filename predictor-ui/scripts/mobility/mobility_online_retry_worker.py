"""Execute causal LSTM retries after rejected shadow evaluation blocks.

This bounded integration probe keeps promotion and connection control disabled.
It does not replace or claim integration with the load lifecycle controller.
"""
import argparse
import contextlib
from contextlib import ExitStack
import csv
import json
import os
from pathlib import Path
import sys
import traceback

import numpy as np
from build_mobility_windows import read_json, require, sha256, write_json
from mobility_learning import baseline_displacements, displacement_to_gps
from mobility_online_buffer import REFERENCE, TrainingPolicy
from mobility_online_buffer_worker import serve
from mobility_online_lstm import OnlineLstm, tensorflow_runtime, training_data, version_name
from mobility_quality_gate import GATE_KIND, QualityPolicy, RelativeQualityGate
from mobility_shadow_evaluation import DelayedEvaluation, MODELS, PREDICTION_FIELDS, EVALUATION_FIELDS

RETRY_PREDICTION_FIELDS = ["model_version"] + PREDICTION_FIELDS
RETRY_EVALUATION_FIELDS = ["model_version"] + EVALUATION_FIELDS


class RetryObserver:
    """Keep separate frozen bundles and future-only evaluation for each retry."""

    def __init__(self, output, trainer, expected_observations=121):
        require(type(expected_observations) is int and 105 <= expected_observations <= 1441,
                "Retry probe requires 105-1441 observations")
        self.expected_observations = expected_observations
        self.observed = self.last_request_number = 0
        require(not (output / "retry.json").exists(), "Refusing existing retry result")
        self.output, self.trainer = output, trainer
        self.versions, self.current = {}, None
        self.observation = None
        self.sequence = 0
        self.phase = "WAITING_FOR_DATA"
        self.deferred = []
        self.stack = ExitStack()
        self.summary = dict(schema_version=1, status="STARTING", mode="RETRY_SHADOW",
                            quality_gate=GATE_KIND, active=False, connection_control=False,
                            promotion="DISABLED", controller_integration="NOT_CONNECTED",
                            expected_observations=expected_observations)
        try:
            self.events = self.stack.enter_context((output / "retry_events.jsonl").open("x", encoding="utf-8"))
            self.predout = self.stack.enter_context((output / "retry_predictions.csv").open("x", encoding="utf-8", newline=""))
            self.evalout = self.stack.enter_context((output / "retry_evaluations.csv").open("x", encoding="utf-8", newline=""))
            self.predwriter = csv.DictWriter(self.predout, fieldnames=RETRY_PREDICTION_FIELDS)
            self.evalwriter = csv.DictWriter(self.evalout, fieldnames=RETRY_EVALUATION_FIELDS)
            self.predwriter.writeheader()
            self.evalwriter.writeheader()
            self.save()
        except BaseException:
            self.close()
            raise

    def describe(self):
        training = "TRAINING" if self.phase == "TRAINING" else "COMPLETE" if self.versions else "NOT_STARTED"
        return dict(mode="RETRY_SHADOW", training=training,
                    scaler="FROZEN" if self.versions else "NOT_FITTED", lifecycle_state=self.phase)

    def save(self):
        self.summary.update(self.describe())
        write_json(self.output / "retry.json", self.summary)

    def event(self, kind, now, **details):
        self.sequence += 1
        self.events.write(json.dumps(dict(sequence=self.sequence, event=kind, sim_time_ms=now, **details),
                                     allow_nan=False) + "\n")
        self.events.flush()

    def observe(self, row):
        now = int(row["time_ms"])
        self.event("OBSERVATION", now, run_id=row["run_id"], device_id=row["device_id"])
        self.observation = dict(row)
        self.observed += 1
        require(self.observed <= self.expected_observations, "Observation count exceeded configured end")
        if self.current is None:
            return
        session = self.versions[self.current]
        evaluated = session["scoring"].observe(row)
        if not evaluated:
            return
        self.evalwriter.writerows(dict(model_version=self.current, **r) for r in evaluated)
        self.evalout.flush()
        session["matured"] += 1
        self.event("EVALUATION", now, model_version=self.current, origin_ms=now - 60000,
                   target_ms=now, matured_origins=session["matured"])
        gate = session["gate"]
        if gate.decision is None:
            decision = gate.observe(self.current, evaluated, now)
            if decision is not None:
                path = self.output / (self.current + "-quality-decision.json")
                require(not path.exists(), "Refusing existing version quality decision")
                write_json(path, decision)
                session["decision_sha256"] = sha256(path)
                self.phase = "RETRAIN_PENDING" if decision["decision"] == "REJECT" else "QUALIFIED_SHADOW"
                self.event("QUALITY_DECISION", now, model_version=self.current, evaluated_origins=gate.count,
                           decision=decision["decision"])
                self.save()

    def request(self, request, snapshot_path):
        now, number, count = request.requested_at_sim_ms, request.request_number, len(request.windows)
        require(self.observation is not None and int(self.observation["time_ms"]) == now,
                "Training must follow the current observation")
        require(number == self.last_request_number + 1 and count == 32 + 64 * (number - 1),
                "Unexpected retry request sequence")
        if self.current is None:
            require(number == 1 and not self.versions, "Expected first training request")
        else:
            require(number > 1, "Repeated or out-of-order retry request")
            previous = self.versions[self.current]
            decision = previous["gate"].decision
            require(decision is not None and previous["matured"] >= 64, "Retry before the current version quality decision")
            if self.observed == self.expected_observations or decision["decision"] == "PASS":
                self.last_request_number = number
                self.deferred.append(count)
                reason = "STREAM_COMPLETE" if self.observed == self.expected_observations else "QUALITY_PASSED_PROMOTION_PENDING"
                self.event("TRAINING_REQUEST_DEFERRED", now, model_version=self.current, training_windows=count,
                           reason=reason)
                return
            require(self.phase == "RETRAIN_PENDING", "Retry requires a rejected candidate")
            previous["scoring"].finish()
            previous["model"].verify_frozen()
        self.fit_request(request, snapshot_path)

    def fit_request(self, request, snapshot_path):
        """Fit an already-authorized immutable request; callers own scheduling."""
        now, number, count = request.requested_at_sim_ms, request.request_number, len(request.windows)
        # Check causal tensors/snapshot before calling even a replaceable trainer.
        training_data(request)
        snapshot = read_json(snapshot_path)
        require(snapshot["request_number"] == number and snapshot["requested_at_sim_ms"] == now
                and snapshot["training_windows"] == count
                and snapshot["windows"] == json.loads(json.dumps([w.record() for w in request.windows])),
                "Retry snapshot differs from the live request")
        version = version_name(number)
        self.last_request_number = number
        self.phase = "TRAINING"
        self.event("TRAINING_STARTED", now, model_version=version, training_windows=count,
                   request_number=number, latest_label_ms=request.windows[-1].completed_at_sim_ms)
        self.save()
        with contextlib.redirect_stdout(sys.stderr):
            model = self.trainer(request, snapshot_path, self.output / "models" / version)
        metadata = model.metadata
        require(metadata["status"] == "COMPLETE" and metadata["reload_check"] == "PASSED"
                and metadata["version"] == version and metadata["request_number"] == number
                and metadata["request_sha256"] == sha256(snapshot_path)
                and metadata["training_cutoff_sim_ms"] == now and metadata["training_windows"] == count,
                "Trainer returned an incomplete or mismatched version")
        model.verify_frozen()
        scoring = DelayedEvaluation()
        scoring.observe(self.observation)
        session = dict(model=model, scoring=scoring, gate=RelativeQualityGate(QualityPolicy(), version, now),
                       matured=0, training_windows=count, cutoff=now, request_number=number)
        self.versions[version] = session
        self.current, self.phase = version, "SHADOW"
        self.event("TRAINING_COMPLETE", now, model_version=version, training_windows=count, scaler="FROZEN")
        self.save()

    def predict(self, pending):
        if self.current is None:
            return
        session = self.versions[self.current]
        require(self.phase in ("SHADOW", "QUALIFIED_SHADOW") and pending.origin_ms >= session["cutoff"],
                "Prediction before model completion or after rejection")
        x = np.asarray([pending.x], dtype=np.float64)
        with contextlib.redirect_stdout(sys.stderr):
            delta = session["model"].predict_delta(x)
        values = dict(baseline_displacements(x), LSTM=delta)
        anchor = np.asarray([[pending.anchor_latitude_deg, pending.anchor_longitude_deg]])
        predictions = []
        for model in MODELS:
            gps = displacement_to_gps(values[model], anchor, REFERENCE)[0]
            predictions.append(dict(model=model, device_id=pending.device_id, origin_ms=pending.origin_ms,
                                    target_ms=pending.target_ms, origin_node=pending.origin_node,
                                    predicted_latitude_deg=gps[0], predicted_longitude_deg=gps[1]))
        session["scoring"].issue(predictions)
        self.predwriter.writerows(dict(model_version=self.current, **r) for r in predictions)
        self.predout.flush()
        self.event("SHADOW_PREDICTION", pending.origin_ms, model_version=self.current, target_ms=pending.target_ms)
        self.publish_predictions(predictions)

    def publish_predictions(self, predictions):
        """Optional live integration hook after the forecast batch is frozen and logged."""

    def finish(self):
        require(self.current is not None and self.phase in ("SHADOW", "QUALIFIED_SHADOW", "RETRAIN_PENDING")
                and self.observed == self.expected_observations, "Retry did not finish")
        records = []
        for version, session in self.versions.items():
            metrics = session["scoring"].finish()
            session["model"].verify_frozen()
            gate, count = session["gate"], session["matured"]
            decision, path = gate.decision, self.output / (version + "-quality-decision.json")
            if decision is None:
                require(not path.exists() and count < 64, "Premature version quality decision")
            else:
                require(read_json(path) == decision and sha256(path) == session["decision_sha256"],
                        "Frozen version quality decision changed")
            records.append(dict(model_version=version, request_number=session["request_number"],
                                training_windows=session["training_windows"], training_cutoff_sim_ms=session["cutoff"],
                                shadow_origins=session["scoring"].issued, matched_origins=count,
                                evaluation_rows=3 * count, quality_evaluated_origins=gate.count,
                                quality_decision=decision["decision"] if decision else "WAITING_FOR_EVALUATION",
                                decision_sha256=session.get("decision_sha256"), remaining_quality_origins=max(0, 64 - count),
                                metrics=metrics, active=False))
        require(sum(r["matched_origins"] for r in records) == self.expected_observations - 41,
                "Incomplete configured retry evaluations")
        for previous in records[:-1]:
            require(previous["quality_decision"] == "REJECT" and previous["matched_origins"] == 64,
                    "Only a fully evaluated rejected version can be retried")
        require([r["training_windows"] for r in records] == [32 + 64 * i for i in range(len(records))],
                "Unexpected fitted prefix growth")
        last = records[-1]
        wanted_phase = {"REJECT": "RETRAIN_PENDING", "PASS": "QUALIFIED_SHADOW",
                        "WAITING_FOR_EVALUATION": "SHADOW"}[last["quality_decision"]]
        require(self.phase == wanted_phase, "Final quality state differs")
        requested_sizes = list(range(32, self.expected_observations - 9 + 1, 64))
        require(self.deferred == requested_sizes[len(records):], "Deferred training requests differ")
        self.summary.update(status="COMPLETE", trained_versions=len(records), versions=records,
                            current_version=self.current, deferred_request_sizes=self.deferred, pending_forecasts=0,
                            shadow_origins=self.expected_observations - 41,
                            evaluation_rows=3 * (self.expected_observations - 41),
                            retraining="COMPLETE" if len(records) > 1 else "NOT_REQUIRED_BY_QUALITY")
        self.save()

    def fail(self, error):
        self.summary.update(status="FAILED", error=str(error))
        self.save()

    def close(self):
        self.stack.close()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--policy", "--training", dest="policy", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    observer = None
    try:
        require(TrainingPolicy(**read_json(args.policy)) == TrainingPolicy(), "Retry probe requires the default policy")
        with contextlib.redirect_stdout(sys.stderr):
            tf = tensorflow_runtime()
        expected = int(os.environ.get("MOBILITY_ONLINE_EXPECTED_OBSERVATIONS", "121"))
        observer = RetryObserver(args.output.resolve(), lambda request, snapshot, output:
                                 OnlineLstm.fit(tf, request, snapshot, output), expected)
        serve(args.policy.resolve(), args.output.resolve(), sys.stdin, sys.stdout, observer, expected)
    except Exception as error:
        if observer is not None:
            observer.fail(error)
        traceback.print_exc(file=sys.stderr)
        return 1
    finally:
        if observer is not None:
            observer.close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
