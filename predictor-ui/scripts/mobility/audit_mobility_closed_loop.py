"""Independent artifact audits for Java-controlled mobility training."""
import numpy as np

from build_mobility_windows import FEATURES, read_json, require, sha256, windows_for_run
from check_mobility_run import rows
from mobility_learning import baseline_displacements, displacement_to_gps, error_metrics, fit_scaler
from mobility_online_buffer import REFERENCE
from mobility_online_lstm import OnlineLstm
from mobility_online_retry_worker import RETRY_PREDICTION_FIELDS, RETRY_EVALUATION_FIELDS
from mobility_quality_gate import GATE_KIND, QualityPolicy, RelativeQualityGate
from mobility_shadow_evaluation import MODELS, distance_m
from run_mobility_online_buffer import jsonlines


def audit_closed_worker(output, samples):
    state = read_json(output / "retry.json")
    require(state["status"] == "COMPLETE" and state["mode"] == "JAVA_SCHEDULED"
            and state["training"] == "COMPLETE" and state["scaler"] == "FROZEN"
            and state["quality_gate"] == GATE_KIND and state["active"] is False
            and state["promotion"] == "JAVA_OWNED" and state["connection_control"] is False
            and state["training_scheduler"] == "JAVA_SHARED_RULES"
            and state["controller_integration"] == "SHARED_COORDINATOR_AND_MANAGER"
            and state["training_window_selection"] == "LATEST_MATURE_WINDOWS"
            and state["expected_observations"] == len(samples), "Closed-loop worker state differs")
    records = state["versions"]
    require(records and [r["model_version"] for r in records] == ["v%04d" % i for i in range(1, len(records) + 1)]
            and [r["request_number"] for r in records] == list(range(1, len(records) + 1)), "Version sequence differs")
    cutoffs = {r["training_cutoff_sim_ms"]: r for r in records}
    require(len(cutoffs) == len(records), "Duplicate fit cutoff")
    completed = jsonlines(output / "online_samples.jsonl")  # Independently checked by audit_online.
    controls = jsonlines(output / "training_controls.jsonl")
    by_control = {r["sim_time_ms"]: r for r in controls}
    require(len(by_control) == len(samples), "Control sequence differs")
    expected_snapshots = []
    for record in records:
        number, count, cutoff = record["request_number"], record["training_windows"], record["training_cutoff_sim_ms"]
        name = "scheduled-request-%04d.json" % number
        require(record["snapshot_file"] == name and record["snapshot_sha256"] == sha256(output / name),
                "Scheduled snapshot path/hash differs")
        require(type(count) is int and 32 <= count <= 4096, "Invalid scheduled window count")
        mature = [w for w in completed if w["completed_at_sim_ms"] <= cutoff][-count:]
        require(len(mature) == count and mature[-1]["completed_at_sim_ms"] == cutoff,
                "Unavailable mature window")
        expected = dict(request_number=number, requested_at_sim_ms=cutoff, training_windows=count,
                        first_origin_ms=mature[0]["origin_ms"], last_origin_ms=mature[-1]["origin_ms"], latest_label_ms=cutoff,
                        training="NOT_STARTED", scaler="NOT_FITTED", mode="REQUEST_ONLY", feature_names=FEATURES,
                        target_names=["delta_east_m", "delta_north_m"], window_selection="LATEST_MATURE_WINDOWS",
                        java_command=by_control[cutoff], windows=mature)
        require(read_json(output / name) == expected, "Snapshot is not the exact latest mature window")
        require(record["training_reason"] == by_control[cutoff]["reason"], "Fit reason differs")
        expected_snapshots.append(name)
    require(sorted(p.name for p in output.glob("scheduled-request-*.json")) == expected_snapshots,
            "Extra scheduled request snapshot")
    predictions, header = rows(output / "retry_predictions.csv")
    evaluations, eval_header = rows(output / "retry_evaluations.csv")
    total = len(samples) - 41
    require(header == RETRY_PREDICTION_FIELDS and eval_header == RETRY_EVALUATION_FIELDS
            and len(predictions) == len(evaluations) == total * 3, "Closed-loop forecast count/schema differs")
    events, sessions, current, decision_files = [], {}, None, []

    def event(kind, tick, **details):
        events.append(dict(sequence=len(events) + 1, event=kind, sim_time_ms=tick, **details))

    for index, row in enumerate(samples):
        tick = int(row["time_ms"])
        event("OBSERVATION", tick, run_id=row["run_id"], device_id=row["device_id"])
        if index >= 41:
            session = sessions[current]
            offset = (index - 41) * 3
            group, scored = predictions[offset:offset + 3], evaluations[offset:offset + 3]
            require([p["model"] for p in group] == list(MODELS) == [r["model"] for r in scored],
                    "Missing or duplicate model forecast")
            for prediction, evaluated in zip(group, scored):
                require(prediction["model_version"] == evaluated["model_version"] == current
                        and int(prediction["origin_ms"]) == int(samples[index - 1]["time_ms"])
                        and int(prediction["target_ms"]) == int(evaluated["evaluated_at_sim_ms"]) == tick,
                        "Mixed version or premature target")
                source = samples[index - 1]
                require(prediction["device_id"] == source["device_id"] and prediction["origin_node"] == source["current_node"]
                        and evaluated["run_id"] == row["run_id"]
                        and {k: evaluated[k] for k in RETRY_PREDICTION_FIELDS} == prediction, "Forecast identity differs")
                actual = [float(row[k]) for k in ("latitude_deg", "longitude_deg")]
                gps = [float(prediction[k]) for k in ("predicted_latitude_deg", "predicted_longitude_deg")]
                require(np.isfinite(gps).all() and abs(gps[0]) <= 90 and abs(gps[1]) <= 180
                        and [float(evaluated[k]) for k in ("actual_latitude_deg", "actual_longitude_deg")] == actual,
                        "Invalid forecast or changed truth")
                error = distance_m(gps, actual)
                require(abs(error - float(evaluated["position_error_m"])) <= 1e-7, "Position error differs")
                session["errors"][prediction["model"]].append(error)
            count = len(session["errors"]["LSTM"])
            event("EVALUATION", tick, model_version=current, origin_ms=tick - 60000, target_ms=tick, matured_origins=count)
            gate = session["gate"]
            if gate.decision is None:
                decision = gate.observe(current, scored, tick)
                if decision is not None:
                    path = output / (current + "-quality-decision.json")
                    require(read_json(path) == decision, "Frozen qualification decision differs")
                    decision_files.append(path.name)
                    event("QUALITY_DECISION", tick, model_version=current, evaluated_origins=64, decision=decision["decision"])
        if tick in cutoffs:
            record = cutoffs[tick]
            current = record["model_version"]
            sessions[current] = dict(gate=RelativeQualityGate(QualityPolicy(), current, tick),
                                     errors={m: [] for m in MODELS})
            event("TRAINING_STARTED", tick, model_version=current, training_windows=record["training_windows"],
                  request_number=record["request_number"], latest_label_ms=tick)
            event("TRAINING_COMPLETE", tick, model_version=current, training_windows=record["training_windows"], scaler="FROZEN")
        if 40 <= index < len(samples) - 1:
            require(current is not None, "Missing first fit")
            event("SHADOW_PREDICTION", tick, model_version=current, target_ms=tick + 60000)
    require(jsonlines(output / "retry_events.jsonl") == events, "Worker causal event order differs")
    require(sorted(p.name for p in output.glob("v*-quality-decision.json")) == sorted(decision_files),
            "Unexpected qualification artifact")
    for record in records:
        session = sessions[record["model_version"]]
        count, decision = len(session["errors"]["LSTM"]), session["gate"].decision
        require(record["matched_origins"] == count and record["remaining_quality_origins"] == max(0, 64 - count)
                and record["quality_decision"] == (decision["decision"] if decision else "WAITING_FOR_EVALUATION")
                and record["metrics"] == {m: error_metrics(session["errors"][m]) for m in MODELS},
                "Per-version independent evaluation differs")
    require(state["trained_versions"] == len(records) and state["current_version"] == current
            and state["shadow_origins"] == total and state["evaluation_rows"] == total * 3
            and state["pending_forecasts"] == 0, "Final worker counters differ")
    return dict(versions=records, trained_versions=len(records), shadow_origins=total, evaluation_rows=total * 3,
                causal_event_order="PASSED", training_snapshot_audit="PASSED", error_recomputation="PASSED")


