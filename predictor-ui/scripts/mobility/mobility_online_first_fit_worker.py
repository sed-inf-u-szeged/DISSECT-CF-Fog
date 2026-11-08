"""One live LSTM training followed by frozen shadow evaluation; promotion stays disabled."""
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
from mobility_online_buffer import REFERENCE
from mobility_online_buffer_worker import serve
from mobility_online_lstm import OnlineLstm, tensorflow_runtime
from mobility_quality_gate import GATE_KIND, QualityPolicy, RelativeQualityGate
from mobility_shadow_evaluation import DelayedEvaluation, PREDICTION_FIELDS, EVALUATION_FIELDS, MODELS


class FirstFitObserver:
    """First-fit integration probe, not the final multi-version lifecycle controller."""

    def __init__(self, output, trainer, quality_policy=None):
        require(not (output / "first-fit.json").exists(), "Refusing existing first-fit result")
        self.output, self.trainer = output, trainer
        require(quality_policy is None or isinstance(quality_policy, QualityPolicy), "Invalid quality policy")
        self.quality_policy, self.quality = quality_policy, None
        self.model = None
        self.scoring = DelayedEvaluation()
        self.stack = ExitStack()
        self.sequence = 0
        self.train_time = None
        self.deferred = []
        self.matured = 0
        self.phase = "WAITING_FOR_DATA"
        self.summary = dict(schema_version=1, status="STARTING", mode="FIRST_FIT_SHADOW",
                            quality_gate=GATE_KIND if quality_policy is not None else "NOT_APPLIED",
                            active=False, connection_control=False)
        try:
            self.events = self.stack.enter_context((output / "first_fit_events.jsonl").open("x", encoding="utf-8"))
            self.predout = self.stack.enter_context((output / "first_fit_predictions.csv").open("x", encoding="utf-8", newline=""))
            self.evalout = self.stack.enter_context((output / "first_fit_evaluations.csv").open("x", encoding="utf-8", newline=""))
            self.predwriter = csv.DictWriter(self.predout, fieldnames=PREDICTION_FIELDS)
            self.evalwriter = csv.DictWriter(self.evalout, fieldnames=EVALUATION_FIELDS)
            self.predwriter.writeheader()
            self.evalwriter.writeheader()
            self.save()
        except BaseException:
            self.stack.close()
            raise

    def describe(self):
        training = "COMPLETE" if self.model is not None else "TRAINING" if self.phase == "TRAINING" else "NOT_STARTED"
        return dict(mode="FIRST_FIT_SHADOW", training=training,
                    scaler="FROZEN" if self.model is not None else "NOT_FITTED", lifecycle_state=self.phase)

    def save(self):
        self.summary.update(self.describe())
        write_json(self.output / "first-fit.json", self.summary)

    def event(self, kind, now, **details):
        self.sequence += 1
        self.events.write(json.dumps(dict(sequence=self.sequence, event=kind, sim_time_ms=now, **details),
                                     allow_nan=False) + "\n")
        self.events.flush()

    def observe(self, row):
        now = int(row["time_ms"])
        self.event("OBSERVATION", now, run_id=row["run_id"], device_id=row["device_id"])
        evaluated = self.scoring.observe(row)
        if evaluated:
            self.evalwriter.writerows(evaluated)
            self.evalout.flush()
            self.matured += 1
            self.event("EVALUATION", now, origin_ms=evaluated[0]["origin_ms"], target_ms=now,
                       model_version="v0001", models=list(MODELS), matured_origins=self.matured)
            if self.quality is not None and self.quality.decision is None:
                decision = self.quality.observe("v0001", evaluated, now)
                if decision is not None:
                    write_json(self.output / "quality-decision.json", decision)
                    self.event("QUALITY_DECISION", now, model_version="v0001",
                               evaluated_origins=decision["evaluated_origins"], decision=decision["decision"])
                    self.summary.update(quality_decision=decision["decision"],
                                        quality_decision_sha256=sha256(self.output / "quality-decision.json"))
                    self.save()

    def request(self, request, snapshot_path):
        now = request.requested_at_sim_ms
        require(self.scoring.current is not None and int(self.scoring.current["time_ms"]) == now,
                "Training request must follow the current observation")
        if self.model is not None:
            self.deferred.append(len(request.windows))
            self.event("TRAINING_REQUEST_DEFERRED", now, request_number=request.request_number,
                       training_windows=len(request.windows), reason="FIRST_FIT_ONLY")
            return
        require(request.request_number == 1 and len(request.windows) == 32, "Expected first 32-window request")
        self.phase = "TRAINING"
        self.event("TRAINING_STARTED", now, model_version="v0001", training_windows=32,
                   request_number=request.request_number, latest_label_ms=request.windows[-1].completed_at_sim_ms)
        self.save()
        with contextlib.redirect_stdout(sys.stderr):
            model = self.trainer(request, snapshot_path, self.output / "models/v0001")
        require(model.metadata["status"] == "COMPLETE" and model.metadata["reload_check"] == "PASSED"
                and model.metadata["training_cutoff_sim_ms"] == now and model.metadata["training_windows"] == 32,
                "Trainer returned an incomplete or mismatched model")
        self.model = model
        self.train_time = now
        if self.quality_policy is not None:
            self.quality = RelativeQualityGate(self.quality_policy, "v0001", now)
        self.phase = "SHADOW"
        self.event("TRAINING_COMPLETE", now, model_version="v0001", training_windows=32, scaler="FROZEN")
        self.save()

    def predict(self, pending):
        if self.model is None:
            return
        now = pending.origin_ms
        require(now >= self.train_time and self.phase == "SHADOW", "Prediction before training completed")
        x = np.asarray([pending.x], dtype=np.float64)
        with contextlib.redirect_stdout(sys.stderr):
            delta = self.model.predict_delta(x)
        anchor = np.array([[pending.anchor_latitude_deg, pending.anchor_longitude_deg]])
        values = dict(baseline_displacements(x), LSTM=delta)
        predictions = []
        for model, value in values.items():
            gps = displacement_to_gps(value, anchor, REFERENCE)[0]
            predictions.append(dict(model=model, device_id=pending.device_id, origin_ms=now,
                                    target_ms=pending.target_ms, origin_node=pending.origin_node,
                                    predicted_latitude_deg=gps[0], predicted_longitude_deg=gps[1]))
        self.scoring.issue(predictions)
        self.predwriter.writerows(predictions)
        self.predout.flush()
        self.event("SHADOW_PREDICTION", now, target_ms=pending.target_ms,
                   model_version="v0001", models=list(MODELS))

    def finish(self):
        require(self.model is not None and self.phase == "SHADOW", "Online training did not finish")
        metrics = self.scoring.finish()
        require(self.scoring.issued == self.matured == 80 and self.matured >= 64, "Incomplete post-training evaluation")
        require(self.deferred == [96], "Unexpected deferred request sequence")
        if self.quality is not None:
            require(self.quality.decision is not None
                    and read_json(self.output / "quality-decision.json") == self.quality.decision
                    and sha256(self.output / "quality-decision.json") == self.summary["quality_decision_sha256"],
                    "Missing or changed frozen quality decision")
        self.model.verify_frozen()
        self.summary.update(status="COMPLETE", trained_versions=1, model_version="v0001", training_windows=32,
                            training_cutoff_sim_ms=self.train_time, shadow_origins=self.scoring.issued,
                            matched_origins=self.matured, evaluation_rows=3 * self.matured, pending_forecasts=0,
                            deferred_request_sizes=self.deferred, minimum_shadow_samples=64,
                            evaluation="TARGET_TIME_MATCH", error_definition="great_circle_position_error_m",
                            earth_radius_m=6378137.0, metrics=metrics)
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
        quality_path = os.environ.get("MOBILITY_ONLINE_QUALITY_POLICY")
        quality_policy = QualityPolicy(**read_json(Path(quality_path))) if quality_path else None
        # Load TensorFlow before READY, within the bridge's startup timeout.
        with contextlib.redirect_stdout(sys.stderr):
            tf = tensorflow_runtime()
        observer = FirstFitObserver(args.output.resolve(), lambda request, snapshot, output:
                                    OnlineLstm.fit(tf, request, snapshot, output), quality_policy)
        serve(args.policy.resolve(), args.output.resolve(), sys.stdin, sys.stdout, observer)
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
