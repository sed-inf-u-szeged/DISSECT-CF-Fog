"""Verify growing LSTM retries and independent future evaluation on official movement."""
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
from build_mobility_windows import read_json, require, sha256, windows_for_run, write_json
from check_mobility_run import rows
from mobility_learning import baseline_displacements, displacement_to_gps, error_metrics, fit_scaler
from mobility_online_buffer import REFERENCE, TrainingPolicy
from mobility_online_lstm import OnlineLstm, tensorflow_runtime
from mobility_online_retry_worker import RETRY_PREDICTION_FIELDS, RETRY_EVALUATION_FIELDS
from mobility_quality_gate import GATE_KIND, QualityPolicy, RelativeQualityGate
from mobility_shadow_evaluation import MODELS, PREDICTION_FIELDS, distance_m
from run_mobility_batch import MAIN, now, prepare
from run_mobility_online_buffer import audit_online, jsonlines
from run_mobility_online_first_fit import artifacts
from run_mobility_shadow import execute_owned
from run_mobility_strategies import RESOURCE, validate_run


def audit_retry(output, samples, expected_observations=121):
    """Reconstruct version transitions from real targets, without trusting saved decisions."""
    require(len(samples) == expected_observations, "Expected the complete development stream")
    state = read_json(output / "retry.json")
    require(state["status"] == "COMPLETE" and state["mode"] == "RETRY_SHADOW"
            and state["quality_gate"] == GATE_KIND and state["training"] == "COMPLETE"
            and state["scaler"] == "FROZEN" and state["active"] is False
            and state["promotion"] == "DISABLED" and state["connection_control"] is False
            and state["expected_observations"] == expected_observations
            and state["controller_integration"] == "NOT_CONNECTED", "Retry state differs")
    predictions, header = rows(output / "retry_predictions.csv")
    evaluations, eval_header = rows(output / "retry_evaluations.csv")
    total = expected_observations - 41
    require(header == RETRY_PREDICTION_FIELDS and eval_header == RETRY_EVALUATION_FIELDS
            and len(predictions) == len(evaluations) == total * 3, "Retry prediction/evaluation schema or count differs")
    expected, sessions, deferred, decision_files = [], {}, [], []
    current = None
    def event(kind, tick, **details):
        expected.append(dict(sequence=len(expected) + 1, event=kind, sim_time_ms=tick, **details))
    for index, row in enumerate(samples):
        tick = int(row["time_ms"])
        event("OBSERVATION", tick, run_id=row["run_id"], device_id=row["device_id"])
        if index >= 41:
            session = sessions[current]
            offset = (index - 41) * 3
            group, scored = predictions[offset:offset + 3], evaluations[offset:offset + 3]
            require([p["model"] for p in group] == list(MODELS)
                    and [p["model"] for p in scored] == list(MODELS), "Missing or duplicated methods")
            for pred, evaluated in zip(group, scored):
                require(pred["model_version"] == evaluated["model_version"] == current
                        and int(pred["origin_ms"]) == int(samples[index - 1]["time_ms"])
                        and int(pred["target_ms"]) == tick, "Missing, premature or mixed-version prediction")
                source = samples[index - 1]
                require(pred["device_id"] == source["device_id"] and pred["origin_node"] == source["current_node"]
                        and evaluated["run_id"] == row["run_id"]
                        and {k: evaluated[k] for k in RETRY_PREDICTION_FIELDS} == pred
                        and int(evaluated["evaluated_at_sim_ms"]) == tick, "Retry evaluation identity/time differs")
                actual = [float(row[k]) for k in ("latitude_deg", "longitude_deg")]
                gps = [float(pred[k]) for k in ("predicted_latitude_deg", "predicted_longitude_deg")]
                require(np.isfinite(gps).all() and abs(gps[0]) <= 90 and abs(gps[1]) <= 180
                        and [float(evaluated[k]) for k in ("actual_latitude_deg", "actual_longitude_deg")] == actual,
                        "Retry evaluation coordinates/truth differ")
                error = distance_m(gps, actual)
                require(abs(error - float(evaluated["position_error_m"])) <= 1e-7, "Retry evaluation error differs")
                session["errors"][pred["model"]].append(error)
            n = len(session["errors"]["LSTM"])
            event("EVALUATION", tick, model_version=current, origin_ms=tick - 60000, target_ms=tick, matured_origins=n)
            gate = session["gate"]
            if gate.decision is None:
                decision = gate.observe(current, scored, tick)
                if decision is not None:
                    path = output / (current + "-quality-decision.json")
                    require(read_json(path) == decision, "Version quality decision differs from actual evaluations")
                    decision_files.append(path.name)
                    session["decision_sha256"] = sha256(path)
                    event("QUALITY_DECISION", tick, model_version=current, evaluated_origins=64,
                          decision=decision["decision"])
        if index >= 40 and (index - 40) % 64 == 0:
            number = 1 + (index - 40) // 64
            count = 32 + 64 * (number - 1)
            if current is None:
                should_fit = True
            else:
                decision = sessions[current]["gate"].decision
                require(decision is not None, "Retry request before quality evaluation completed")
                should_fit = index < expected_observations - 1 and decision["decision"] == "REJECT"
            if should_fit:
                current = "v%04d" % number
                sessions[current] = dict(request_number=number, training_windows=count, cutoff=tick,
                                         gate=RelativeQualityGate(QualityPolicy(), current, tick),
                                         errors={model: [] for model in MODELS})
                event("TRAINING_STARTED", tick, model_version=current, training_windows=count,
                      request_number=number, latest_label_ms=tick)
                event("TRAINING_COMPLETE", tick, model_version=current, training_windows=count, scaler="FROZEN")
            else:
                deferred.append(count)
                reason = "STREAM_COMPLETE" if index == expected_observations - 1 else "QUALITY_PASSED_PROMOTION_PENDING"
                event("TRAINING_REQUEST_DEFERRED", tick, model_version=current, training_windows=count, reason=reason)
        if 40 <= index < expected_observations - 1:
            event("SHADOW_PREDICTION", tick, model_version=current, target_ms=tick + 60000)
    require(jsonlines(output / "retry_events.jsonl") == expected, "Retry causal event order differs")
    require(sorted(p.name for p in output.glob("v*-quality-decision.json")) == sorted(decision_files),
            "Unexpected or premature later-version quality decision")
    records = []
    for version, session in sessions.items():
        gate, decision = session["gate"], session["gate"].decision
        n = len(session["errors"]["LSTM"])
        records.append(dict(model_version=version, request_number=session["request_number"],
                            training_windows=session["training_windows"], training_cutoff_sim_ms=session["cutoff"],
                            shadow_origins=n, matched_origins=n, evaluation_rows=n * 3,
                            quality_evaluated_origins=gate.count,
                            quality_decision=decision["decision"] if decision else "WAITING_FOR_EVALUATION",
                            decision_sha256=session.get("decision_sha256"), remaining_quality_origins=max(0, 64 - n),
                            active=False, metrics={m: error_metrics(session["errors"][m]) for m in MODELS}))
    require(state["versions"] == records, "Per-version counters or metrics differ")
    phase = {"REJECT": "RETRAIN_PENDING", "PASS": "QUALIFIED_SHADOW",
             "WAITING_FOR_EVALUATION": "SHADOW"}[records[-1]["quality_decision"]]
    retraining = "COMPLETE" if len(records) > 1 else "NOT_REQUIRED_BY_QUALITY"
    require(state["trained_versions"] == len(records) and state["current_version"] == current
            and state["shadow_origins"] == total and state["evaluation_rows"] == total * 3
            and state["pending_forecasts"] == 0 and state["deferred_request_sizes"] == deferred
            and state["lifecycle_state"] == phase and state["retraining"] == retraining,
            "Retry final counters/state differ")
    return dict(trained_versions=len(records), versions=records, retraining=retraining,
                current_version=current, lifecycle_state=phase, deferred_request_sizes=deferred,
                shadow_origins=total, evaluation_rows=total * 3, pending_forecasts=0, active=False,
                causal_event_order="PASSED", quality_recomputation="PASSED", error_recomputation="PASSED")


