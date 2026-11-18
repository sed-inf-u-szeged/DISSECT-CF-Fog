"""Validate pilot exports for dataset collection, without fitting/evaluating learned models."""
import csv
import hashlib
import math
from pathlib import Path

from check_dataset_plan import read_properties, require

FILES = ("run.properties", "scenario-effective.properties", "route_waypoints.csv",
         "mobility_samples.csv", "observations.csv", "predictions.csv", "evaluations.csv",
         "node_load.csv", "offload_events.csv", "coverage_summary.csv")
HEADER = "run_id,device_id,time_ms,latitude_deg,longitude_deg,delta_time_s,displacement_m,observed_speed_m_s,heading_deg,motion_valid,heading_valid,current_node".split(",")


def sha256(path):
    digest = hashlib.sha256()
    with Path(path).open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def rows(path):
    with path.open(encoding="utf-8-sig", newline="") as stream:
        reader = csv.DictReader(stream)
        result = list(reader)
        require(reader.fieldnames is not None and len(set(reader.fieldnames)) == len(reader.fieldnames), "Invalid CSV header: " + path.name)
        require(all(None not in r and all(v is not None for v in r.values()) for r in result), "Malformed CSV: " + path.name)
        return result, reader.fieldnames


def properties(path):
    result = {}
    for line in path.read_text(encoding="utf-8-sig").splitlines():
        if not line.strip() or line.lstrip().startswith("#"):
            continue
        key, value = line.split("=", 1)
        require(key not in result, "Duplicate run property: " + key)
        result[key] = value
    return result


def motion(a, b):
    lat1, lat2 = (math.radians(float(r["latitude_deg"])) for r in (a, b))
    dl = math.radians(float(b["longitude_deg"]) - float(a["longitude_deg"]))
    y = math.sin(dl) * math.cos(lat2)
    x = math.cos(lat1) * math.sin(lat2) - math.sin(lat1) * math.cos(lat2) * math.cos(dl)
    dot = math.sin(lat1) * math.sin(lat2) + math.cos(lat1) * math.cos(lat2) * math.cos(dl)
    distance = 6378137.0 * math.atan2(math.hypot(x, y), dot)
    return distance, math.degrees(math.atan2(y, x)) % 360


