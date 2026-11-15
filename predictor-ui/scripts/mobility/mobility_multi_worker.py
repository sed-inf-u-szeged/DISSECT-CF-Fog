"""Multiplex independent causal device sessions through one sequential Python worker."""
import argparse
from contextlib import ExitStack, redirect_stdout
import csv
import gc
import io
import json
import os
from pathlib import Path
import sys
import time
import traceback

import numpy as np
from build_mobility_windows import read_json, require, sha256, write_json
from mobility_closed_loop_worker import ClosedLoopObserver
from mobility_online_buffer import OnlineSampleBuffer, TrainingPolicy
from mobility_online_lstm import OnlineLstm, tensorflow_runtime
from mobility_shadow_worker import HEADER

PROTOCOL = "MOBILITY_MULTI_CLOSED_LOOP_V1"


class DeviceReply:
    def __init__(self, stream, device_id):
        self.stream, self.device_id, self.pending = stream, device_id, ""

    def write(self, text):
        self.pending += text
        while "\n" in self.pending:
            line, self.pending = self.pending.split("\n", 1)
            self.stream.write("DEVICE\t" + self.device_id + "\t" + line + "\n")
        return len(text)

    def flush(self):
        self.stream.flush()


class TimedModel:
    def __init__(self, model, session):
        self.model, self.session = model, session

    def __getattr__(self, name):
        return getattr(self.model, name)

    def predict_delta(self, x):
        started = time.perf_counter_ns()
        result = self.model.predict_delta(x)
        self.session.timing("PREDICT", self.metadata["version"], time.perf_counter_ns() - started)
        return result

    def release(self):
        # Saved artifacts remain available for verification and replay.
        if isinstance(self.model, OnlineLstm):
            self.model.model = None


class DeviceSession:
    def __init__(self, output, run_id, device_id, policy_path, expected, trainer, reply, methods=(), tf=None):
        self.output, self.run_id, self.device_id = output, run_id, device_id
        self.expected, self.now = expected, -1
        self.stack = ExitStack()
        self.buffer = OnlineSampleBuffer(TrainingPolicy(**read_json(policy_path)))
        self.policy_hash = sha256(policy_path)
        self.policy_path, self.issued, self.finished = policy_path, 0, False
        self.reply = DeviceReply(reply, device_id)
        self.observer = None
        self.comparison = None
        self.times = {"TRAIN": [], "PREDICT": []}
        try:
            self.raw_stream = self.stack.enter_context((output / "online_observations.csv").open("x", encoding="utf-8", newline=""))
            self.raw = csv.DictWriter(self.raw_stream, fieldnames=HEADER)
            self.raw.writeheader()
            self.samples = self.stack.enter_context((output / "online_samples.jsonl").open("x", encoding="utf-8"))
            self.inputs = self.stack.enter_context((output / "online_inputs.jsonl").open("x", encoding="utf-8"))
            self.timing_stream = self.stack.enter_context((output / "timings.csv").open("x", encoding="utf-8", newline=""))
            self.timing_writer = csv.writer(self.timing_stream)
            self.timing_writer.writerow(["device_id", "sim_time_ms", "operation", "model_version", "wall_seconds"])

            def timed_trainer(request, snapshot, directory):
                started = time.perf_counter_ns()
                model = trainer(request, snapshot, directory)
                self.timing("TRAIN", model.metadata["version"], time.perf_counter_ns() - started)
                return TimedModel(model, self)

            self.observer = ClosedLoopObserver(output, timed_trainer, self.reply, expected)
            if methods:
                from mobility_comparison_models import ComparisonSession
                self.comparison = ComparisonSession(output, list(methods), tf, run_id, device_id)
            self.save("RUNNING")
        except BaseException:
            self.close()
            raise

    def timing(self, operation, version, nanos):
        seconds = nanos / 1e9
        self.times[operation].append(seconds)
        self.timing_writer.writerow([self.device_id, self.now, operation, version, seconds])
        self.timing_stream.flush()

    def save(self, status, **extra):
        write_json(self.output / "multi-worker-device.json", dict(schema_version=1, status=status,
                   run_id=self.run_id, device_id=self.device_id, policy_sha256=self.policy_hash,
                   observations=self.buffer.history.count, completed_windows=self.buffer.completed_count,
                   prediction_origins=self.issued, expected_observations=self.expected, **extra))

    def observe(self, line, command):
        require(not self.finished, "Observation after device completion")
        parts = line.split("\t", 2)
        require(len(parts) == 3 and parts[0] == "OBS" and parts[1] in ("true", "false"), "Invalid observation frame")
        fields = next(csv.reader(io.StringIO(parts[2])))
        require(len(fields) == len(HEADER) and fields[:2] == [self.run_id, self.device_id], "Observation routed to wrong device or run")
        require(command.get("device_id") == self.device_id and command.get("run_id") == self.run_id,
                "Control routed to wrong device or run")
        row, pending, labelled, _ = self.buffer.observe(parts[2], parts[1] == "true")
        self.now = int(row["time_ms"])
        self.raw.writerow(row)
        self.raw_stream.flush()
        if self.comparison:
            self.comparison.observe(row)
        self.observer.observe(row)
        if labelled is not None:
            self.samples.write(json.dumps(labelled.record(), allow_nan=False) + "\n")
            self.samples.flush()
        old = self.observer.current
        self.observer.control(command, self.buffer)
        if self.comparison and command["action"] == "TRAIN":
            request = self.buffer.scheduled_request(command["request_number"], command["window_size"], self.now)
            self.comparison.fit(request, self.output / ("scheduled-request-%04d.json" % command["request_number"]))
        if self.comparison:
            if pending is not None:
                self.comparison.predict(pending)
            print("COMPARISON\t" + json.dumps(self.comparison.preview(self.now), allow_nan=False, separators=(",", ":")), file=self.reply, flush=True)
        if old is not None and old != self.observer.current:
            self.observer.versions[old]["model"].release()
            gc.collect()
        if pending is None:
            print("WAIT\t" + str(self.now), file=self.reply, flush=True)
        else:
            from dataclasses import asdict
            self.inputs.write(json.dumps(asdict(pending), allow_nan=False) + "\n")
            self.inputs.flush()
            self.observer.predict(pending)
            self.issued += 1
            print("PRED\t%d\t%d\t%.17g\t%.17g" % (self.now, pending.target_ms,
                  pending.anchor_latitude_deg, pending.anchor_longitude_deg), file=self.reply, flush=True)

    def finish(self):
        result = self.buffer.finish()
        require(result["observations"] == self.expected and self.issued == result["completed_windows"] == self.expected - 9,
                "Incomplete per-device observation stream")
        require(sha256(self.policy_path) == self.policy_hash, "Training policy changed")
        self.observer.finish()
        if self.comparison:
            self.comparison.finish(self.expected)
        self.finished = True
        metrics = {key.lower(): dict(count=len(values), total_seconds=sum(values),
                   mean_ms=float(np.mean(values) * 1000) if values else None,
                   p50_ms=float(np.percentile(values, 50) * 1000) if values else None,
                   p95_ms=float(np.percentile(values, 95) * 1000) if values else None)
                   for key, values in self.times.items()}
        self.save("COMPLETE", timings=metrics)
        return dict(device_id=self.device_id, **metrics)

    def fail(self, error):
        self.save("FAILED", error=str(error))
        if self.observer is not None:
            self.observer.fail(error)

    def close(self):
        if self.observer is not None:
            self.observer.close()
        if self.comparison is not None:
            self.comparison.close()
        self.stack.close()


