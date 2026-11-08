"""Collect isolated Mobility simulations; default: three training-only smoke cases.

Uses the installed dataset plan. Never trains a model, opens UI, resumes, overwrites
an earlier batch, or changes a route split. --all explicitly selects all 36 cases.
"""
import argparse
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import uuid

from check_dataset_plan import validate, require
from check_mobility_run import sha256, validate_run

MAIN = "hu.u_szeged.inf.fog.simulator.demo.simple.MobilityPredictionSimulation"
BASE = "simulator/src/main/java/hu/u_szeged/inf/fog/simulator/"
SOURCES = [BASE + name + ".java" for name in (
    "demo/simple/MobilityPredictionSimulation", "demo/simple/MobilityScenarioConfig",
    "iot/mobility/MobilitySampleRecorder", "application/MobilityTaskMetrics",
    "iot/MobilityDeviceMetrics", "prediction/communication/launchers/MobilityUiLauncher",
    "prediction/lifecycle/LifecycleAssessment", "prediction/lifecycle/LifecycleAssessmentSource",
    "prediction/lifecycle/LifecycleCoordinator", "prediction/lifecycle/LifecycleManager",
    "prediction/mobility/MobilityLifecycleAdapter", "prediction/OnlineTrainingSchedule")]
PLAN = "simulator/src/main/resources/demo/mobility/dataset-v1/dataset-plan.json"
JAR = "simulator/target/dissect-cf-fog-1.0.0-SNAPSHOT-jar-with-dependencies.jar"
XML = "simulator/src/main/resources/demo/LPDS_original.xml"


def now():
    return datetime.now(timezone.utc).isoformat()


def write_json(path, value):
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    os.replace(str(temporary), str(path))


def command_output(args, root):
    try:
        result = subprocess.run(args, cwd=str(root), stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                timeout=15, check=False)
        return {"exit_code": result.returncode, "output": result.stdout.decode("utf-8", errors="replace").strip()}
    except (OSError, subprocess.TimeoutExpired) as error:
        return {"unavailable": str(error)}


def execute(command, root, log, timeout):
    # Java runs as a direct child; --no-ui prevents additional UI processes.
    with log.open("wb") as stream:
        process = subprocess.Popen(command, cwd=str(root), stdout=stream, stderr=subprocess.STDOUT)
        try:
            return process.wait(timeout=timeout)
        except BaseException:
            process.kill()
            process.wait()
            raise