def validate_run(directory, case, config):
    directory = Path(directory)
    require(all((directory / f).is_file() for f in FILES), "Missing exported file")
    prop = properties(directory / "run.properties")
    require(prop["schema_version"] == "2" and prop["sample_schema_version"] == "1", "Unexpected schema")
    require(prop["scenario_name"] == case["case_id"] and int(prop["seed"]) == case["seed"] and float(prop["speed_m_s"]) == case["speed_m_s"], "Scenario identity differs")
    require(prop["accounting_check"] == "passed", "Simulator accounting failed")
    require(prop["sample_file"] == "mobility_samples.csv" and int(prop["interval_ms"]) == 60000 and int(prop["nominal_generation_duration_ms"]) == 7200000, "Sampling protocol differs")
    require(prop["sample_speed_kind"] == "endpoint_displacement_over_elapsed_time" and prop["sample_heading_kind"] == "initial_bearing_previous_to_current", "Motion definitions differ")
    original, effective = read_properties(config), read_properties(directory / "scenario-effective.properties")
    require(original["scenario_name"] == effective["scenario_name"] and int(original["seed"]) == int(effective["seed"]) and float(original["speed_m_s"]) == float(effective["speed_m_s"]), "Effective config differs")
    points = lambda p: [[float(x) for x in row.split(",")] for row in p["waypoints_m"].split(";")]
    require(points(original) == points(effective), "Effective route differs")
    waypoints, _ = rows(directory / "route_waypoints.csv")
    require([[float(r["east_m"]), float(r["north_m"])] for r in waypoints] == points(original), "Exported waypoint route differs")
    samples, header = rows(directory / "mobility_samples.csv")
    require(header == HEADER, "Sample header differs")
    require(len(samples) == int(prop["sample_rows"]) == 121, "Expected 121 observations for this pilot")
    observations, _ = rows(directory / "observations.csv")
    if prop.get("sample_window_policy") == "fixed_121_observations":
        require(len(observations) == int(prop["observation_rows"]) >= len(samples), "Observation count differs")
        require(int(prop["drain_observation_rows"]) == len(observations) - len(samples), "Drain observation count differs")
        require(int(prop["sample_window_start_ms"]) == int(samples[0]["time_ms"])
                and int(prop["sample_window_end_ms"]) == int(samples[-1]["time_ms"]), "Sample window bounds differ")
    else:
        require(len(observations) == len(samples), "Observation count differs")
    for i, obs in enumerate(observations):
        require(obs["device_id"] == "0" and obs["current_node"] in ("NONE", "mobility-west", "mobility-east"), "Invalid replay identity")
        require(i == 0 or int(obs["time_ms"]) - int(observations[i - 1]["time_ms"]) == 60000, "Replay sampling gap")
        require(all(math.isfinite(float(obs[k])) and abs(float(obs[k])) <= limit
                    for k, limit in (("latitude", 90), ("longitude", 180))), "Invalid replay coordinates")
    for i, (r, obs) in enumerate(zip(samples, observations)):
        require(r["run_id"] == directory.name and r["device_id"] == obs["device_id"] == "0", "Run/device identity differs")
        require(r["current_node"] == obs["current_node"] and r["current_node"] in ("NONE", "mobility-west", "mobility-east"), "Connection label differs")
        require(int(r["time_ms"]) == int(obs["time_ms"]), "Observation time differs")
        for sample_key, obs_key, limit in (("latitude_deg", "latitude", 90), ("longitude_deg", "longitude", 180)):
            value = float(r[sample_key])
            require(math.isfinite(value) and abs(value) <= limit and value == float(obs[obs_key]), "Position is invalid or differs")
        if i == 0:
            require(r["motion_valid"] == r["heading_valid"] == "false" and all(r[k] == "" for k in HEADER[5:9]), "Initial motion must be unavailable")
            continue
        require(int(r["time_ms"]) - int(samples[i - 1]["time_ms"]) == 60000, "Non-contiguous sampling")
        require(r["motion_valid"] == "true" and float(r["delta_time_s"]) == 60, "Invalid motion interval")
        distance, heading = motion(samples[i - 1], r)
        require(abs(float(r["displacement_m"]) - distance) <= 1e-5 and abs(float(r["observed_speed_m_s"]) - distance / 60) <= 1e-6, "Derived speed/displacement differs")
        if distance > 1e-6:
            require(r["heading_valid"] == "true" and abs((float(r["heading_deg"]) - heading + 180) % 360 - 180) < 1e-5, "Derived heading differs")
        else:
            require(r["heading_valid"] == "false" and r["heading_deg"] == "", "Stationary heading must be unavailable")
    require(int(samples[-1]["time_ms"]) - int(samples[0]["time_ms"]) == 7200000, "Observed duration differs")
    predictions, _ = rows(directory / "predictions.csv")
    evaluations, _ = rows(directory / "evaluations.csv")
    require(len(predictions) == len(evaluations) == int(prop["issued"]) == int(prop["matched"]), "Forecast matching incomplete")
    times = {int(r["time_ms"]): r for r in samples}
    seen = set()
    for p, e in zip(predictions, evaluations):
        origin, target = int(p["origin_ms"]), int(p["target_ms"])
        require(origin not in seen and target == origin + 60000 and origin in times and target in times, "Invalid forecast times")
        seen.add(origin)
        require(p["device_id"] == e["device_id"] == "0" and p["origin_node"] == e["origin_node"] == times[origin]["current_node"] != "NONE", "Forecast origin differs")
        require(int(e["origin_ms"]) == origin and int(e["target_ms"]) == target, "Evaluation join differs")
        require(float(e["actual_latitude"]) == float(times[target]["latitude_deg"]) and float(e["actual_longitude"]) == float(times[target]["longitude_deg"]), "Evaluation actual position differs")
    load, _ = rows(directory / "node_load.csv")
    final = [r for r in load if r["sample_kind"] == "FINAL"]
    require(len(final) == 2 and {r["node"] for r in final} == {"mobility-west", "mobility-east"}, "Missing final node settlement")
    require(len({r["time_ms"] for r in final}) == 1 and int(final[0]["time_ms"]) >= int(samples[-1]["time_ms"]), "Invalid settlement time")
    for r in final:
        require(all(int(r[k]) == 0 for k in ("waiting_assignment_bytes", "running_tasks", "incoming_offload_transfers", "offload_in_flight_bytes_total")), "Final work has not drained")
        require(int(r["device_upload_completed_bytes_derived"]) + int(r["offload_received_bytes"]) - int(r["offload_sent_bytes"]) == int(r["net_received_counter_bytes"]) == int(r["assigned_to_compute_bytes"]), "Final node conservation failed")
    generated = int(prop["generated_bytes"])
    require(generated == int(prop["upload_completed_bytes"]) == int(prop["assigned_bytes_final"]) > 0, "Final run conservation failed")
    require(sum(int(r["device_upload_completed_bytes_derived"]) for r in final) == sum(int(r["assigned_to_compute_bytes"]) for r in final) == generated, "Final global totals differ")
    require(sum(int(r["offload_sent_bytes"]) for r in final) == sum(int(r["offload_received_bytes"]) for r in final) == int(prop["offload_bytes"]), "Offload totals differ")
    return {"sample_rows": len(samples), "observation_rows": len(observations),
            "drain_observation_rows": len(observations) - len(samples), "disconnected_rows": sum(r["current_node"] == "NONE" for r in samples),
            "first_time_ms": int(samples[0]["time_ms"]), "last_time_ms": int(samples[-1]["time_ms"]),
            "export_sha256": {name: sha256(directory / name) for name in FILES}}
