"""Run 1-100 devices in one simulator, then audit isolated lifecycles and report scaling."""
import argparse
import contextlib
import csv
from dataclasses import asdict
from datetime import datetime, timezone
import importlib.util
import os
from pathlib import Path
import platform
import re
import shutil
import signal
import subprocess
import sys
import time
import uuid

import numpy as np
from audit_mobility_multi import audit_run
from build_mobility_windows import read_json, require, sha256, write_json
from mobility_online_buffer import TrainingPolicy
from mobility_online_lstm import tensorflow_runtime
from run_mobility_batch import prepare, MAIN, now
from run_mobility_online_first_fit import artifacts
from run_mobility_strategies import RESOURCE


def execute_monitored(command, root, log, environment, timeout, psutil):
    """Sample only the launched JVM and its descendants; audit/compile time is excluded."""
    peak = samples = 0
    started = time.monotonic()
    with log.open("wb") as stream:
        process = subprocess.Popen(command, cwd=root, env=environment, stdout=stream, stderr=subprocess.STDOUT,
                                   start_new_session=os.name != "nt")
        owned = psutil.Process(process.pid)
        next_progress = 15
        try:
            while process.poll() is None:
                elapsed = time.monotonic() - started
                if elapsed > timeout:
                    raise TimeoutError("Simulation timeout after %d seconds; see %s" % (timeout, log))
                try:
                    children = [owned] + owned.children(recursive=True)
                except psutil.Error:
                    children = []
                rss = 0
                for child in children:
                    try:
                        rss += child.memory_info().rss
                    except psutil.Error:
                        pass
                peak, samples = max(peak, rss), samples + 1
                if elapsed >= next_progress:
                    print("  Simulation running: %.0f s; sampled process-tree peak %.1f MiB" % (elapsed, peak / 2**20), flush=True)
                    next_progress += 15
                time.sleep(.25)
            require(process.returncode == 0, "Simulation failed; see " + str(log))
        except BaseException:
            if process.poll() is None:
                if os.name == "nt":
                    subprocess.run(["taskkill", "/PID", str(process.pid), "/T", "/F"], capture_output=True, check=False)
                else:
                    try:
                        os.killpg(process.pid, signal.SIGKILL)
                    except ProcessLookupError:
                        pass
                process.wait()
            raise
    return dict(simulation_wall_seconds=time.monotonic() - started, peak_rss_sampled_mib=peak / 2**20,
                memory_sample_count=samples, memory_sample_interval_s=.25, memory_scope="JAVA_AND_WORKER_PROCESS_TREE")


def timing_summary(rows):
    output = {}
    for operation in ("TRAIN", "PREDICT"):
        values = [float(r["wall_seconds"]) for r in rows if r["operation"] == operation]
        require(values, "Missing timing samples")
        prefix = operation.lower()
        output.update({prefix + "_calls":len(values), prefix + "_total_seconds":sum(values),
                       prefix + "_mean_ms":float(np.mean(values) * 1000), prefix + "_p95_ms":float(np.percentile(values, 95) * 1000)})
    return output


def scenario_with_limits(original, minutes, drain_minutes):
    """Override run limits in the case copy; never edit the user's scenario file."""
    lines = [line for line in original.splitlines()
             if re.split(r"[\s=:]", line.strip(), maxsplit=1)[0] not in ("duration_minutes", "drain_minutes")]
    return "\n".join(lines) + "\nduration_minutes=%d\ndrain_minutes=%d\n" % (minutes, drain_minutes)


