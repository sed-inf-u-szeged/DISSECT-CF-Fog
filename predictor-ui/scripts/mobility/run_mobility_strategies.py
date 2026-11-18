"""Check both official movement strategies and repeat the random run with the same seed.

Uses isolated JVMs and fresh output directories. Does not train or load learned models.
"""
import argparse
from datetime import datetime, timezone
import math
import os
from pathlib import Path
import shutil
import subprocess
import sys
import uuid

from check_dataset_plan import require
from check_mobility_run import HEADER, motion, properties, rows, sha256
from run_mobility_batch import JAR, MAIN, SOURCES, XML, now, prepare, write_json

RESOURCE = "simulator/src/main/resources/demo/mobility/"


def validate_run(run, config_path):
    config = properties(config_path)
    effective = properties(run / "scenario-effective.properties")
    prop = properties(run / "run.properties")
    strategy = config["mobility_strategy"]
    require(prop["mobility_strategy"] == effective["mobility_strategy"] == strategy, "Strategy differs")
    for key, value in config.items():
        if key in ("scenario_name", "mobility_strategy", "route_repeats", "waypoints_m"):
            if key == "waypoints_m":
                parse = lambda text: [[float(v) for v in p.split(",")] for p in text.split(";")]
                require(parse(value) == parse(effective[key]), "Effective waypoints differ")
            else:
                require(value == effective[key], "Effective setting differs: " + key)
        else:
            require(float(value) == float(effective[key]), "Effective setting differs: " + key)
    require(prop["scenario_name"] == config["scenario_name"] and prop["seed"] == config["seed"], "Run identity differs")
    require(prop["route_repeats"] == "false", "Smoke runs must not repeat waypoints")
    require(prop["sample_schema_version"] == "1" and prop["sample_file"] == "mobility_samples.csv", "Sample schema differs")
    require(prop["sample_speed_kind"] == "endpoint_displacement_over_elapsed_time"
            and prop["sample_heading_kind"] == "initial_bearing_previous_to_current", "Motion definitions differ")
    expected_class = "RandomWalkMobilityStrategy" if strategy == "random_walk" else "NomadicMobilityStrategy"
    require(prop["route_strategy"] == expected_class, "Official class differs")
    require(int(prop["interval_ms"]) == 60000 and prop["accounting_check"] == "passed", "Sampling/accounting failed")
    samples, header = rows(run / "mobility_samples.csv")
    observations, _ = rows(run / "observations.csv")
    require(header == HEADER, "Shared sample schema differs")
    duration = int(config.get("duration_minutes", "120"))
    require(len(samples) == int(prop["sample_rows"]) == duration + 1, "Configured observation count differs")
    require(len(observations) == int(prop["observation_rows"]) >= len(samples), "Observation count differs")
    require(len(observations) - len(samples) == int(prop["drain_observation_rows"]), "Drain row count differs")
    require(int(samples[-1]["time_ms"]) - int(samples[0]["time_ms"]) == duration * 60000, "Observation span differs")
    require(int(prop["nominal_generation_duration_ms"]) == duration * 60000
            and prop["sample_window_policy"] == "fixed_%d_observations" % (duration + 1),
            "Configured sample duration policy differs")
    moving = stationary = 0
    start = {"latitude_deg": effective["start_latitude_deg"], "longitude_deg": effective["start_longitude_deg"]}
    for i, (sample, observation) in enumerate(zip(samples, observations)):
        require(sample["run_id"] == run.name and sample["device_id"] == observation["device_id"] == "0", "Mixed identity")
        require(sample["time_ms"] == observation["time_ms"]
                and sample["current_node"] == observation["current_node"], "Observation join differs")
        for key, observed, limit in (("latitude_deg", "latitude", 90), ("longitude_deg", "longitude", 180)):
            value = float(sample[key])
            require(math.isfinite(value) and abs(value) <= limit
                    and value == float(observation[observed]), "Invalid or mismatched position")
        if strategy == "random_walk":
            require(motion(start, sample)[0] <= float(effective["radius_m"]) + 1e-5, "Outside random walk radius")
        if i == 0:
            require(sample["motion_valid"] == sample["heading_valid"] == "false"
                    and all(sample[k] == "" for k in HEADER[5:9]), "First motion must be unavailable")
            continue
        require(int(sample["time_ms"]) - int(samples[i - 1]["time_ms"]) == 60000
                and float(sample["delta_time_s"]) == 60 and sample["motion_valid"] == "true", "Sampling gap")
        distance, heading = motion(samples[i - 1], sample)
        require(abs(float(sample["displacement_m"]) - distance) < 1e-5
                and abs(float(sample["observed_speed_m_s"]) - distance / 60) < 1e-6, "Motion recomputation failed")
        if distance > 1e-6:
            moving += 1
            require(sample["heading_valid"] == "true"
                    and abs((float(sample["heading_deg"]) - heading + 180) % 360 - 180) < 1e-5, "Heading differs")
        else:
            stationary += 1
            require(sample["heading_valid"] == "false" and sample["heading_deg"] == "", "Stationary heading differs")
        if strategy == "random_walk":
            speed = float(sample["observed_speed_m_s"])
            require(float(effective["speed_min_m_s"]) - 1e-6 <= speed
                    <= float(effective["speed_max_m_s"]) + 1e-6, "Random speed unit/range differs")
    require(moving > 0, "No moving observations")
    if strategy == "nomadic":
        points, _ = rows(run / "route_waypoints.csv")
        require(motion(points[-1], samples[-1])[0] < 1e-5 and stationary > 0, "Nomadic did not stop at its endpoint")
    else:
        require(not (run / "route_waypoints.csv").exists() and "route_file" not in prop
                and "speed_m_s" not in prop, "Random walk must not claim waypoints or constant speed")
        require(len({round(float(r["observed_speed_m_s"]), 5) for r in samples[1:]}) > 1, "Random speed did not vary")
    predictions, _ = rows(run / "predictions.csv")
    evaluations, _ = rows(run / "evaluations.csv")
    require(len(predictions) == len(evaluations) == int(prop["issued"]) == int(prop["matched"]), "Unmatched forecasts")
    timeline = {int(r["time_ms"]): r for r in samples}
    for forecast, evaluation in zip(predictions, evaluations):
        origin, target = int(forecast["origin_ms"]), int(forecast["target_ms"])
        require(target == origin + 60000 and origin in timeline and target in timeline, "Forecast time differs")
        require(int(evaluation["origin_ms"]) == origin and int(evaluation["target_ms"]) == target, "Evaluation time differs")
        require(float(evaluation["actual_latitude"]) == float(timeline[target]["latitude_deg"])
                and float(evaluation["actual_longitude"]) == float(timeline[target]["longitude_deg"]), "Future truth differs")
    final, _ = rows(run / "node_load.csv")
    final = [r for r in final if r["sample_kind"] == "FINAL"]
    require(len(final) == 2, "Missing final node accounting")
    generated = int(prop["generated_bytes"])
    require(generated == int(prop["upload_completed_bytes"]) == int(prop["assigned_bytes_final"]) > 0, "Byte conservation failed")
    require(sum(int(r["assigned_to_compute_bytes"]) for r in final) == generated, "Node totals differ")
    for record in final:
        require(all(int(record[k]) == 0 for k in ("waiting_assignment_bytes", "running_tasks",
                "incoming_offload_transfers", "offload_in_flight_bytes_total")), "Unfinished node work")
    return {"sample_rows": len(samples), "moving_intervals": moving, "stationary_intervals": stationary,
            "schema": header, "accounting": "PASSED",
            "export_sha256": {p.name: sha256(p) for p in sorted(run.iterdir()) if p.is_file()}}, samples


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
        for relative in SOURCES + [JAR, XML, RESOURCE + "official-nomadic.properties", RESOURCE + "official-random-walk.properties"]:
            require((root / relative).is_file(), "Missing prerequisite: " + relative)
        batch = root / "simulator/sim_res/mobility-strategies" / (
            datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ") + "-" + uuid.uuid4().hex[:12])
        batch.mkdir(parents=True, exist_ok=False)
        state = {"status": "RUNNING", "started_at_utc": now(), "training": "NOT_STARTED", "runs": []}
        write_json(batch / "strategies.json", state)
        print("Strategy checks: " + str(batch), flush=True)
        classpath, xml = prepare(root, batch, java)
        environment = dict(os.environ)
        for key in ("MOBILITY_SHADOW_PYTHON", "MOBILITY_SHADOW_SCRIPT", "MOBILITY_SHADOW_MODEL"):
            environment.pop(key, None)
        random_reference = None
        cases = (("nomadic", "official-nomadic.properties"), ("random-walk", "official-random-walk.properties"),
                 ("random-walk-repeat", "official-random-walk.properties"))
        for number, (name, filename) in enumerate(cases, 1):
            case = batch / name
            case.mkdir()
            config = case / filename
            shutil.copy2(root / RESOURCE / filename, config)
            command = [java, "-cp", classpath, MAIN, "--no-ui", "--scenario=" + str(config), str(xml), str(case / "output")]
            write_json(case / "command.json", command)
            with (case / "simulation.log").open("wb") as log:
                result = subprocess.run(command, cwd=root, env=environment, stdout=log, stderr=subprocess.STDOUT, timeout=90)
            require(result.returncode == 0, "Simulation failed; read " + str(case / "simulation.log"))
            runs = list((case / "output").glob("mobility-*"))
            require(len(runs) == 1, "Expected one isolated simulation output")
            checked, samples = validate_run(runs[0], config)
            normalized = [{k: v for k, v in r.items() if k != "run_id"} for r in samples]
            if name == "random-walk":
                random_reference = normalized
            elif name == "random-walk-repeat":
                require(normalized == random_reference, "Same-seed simulation replay differs")
            state["runs"].append({"case": name, "output": str(runs[0].relative_to(batch)),
                                  "config_sha256": sha256(config), **checked})
            write_json(batch / "strategies.json", state)
            print(f"[{number}/3] {name}: PASSED rows={checked['sample_rows']} "
                  f"moving={checked['moving_intervals']} stationary={checked['stationary_intervals']}", flush=True)
        state.update(status="COMPLETE", same_seed_replay="PASSED", finished_at_utc=now())
        write_json(batch / "strategies.json", state)
        print("strategies=2 runs=3 sample_rows=363 same_seed_replay=PASSED training=NOT_STARTED")
        print("MOBILITY_OFFICIAL_STRATEGIES_OK")
        print("Manifest: " + str(batch / "strategies.json"))
        return 0
    except Exception as error:
        if batch is not None and state is not None:
            state.update(status="FAILED", error=str(error))
            write_json(batch / "strategies.json", state)
        print("MOBILITY_OFFICIAL_STRATEGIES_FAILED: " + str(error), file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
