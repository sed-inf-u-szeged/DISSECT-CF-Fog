"""Train and register a TCN candidate using the current neural training profile.

Example:
    python tcn_trainer.py --datasets-directory DATA --output-directory OUTPUT

A single positional JSON argument also supports PythonTrainLauncher's contract.
The existing LSTM modules supply data preparation and training/serving mechanics;
their entry points and their LSTM registration function are never called.
Each attempt has a separate output folder. Registration uses model_type=TCN
and activate=False; simulator routing is a separate integration step.
"""

import argparse
import json
import os
import shutil
import sys
import tempfile
from datetime import datetime, timezone
from pathlib import Path

SCRIPTS_DIRECTORY = Path(__file__).resolve().parents[1]
if str(SCRIPTS_DIRECTORY) not in sys.path:
    sys.path.insert(0, str(SCRIPTS_DIRECTORY))

from model_registry import ModelRegistry
from predictor_models import lstm_optimized_training, lstm_trainer, tcn_network


PROFILE = "combined_mae_mean10"


def write_json_atomic(path, value):
    path = Path(path)
    temporary = path.with_name(path.name + ".tmp")
    with temporary.open("w", encoding="utf-8", newline="\n") as stream:
        json.dump(value, stream, indent=2, ensure_ascii=False, allow_nan=False)
        stream.write("\n")
        stream.flush()
        os.fsync(stream.fileno())
    os.replace(temporary, path)


def validate_settings(settings):
    defaults = {
        "inputSize": 192,
        "outputSize": 64,
        "scale": False,
        "smoothing": None,
        "lossFunction": PROFILE,
        "optimizer": "Adam",
        "epochs": 40,
        "modelName": "tcn_i192_o64.keras",
    }
    result = {**defaults, **settings}
    if result["scale"] is not False or result.get("smoothing") is not None:
        raise ValueError("TCN requires external scale=False and smoothing=None.")
    if result["lossFunction"] != PROFILE or result["optimizer"] != "Adam":
        raise ValueError("This TCN profile requires combined_mae_mean10 and Adam.")
    for key in ("inputSize", "outputSize", "epochs"):
        value = result[key]
        if isinstance(value, bool) or int(value) != float(value) or int(value) < 1:
            raise ValueError(f"{key} must be a positive integer.")
        result[key] = int(value)
    if (result["inputSize"], result["outputSize"]) != (192, 64):
        raise ValueError("This training profile requires input192/output64.")

    name = str(result["modelName"]).strip()
    if not name or "/" in name or "\\" in name or not name.endswith(".keras"):
        raise ValueError("modelName must be a plain .keras filename.")
    result["modelName"] = name
    for key in ("datasetsDirectory", "modelOutputLocation"):
        if not str(result.get(key, "")).strip():
            raise ValueError(f"{key} is required.")
        result[key] = str(Path(result[key]).expanduser().resolve())
    if not Path(result["datasetsDirectory"]).is_dir():
        raise FileNotFoundError(result["datasetsDirectory"])
    if not lstm_trainer.load_file_paths(result["datasetsDirectory"]):
        raise ValueError("No training CSV files were found.")
    if result.get("modelStoreDirectory"):
        result["modelStoreDirectory"] = str(
            Path(result["modelStoreDirectory"]).expanduser().resolve()
        )
    return result


