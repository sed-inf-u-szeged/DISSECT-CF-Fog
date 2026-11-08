"""Execute Java-authorized fits on immutable mature windows, including after fallback."""
import argparse
import contextlib
import json
import os
from pathlib import Path
import sys
import traceback

from build_mobility_windows import FEATURES, read_json, require, sha256, write_json
from mobility_online_buffer import TrainingPolicy
from mobility_online_buffer_worker import serve
from mobility_online_lstm import OnlineLstm, tensorflow_runtime, version_name
from mobility_shared_lifecycle_worker import SharedLifecycleObserver

PROTOCOL = "MOBILITY_CLOSED_LOOP_V1"


class ClosedLoopObserver(SharedLifecycleObserver):
    """Training authority is the Java command, never a Python qualification flag."""

    def __init__(self, output, trainer, reply, expected_observations=361):
        super().__init__(output, trainer, reply, expected_observations)
        self.controls = self.stack.enter_context((output / "training_controls.jsonl").open("x", encoding="utf-8"))
        self.control_count = 0
        self.summary.update(mode="JAVA_SCHEDULED", controller_integration="SHARED_COORDINATOR_AND_MANAGER",
                            training_scheduler="JAVA_SHARED_RULES", promotion="JAVA_OWNED",
                            training_window_selection="LATEST_MATURE_WINDOWS")
        self.save()

    def describe(self):
        return dict(super().describe(), mode="JAVA_SCHEDULED")

    def request(self, request, snapshot_path):
        """Legacy buffer milestones remain auditable but cannot launch a fit."""

    def control(self, command, buffer):
        row, now = self.observation, int(self.observation["time_ms"])
        keys = {"sequence", "sim_time_ms", "run_id", "device_id", "action", "reason", "available_windows",
                "window_size", "request_number", "model_version", "previous_version", "previous_state"}
        require(isinstance(command, dict) and command.keys() == keys, "Invalid Java command fields")
        require(all(type(command[k]) is int for k in ("sequence", "sim_time_ms", "available_windows", "window_size", "request_number")),
                "Invalid Java command counts")
        require(command["sequence"] == self.control_count + 1 == self.observed and command["sim_time_ms"] == now
                and (command["run_id"], command["device_id"]) == (row["run_id"], row["device_id"])
                and command["available_windows"] == buffer.completed_count
                and command["previous_version"] == (self.current or "")
                and 32 <= command["window_size"] <= 4096, "Stale or mismatched Java command")
        action, reason = command["action"], command["reason"]
        require(action in ("HOLD", "TRAIN"), "Unknown training action")
        if action == "HOLD":
            require(command["request_number"] == 0 and command["model_version"] == ""
                    and reason in ("STREAM_COMPLETE", "WARMUP", "ACTIVE", "EVALUATING", "WAITING_FOR_NEW_DATA"),
                    "Invalid hold command")
            require((reason == "STREAM_COMPLETE") == (self.observed == self.expected_observations),
                    "Stream completion differs")
        else:
            number = len(self.versions) + 1
            require(self.observed < self.expected_observations and command["request_number"] == number
                    and command["model_version"] == version_name(number), "Invalid scheduled version")
            if self.current is None:
                require(reason == "INITIAL" and command["previous_state"] == "NONE" and command["window_size"] == 32,
                        "Invalid initial fit")
            else:
                previous = self.versions[self.current]
                decision = previous["gate"].decision
                require(decision is not None and previous["matured"] >= 64, "Refit before independent qualification")
                require((reason, command["previous_state"], decision["decision"]) in
                        (("REJECTION", "FAILED", "REJECT"), ("DEGRADATION", "DEGRADED", "PASS")),
                        "Refit does not follow rejection or degradation")
                expected_size = min(4096, previous["training_windows"] + (64 if reason == "REJECTION" else 0))
                require(command["window_size"] == expected_size, "Shared window growth differs")
                require(now - previous["cutoff"] >= 64 * 60000, "Refit requires new mature data")
                previous["scoring"].finish()
                previous["model"].verify_frozen()
            request = buffer.scheduled_request(number, command["window_size"], now)
            path = self.output / ("scheduled-request-%04d.json" % number)
            require(not path.exists(), "Refusing existing scheduled snapshot")
            snapshot = dict(**request.metadata(), feature_names=FEATURES,
                            target_names=["delta_east_m", "delta_north_m"],
                            window_selection="LATEST_MATURE_WINDOWS", java_command=command,
                            windows=[w.record() for w in request.windows])
            write_json(path, snapshot)
            self.fit_request(request, path)
            self.versions[self.current].update(training_reason=reason, snapshot_file=path.name,
                                               snapshot_sha256=sha256(path))
        self.controls.write(json.dumps(command, allow_nan=False) + "\n")
        self.controls.flush()
        self.control_count += 1

    def finish(self):
        require(self.current is not None and self.observed == self.expected_observations
                and self.control_count == self.observed, "Incomplete Java-controlled stream")
        records = []
        for version, session in self.versions.items():
            metrics = session["scoring"].finish()
            session["model"].verify_frozen()
            path = self.output / session["snapshot_file"]
            require(sha256(path) == session["snapshot_sha256"], "Scheduled snapshot changed")
            decision = session["gate"].decision
            decision_path = self.output / (version + "-quality-decision.json")
            if decision is None:
                require(not decision_path.exists() and session["matured"] < 64, "Premature qualification")
            else:
                require(read_json(decision_path) == decision and sha256(decision_path) == session["decision_sha256"],
                        "Frozen first qualification changed")
            records.append(dict(model_version=version, request_number=session["request_number"],
                                training_windows=session["training_windows"], training_cutoff_sim_ms=session["cutoff"],
                                matched_origins=session["matured"], training_reason=session["training_reason"],
                                snapshot_file=session["snapshot_file"], snapshot_sha256=session["snapshot_sha256"],
                                quality_decision=decision["decision"] if decision else "WAITING_FOR_EVALUATION",
                                remaining_quality_origins=max(0, 64 - session["matured"]), metrics=metrics))
        require(sum(r["matched_origins"] for r in records) == self.expected_observations - 41,
                "Unmatched closed-loop forecasts")
        self.summary.update(status="COMPLETE", trained_versions=len(records), versions=records,
                            current_version=self.current, pending_forecasts=0,
                            shadow_origins=self.expected_observations - 41,
                            evaluation_rows=3 * (self.expected_observations - 41))
        self.save()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--policy", "--training", dest="policy", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    observer = None
    try:
        require(TrainingPolicy(**read_json(args.policy)) == TrainingPolicy(), "Expected the verified policy")
        with contextlib.redirect_stdout(sys.stderr):
            tf = tensorflow_runtime()
        expected = int(os.environ.get("MOBILITY_ONLINE_EXPECTED_OBSERVATIONS", "361"))
        observer = ClosedLoopObserver(args.output.resolve(), lambda request, snapshot, output:
                                      OnlineLstm.fit(tf, request, snapshot, output), sys.stdout, expected)
        serve(args.policy.resolve(), args.output.resolve(), sys.stdin, sys.stdout, observer, expected, PROTOCOL)
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