def audit_retry_models(output, samples, records, tf):
    """Independently reload every saved version and reconstruct only its fitted prefix."""
    windows, _ = windows_for_run(dict(case_id="retry", route_id="official-strategy", split="development",
                                      run_id=output.name), samples, REFERENCE, expected_observations=len(samples))
    require(sorted(p.name for p in (output / "models").iterdir()) == [r["model_version"] for r in records],
            "Unexpected saved model versions")
    predictions, _ = rows(output / "retry_predictions.csv")
    lookup = {(r["model_version"], r["model"], int(r["origin_ms"])): r for r in predictions}
    for record in records:
        version, count = record["model_version"], record["training_windows"]
        directory = output / "models" / version
        model = OnlineLstm.load(tf, directory)
        metadata = model.metadata
        require(metadata["version"] == version and metadata["request_number"] == record["request_number"]
                and metadata["training_windows"] == count
                and metadata["training_cutoff_sim_ms"] == record["training_cutoff_sim_ms"]
                and metadata["request_sha256"] == sha256(output / ("training-request-%04d.json" % record["request_number"]))
                and metadata["reload_check"] == "PASSED" and metadata["epochs"] == 20
                and metadata["parameters"] == 5554 and metadata["active"] is False, "Retry model metadata differs")
        history, columns = rows(directory / "history.csv")
        require(columns == ["epoch", "train_scaled_mse"] and [int(r["epoch"]) for r in history] == list(range(1, 21))
                and all(np.isfinite(float(r["train_scaled_mse"])) for r in history), "Retry training history differs")
        data = dict(x=np.asarray([w["x"] for w in windows[:count]]),
                    y=np.asarray([w["y"] for w in windows[:count]]),
                    records=[dict(w, split="train") for w in windows[:count]])
        scaler = fit_scaler(data)
        require(all(model.scaler[key] == value for key, value in scaler.items()), "Retry scaler prefix differs")
        selected = windows[count:count + record["shadow_origins"]]
        for w in selected:
            x = np.asarray([w["x"]], dtype=np.float64)
            anchor = np.asarray([[w["anchor_latitude_deg"], w["anchor_longitude_deg"]]])
            values = dict(baseline_displacements(x), LSTM=model.predict_delta(x))
            for name in MODELS:
                gps = displacement_to_gps(values[name], anchor, REFERENCE)[0]
                logged = lookup[(version, name, w["origin_ms"])]
                require(np.allclose(gps, [float(logged["predicted_latitude_deg"]),
                                         float(logged["predicted_longitude_deg"])], rtol=0, atol=1e-8),
                        "Retry saved model replay differs")
        model.verify_frozen()
    return dict(scaler_prefix_audit="PASSED", saved_model_replay="PASSED", model_versions_frozen="PASSED")


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--project-root", type=Path, default=Path(__file__).resolve().parents[3])
    parser.add_argument("--java", default="java")
    parser.add_argument("--long", action="store_true", help="Run 232 simulated minutes to evaluate three 64-origin blocks")
    args = parser.parse_args(argv)
    batch = state = None
    try:
        root, java = args.project_root.resolve(), shutil.which(args.java)
        require(java is not None, "JDK Java not found")
        with contextlib.redirect_stdout(sys.stderr):
            tf = tensorflow_runtime()
        policy = TrainingPolicy()
        expected_observations = 233 if args.long else 121
        batch = root / "simulator/sim_res" / ("mobility-online-long-run" if args.long else "mobility-online-retry") / (
            datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ") + "-" + uuid.uuid4().hex[:12])
        batch.mkdir(parents=True, exist_ok=False)
        state = dict(schema_version=1, status="RUNNING", started_at_utc=now(), mode="RETRY_SHADOW",
                     expected_observations=expected_observations, training_policy=asdict(policy), quality_policy=asdict(QualityPolicy()), quality_gate=GATE_KIND,
                     promotion="DISABLED", active=False, controller_integration="NOT_CONNECTED", runs=[])
        write_json(batch / "online-retry.json", state)
        print("Online retry checks: " + str(batch), flush=True)
        source = batch / "source"
        source.mkdir()
        for script in Path(__file__).parent.glob("*.py"):
            shutil.copy2(script, source / script.name)
        source_hashes = artifacts(source)
        write_json(batch / "source-sha256.json", source_hashes)
        classpath, xml = prepare(root, batch, java)
        for number, name in enumerate(("nomadic", "random-walk"), 1):
            print(f"[{number}/2] {name}: observations={expected_observations}; retry after REJECT; please wait", flush=True)
            case = batch / name
            case.mkdir()
            config = case / ("official-" + name + ".properties")
            shutil.copy2(root / RESOURCE / config.name, config)
            if args.long:
                with config.open("a", encoding="utf-8") as stream:
                    stream.write("\nduration_minutes=232\n")
            config_hash = sha256(config)
            policy_file = case / "training-policy.json"
            write_json(policy_file, asdict(policy))
            policy_hash = sha256(policy_file)
            environment = dict(os.environ)
            environment.pop("MOBILITY_ONLINE_QUALITY_POLICY", None)
            environment.update(MOBILITY_SHADOW_PYTHON=sys.executable,
                               MOBILITY_SHADOW_SCRIPT=str(source / "mobility_online_retry_worker.py"),
                               MOBILITY_SHADOW_MODEL=str(policy_file), MOBILITY_SHADOW_REPLY_TIMEOUT_SECONDS="300",
                               MOBILITY_ONLINE_EXPECTED_OBSERVATIONS=str(expected_observations),
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
            result = audit_online(output, samples, policy, worker_mode="RETRY_SHADOW", training="COMPLETE", scaler="FROZEN",
                                  expected_observations=expected_observations)
            result.update(audit_retry(output, samples, expected_observations))
            result.update(audit_retry_models(output, samples, result["versions"], tf))
            require(sha256(config) == config_hash and sha256(policy_file) == policy_hash
                    and read_json(output / "online-worker.json")["policy_sha256"] == policy_hash,
                    "Runtime scenario or policy changed")
            state["runs"].append(dict(case=name, output=output.relative_to(batch).as_posix(),
                                      config_sha256=config_hash, accounting=movement["accounting"],
                                      artifact_sha256=artifacts(output), **result))
            write_json(batch / "online-retry.json", state)
            for version in result["versions"]:
                print("  %s training_windows=%d evaluated_origins=%d quality_decision=%s remaining=%d" %
                      (version["model_version"], version["training_windows"], version["matched_origins"],
                       version["quality_decision"], version["remaining_quality_origins"]), flush=True)
            print(f"[{number}/2] {name}: PASSED trained_versions={result['trained_versions']} "
                  f"shadow_origins={result['shadow_origins']} evaluation_rows={result['evaluation_rows']} active=false", flush=True)
        require(source_hashes == artifacts(source), "Source changed during verification")
        for record in state["runs"]:
            require(record["artifact_sha256"] == artifacts(batch / record["output"]), "Run artifacts changed")
        state.update(status="COMPLETE", finished_at_utc=now(), training="COMPLETE", scaler="FROZEN_PER_VERSION")
        write_json(batch / "online-retry.json", state)
        print("strategies=2 trained_versions=%d shadow_origins=%d evaluation_rows=%d" %
              (sum(r["trained_versions"] for r in state["runs"]),
               sum(r["shadow_origins"] for r in state["runs"]), sum(r["evaluation_rows"] for r in state["runs"])))
        print("promotion=DISABLED connection_control=DISABLED controller_integration=NOT_CONNECTED")
        print("MOBILITY_ONLINE_LONG_RUN_OK" if args.long else "MOBILITY_ONLINE_RETRY_OK")
        print("Manifest: " + str(batch / "online-retry.json"))
        return 0
    except Exception as error:
        if batch is not None and state is not None:
            state.update(status="FAILED", error=str(error))
            write_json(batch / "online-retry.json", state)
        print("MOBILITY_ONLINE_RETRY_FAILED: " + str(error), file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
