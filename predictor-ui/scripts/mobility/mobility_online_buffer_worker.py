"""Receive live observations and publish causal samples/requests, with optional training hooks."""
import argparse
from contextlib import ExitStack
import csv
from dataclasses import asdict
import json
from pathlib import Path
import sys
import traceback

import numpy as np
from build_mobility_windows import FEATURES, read_json, require, sha256, write_json
from mobility_learning import baseline_displacements, displacement_to_gps
from mobility_online_buffer import OnlineSampleBuffer, TrainingPolicy, REFERENCE
from mobility_shadow_worker import HEADER

PREDICTION_FIELDS = ["model", "run_id", "device_id", "origin_ms", "target_ms",
                     "predicted_latitude_deg", "predicted_longitude_deg"]


def serve(policy_path, output, stream, reply, observer=None, expected_observations=121, protocol=None):
    require(type(expected_observations) is int and 11 <= expected_observations <= 1441,
            "Invalid configured observation count")
    policy = TrainingPolicy(**read_json(policy_path))
    require(output.is_dir() and not (output / "online-worker.json").exists(), "Expected fresh run output")
    state = dict(schema_version=1, status="STARTING", training="NOT_STARTED", scaler="NOT_FITTED",
                 mode="REQUEST_ONLY", connection_control=False, prediction_source="HoldPosition",
                 history_points=8, horizon_ms=60000, feature_names=FEATURES,
                 policy=asdict(policy), policy_sha256=sha256(policy_path))
    if observer is not None:
        state.update(observer.describe())
    write_json(output / "online-worker.json", state)
    try:
        buffer = OnlineSampleBuffer(policy)
        sequence = issued = 0
        with ExitStack() as stack:
            def opened(name):
                return stack.enter_context((output / name).open("x", encoding="utf-8", newline=""))
            rawout = opened("online_observations.csv")
            rawwriter = csv.DictWriter(rawout, fieldnames=HEADER)
            rawwriter.writeheader()
            journals = {kind: opened("online_" + kind + ".jsonl")
                        for kind in ("inputs", "samples", "training_requests", "events")}
            predout = opened("online_predictions.csv")
            predwriter = csv.DictWriter(predout, fieldnames=PREDICTION_FIELDS)
            predwriter.writeheader()

            def record(kind, value):
                journals[kind].write(json.dumps(value, allow_nan=False, separators=(",", ":")) + "\n")
                journals[kind].flush()

            def event(kind, now, **details):
                nonlocal sequence
                sequence += 1
                record("events", dict(sequence=sequence, event=kind, sim_time_ms=now, **details))

            require(protocol is None or protocol in ("MOBILITY_SHARED_LIFECYCLE_V1", "MOBILITY_CLOSED_LOOP_V1"), "Unknown live protocol")
            print("READY" if protocol is None else "READY\t" + protocol, file=reply, flush=True)
            ended = False
            stream = iter(stream)
            for line in stream:
                line = line.rstrip("\r\n")
                if line == "END":
                    ended = True
                    break
                parts = line.split("\t", 2)
                require(len(parts) == 3 and parts[0] == "OBS" and parts[1] in ("true", "false"),
                        "Invalid Java request")
                row, pending, labelled, request = buffer.observe(parts[2], parts[1] == "true")
                now = int(row["time_ms"])
                rawwriter.writerow(row)
                rawout.flush()
                event("OBSERVATION", now, run_id=row["run_id"], device_id=row["device_id"])
                if observer is not None:
                    observer.observe(row)
                if labelled is not None:
                    record("samples", labelled.record())
                    event("LABEL_COMPLETED", now, origin_ms=labelled.window.origin_ms,
                          target_ms=labelled.window.target_ms, completed_windows=buffer.completed_count)
                if request is not None:
                    name = "training-request-%04d.json" % request.request_number
                    snapshot = dict(**request.metadata(), policy=asdict(policy),
                                    feature_names=FEATURES, target_names=["delta_east_m", "delta_north_m"],
                                    windows=[w.record() for w in request.windows])
                    require(not (output / name).exists(), "Refusing existing request snapshot")
                    write_json(output / name, snapshot)
                    metadata = dict(**request.metadata(), snapshot_file=name, snapshot_sha256=sha256(output / name))
                    record("training_requests", metadata)
                    event("TRAINING_REQUEST", now, request_number=request.request_number,
                          training_windows=len(request.windows))
                    if observer is not None:
                        observer.request(request, output / name)
                        state.update(observer.describe())
                        write_json(output / "online-worker.json", state)
                if protocol == "MOBILITY_CLOSED_LOOP_V1":
                    control = next(stream, "").rstrip("\r\n")
                    require(control.startswith("CONTROL\t") and len(control) <= 4096 and observer is not None,
                            "Missing bounded Java training control")
                    observer.control(json.loads(control.split("\t", 1)[1]), buffer)
                    state.update(observer.describe())
                    write_json(output / "online-worker.json", state)
                if pending is None:
                    print("WAIT\t" + str(now), file=reply, flush=True)
                    continue
                record("inputs", asdict(pending))
                x = np.asarray([pending.x], dtype=np.float64)
                anchor = np.array([[pending.anchor_latitude_deg, pending.anchor_longitude_deg]])
                for model, delta in baseline_displacements(x).items():
                    gps = displacement_to_gps(delta, anchor, REFERENCE)[0]
                    predwriter.writerow(dict(model=model, run_id=pending.run_id, device_id=pending.device_id,
                                             origin_ms=now, target_ms=pending.target_ms,
                                             predicted_latitude_deg=gps[0], predicted_longitude_deg=gps[1]))
                predout.flush()
                issued += 1
                event("INPUT_CREATED", now, target_ms=pending.target_ms)
                if observer is not None:
                    observer.predict(pending)
                # Reuse the existing bridge protocol. Its receipts are method-neutral.
                print("PRED\t%d\t%d\t%.17g\t%.17g" % (now, pending.target_ms,
                      pending.anchor_latitude_deg, pending.anchor_longitude_deg), file=reply, flush=True)
            require(ended, "Java stream closed without END")
            result = buffer.finish()
            require(result["observations"] == expected_observations
                    and result["completed_windows"] == issued == expected_observations - 9,
                    "Incomplete development simulation stream")
            require(sha256(policy_path) == state["policy_sha256"], "Policy changed during simulation")
            if observer is not None:
                observer.finish()
                state.update(observer.describe())
            state.update(status="COMPLETE", prediction_origins=issued, **result)
            write_json(output / "online-worker.json", state)
            print("DONE\t%d\t%d" % (result["observations"], issued), file=reply, flush=True)
    except BaseException as error:
        state.update(status="FAILED", error=str(error))
        write_json(output / "online-worker.json", state)
        if observer is not None:
            observer.fail(error)
        raise


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    # --training is a compatibility alias for the existing Java bridge's argument.
    # This worker reads only the policy JSON, never a trained model directory.
    parser.add_argument("--policy", "--training", dest="policy", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    try:
        serve(args.policy.resolve(), args.output.resolve(), sys.stdin, sys.stdout)
    except Exception:
        traceback.print_exc(file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
