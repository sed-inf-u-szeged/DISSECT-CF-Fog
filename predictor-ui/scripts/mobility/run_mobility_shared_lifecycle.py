"""Verify live shared-manager activation and fallback on both official strategies.

This 297-observation probe reuses the verified Python trainer/scheduler. The shared
Java manager owns activation, selection and fallback; connection control stays off.
"""
import argparse
import contextlib
from dataclasses import asdict
from datetime import datetime, timezone
import math
import os
from pathlib import Path
import shutil
import sys
import uuid

from build_mobility_windows import read_json, require, sha256, write_json
from check_mobility_run import rows
from mobility_learning import error_metrics
from mobility_online_buffer import TrainingPolicy
from mobility_online_lstm import tensorflow_runtime
from mobility_quality_gate import QualityPolicy
from mobility_shadow_evaluation import MODELS, distance_m
from run_mobility_batch import MAIN, now, prepare
from run_mobility_online_buffer import audit_online, jsonlines
from run_mobility_online_first_fit import artifacts
from run_mobility_online_retry import audit_retry, audit_retry_models
from run_mobility_shadow import execute_owned
from run_mobility_strategies import RESOURCE, validate_run

EXPECTED = 297


def same(actual, expected, context):
    """Strict structure with tiny cross-language floating-point tolerance."""
    if isinstance(expected, dict):
        require(isinstance(actual, dict) and actual.keys() == expected.keys(), context + ": fields differ")
        for key, value in expected.items():
            same(actual[key], value, context + "." + key)
    elif isinstance(expected, list):
        require(isinstance(actual, list) and len(actual) == len(expected), context + ": length differs")
        for index, value in enumerate(expected):
            same(actual[index], value, context + "[%d]" % index)
    elif isinstance(expected, float):
        require(type(actual) in (int, float) and math.isfinite(actual)
                and abs(actual - expected) <= 1e-7, context + ": numeric value differs")
    else:
        require(type(actual) is type(expected) and actual == expected, context + ": value differs")


def block_report(errors):
    metrics = {model: error_metrics(values[-64:]) for model, values in errors.items()}
    reference = min(MODELS[:2], key=lambda model:
                    (metrics[model]["rmse_m"], metrics[model]["mean_error_m"], MODELS.index(model)))
    passed = (metrics["LSTM"]["rmse_m"] < metrics[reference]["rmse_m"]
              and metrics["LSTM"]["mean_error_m"] <= metrics[reference]["mean_error_m"])
    report = dict(evaluated_origins=64, reference_baseline=reference,
                  metrics={model: {key: metrics[model][key] for key in ("mean_error_m", "rmse_m")}
                           for model in MODELS})
    return passed, report