def main(settings):
    settings = validate_settings(settings)
    output = Path(settings["modelOutputLocation"])
    attempts = output / "tcn_training"
    attempts.mkdir(parents=True, exist_ok=True)
    stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S%fZ")
    attempt = Path(tempfile.mkdtemp(prefix=stamp + "_", dir=attempts))
    working = {**settings, "modelOutputLocation": str(attempt)}
    write_json_atomic(attempt / "training_settings.json", working)

    architecture = {
        "name": "tcn_small_v1",
        "channels": tcn_network.CHANNELS,
        "kernel_size": tcn_network.KERNEL_SIZE,
        "dilations": list(tcn_network.DILATIONS),
        "convolutions_per_block": 2,
        "spatial_dropout": tcn_network.DROPOUT,
        "receptive_field": tcn_network.RECEPTIVE_FIELD,
        "readout": "last_step_dense",
        "weight_normalization": False,
    }

    def build_model(model_settings):
        model = tcn_network.get_model(model_settings)
        architecture["core_parameters"] = model.count_params()
        return model

    def register_candidate(model_settings, model_path):
        # The reused runner writes this report inside our new attempt folder.
        source_report = attempt / "optimized_training_report.json"
        report = json.loads(source_report.read_text(encoding="utf-8"))
        report.update({
            "model_type": "TCN",
            "architecture": architecture,
            "input_size": model_settings["inputSize"],
            "output_size": model_settings["outputSize"],
            "optimizer": "Adam",
            "learning_rate": 0.001,
            "datasets_directory": settings["datasetsDirectory"],
            "serving_output": "original scale; clipped at zero",
        })
        report_path = attempt / "tcn_training_report.json"
        write_json_atomic(report_path, report)
        source_report.unlink()

        registry = ModelRegistry(settings.get("modelStoreDirectory"))
        shutil.copy2(
            registry.store_directory / "registry.json",
            attempt / "registry_before_tcn.json",
        )
        registered = registry.register_artifact(
            source_path=model_path,
            feature_name="shared",
            model_type="TCN",
            metadata={
                "input_size": model_settings["inputSize"],
                "output_size": model_settings["outputSize"],
                "loss_function": PROFILE,
                "optimizer": "Adam",
                "learning_rate": 0.001,
                "epochs": model_settings["epochs"],
                "batch_size": report["batch_size"],
                "training_seed": report["training_seed"],
                "checkpoint_rule": report["checkpoint_rule"],
                "architecture": architecture,
                "training_report_path": str(report_path),
            },
            preprocessing={
                "scale": False,
                "smoothing": None,
                "strategy": "embedded_train_standardization_v1",
                "target_smoothing": False,
            },
            activate=False,
        )
        result = {
            "feature_name": registered["feature_name"],
            "model_type": registered["model_type"],
            "model_version": registered["model_version"],
            "artifact_path": str(
                (registry.store_directory / registered["artifact_path"]).resolve()
            ),
            "training_report_path": str(report_path),
        }
        write_json_atomic(attempt / "tcn_training_result.json", result)
        write_json_atomic(output / "tcn_training_report.json", report)
        write_json_atomic(output / "tcn_training_result.json", result)
        return result

    lstm_trainer.send_to_pyshell({
        "progress": 1, "message": "Training TCN", "directory": str(attempt)
    })
    result = lstm_optimized_training.run(working, (
        lstm_trainer.load_file_paths,
        lstm_trainer.load_dataframes,
        lstm_trainer.prepare_dataframes,
        lstm_trainer.make_data,
        build_model,
        lstm_trainer.save_model,
        register_candidate,
    ))
    lstm_trainer.send_to_pyshell({
        "progress": 8, "message": "Done", "model": result,
    })
    return result


def parse_arguments():
    if len(sys.argv) == 2 and sys.argv[1].lstrip().startswith("{"):
        settings = json.loads(sys.argv[1])
        if not isinstance(settings, dict):
            raise ValueError("Training settings must be a JSON object.")
        return settings
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--datasets-directory", required=True)
    parser.add_argument("--output-directory", required=True)
    parser.add_argument("--epochs", type=int, default=40)
    parser.add_argument("--model-store", help="Optional separate registry directory")
    args = parser.parse_args()
    return {
        "datasetsDirectory": args.datasets_directory,
        "modelOutputLocation": args.output_directory,
        "epochs": args.epochs,
        "modelStoreDirectory": args.model_store,
    }


if __name__ == "__main__":
    main(parse_arguments())