def audit_closed_models(output, samples, records, tf):
    windows, _ = windows_for_run(dict(case_id="closed-loop", route_id="official-strategy", split="development",
                                      run_id=output.name), samples, REFERENCE, expected_observations=len(samples))
    require(sorted(p.name for p in (output / "models").iterdir()) == [r["model_version"] for r in records],
            "Unexpected model directory")
    predictions, _ = rows(output / "retry_predictions.csv")
    lookup = {(r["model_version"], r["model"], int(r["origin_ms"])): r for r in predictions}
    require(len(lookup) == len(predictions), "Duplicate saved prediction")
    for record in records:
        version, count, cutoff = record["model_version"], record["training_windows"], record["training_cutoff_sim_ms"]
        directory = output / "models" / version
        model = OnlineLstm.load(tf, directory)
        metadata = model.metadata
        require(metadata["version"] == version and metadata["request_number"] == record["request_number"]
                and metadata["training_windows"] == count and metadata["training_cutoff_sim_ms"] == cutoff
                and metadata["request_sha256"] == record["snapshot_sha256"]
                and metadata["reload_check"] == "PASSED" and metadata["epochs"] == 20
                and metadata["batch_size"] == 32 and metadata["seed"] == 42
                and metadata["parameters"] == 5554 and metadata["active"] is False
                and metadata["scaler_fit"] == "LATEST_MATURE_REQUEST_ONLY", "Model provenance differs")
        history, columns = rows(directory / "history.csv")
        require(columns == ["epoch", "train_scaled_mse"] and [int(r["epoch"]) for r in history] == list(range(1, 21))
                and all(np.isfinite(float(r["train_scaled_mse"])) for r in history), "Training history differs")
        mature = [w for w in windows if w["target_ms"] <= cutoff][-count:]
        data = dict(x=np.asarray([w["x"] for w in mature]), y=np.asarray([w["y"] for w in mature]),
                    records=[dict(w, split="train") for w in mature])
        scaler = fit_scaler(data)
        require(all(model.scaler[k] == value for k, value in scaler.items()), "Scaler saw wrong or future data")
        selected = [w for w in windows if w["origin_ms"] >= cutoff][:record["matched_origins"]]
        require(len(selected) == record["matched_origins"], "Saved model forecast range differs")
        for window in selected:
            x = np.asarray([window["x"]], dtype=np.float64)
            anchor = np.asarray([[window["anchor_latitude_deg"], window["anchor_longitude_deg"]]])
            values = dict(baseline_displacements(x), LSTM=model.predict_delta(x))
            for name in MODELS:
                gps = displacement_to_gps(values[name], anchor, REFERENCE)[0]
                logged = lookup[(version, name, window["origin_ms"])]
                require(np.allclose(gps, [float(logged["predicted_latitude_deg"]), float(logged["predicted_longitude_deg"])],
                                    rtol=0, atol=1e-8), "Saved model/scaler replay differs")
        model.verify_frozen()
    return dict(scaler_window_audit="PASSED", saved_model_replay="PASSED", model_versions_frozen="PASSED")
