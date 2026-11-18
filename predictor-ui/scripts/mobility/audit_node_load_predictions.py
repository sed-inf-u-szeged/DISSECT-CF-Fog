"""Reconstruct one-step persistence forecasts and target matching from node samples."""
import argparse
import csv
import json
import math
from pathlib import Path

from audit_node_load_samples import HEADER as SAMPLE_HEADER, PHASE, require

PREDICTION_HEADER = ["run_id", "node_id", "task", "model", "model_version", "target_metric", "unit",
                     "origin_sample_index", "last_input_time_ms", "origin_time_ms", "target_time_ms",
                     "horizon_ms", "predicted_waiting_assignment_bytes", "inference_duration_ns"]
EVALUATION_HEADER = PREDICTION_HEADER + ["actual_sample_index", "actual_time_ms",
                                      "actual_waiting_assignment_bytes", "error_bytes", "absolute_error_bytes"]


def read_rows(path, header):
    with path.open(encoding="utf-8-sig", newline="") as stream:
        reader = csv.DictReader(stream)
        require(reader.fieldnames == header, "Wrong columns: " + path.name)
        result = list(reader)
    require(all(None not in row and None not in row.values() for row in result), "Malformed CSV row")
    return result


def metrics(predictions, errors):
    n = len(errors)
    return dict(generated=len(predictions), matched=n, unmatched=len(predictions) - n,
                mae_bytes=sum(abs(x) for x in errors) / n if n else None,
                rmse_bytes=math.sqrt(sum(x * x for x in errors) / n) if n else None,
                mean_error_bytes=sum(errors) / n if n else None,
                mean_inference_ms=sum(int(p["inference_duration_ns"]) for p in predictions)
                / len(predictions) / 1e6 if predictions else None)


def check_metrics(actual, expected):
    for key, value in expected.items():
        recorded = actual[key]
        require(recorded is None if value is None else
                type(recorded) in (int, float) and math.isfinite(recorded)
                and math.isclose(recorded, value, rel_tol=1e-10, abs_tol=1e-10),
                "Incorrect summary: " + key)


def audit(output):
    output = Path(output)
    meta = json.loads((output / "node_load_sampling.json").read_text(encoding="utf-8"))
    summary = json.loads((output / "node_load_prediction_summary.json").read_text(encoding="utf-8"))
    nodes, count, interval = meta["node_ids"], meta["sample_count_per_node"], meta["interval_ms"]
    require(nodes and len(nodes) == len(set(nodes)) and count > 0 and interval > 0, "Invalid sample grid")
    require(meta["schema_version"] == 1 and meta["sample_phase"] == PHASE and meta["start_ms"] == 0,
            "Unknown sampling contract")
    require(meta["last_sample_ms"] == (count - 1) * interval <= meta["sample_until_ms"]
            and meta["last_sample_ms"] <= meta["simulation_end_ms"]
            and count * interval > min(meta["sample_until_ms"], meta["simulation_end_ms"]), "Invalid sampling end")
    samples = read_rows(output / "node_load_samples.csv", SAMPLE_HEADER)
    predictions = read_rows(output / "node_load_predictions.csv", PREDICTION_HEADER)
    evaluations = read_rows(output / "node_load_evaluations.csv", EVALUATION_HEADER)
    require(len(samples) == meta["rows"] == len(predictions) == count * len(nodes), "Wrong prediction/sample count")
    require(len(evaluations) == (count - 1) * len(nodes), "Missing or invented target evaluations")
    common = dict(run_id=meta["run_id"], task="node_load", model="Persistence", model_version="fixed-v1",
                  target_metric="waiting_assignment_bytes", unit="B")
    by_node = {node: [] for node in nodes}
    errors = {node: [] for node in nodes}
    for position, (sample, prediction) in enumerate(zip(samples, predictions)):
        index, node = position // len(nodes), nodes[position % len(nodes)]
        time = index * interval
        require((sample["run_id"], sample["node_id"], sample["sample_phase"], sample["sample_index"], sample["time_ms"])
                == (meta["run_id"], node, PHASE, str(index), str(time)), "Mixed, missing or duplicate source sample")
        waiting = int(sample["waiting_assignment_bytes"])
        require(waiting >= 0, "Negative waiting bytes")
        expected = dict(common, node_id=node, origin_sample_index=str(index), last_input_time_ms=str(time),
                        origin_time_ms=str(time), target_time_ms=str(time + interval), horizon_ms=str(interval),
                        predicted_waiting_assignment_bytes=str(waiting))
        require(all(prediction[key] == value for key, value in expected.items()),
                "Forecast differs from origin observation or causal time identity")
        require(int(prediction["inference_duration_ns"]) >= 0, "Invalid inference duration")
        by_node[node].append(prediction)
        if index:
            previous = predictions[position - len(nodes)]
            error = int(previous["predicted_waiting_assignment_bytes"]) - waiting
            expected_evaluation = dict(previous, actual_sample_index=str(index), actual_time_ms=str(time),
                                       actual_waiting_assignment_bytes=str(waiting), error_bytes=str(error),
                                       absolute_error_bytes=str(abs(error)))
            require(evaluations[position - len(nodes)] == expected_evaluation,
                    "Evaluation target, model, node or error differs")
            errors[node].append(error)
    require(all(summary[key] == value for key, value in common.items()), "Wrong summary identity")
    require(summary["schema_version"] == 1 and summary["horizon_ms"] == interval
            and summary["sample_phase"] == PHASE and summary["role"] == "BASELINE_COMPARISON"
            and summary["controls_simulation"] is False and summary["qualification_required"] is False
            and summary["execution_mode"] == "SIMULATION_CALLBACK"
            and summary["evaluation_scope"] == "ALL_MATCHED_GRID_TARGETS_INCLUDING_STARTUP"
            and summary["inference_timing_scope"] == "PREDICT_CALL_AND_CLOCK_OVERHEAD_EXCLUDING_IO",
            "Wrong baseline scope")
    require([row["node_id"] for row in summary["nodes"]] == nodes, "Wrong summary nodes")
    for node, row in zip(nodes, summary["nodes"]):
        check_metrics(row, metrics(by_node[node], errors[node]))
        require(row["last_origin_time_ms"] == (count - 1) * interval
                and row["pending_target_time_ms"] == count * interval, "Tail target incorrectly closed")
    check_metrics(summary["aggregate"], metrics(predictions, [e for node in nodes for e in errors[node]]))
    return dict(nodes=len(nodes), generated=len(predictions), matched=len(evaluations),
                unmatched=len(predictions) - len(evaluations), horizon_ms=interval,
                mae_bytes=summary["aggregate"]["mae_bytes"], rmse_bytes=summary["aggregate"]["rmse_bytes"])


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("run", type=Path, help="The mobility run root, containing node_load_samples.csv")
    args = parser.parse_args()
    print("NODE_LOAD_BASELINE_OK " + json.dumps(audit(args.run), sort_keys=True))
