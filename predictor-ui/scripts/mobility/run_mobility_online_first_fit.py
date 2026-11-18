"""Verify one actual online LSTM fit and future shadow scoring on each official strategy."""
import argparse
import contextlib
from dataclasses import asdict
from datetime import datetime, timezone
import os
from pathlib import Path
import shutil
import sys
import uuid

import numpy as np
from build_mobility_windows import FEATURES, read_json, require, sha256, windows_for_run, write_json
from check_mobility_run import rows
from mobility_learning import fit_scaler, baseline_displacements, displacement_to_gps, error_metrics
from mobility_online_buffer import REFERENCE, TrainingPolicy
from mobility_online_lstm import OnlineLstm, tensorflow_runtime
from mobility_quality_gate import GATE_KIND, QualityPolicy, RelativeQualityGate
from mobility_shadow_evaluation import MODELS, PREDICTION_FIELDS, EVALUATION_FIELDS, distance_m
from run_mobility_batch import MAIN, now, prepare
from run_mobility_online_buffer import audit_online, jsonlines
from run_mobility_shadow import execute_owned
from run_mobility_strategies import RESOURCE, validate_run


def artifacts(directory):
    return {p.relative_to(directory).as_posix(): sha256(p)
            for p in sorted(directory.rglob("*")) if p.is_file()}


