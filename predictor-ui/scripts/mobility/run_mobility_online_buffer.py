"""Check live causal training requests on both official strategies, without fitting models."""
import argparse
from dataclasses import asdict
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import shutil
import sys
import uuid

import numpy as np
from build_mobility_windows import FEATURES, read_json, require, sha256, windows_for_run, write_json
from check_mobility_run import rows
from mobility_learning import baseline_displacements, displacement_to_gps
from mobility_online_buffer import REFERENCE, TrainingPolicy
from mobility_online_buffer_worker import PREDICTION_FIELDS
from mobility_shadow_worker import HEADER
from run_mobility_batch import MAIN, now, prepare
from run_mobility_shadow import execute_owned
from run_mobility_strategies import RESOURCE, validate_run


def jsonlines(path):
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines()]


def audit_online(output, samples, policy, *, worker_mode="REQUEST_ONLY", training="NOT_STARTED", scaler="NOT_FITTED", expected_observations=121):
    """Independent post-run audit. Source targets are never fed to the live worker early."""
    state = read_json(output / "online-worker.json")
    require(state["status"] == "COMPLETE" and state["training"] == training
            and state["scaler"] == scaler and state["mode"] == worker_mode
            and state["prediction_source"] == "HoldPosition" and state["connection_control"] is False
            and state["history_points"] == 8 and state["horizon_ms"] == 60000
            and state["feature_names"] == FEATURES and state["policy"] == asdict(policy), "Worker state differs")
    received, header = rows(output / "online_observations.csv")
    require(header == HEADER and received == samples, "Worker observations differ from simulator")
    run = dict(case_id="online-check", route_id="official-strategy", split="development", run_id=output.name)
    expected, skipped = windows_for_run(run, samples, REFERENCE, expected_observations=expected_observations)
    require(len(samples) == expected_observations and len(expected) == expected_observations - 9 and skipped == 1, "Unexpected development window count")
    inputs = jsonlines(output / "online_inputs.jsonl")
    completed = jsonlines(output / "online_samples.jsonl")
    require(len(inputs) == len(completed) == len(expected), "Missing inputs or completed labels")
    keys = ("run_id", "device_id", "origin_ms", "target_ms", "history_time_ms",
            "anchor_latitude_deg", "anchor_longitude_deg", "origin_node", "x")
    expected_inputs = [{k: w[k] for k in keys} for w in expected]
    expected_completed = [dict(i, completed_at_sim_ms=w["target_ms"], y=w["y"],
                               target_latitude_deg=w["target_latitude_deg"],
                               target_longitude_deg=w["target_longitude_deg"])
                          for i, w in zip(expected_inputs, expected)]
    require(inputs == expected_inputs, "Online/offline input parity failed")
    require(completed == expected_completed, "Label timing, displacement or identity differs")
    requests = jsonlines(output / "online_training_requests.jsonl")
    sizes = []
    size = policy.first_windows
    while size <= len(expected):
        sizes.append(size)
        if size == policy.max_training_windows:
            break
        size = min(size + policy.growth_windows, policy.max_training_windows)
    require(len(requests) == len(sizes), "Training request count differs")
    for index, (request, size) in enumerate(zip(requests, sizes), 1):
        snapshot_name = "training-request-%04d.json" % index
        require(request["snapshot_file"] == snapshot_name, "Unexpected request snapshot path")
        require(request["snapshot_sha256"] == sha256(output / snapshot_name), "Training snapshot changed")
        metadata = dict(request_number=index, requested_at_sim_ms=expected[size - 1]["target_ms"],
                        training_windows=size, first_origin_ms=expected[0]["origin_ms"],
                        last_origin_ms=expected[size - 1]["origin_ms"],
                        latest_label_ms=expected[size - 1]["target_ms"],
                        training="NOT_STARTED", scaler="NOT_FITTED", mode="REQUEST_ONLY")
        require(request == dict(metadata, snapshot_file=snapshot_name,
                                snapshot_sha256=sha256(output / snapshot_name)), "Request cutoff differs")
        snapshot = read_json(output / snapshot_name)
        require(snapshot == dict(metadata, policy=asdict(policy), feature_names=FEATURES,
                                 target_names=["delta_east_m", "delta_north_m"],
                                 windows=expected_completed[:size]), "Snapshot includes future or altered samples")
    require(sorted(p.name for p in output.glob("training-request-*.json"))
            == [r["snapshot_file"] for r in requests], "Extra training request snapshots")
    expected_events = []
    def event(kind, tick, **details):
        expected_events.append(dict(sequence=len(expected_events) + 1, event=kind, sim_time_ms=tick, **details))
    by_origin = {w["origin_ms"]: w for w in expected}
    by_target = {w["target_ms"]: (i, w) for i, w in enumerate(expected, 1)}
    by_request = {r["requested_at_sim_ms"]: r for r in requests}
    for row in samples:
        tick = int(row["time_ms"])
        event("OBSERVATION", tick, run_id=row["run_id"], device_id=row["device_id"])
        if tick in by_target:
            count, window = by_target[tick]
            event("LABEL_COMPLETED", tick, origin_ms=window["origin_ms"], target_ms=tick, completed_windows=count)
        if tick in by_request:
            request = by_request[tick]
            event("TRAINING_REQUEST", tick, request_number=request["request_number"],
                  training_windows=request["training_windows"])
        if tick in by_origin:
            event("INPUT_CREATED", tick, target_ms=by_origin[tick]["target_ms"])
    require(jsonlines(output / "online_events.jsonl") == expected_events, "Causal event order differs")
    predictions, header = rows(output / "online_predictions.csv")
    require(header == PREDICTION_FIELDS and len(predictions) == 2 * len(expected), "Baseline prediction count/schema differs")
    lookup = {(r["model"], int(r["origin_ms"])): r for r in predictions}
    require(len(lookup) == len(predictions), "Duplicate baseline predictions")
    for w in expected:
        x = np.asarray([w["x"]], dtype=np.float64)
        anchor = np.array([[w["anchor_latitude_deg"], w["anchor_longitude_deg"]]])
        for model, delta in baseline_displacements(x).items():
            r = lookup[(model, w["origin_ms"])]
            gps = displacement_to_gps(delta, anchor, REFERENCE)[0]
            require(r["run_id"] == w["run_id"] and r["device_id"] == w["device_id"]
                    and int(r["target_ms"]) == w["target_ms"], "Baseline identity/horizon differs")
            require(np.array_equal([float(r["predicted_latitude_deg"]), float(r["predicted_longitude_deg"])], gps),
                    "Baseline value differs")
    receipts, _ = rows(output / "shadow_receipts.csv")
    require(len(receipts) == len(expected), "Missing bridge receipts")
    for receipt, w in zip(receipts, expected):
        require(int(receipt["origin_ms"]) == int(receipt["received_at_sim_ms"]) == w["origin_ms"]
                and int(receipt["target_ms"]) == w["target_ms"]
                and float(receipt["predicted_latitude"]) == w["anchor_latitude_deg"]
                and float(receipt["predicted_longitude"]) == w["anchor_longitude_deg"], "Bridge timing/value differs")
    next_size = (None if sizes and sizes[-1] == policy.max_training_windows else
                 min(sizes[-1] + policy.growth_windows, policy.max_training_windows) if sizes else policy.first_windows)
    require(state["observations"] == expected_observations
            and state["completed_windows"] == state["prediction_origins"] == len(expected)
            and state["pending_windows"] == 0 and state["training_requests"] == len(requests)
            and state["retained_windows"] == min(len(expected), policy.max_training_windows)
            and state["next_request_windows"] == next_size, "Final counters differ")
    return dict(observations=expected_observations, completed_windows=len(expected), request_sizes=sizes, pending_windows=0,
                feature_parity="EXACT", labels="TARGET_TIME_MATCH", snapshot_audit="PASSED",
                event_order="PASSED", reply_timing="SAME_SIMULATION_TICK")


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--project-root", type=Path, default=Path(__file__).resolve().parents[3])
    parser.add_argument("--java", default="java")
    args = parser.parse_args(argv)
    batch = state = None
    try:
        root = args.project_root.resolve()
        java = shutil.which(args.java)
        require(java is not None, "JDK Java not found")
        policy = TrainingPolicy()
        batch = root / "simulator/sim_res/mobility-online-buffer" / (
            datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ") + "-" + uuid.uuid4().hex[:12])
        batch.mkdir(parents=True, exist_ok=False)
        state = dict(schema_version=1, status="RUNNING", started_at_utc=now(), training="NOT_STARTED",
                     mode="REQUEST_ONLY", policy=asdict(policy), runs=[])
        write_json(batch / "online-buffer.json", state)
        print("Online buffer checks: " + str(batch), flush=True)
        source = batch / "source"
        source.mkdir()
        for script in Path(__file__).parent.glob("*.py"):
            shutil.copy2(script, source / script.name)
        source_hashes = {p.name: sha256(p) for p in sorted(source.glob("*.py"))}
        write_json(batch / "source-sha256.json", source_hashes)
        classpath, xml = prepare(root, batch, java)
        for number, name in enumerate(("nomadic", "random-walk"), 1):
            case = batch / name
            case.mkdir()
            config = case / ("official-" + name + ".properties")
            shutil.copy2(root / RESOURCE / config.name, config)
            policy_file = case / "training-policy.json"
            write_json(policy_file, asdict(policy))
            environment = dict(os.environ)
            environment.update(MOBILITY_SHADOW_PYTHON=sys.executable,
                               MOBILITY_SHADOW_SCRIPT=str(source / "mobility_online_buffer_worker.py"),
                               MOBILITY_SHADOW_MODEL=str(policy_file), PYTHONDONTWRITEBYTECODE="1")
            command = [java, "-cp", classpath, MAIN, "--no-ui", "--scenario=" + str(config),
                       str(xml), str(case / "output")]
            write_json(case / "command.json", command)
            code = execute_owned(command, root, case / "simulation.log", environment)
            require(code == 0, "Simulation failed; read " + str(case / "simulation.log"))
            outputs = list((case / "output").glob("mobility-*"))
            require(len(outputs) == 1, "Expected one isolated simulation output")
            output = outputs[0]
            movement, samples = validate_run(output, config)
            result = audit_online(output, samples, policy)
            require(read_json(output / "online-worker.json")["policy_sha256"] == sha256(policy_file), "Policy differs")
            state["runs"].append(dict(case=name, output=str(output.relative_to(batch)),
                                      config_sha256=sha256(config), accounting=movement["accounting"],
                                      export_sha256=movement["export_sha256"], **result))
            write_json(batch / "online-buffer.json", state)
            print(f"[{number}/2] {name}: PASSED observations=121 completed_windows=112 "
                  "requests=32,96 pending_windows=0", flush=True)
        require(source_hashes == {p.name: sha256(p) for p in sorted(source.glob("*.py"))}, "Source changed")
        for record in state["runs"]:
            output = batch / record["output"]
            require(record["export_sha256"] == {p.name: sha256(p) for p in sorted(output.iterdir()) if p.is_file()},
                    "Run artifacts changed after validation: " + record["case"])
        state.update(status="COMPLETE", finished_at_utc=now())
        write_json(batch / "online-buffer.json", state)
        print("strategies=2 completed_windows=224 training_requests=4 training=NOT_STARTED scaler=NOT_FITTED")
        print("MOBILITY_ONLINE_BUFFER_OK")
        print("Manifest: " + str(batch / "online-buffer.json"))
        return 0
    except Exception as error:
        if batch is not None and state is not None:
            state.update(status="FAILED", error=str(error))
            write_json(batch / "online-buffer.json", state)
        print("MOBILITY_ONLINE_BUFFER_FAILED: " + str(error), file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
