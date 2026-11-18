"""Verify Java-scheduled training, independent qualification, activation and fallback/refit.

Both official strategies use the shared load eligibility/window-growth rules.
Connection control and additional model families are outside this bounded probe.
"""
import argparse
import contextlib
from dataclasses import asdict
from datetime import datetime, timezone
import os
from pathlib import Path
import shutil
import sys
import uuid

from build_mobility_windows import read_json, require, sha256, write_json
from mobility_online_buffer import TrainingPolicy
from mobility_online_lstm import tensorflow_runtime
from mobility_quality_gate import QualityPolicy
from run_mobility_batch import MAIN, now, prepare
from run_mobility_online_buffer import audit_online
from run_mobility_online_first_fit import artifacts
from run_mobility_shared_lifecycle import audit_shared
from audit_mobility_closed_loop import audit_closed_worker, audit_closed_models
from run_mobility_shadow import execute_owned
from run_mobility_strategies import RESOURCE, validate_run

EXPECTED = 361


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
        batch = root / "simulator/sim_res/mobility-closed-loop" / (
            datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ") + "-" + uuid.uuid4().hex[:12])
        batch.mkdir(parents=True, exist_ok=False)
        state = dict(schema_version=1, status="RUNNING", started_at_utc=now(), expected_observations=EXPECTED,
                     training_policy=asdict(policy), quality_policy=asdict(QualityPolicy()),
                     controller_integration="SHARED_COORDINATOR_AND_MANAGER", training_scheduler="JAVA_SHARED_RULES",
                     retraining_after_degradation="ENABLED", connection_control=False, runs=[])
        manifest = batch / "closed-loop.json"
        write_json(manifest, state)
        print("Closed-loop lifecycle checks: " + str(batch), flush=True)
        source = batch / "source"
        source.mkdir()
        for script in Path(__file__).parent.glob("*.py"):
            shutil.copy2(script, source / script.name)
        source_hashes = artifacts(source)
        write_json(batch / "source-sha256.json", source_hashes)
        classpath, xml = prepare(root, batch, java)
        runtime_hashes = artifacts(batch / "runtime")
        for number, name in enumerate(("nomadic", "random-walk"), 1):
            print(f"[{number}/2] {name}: observations={EXPECTED}; Java training, promotion and fallback; please wait", flush=True)
            case = batch / name
            case.mkdir()
            config = case / ("official-" + name + ".properties")
            shutil.copy2(root / RESOURCE / config.name, config)
            with config.open("a", encoding="utf-8") as stream:
                stream.write("\nduration_minutes=360\n")
            config_hash = sha256(config)
            policy_file = case / "training-policy.json"
            write_json(policy_file, asdict(policy))
            policy_hash = sha256(policy_file)
            environment = dict(os.environ)
            environment.pop("MOBILITY_ONLINE_QUALITY_POLICY", None)
            environment.update(MOBILITY_SHADOW_PYTHON=sys.executable,
                               MOBILITY_SHADOW_SCRIPT=str(source / "mobility_closed_loop_worker.py"),
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
            result = audit_online(output, samples, policy, worker_mode="JAVA_SCHEDULED", training="COMPLETE", scaler="FROZEN",
                                  expected_observations=EXPECTED)
            result.update(audit_closed_worker(output, samples))
            result.update(audit_closed_models(output, samples, result["versions"], tf))
            result.update(audit_shared(output, samples, result["versions"], closed_loop=True))
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
                  f"retrained_after_degradation={result['degradation_retrainings']} selected_matched={result['selected_matched']} active_lstm={str(result['active_lstm']).lower()}", flush=True)
        require(source_hashes == artifacts(source) and runtime_hashes == artifacts(batch / "runtime"),
                "Source or compiled runtime changed")
        for record in state["runs"]:
            require(record["artifact_sha256"] == artifacts(batch / record["output"]), "Run artifacts changed")
        state.update(status="COMPLETE", finished_at_utc=now())
        write_json(manifest, state)
        print("controller_integration=SHARED_COORDINATOR_AND_MANAGER training_scheduler=JAVA_SHARED_RULES")
        print("retraining_after_degradation=ENABLED connection_control=DISABLED")
        print("MOBILITY_CLOSED_LOOP_OK")
        print("Manifest: " + str(manifest))
        return 0
    except Exception as error:
        if batch is not None and state is not None:
            state.update(status="FAILED", error=str(error))
            write_json(batch / "closed-loop.json", state)
        print("MOBILITY_CLOSED_LOOP_FAILED: " + str(error), file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