def serve_multi(policy, output, run_id, count, expected, trainer, stream, reply, methods=(), tf=None):
    require(type(count) is int and 1 <= count <= 100 and 105 <= expected <= 1441, "Invalid device or observation count")
    require(run_id and TrainingPolicy(**read_json(policy)) == TrainingPolicy(), "Invalid run identity or policy")
    state = dict(schema_version=1, status="RUNNING", run_id=run_id, device_count=count,
                 worker_processes=1, scheduling="SEQUENTIAL_SHARED_WORKER", connection_control=False)
    manifest = output / "multi-worker.json"
    require(not manifest.exists(), "Refusing existing worker output")
    sessions = {}
    write_json(manifest, state)
    try:
        for i in range(count):
            child = output / "devices" / str(i)
            child.mkdir(parents=True, exist_ok=True)
            sessions[str(i)] = DeviceSession(child, run_id, str(i), policy, expected, trainer, reply, methods, tf)
        print("READY\t" + PROTOCOL, file=reply, flush=True)
        lines, ended, tick = iter(stream), False, None
        for line in lines:
            line = line.rstrip("\r\n")
            if line == "END":
                ended = True
                break
            fields = line.split("\t")
            require(len(fields) == 2 and fields[0] == "DEVICE" and fields[1] in sessions, "Unknown device frame")
            device_id = fields[1]
            observation = next(lines, "").rstrip("\r\n")
            control = next(lines, "").rstrip("\r\n")
            require(control.startswith("CONTROL\t") and len(control) <= 4096, "Missing bounded device control")
            command = json.loads(control.split("\t", 1)[1])
            now = command.get("sim_time_ms")
            require(type(now) is int and now >= 0, "Invalid shared simulation time")
            # SmartDevice intentionally gives each device an independent 0-3 minute
            # start delay. Only its own 60-second stream must be contiguous.
            require(tick is None or now >= tick, "Simulation time moved backwards across devices")
            tick = now
            sessions[device_id].observe(observation, command)
        require(ended, "Worker stream ended without complete END")
        metrics = [session.finish() for session in sessions.values()]
        state.update(status="COMPLETE", devices=metrics, observations=count * expected,
                     prediction_origins=count * (expected - 9))
        write_json(manifest, state)
        print("DONE\t%d\t%d\t%d" % (count, count * expected, count * (expected - 9)), file=reply, flush=True)
        return state
    except BaseException as error:
        state.update(status="FAILED", error=str(error))
        write_json(manifest, state)
        for session in sessions.values():
            session.fail(error)
        raise
    finally:
        for session in sessions.values():
            session.close()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--training", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--run-id", required=True)
    parser.add_argument("--devices", type=int, required=True)
    parser.add_argument("--observations", type=int, required=True)
    args = parser.parse_args()
    try:
        with redirect_stdout(sys.stderr):
            tf = tensorflow_runtime()
        from mobility_comparison_models import validate_methods, preflight
        methods = validate_methods(json.loads(os.environ.get("MOBILITY_COMPARISON_MODELS", "[]")))
        preflight(methods)
        serve_multi(args.training, args.output, args.run_id, args.devices, args.observations,
                    lambda request, snapshot, directory: OnlineLstm.fit(tf, request, snapshot, directory), sys.stdin, sys.stdout, methods, tf)
        return 0
    except Exception:
        traceback.print_exc(file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