def audit_first_fit(output, samples, quality_policy=None):
    """Independently reconstruct allowed event order and target-time scores."""
    state = read_json(output / "first-fit.json")
    cutoff = int(samples[40]["time_ms"])
    quality_kind = GATE_KIND if quality_policy is not None else "NOT_APPLIED"
    quality_decision = read_json(output / "quality-decision.json") if quality_policy is not None else None
    require(quality_policy is not None or not (output / "quality-decision.json").exists(), "Unexpected quality artifact")
    require(state["status"] == "COMPLETE" and state["mode"] == "FIRST_FIT_SHADOW"
            and state["training"] == "COMPLETE" and state["scaler"] == "FROZEN"
            and state["lifecycle_state"] == "SHADOW" and state["active"] is False
            and state["connection_control"] is False and state["quality_gate"] == quality_kind,
            "First-fit state differs")
    require(state["trained_versions"] == 1 and state["model_version"] == "v0001"
            and state["training_windows"] == 32 and state["training_cutoff_sim_ms"] == cutoff
            and state["shadow_origins"] == state["matched_origins"] == 80
            and state["evaluation_rows"] == 240 and state["pending_forecasts"] == 0
            and state["deferred_request_sizes"] == [96] and state["minimum_shadow_samples"] == 64,
            "First-fit counters differ")
    expected = []
    def event(kind, tick, **details):
        expected.append(dict(sequence=len(expected) + 1, event=kind, sim_time_ms=tick, **details))
    for index, row in enumerate(samples):
        tick = int(row["time_ms"])
        event("OBSERVATION", tick, run_id=row["run_id"], device_id=row["device_id"])
        if index >= 41:
            event("EVALUATION", tick, origin_ms=tick - 60000, target_ms=tick,
                  model_version="v0001", models=list(MODELS), matured_origins=index - 40)
        if quality_policy is not None and index == 40 + quality_policy.minimum_evaluated_origins:
            event("QUALITY_DECISION", tick, model_version="v0001",
                  evaluated_origins=quality_policy.minimum_evaluated_origins, decision=quality_decision["decision"])
        if index == 40:
            event("TRAINING_STARTED", tick, model_version="v0001", training_windows=32,
                  request_number=1, latest_label_ms=tick)
            event("TRAINING_COMPLETE", tick, model_version="v0001", training_windows=32, scaler="FROZEN")
        if index == 104:
            event("TRAINING_REQUEST_DEFERRED", tick, request_number=2, training_windows=96, reason="FIRST_FIT_ONLY")
        if 40 <= index <= 119:
            event("SHADOW_PREDICTION", tick, target_ms=tick + 60000, model_version="v0001", models=list(MODELS))
    require(jsonlines(output / "first_fit_events.jsonl") == expected, "First-fit causal event order differs")
    predictions, header = rows(output / "first_fit_predictions.csv")
    evaluations, eval_header = rows(output / "first_fit_evaluations.csv")
    require(header == PREDICTION_FIELDS and eval_header == EVALUATION_FIELDS
            and len(predictions) == len(evaluations) == 240, "First-fit row count/schema differs")
    expected_keys = [(model, int(row["time_ms"])) for row in samples[40:120] for model in MODELS]
    require([(p["model"], int(p["origin_ms"])) for p in predictions] == expected_keys
            and [(p["model"], int(p["origin_ms"])) for p in evaluations] == expected_keys,
            "Missing, duplicate or premature shadow predictions")
    by_time = {int(row["time_ms"]): row for row in samples}
    errors = {model: [] for model in MODELS}
    for pred, evaluated in zip(predictions, evaluations):
        origin, target = int(pred["origin_ms"]), int(pred["target_ms"])
        require(target == origin + 60000 and target in by_time, "Shadow horizon differs")
        source, truth = by_time[origin], by_time[target]
        require(pred["device_id"] == source["device_id"] and pred["origin_node"] == source["current_node"]
                and evaluated["run_id"] == truth["run_id"]
                and {k: evaluated[k] for k in PREDICTION_FIELDS} == pred
                and int(evaluated["evaluated_at_sim_ms"]) == target, "Shadow identity or target time differs")
        gps = [float(pred[k]) for k in ("predicted_latitude_deg", "predicted_longitude_deg")]
        actual = [float(truth[k]) for k in ("latitude_deg", "longitude_deg")]
        require(np.isfinite(gps).all() and abs(gps[0]) <= 90 and abs(gps[1]) <= 180, "Invalid predicted GPS")
        require([float(evaluated[k]) for k in ("actual_latitude_deg", "actual_longitude_deg")] == actual,
                "Evaluation truth differs")
        error = distance_m(gps, actual)
        require(abs(float(evaluated["position_error_m"]) - error) <= 1e-7, "Position error differs")
        errors[pred["model"]].append(error)
    metrics = {model: error_metrics(values) for model, values in errors.items()}
    for model in MODELS:
        require(set(state["metrics"][model]) == set(metrics[model]) and all(
            abs(state["metrics"][model][key] - value) <= 1e-7 for key, value in metrics[model].items()),
            "Shadow metrics differ")
    quality_result = {}
    if quality_policy is not None:
        gate = RelativeQualityGate(quality_policy, "v0001", cutoff)
        for index in range(quality_policy.minimum_evaluated_origins):
            group = evaluations[index * 3:(index + 1) * 3]
            gate.observe("v0001", group, int(group[0]["target_ms"]))
        require(quality_decision == gate.decision, "Quality decision differs from the first matured block")
        require(state["quality_decision"] == quality_decision["decision"]
                and state["quality_decision_sha256"] == sha256(output / "quality-decision.json"),
                "Quality decision hash/state differs")
        quality_result = dict(quality_result=quality_decision, quality_recomputation="PASSED")
    return dict(training_windows=32, trained_versions=1, shadow_origins=80, matched_origins=80,
                evaluation_rows=240, pending_forecasts=0, deferred_request_sizes=[96],
                training="COMPLETE", scaler="FROZEN", lifecycle_state="SHADOW", active=False,
                quality_gate=quality_kind, shadow_event_order="PASSED", error_recomputation="PASSED",
                metrics=metrics, **quality_result)