def audit_shared(output, samples, training_records, *, closed_loop=False):
    """Replay Java ownership from actual targets, never from a saved active flag."""
    state = read_json(output / "shared-lifecycle.json")
    events = jsonlines(output / "shared_lifecycle_events.jsonl")
    predictions, _ = rows(output / "retry_predictions.csv")
    lookup = {(r["model_version"], int(r["origin_ms"]), r["model"]):
              [float(r["predicted_latitude_deg"]), float(r["predicted_longitude_deg"])] for r in predictions}
    run, device = samples[0]["run_id"], samples[0]["device_id"]
    feature = "mobility:%d:%s:%d:%s:position" % (len(run), run, len(device), device)
    baseline = feature + ":HoldPosition"
    owner = baseline
    cursor = promotions = fallbacks = selected_lstm = 0
    sessions = {}
    current = selected_pending = None
    selected_rows, evaluated_rows = [], []
    by_cutoff = {record["training_cutoff_sim_ms"]: record for record in training_records}
    require(len(by_cutoff) == len(training_records), "Duplicate training cutoff")
    controls = jsonlines(output / "training_controls.jsonl") if closed_loop else []
    if closed_loop:
        require(len(controls) == len(samples), "Missing or duplicate training control")
    training_window, last_attempt, degradation_retrainings = 32, -1, 0

    def event(kind, tick, **details):
        nonlocal cursor
        require(cursor < len(events), "Missing shared lifecycle event: " + kind)
        expected = dict(sequence=cursor + 1, event=kind, sim_time_ms=tick, run_id=run, device_id=device, **details)
        same(events[cursor], expected, "shared event %d" % (cursor + 1))
        cursor += 1

    for index, row in enumerate(samples):
        tick = int(row["time_ms"])
        actual = [float(row["latitude_deg"]), float(row["longitude_deg"])]
        event("OBSERVATION", tick, latitude_deg=actual[0], longitude_deg=actual[1])
        if selected_pending is not None:
            error = distance_m(selected_pending["point"], actual)
            event("SELECTED_EVALUATION", tick, origin_ms=tick - 60000, model=selected_pending["model"],
                  model_version=selected_pending["version"], position_error_m=error)
            evaluated_rows.append(dict(origin_ms=str(tick - 60000), target_ms=str(tick),
                                       model=selected_pending["model"], model_version=selected_pending["version"],
                                       actual_latitude_deg=actual[0], actual_longitude_deg=actual[1], position_error_m=error))
            selected_pending = None
        if index >= 41:
            session = sessions[current]
            for model in MODELS:
                session["errors"][model].append(distance_m(lookup[(current, tick - 60000, model)], actual))
            count = len(session["errors"]["LSTM"])
            event("EVALUATION", tick, model_version=current, origin_ms=tick - 60000,
                  errors_m=[session["errors"][m][-1] for m in MODELS], matched_origins=count)
            if session["decision"] is None and count == 64:
                passed, quality = block_report(session["errors"])
                session.update(decision="PASS" if passed else "REJECT", quality=quality, decided_at=tick)
                event("QUALITY_DECISION", tick, **quality, model_version=current, decision=session["decision"])
                if passed:
                    owner = session["predictor"]
                    promotions += 1
                    session["state"] = "ACTIVE"
                    event("PROMOTED", tick, model_version=current, active_predictor=owner)
                else:
                    session["state"] = "FAILED"
                    if closed_loop:
                        training_window = min(4096, training_window + 64)
                    event("REJECTED", tick, model_version=current)
            elif session["state"] == "ACTIVE":
                passed, quality = block_report(session["errors"])
                session["monitored"] += 1
                session["state"] = "ACTIVE" if passed else "DEGRADED"
                event("MONITORED", tick, **quality, model_version=current, state=session["state"])
                if not passed:
                    owner = baseline
                    fallbacks += 1
                    event("FALLBACK", tick, model_version=current, active_predictor=owner, reason="RELATIVE_QUALITY_DEGRADED")
        if closed_loop:
            available = max(0, index - 8)
            previous_state = sessions[current]["state"] if current else "NONE"
            retry = previous_state in ("FAILED", "DEGRADED")
            eligible = (index < len(samples) - 1 and available >= training_window
                        and (current is None or retry) and (last_attempt < 0 or available - last_attempt >= 64))
            reason = ("STREAM_COMPLETE" if index == len(samples) - 1 else
                      ("INITIAL" if current is None else "DEGRADATION" if previous_state == "DEGRADED" else "REJECTION") if eligible else
                      "WARMUP" if current is None else "ACTIVE" if previous_state == "ACTIVE" else
                      "WAITING_FOR_NEW_DATA" if retry else "EVALUATING")
            details = dict(action="TRAIN" if eligible else "HOLD", reason=reason, available_windows=available,
                           window_size=training_window, request_number=len(sessions) + 1 if eligible else 0,
                           model_version="v%04d" % (len(sessions) + 1) if eligible else "",
                           previous_version=current or "", previous_state=previous_state)
            event("CONTROL", tick, **details)
            same(controls[index], dict(details, sequence=index + 1, sim_time_ms=tick, run_id=run, device_id=device),
                 "Java training control")
            require(eligible == (tick in by_cutoff), "Training does not follow the shared schedule")
            if eligible:
                record = by_cutoff[tick]
                require(record["training_windows"] == training_window
                        and record["request_number"] == len(sessions) + 1
                        and record["model_version"] == details["model_version"]
                        and record["training_reason"] == reason, "Training command/result differ")
                last_attempt = available
                degradation_retrainings += reason == "DEGRADATION"
        if tick in by_cutoff:
            record = by_cutoff[tick]
            require(current is None or sessions[current]["state"] == "FAILED"
                    or (closed_loop and sessions[current]["state"] == "DEGRADED"), "Training did not follow shared rejection/fallback")
            current = record["model_version"]
            sessions[current] = dict(predictor=feature + ":LSTM:" + current, count=record["training_windows"],
                                     cutoff=tick, decision=None, quality=None, decided_at=-1, state="EVALUATING",
                                     errors={m: [] for m in MODELS}, monitored=0, selected=0)
            if closed_loop:
                sessions[current]["training_reason"] = record["training_reason"]
            event("TRAINING_STARTED", tick, model_version=current, training_windows=record["training_windows"])
            event("TRAINING_COMPLETE", tick, model_version=current, training_windows=record["training_windows"])
        if 40 <= index < len(samples) - 1:
            event("FORECAST", tick, model_version=current, target_ms=tick + 60000,
                  predictions=[dict(model=m, latitude_deg=lookup[(current, tick, m)][0],
                                    longitude_deg=lookup[(current, tick, m)][1]) for m in MODELS])
        if 8 <= index < len(samples) - 1:
            use_lstm = current is not None and owner == sessions[current]["predictor"]
            model, version = ("LSTM", current) if use_lstm else ("HoldPosition", "")
            point = lookup[(current, tick, "LSTM")] if use_lstm else actual
            event("SELECTED", tick, model=model, model_version=version, predictor_name=owner,
                  target_ms=tick + 60000, latitude_deg=point[0], longitude_deg=point[1])
            selected_rows.append(dict(origin_ms=str(tick), target_ms=str(tick + 60000), model=model, model_version=version,
                                      predictor_name=owner, predicted_latitude_deg=point[0], predicted_longitude_deg=point[1]))
            selected_pending = dict(model=model, version=version, point=point)
            if use_lstm:
                selected_lstm += 1
                sessions[current]["selected"] += 1
    event("COMPLETE", int(samples[-1]["time_ms"]))
    require(cursor == len(events) and selected_pending is None, "Extra events or unsettled selected forecast")
    for name, expected, numeric in (
            ("shared_selected_predictions.csv", selected_rows, ("predicted_latitude_deg", "predicted_longitude_deg")),
            ("shared_selected_evaluations.csv", evaluated_rows, ("actual_latitude_deg", "actual_longitude_deg", "position_error_m"))):
        logged, _ = rows(output / name)
        for item in logged:
            for key in numeric:
                item[key] = float(item[key])
        same(logged, expected, name)
    records = []
    for version, session in sessions.items():
        records.append(dict(model_version=version, predictor_name=session["predictor"], training_windows=session["count"],
                            training_cutoff_sim_ms=session["cutoff"], matched_origins=len(session["errors"]["LSTM"]),
                            quality_decision=session["decision"] or "WAITING_FOR_EVALUATION", decided_at_sim_ms=session["decided_at"],
                            quality=session["quality"], state=session["state"], monitor_evaluations=session["monitored"],
                            selected_origins=session["selected"]))
        if closed_loop:
            records[-1]["training_reason"] = session["training_reason"]
    expected_state = dict(schema_version=1, status="COMPLETE", run_id=run, device_id=device, feature_name=feature,
                          controller_integration="SHARED_COORDINATOR_AND_MANAGER", training_scheduler="PYTHON_MOBILITY",
                          retraining_after_degradation="DEFERRED", connection_control=False, observations=len(samples),
                          expected_observations=len(samples), selected_origins=len(samples) - 9, selected_matched=len(samples) - 9,
                          lstm_selected_origins=selected_lstm, promotions=promotions, fallbacks=fallbacks,
                          active_predictor=owner, active_lstm=owner != baseline, pending_forecasts=0, versions=records)
    if closed_loop:
        expected_state.update(training_scheduler="JAVA_SHARED_RULES", retraining_after_degradation="ENABLED",
                              degradation_retrainings=degradation_retrainings, training_window_selection="LATEST_MATURE_WINDOWS")
    same(state, expected_state, "shared final state")
    require([r["quality_decision"] for r in records] == [r["quality_decision"] for r in training_records],
            "Java/Python qualification decisions disagree")
    return dict(shared_versions=records, promotions=promotions, fallbacks=fallbacks,
                **(dict(degradation_retrainings=degradation_retrainings, training_schedule_audit="PASSED") if closed_loop else {}),
                active_lstm=owner != baseline, lstm_selected_origins=selected_lstm,
                selected_origins=len(selected_rows), selected_matched=len(evaluated_rows),
                shared_event_audit="PASSED", selected_prediction_audit="PASSED", shared_quality_recomputation="PASSED")


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--project-root", type=Path, default=Path(__file__).resolve().parents[3])
    parser.add_argument("--java", default="java")
    args = parser.parse_args(argv)
    batch = state = None
    try:
        root, java = args.project_root.resolve(), shutil.which(args.java)
        require(java is not None, "JDK Java not found")
        with contextlib.redirect_stdout(sys.stderr):
            tf = tensorflow_runtime()
        policy = TrainingPolicy()
        batch = root / "simulator/sim_res/mobility-shared-lifecycle" / (
            datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ") + "-" + uuid.uuid4().hex[:12])
        batch.mkdir(parents=True, exist_ok=False)
        state = dict(schema_version=1, status="RUNNING", started_at_utc=now(), expected_observations=EXPECTED,
                     training_policy=asdict(policy), quality_policy=asdict(QualityPolicy()),
                     controller_integration="SHARED_COORDINATOR_AND_MANAGER", training_scheduler="PYTHON_MOBILITY",
                     retraining_after_degradation="DEFERRED", connection_control=False, runs=[])
        manifest = batch / "shared-lifecycle.json"
        write_json(manifest, state)
        print("Shared lifecycle checks: " + str(batch), flush=True)
        source = batch / "source"
        source.mkdir()
        for script in Path(__file__).parent.glob("*.py"):
            shutil.copy2(script, source / script.name)
        source_hashes = artifacts(source)
        write_json(batch / "source-sha256.json", source_hashes)
        classpath, xml = prepare(root, batch, java)
        runtime_hashes = artifacts(batch / "runtime")
        for number, name in enumerate(("nomadic", "random-walk"), 1):
            print(f"[{number}/2] {name}: observations={EXPECTED}; shared promotion and monitoring; please wait", flush=True)
            case = batch / name
            case.mkdir()
            config = case / ("official-" + name + ".properties")
            shutil.copy2(root / RESOURCE / config.name, config)
            with config.open("a", encoding="utf-8") as stream:
                stream.write("\nduration_minutes=296\n")
            config_hash = sha256(config)
            policy_file = case / "training-policy.json"
            write_json(policy_file, asdict(policy))
            policy_hash = sha256(policy_file)
            environment = dict(os.environ)
            environment.pop("MOBILITY_ONLINE_QUALITY_POLICY", None)
            environment.update(MOBILITY_SHADOW_PYTHON=sys.executable,
                               MOBILITY_SHADOW_SCRIPT=str(source / "mobility_shared_lifecycle_worker.py"),
                               MOBILITY_SHADOW_MODEL=str(policy_file), MOBILITY_SHADOW_REPLY_TIMEOUT_SECONDS="300",
                               MOBILITY_ONLINE_EXPECTED_OBSERVATIONS=str(EXPECTED), PYTHONDONTWRITEBYTECODE="1")
            command = [java, "-cp", classpath, MAIN, "--no-ui", "--scenario=" + str(config), str(xml), str(case / "output")]
            write_json(case / "command.json", command)
            require(execute_owned(command, root, case / "simulation.log", environment) == 0,
                    "Simulation failed; read " + str(case / "simulation.log"))
            outputs = list((case / "output").glob("mobility-*"))
            require(len(outputs) == 1, "Expected one isolated output")
            output = outputs[0]
            movement, samples = validate_run(output, config)
            result = audit_online(output, samples, policy, worker_mode="RETRY_SHADOW", training="COMPLETE", scaler="FROZEN",
                                  expected_observations=EXPECTED)
            result.update(audit_retry(output, samples, EXPECTED))
            result.update(audit_retry_models(output, samples, result["versions"], tf))
            result.update(audit_shared(output, samples, result["versions"]))
            # The inherited 'active' field describes the Python shadow trainer only.
            result.pop("active", None)
            require(sha256(config) == config_hash and sha256(policy_file) == policy_hash
                    and read_json(output / "online-worker.json")["policy_sha256"] == policy_hash,
                    "Runtime scenario or policy changed")
            state["runs"].append(dict(case=name, output=output.relative_to(batch).as_posix(),
                                      accounting=movement["accounting"], artifact_sha256=artifacts(output), **result))
            write_json(manifest, state)
            for version in result["shared_versions"]:
                print("  %s training_windows=%d matched=%d decision=%s shared_state=%s selected=%d" %
                      (version["model_version"], version["training_windows"], version["matched_origins"],
                       version["quality_decision"], version["state"], version["selected_origins"]), flush=True)
            print(f"[{number}/2] {name}: PASSED promotions={result['promotions']} fallbacks={result['fallbacks']} "
                  f"selected_matched={result['selected_matched']} active_lstm={str(result['active_lstm']).lower()}", flush=True)
        require(source_hashes == artifacts(source) and runtime_hashes == artifacts(batch / "runtime"),
                "Source or compiled runtime changed")
        for record in state["runs"]:
            require(record["artifact_sha256"] == artifacts(batch / record["output"]), "Run artifacts changed")
        state.update(status="COMPLETE", finished_at_utc=now())
        write_json(manifest, state)
        print("controller_integration=SHARED_COORDINATOR_AND_MANAGER training_scheduler=PYTHON_MOBILITY")
        print("retraining_after_degradation=DEFERRED connection_control=DISABLED")
        print("MOBILITY_SHARED_LIFECYCLE_OK")
        print("Manifest: " + str(manifest))
        return 0
    except Exception as error:
        if batch is not None and state is not None:
            state.update(status="FAILED", error=str(error))
            write_json(batch / "shared-lifecycle.json", state)
        print("MOBILITY_SHARED_LIFECYCLE_FAILED: " + str(error), file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