def prepare(root, batch, java):
    runtime = batch / "runtime"
    runtime.mkdir()
    classes = runtime / "classes"
    shutil.copytree(root / "simulator/target/classes", classes)
    jar = runtime / "dependencies.jar"
    shutil.copy2(root / JAR, jar)
    shutil.copy2(root / XML, runtime / "nodes.xml")
    source_files = []
    for relative in SOURCES:
        target = runtime / "source" / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(root / relative, target)
        source_files.append(str(target))
    classpath = str(classes) + os.pathsep + str(jar)
    command = [java, "-m", "jdk.compiler/com.sun.tools.javac.Main", "-proc:none", "--release", "11",
               "-cp", classpath, "-d", str(classes)] + source_files
    write_json(runtime / "compile-command.json", command)
    require(execute(command, root, batch / "compile.log", 120) == 0,
            "Compilation failed; read compile.log. A JDK (not only JRE) is required.")
    # Record exact compiled runtime, not just a possibly dirty Git revision.
    hashes = {str(p.relative_to(runtime)).replace(os.sep, "/"): sha256(p)
              for p in sorted(runtime.rglob("*")) if p.is_file()}
    write_json(batch / "runtime-sha256.json", hashes)
    return classpath, runtime / "nodes.xml"


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--project-root", type=Path, default=Path(__file__).resolve().parents[3])
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--smoke", action="store_true", help="Three speeds of rect_01 (default)")
    mode.add_argument("--all", action="store_true", help="All 36 cases, including validation/test")
    mode.add_argument("--regression", action="store_true", help="Four train cases covering reconnect and drain regressions")
    parser.add_argument("--java", default="java", help="Path to the JDK java executable")
    parser.add_argument("--timeout-seconds", type=int, default=120, help="Wall-clock limit per simulation")
    args = parser.parse_args(argv)
    batch = state = None
    try:
        root = args.project_root.resolve()
        require(args.timeout_seconds >= 1, "Timeout must be positive")
        java = shutil.which(args.java)
        require(java is not None, "Java not found; supply --java with a JDK java.exe path")
        manifest = root / PLAN
        validate(manifest)
        plan = json.loads(manifest.read_text(encoding="utf-8-sig"))
        regression = {"rect_01_s20", "rect_02_s20", "rect_02_s41", "rect_03_s20"}
        selected = [r for r in plan["runs"] if args.all or (
            r["case_id"] in regression if args.regression else r["route_id"] == "rect_01")]
        require(len(selected) == (36 if args.all else 4 if args.regression else 3), "Selection differs from pilot protocol")
        require(args.all or all(r["split"] == "train" for r in selected), "Smoke cases must be train-only")
        for relative in SOURCES + [JAR, XML]:
            require((root / relative).is_file(), "Missing prerequisite: " + relative)
        require((root / "simulator/target/classes").is_dir(), "Missing target/classes; compile the simulator first")
        batch = root / "simulator/sim_res/mobility-batches" / (
            datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ") + "-" + uuid.uuid4().hex[:12])
        batch.mkdir(parents=True, exist_ok=False)
        state = {"batch_schema_version": 1, "dataset_id": plan["dataset_id"],
                 "mode": "all" if args.all else "connection_regression" if args.regression else "smoke", "status": "PREPARING", "started_at_utc": now(),
                 "training": "NOT_STARTED", "selected_cases": len(selected),
                 "manifest_sha256": sha256(manifest), "git_head": command_output(["git", "rev-parse", "HEAD"], root),
                 "git_status": command_output(["git", "status", "--porcelain=v1"], root),
                 "java_version": command_output([java, "-version"], root), "runs": []}
        write_json(batch / "batch.json", state)
        print("Batch: " + str(batch), flush=True)
        # Freeze the entire plan (including unused configs) and validate the copy.
        shutil.copytree(manifest.parent, batch / "plan")
        validate(batch / "plan/dataset-plan.json")
        require(sha256(batch / "plan/dataset-plan.json") == state["manifest_sha256"], "Plan changed while copying")
        audit = batch / "collector"
        audit.mkdir()
        for name in ("run_mobility_batch.py", "check_mobility_run.py", "check_dataset_plan.py"):
            shutil.copy2(Path(__file__).parent / name, audit / name)
        for case in selected:
            state["runs"].append(dict(case, status="PENDING", run_id=None, output_directory=None))
        write_json(batch / "batch.json", state)
        classpath, xml = prepare(root, batch, java)
        state["status"] = "RUNNING"
        for index, record in enumerate(state["runs"], 1):
            case_dir = batch / "cases" / record["case_id"]
            case_dir.mkdir(parents=True)
            output_parent = case_dir / "output"
            output_parent.mkdir()
            config = batch / "plan" / record["config_path"]
            command = [java, "-cp", classpath, MAIN, "--no-ui", "--scenario=" + str(config), str(xml), str(output_parent)]
            record.update(status="RUNNING", started_at_utc=now(), command=command,
                          log=str((case_dir / "simulation.log").relative_to(batch)).replace(os.sep, "/"))
            write_json(batch / "batch.json", state)
            print("[{}/{}] {} ({})".format(index, len(selected), record["case_id"], record["split"]), flush=True)
            try:
                code = execute(command, root, case_dir / "simulation.log", args.timeout_seconds)
                record["exit_code"] = code
                outputs = list(output_parent.glob("mobility-*"))
                require(len(outputs) == 1 and outputs[0].is_dir(), "Expected exactly one isolated result directory")
                result = outputs[0]
                record.update(run_id=result.name, output_directory=str(result.relative_to(batch)).replace(os.sep, "/"))
                require(code == 0, "Simulation exited with code " + str(code))
                log_text = (case_dir / "simulation.log").read_text(encoding="utf-8", errors="replace")
                require(all(marker in log_text for marker in ("MOBILITY_SIMULATION_OK", "MOBILITY_ACCOUNTING_OK", "MOBILITY_SAMPLES_OK")), "Missing simulation completion marker")
                detail = validate_run(result, record, config)
                record.update(status="PASSED", **detail)
                print("  PASSED rows={}".format(detail["sample_rows"]), flush=True)
            except BaseException as error:
                record.update(status="INTERRUPTED" if isinstance(error, KeyboardInterrupt) else "FAILED", error=str(error) or type(error).__name__)
                # Preserve partial output paths even on timeout or invalid export.
                record["partial_outputs"] = [str(p.relative_to(batch)).replace(os.sep, "/") for p in output_parent.iterdir()]
                raise
            finally:
                record["finished_at_utc"] = now()
                write_json(batch / "batch.json", state)
        state["status"] = "COMPLETE"
        state["sample_rows"] = sum(r["sample_rows"] for r in state["runs"])
        print("cases={} passed={} sample_rows={} training=NOT_STARTED".format(len(selected), len(selected), state["sample_rows"]), flush=True)
        print("MOBILITY_BATCH_OK", flush=True)
        return 0
    except (Exception, KeyboardInterrupt) as error:
        if state is not None:
            state.update(status="INTERRUPTED" if isinstance(error, KeyboardInterrupt) else "FAILED", error=str(error) or type(error).__name__)
        print("MOBILITY_BATCH_FAILED: " + (str(error) or type(error).__name__), file=sys.stderr)
        return 1
    finally:
        if batch is not None and state is not None:
            state["finished_at_utc"] = now()
            write_json(batch / "batch.json", state)
            print("Batch manifest: " + str(batch / "batch.json"), flush=True)


if __name__ == "__main__":
    sys.exit(main())