def audit_model(output, samples, tf):
    """Reload the saved bundle and recompute scaling from the causal training prefix."""
    directory = output / "models/v0001"
    model = OnlineLstm.load(tf, directory)
    require(model.metadata["reload_check"] == "PASSED" and model.metadata["epochs"] == 20
            and model.metadata["parameters"] == 5554 and model.metadata["request_number"] == 1
            and model.metadata["training_windows"] == 32
            and model.metadata["training_cutoff_sim_ms"] == int(samples[40]["time_ms"])
            and model.metadata["request_sha256"] == sha256(output / "training-request-0001.json")
            and model.metadata["active"] is False and model.metadata["quality_gate"] == "NOT_APPLIED",
            "Online model metadata differs")
    require(sorted(p.name for p in (output / "models").iterdir()) == ["v0001"], "Unexpected extra model version")
    history, columns = rows(directory / "history.csv")
    require(columns == ["epoch", "train_scaled_mse"] and [int(r["epoch"]) for r in history] == list(range(1, 21))
            and all(np.isfinite(float(r["train_scaled_mse"])) for r in history), "Training history differs")
    windows, _ = windows_for_run(dict(case_id="first-fit", route_id="official-strategy", split="development",
                                      run_id=output.name), samples, REFERENCE)
    x = np.asarray([w["x"] for w in windows], dtype=np.float64)
    y = np.asarray([w["y"] for w in windows], dtype=np.float64)
    scaler = fit_scaler(dict(x=x[:32], y=y[:32], records=[dict(w, split="train") for w in windows[:32]]))
    require(all(model.scaler[key] == value for key, value in scaler.items()), "Scaler fitted outside training prefix")
    selected = windows[32:]
    predictions, _ = rows(output / "first_fit_predictions.csv")
    lookup = {(r["model"], int(r["origin_ms"])): r for r in predictions}
    for w in selected:
        features = np.asarray([w["x"]], dtype=np.float64)
        anchor = np.asarray([[w["anchor_latitude_deg"], w["anchor_longitude_deg"]]])
        values = dict(baseline_displacements(features), LSTM=model.predict_delta(features))
        for name, value in values.items():
            gps = displacement_to_gps(value, anchor, REFERENCE)[0]
            logged = lookup[(name, w["origin_ms"])]
            require(np.allclose(gps, [float(logged["predicted_latitude_deg"]), float(logged["predicted_longitude_deg"])],
                                rtol=0, atol=1e-8), "Saved model replay differs")
    model.verify_frozen()
    return dict(scaler_prefix_audit="PASSED", saved_model_replay="PASSED", model_frozen="PASSED")


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--project-root", type=Path, default=Path(__file__).resolve().parents[3])
    parser.add_argument("--java", default="java")
    quality_args = parser.add_mutually_exclusive_group()
    quality_args.add_argument("--quality-gate", action="store_true", help="Enable the default 64-origin relative quality gate")
    quality_args.add_argument("--quality-policy", type=Path, help="Enable the gate with a QualityPolicy JSON file")
    args = parser.parse_args(argv)
    batch = state = None
    try:
        root = args.project_root.resolve()
        java = shutil.which(args.java)
        require(java is not None, "JDK Java not found")
        quality_policy = (QualityPolicy(**read_json(args.quality_policy)) if args.quality_policy else
                          QualityPolicy() if args.quality_gate else None)
        require(quality_policy is None or quality_policy.minimum_evaluated_origins <= 80,
                "This fixture has only 80 post-training origins; increase fixture duration before using a larger gate")
        result_kind = "mobility-online-quality-gate" if quality_policy is not None else "mobility-online-first-fit"
        manifest_name = "quality-gate.json" if quality_policy is not None else "online-first-fit.json"
        with contextlib.redirect_stdout(sys.stderr):
            tf = tensorflow_runtime()
        policy = TrainingPolicy()
        batch = root / "simulator/sim_res" / result_kind / (
            datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ") + "-" + uuid.uuid4().hex[:12])
        batch.mkdir(parents=True, exist_ok=False)
        state = dict(schema_version=1, status="RUNNING", mode="FIRST_FIT_SHADOW", started_at_utc=now(),
                     policy=asdict(policy), quality_gate=GATE_KIND if quality_policy is not None else "NOT_APPLIED",
                     quality_policy=asdict(quality_policy) if quality_policy is not None else None, active=False, runs=[])
        write_json(batch / manifest_name, state)
        print("Online first-fit checks: " + str(batch), flush=True)
        source = batch / "source"
        source.mkdir()
        for script in Path(__file__).parent.glob("*.py"):
            shutil.copy2(script, source / script.name)
        source_hashes = artifacts(source)
        write_json(batch / "source-sha256.json", source_hashes)
        classpath, xml = prepare(root, batch, java)
        for number, name in enumerate(("nomadic", "random-walk"), 1):
            print(f"[{number}/2] {name}: training after 32 completed windows; please wait", flush=True)
            case = batch / name
            case.mkdir()
            config = case / ("official-" + name + ".properties")
            shutil.copy2(root / RESOURCE / config.name, config)
            policy_file = case / "training-policy.json"
            write_json(policy_file, asdict(policy))
            environment = dict(os.environ)
            environment.pop("MOBILITY_ONLINE_QUALITY_POLICY", None)
            if quality_policy is not None:
                quality_file = case / "quality-policy.json"
                write_json(quality_file, asdict(quality_policy))
                environment["MOBILITY_ONLINE_QUALITY_POLICY"] = str(quality_file)
            environment.update(MOBILITY_SHADOW_PYTHON=sys.executable,
                               MOBILITY_SHADOW_SCRIPT=str(source / "mobility_online_first_fit_worker.py"),
                               MOBILITY_SHADOW_MODEL=str(policy_file), MOBILITY_SHADOW_REPLY_TIMEOUT_SECONDS="300",
                               PYTHONDONTWRITEBYTECODE="1")
            command = [java, "-cp", classpath, MAIN, "--no-ui", "--scenario=" + str(config),
                       str(xml), str(case / "output")]
            write_json(case / "command.json", command)
            require(execute_owned(command, root, case / "simulation.log", environment) == 0,
                    "Simulation failed; read " + str(case / "simulation.log"))
            outputs = list((case / "output").glob("mobility-*"))
            require(len(outputs) == 1, "Expected one isolated simulation output")
            output = outputs[0]
            movement, samples = validate_run(output, config)
            result = audit_online(output, samples, policy, worker_mode="FIRST_FIT_SHADOW", training="COMPLETE", scaler="FROZEN")
            result.update(audit_first_fit(output, samples, quality_policy), **audit_model(output, samples, tf))
            if quality_policy is not None:
                require(read_json(quality_file) == asdict(quality_policy), "Quality policy changed")
            require(read_json(output / "online-worker.json")["policy_sha256"] == sha256(policy_file), "Policy changed")
            state["runs"].append(dict(case=name, output=output.relative_to(batch).as_posix(),
                                      config_sha256=sha256(config), accounting=movement["accounting"],
                                      artifact_sha256=artifacts(output), **result))
            write_json(batch / manifest_name, state)
            print(f"[{number}/2] {name}: PASSED training_windows=32 shadow_origins=80 "
                  "evaluation_rows=240 deferred_request=96 active=false", flush=True)
            if quality_policy is not None:
                decision = result["quality_result"]
                print("  quality_decision=%s evaluated_origins=%d reference=%s reasons=%s" %
                      (decision["decision"], decision["evaluated_origins"], decision["reference_baseline"],
                       ",".join(decision["reason_codes"])), flush=True)
        require(source_hashes == artifacts(source), "Source changed during verification")
        for record in state["runs"]:
            require(record["artifact_sha256"] == artifacts(batch / record["output"]), "Run artifacts changed")
        state.update(status="COMPLETE", finished_at_utc=now(), training="COMPLETE", scaler="FROZEN")
        write_json(batch / manifest_name, state)
        print("strategies=2 trained_versions=2 shadow_origins=160 evaluation_rows=480 training=COMPLETE scaler=FROZEN")
        print("quality_gate=%s promotion=DISABLED retraining=DEFERRED" % state["quality_gate"])
        print("MOBILITY_ONLINE_QUALITY_GATE_OK" if quality_policy is not None else "MOBILITY_ONLINE_FIRST_FIT_OK")
        print("Manifest: " + str(batch / manifest_name))
        return 0
    except Exception as error:
        if batch is not None and state is not None:
            state.update(status="FAILED", error=str(error))
            write_json(batch / manifest_name, state)
        print("MOBILITY_ONLINE_FIRST_FIT_FAILED: " + str(error), file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
