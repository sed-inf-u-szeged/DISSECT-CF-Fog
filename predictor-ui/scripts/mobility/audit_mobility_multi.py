"""Reconstruct each device's windows, qualifications, ownership and saved model outputs."""
import csv
import json
import math
from pathlib import Path
import subprocess

from audit_mobility_closed_loop import audit_closed_worker, audit_closed_models
from build_mobility_windows import read_json, require, windows_for_run
from check_mobility_run import rows, motion
from mobility_online_buffer import REFERENCE
from mobility_shadow_worker import HEADER
from run_mobility_online_buffer import jsonlines
from run_mobility_shared_lifecycle import audit_shared


def audit_device(child, manifest, device_id, tf):
    samples, header = rows(child / "mobility_samples.csv")
    received, received_header = rows(child / "online_observations.csv")
    count = manifest["expected_observations_per_device"]
    require(header == received_header == HEADER and received == samples and len(samples) == count,
            "Java/Python per-device observations differ")
    for i, row in enumerate(samples):
        require(row["run_id"] == manifest["run_id"] and row["device_id"] == device_id, "Mixed device/run observations")
        if i == 0:
            require(row["motion_valid"] == row["heading_valid"] == "false"
                    and all(row[k] == "" for k in ("delta_time_s", "displacement_m", "observed_speed_m_s", "heading_deg")),
                    "First motion record must be unavailable")
        else:
            distance, heading = motion(samples[i - 1], row)
            require(row["motion_valid"] == "true" and float(row["delta_time_s"]) == 60
                    and math.isclose(float(row["displacement_m"]), distance, abs_tol=1e-5)
                    and math.isclose(float(row["observed_speed_m_s"]), distance / 60, abs_tol=1e-6), "Observed movement differs")
            require(row["heading_valid"] == ("true" if distance > 1e-6 else "false"), "Heading mask differs")
            if distance > 1e-6:
                require(abs((float(row["heading_deg"]) - heading + 180) % 360 - 180) < 1e-5, "Heading differs")
            else:
                require(row["heading_deg"] == "", "Stationary heading must be absent")
    run = dict(case_id="device-" + device_id, route_id="official-strategy", split="development", run_id=manifest["run_id"])
    windows, skipped = windows_for_run(run, samples, REFERENCE, expected_observations=count, device_id=device_id)
    require(skipped == 1 and len(windows) == count - 9, "Unexpected mature window count")
    keys = ("run_id", "device_id", "origin_ms", "target_ms", "history_time_ms", "anchor_latitude_deg", "anchor_longitude_deg", "origin_node", "x")
    inputs = [{k: w[k] for k in keys} for w in windows]
    completed = [dict(x, completed_at_sim_ms=w["target_ms"], y=w["y"], target_latitude_deg=w["target_latitude_deg"],
                      target_longitude_deg=w["target_longitude_deg"]) for x, w in zip(inputs, windows)]
    require(jsonlines(child / "online_inputs.jsonl") == inputs and jsonlines(child / "online_samples.jsonl") == completed,
            "Online features, label timing or device isolation differs from independent reconstruction")
    worker = read_json(child / "multi-worker-device.json")
    require(worker["status"] == "COMPLETE" and worker["device_id"] == device_id and worker["run_id"] == manifest["run_id"]
            and worker["observations"] == count and worker["prediction_origins"] == count - 9, "Incomplete worker device")
    result = audit_closed_worker(child, samples)
    result.update(audit_shared(child, samples, result["versions"], closed_loop=True))
    result.update(audit_closed_models(child, samples, result["versions"], tf, run_id=manifest["run_id"], device_id=device_id))
    from audit_mobility_comparison import audit_comparison
    result["comparison"] = audit_comparison(child, tf)
    tf.keras.backend.clear_session()
    return dict(device_id=device_id, observations=count, feature_parity="EXACT", **result)


def audit_run(output, node, checker, tf):
    manifest = read_json(output / "multi-device.json")
    worker = read_json(output / "multi-worker.json")
    require(manifest["status"] == worker["status"] == "COMPLETE" and manifest["run_id"] == worker["run_id"]
            and manifest["device_count"] == worker["device_count"], "Multi-device completion differs")
    count, expected = manifest["device_count"], manifest["expected_observations_per_device"]
    require([d["device_id"] for d in manifest["devices"]] == [str(i) for i in range(count)]
            and [d["path"] for d in manifest["devices"]] == ["devices/" + str(i) for i in range(count)]
            and worker["observations"] == count * expected and worker["prediction_origins"] == count * (expected - 9),
            "Missing, mixed or extra device streams")
    # The UI reader also validates every shared load snapshot and transfer against global accounting.
    result = subprocess.run([node, str(checker), str(output)], stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                            text=True, encoding="utf-8", timeout=180, check=False)
    (output / "ui-validation.log").write_text(result.stdout, encoding="utf-8")
    require(result.returncode == 0 and "MOBILITY_UI_DATA_OK" in result.stdout, "Display/accounting audit failed; see ui-validation.log")
    devices = []
    timings = []
    for entry in manifest["devices"]:
        child = output / entry["path"]
        devices.append(audit_device(child, manifest, entry["device_id"], tf))
        with (child / "timings.csv").open(encoding="utf-8", newline="") as stream:
            logged = list(csv.DictReader(stream))
        require(all(r["device_id"] == entry["device_id"] and r["operation"] in ("TRAIN", "PREDICT")
                    and math.isfinite(float(r["wall_seconds"])) and float(r["wall_seconds"]) >= 0 for r in logged), "Invalid per-device timings")
        require(sum(r["operation"] == "PREDICT" for r in logged) == expected - 41
                and sum(r["operation"] == "TRAIN" for r in logged) == devices[-1]["trained_versions"], "Timing sample counts differ")
        timings.extend(logged)
        print("  Device %s: audited %d targets; promotions=%d fallbacks=%d" %
              (entry["device_id"], devices[-1]["selected_matched"], devices[-1]["promotions"], devices[-1]["fallbacks"]), flush=True)
    return devices, timings