def main(argv=None, *, scenario_override=None, progress=None):
    # The UI calls the same audited runner; no second simulation implementation.
    def report(stage, **details):
        if progress is not None:
            progress(stage, **details)
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--project-root", type=Path, default=Path(__file__).resolve().parents[3])
    parser.add_argument("--devices", type=int, nargs="+", default=[10])
    parser.add_argument("--minutes", type=int, default=120)
    parser.add_argument("--drain-minutes", type=int, default=120,
                        help="Additional simulated minutes allowed for workload drain (1-1440; sampling stays fixed)")
    parser.add_argument("--strategy", choices=["random-walk", "nomadic"], default="random-walk")
    parser.add_argument("--comparison-models", nargs="*", choices=["IMM/Kalman", "XGBoost", "TCN"], default=[])
    parser.add_argument("--load-models", nargs="*", choices=["LSTM", "TCN", "GRU", "PatchTST"], default=[])
    parser.add_argument("--load-profile", choices=["candidate", "observe"], default="candidate")
    parser.add_argument("--load-epochs", type=int, default=40)
    parser.add_argument("--repeats", type=int, default=1)
    parser.add_argument("--timeout-seconds", type=int, default=None)
    parser.add_argument("--java", default="java")
    parser.add_argument("--node", default="node")
    args = parser.parse_args(argv)
    batch = state = None
    try:
        require(all(1 <= n <= 100 for n in args.devices) and len(set(args.devices)) == len(args.devices), "Device counts must be distinct integers from 1 to 100")
        require(104 <= args.minutes <= 1440 and 1 <= args.repeats <= 10, "Use 104-1440 minutes and 1-10 repeats")
        require(1 <= args.drain_minutes <= 1440, "Use 1-1440 drain minutes")
        require(args.timeout_seconds is None or args.timeout_seconds >= 60, "Timeout must be at least 60 seconds")
        require(importlib.util.find_spec("tensorflow") is not None, "Activate the existing TensorFlow environment first")
        try:
            import psutil
        except ImportError as error:
            raise ValueError("Install memory sampling support: python -m pip install -r predictor-ui/scripts/mobility/requirements-multi.txt") from error
        from mobility_comparison_models import validate_methods, preflight
        methods = validate_methods(args.comparison_models)
        preflight(methods)
        from node_load_worker import validate_options, preflight as load_preflight
        load_models = validate_options(args.load_models, args.load_profile, args.load_epochs)
        load_preflight(load_models)
        root = args.project_root.resolve()
        java, node = shutil.which(args.java), shutil.which(args.node)
        require(java and node, "JDK Java and Node.js must be on PATH")
        batch = root / "simulator/sim_res/mobility-multi" / (datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ") + "-" + uuid.uuid4().hex[:12])
        batch.mkdir(parents=True, exist_ok=False)
        state = dict(schema_version=1, status="RUNNING", started_at_utc=now(), devices=args.devices, minutes=args.minutes,
                     drain_minutes=args.drain_minutes, comparison_models=methods,
                     load_models=load_models, load_profile=args.load_profile, load_epochs=args.load_epochs,
                     strategy=args.strategy, repeats=args.repeats, connection_control=False, random_stream="SHARED_SEED_SYNCER",
                     environment=dict(python=sys.version, platform=platform.platform(), psutil=psutil.__version__), runs=[])
        manifest = batch / "multi-device-batch.json"
        write_json(manifest, state)
        print("Multi-device batch: " + str(batch), flush=True)
        report("PREPARING", batch=str(batch))
        source = batch / "source"
        source.mkdir()
        for path in Path(__file__).parent.glob("*.py"):
            shutil.copy2(path, source / path.name)
        # Baseline implementations also belong to the frozen run.
        scripts = root / "predictor-ui/scripts"
        for name in ("predictor_models", "app_utils"):
            shutil.copytree(scripts / name, source / name, ignore=shutil.ignore_patterns("__pycache__", "*.pyc"))
        shutil.copy2(scripts / "model_registry.py", source / "model_registry.py")
        shutil.copy2(scripts / "error_metrics.py", source / "error_metrics.py")
        source_hashes = artifacts(source)
        write_json(batch / "source-sha256.json", source_hashes)
        classpath, xml = prepare(root, batch, java)
        runtime_hashes = artifacts(batch / "runtime")
        # Snapshot the exact display/accounting validator alongside the worker source.
        checker_root = batch / "ui-validator"
        checker_root.mkdir()
        for name in ("check-mobility-results.js", "mobility-results.js", "mobility-baseline-results.js", "mobility-learning-results.js", "mobility-multi-results.js", "mobility-comparison-results.js", "node-load-results.js"):
            shutil.copy2(root / "predictor-ui/ui" / name, checker_root / name)
        checker_hashes = artifacts(checker_root)
        tf = None
        summaries = []
        for number in args.devices:
            for repeat in range(1, args.repeats + 1):
                case = batch / ("devices-%03d-repeat-%02d" % (number, repeat))
                case.mkdir()
                config = case / ("official-" + args.strategy + ".properties")
                original = scenario_override if scenario_override is not None else (root / RESOURCE / config.name).read_text(encoding="utf-8-sig")
                config.write_text(scenario_with_limits(original, args.minutes, args.drain_minutes), encoding="utf-8")
                policy = case / "training-policy.json"
                write_json(policy, asdict(TrainingPolicy()))
                config_hash, policy_hash = sha256(config), sha256(policy)
                environment = dict(os.environ, MOBILITY_SHADOW_PYTHON=sys.executable,
                                   MOBILITY_SHADOW_SCRIPT=str(source / "mobility_multi_worker.py"), MOBILITY_SHADOW_MODEL=str(policy),
                                   PYTHONDONTWRITEBYTECODE="1", MOBILITY_COMPARISON_MODELS=__import__("json").dumps(methods),
                                   NODE_LOAD_MODELS=__import__("json").dumps(load_models), NODE_LOAD_PROFILE=args.load_profile,
                                   NODE_LOAD_EPOCHS=str(args.load_epochs), NODE_LOAD_SCRIPT=str(source / "node_load_worker.py"))
                environment.pop("MOBILITY_ONLINE_QUALITY_POLICY", None)
                command = [java, "-cp", classpath, MAIN, "--no-ui", "--devices=" + str(number),
                           "--scenario=" + str(config), str(xml), str(case / "output")]
                write_json(case / "command.json", command)
                print("[%d devices, repeat %d] Simulation and sequential independent LSTMs" % (number, repeat), flush=True)
                report("RUNNING", batch=str(batch), case=str(case), device_count=number)
                timings = execute_monitored(command, root, case / "simulation.log", environment,
                                            args.timeout_seconds or max(600, int(number * 180 * args.minutes / 120),
                                                                        1800 if load_models else 0), psutil)
                outputs = list((case / "output").glob("mobility-multi-*"))
                require(len(outputs) == 1, "Expected exactly one multi-device simulation output")
                output = outputs[0]
                print("  Simulation complete; checking saved models and lifecycle decisions", flush=True)
                report("AUDITING", output=str(output))
                if tf is None:
                    with contextlib.redirect_stdout(sys.stderr):
                        tf = tensorflow_runtime()
                    state["environment"]["tensorflow"] = tf.__version__
                devices, operations = audit_run(output, node, checker_root / "check-mobility-results.js", tf)
                from audit_node_load_models import audit as audit_load_models
                load_audit = audit_load_models(output, tf=tf)
                require(read_json(output / "node_load_model_summary.json")["models"] == load_models,
                        "Configured load models differ from recorded models")
                write_json(output / "node_load_model_audit.json", load_audit)
                require(sha256(config) == config_hash and sha256(policy) == policy_hash, "Configuration changed during run")
                require(all(read_json(output / "devices" / str(i) / "multi-worker-device.json")["policy_sha256"] == policy_hash
                            for i in range(number)), "Worker policy differs")
                write_json(output / "audit.json", dict(status="COMPLETE", devices=devices,
                           accounting="PASSED", feature_isolation="PASSED", saved_model_replay="PASSED"))
                drain = read_json(output / "drain-state.json")
                # Timed.fire() advances the clock by one tick after dispatching an event.
                require(drain["next_event_ms"] == -1 and drain["simulation_time_ms"] <= drain["simulation_limit_ms"] + 1,
                        "Simulation did not complete within its drain allowance")
                summary = dict(device_count=number, repeat=repeat, minutes=args.minutes, drain_minutes=args.drain_minutes, comparison_models=methods,
                               final_settlement_sim_minutes=drain["simulation_time_ms"] / 60000,
                               **timings, **timing_summary(operations))
                summaries.append(summary)
                with (batch / "scaling.csv").open("w", encoding="utf-8", newline="") as stream:
                    writer = csv.DictWriter(stream, fieldnames=list(summary))
                    writer.writeheader(); writer.writerows(summaries)
                state["runs"].append(dict(device_count=number, repeat=repeat, output=output.relative_to(batch).as_posix(),
                                         performance=summary, devices=devices, artifact_sha256=artifacts(output)))
                write_json(manifest, state)
                print("  PASSED. Open mobility results: " + str(output), flush=True)
                report("RESULT_READY", output=str(output))
        require(artifacts(source) == source_hashes and artifacts(batch / "runtime") == runtime_hashes
                and artifacts(checker_root) == checker_hashes, "Source, validator or runtime changed")
        state.update(status="COMPLETE", finished_at_utc=now())
        write_json(manifest, state)
        print("MOBILITY_MULTI_DEVICE_OK")
        print("Scaling table: " + str(batch / "scaling.csv"))
        report("COMPLETE", batch=str(batch), output=str(output))
        return 0
    except KeyboardInterrupt:
        if batch is not None and state is not None:
            state.update(status="CANCELLED", finished_at_utc=now())
            write_json(batch / "multi-device-batch.json", state)
        report("CANCELLED")
        raise
    except Exception as error:
        if batch is not None and state is not None:
            state.update(status="FAILED", error=str(error))
            write_json(batch / "multi-device-batch.json", state)
        print("MOBILITY_MULTI_DEVICE_FAILED: " + str(error), file=sys.stderr)
        report("FAILED", error=str(error))
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
