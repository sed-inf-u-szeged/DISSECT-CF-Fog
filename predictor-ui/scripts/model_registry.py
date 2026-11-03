import hashlib
import json
import os
import platform
import re
import shutil
import sys
import threading
from datetime import datetime, timezone
from importlib.metadata import PackageNotFoundError, version as package_version
from pathlib import Path


class ModelRegistry:
    SCHEMA_VERSION = 1
    STORE_ENVIRONMENT_VARIABLE = "DISSECT_MODEL_STORE"

    SUPPORTED_ARTIFACT_EXTENSIONS = {
        ".keras",
        ".h5",
        ".joblib",
        ".pkl",
        ".pickle",
    }

    def __init__(self, store_directory=None):
        self._lock = threading.RLock()
        self._store_directory = self._resolve_store_directory(store_directory)
        self._registry_path = self._store_directory / "registry.json"

        self._store_directory.mkdir(parents=True, exist_ok=True)
        self._initialize_registry()

    @property
    def store_directory(self):
        return self._store_directory

    @staticmethod
    def _resolve_store_directory(store_directory):
        if store_directory:
            return Path(store_directory).expanduser().resolve()

        environment_path = os.environ.get(
            ModelRegistry.STORE_ENVIRONMENT_VARIABLE
        )

        if environment_path:
            return Path(environment_path).expanduser().resolve()

        scripts_directory = Path(__file__).resolve().parent
        predictor_ui_directory = scripts_directory.parent

        return (predictor_ui_directory / "model_store").resolve()

    @staticmethod
    def _safe_segment(value, field_name):
        if value is None:
            raise ValueError(f"{field_name} must not be None")

        original_value = str(value).strip()

        if not original_value:
            raise ValueError(f"{field_name} must not be empty")

        safe_value = re.sub(
            r"[^A-Za-z0-9._-]+",
            "_",
            original_value,
        ).strip("._-")

        if not safe_value or safe_value in {".", ".."}:
            raise ValueError(f"Invalid {field_name}: {original_value}")

        return safe_value

    @staticmethod
    def _new_version():
        return datetime.now(timezone.utc).strftime(
            "%Y%m%dT%H%M%S%fZ"
        )

    @staticmethod
    def _utc_now():
        return datetime.now(timezone.utc).isoformat()

    @staticmethod
    def _json_safe(value):
        if isinstance(value, Path):
            return value.as_posix()

        if isinstance(value, dict):
            return {
                str(key): ModelRegistry._json_safe(item)
                for key, item in value.items()
            }

        if isinstance(value, (list, tuple, set)):
            return [
                ModelRegistry._json_safe(item)
                for item in value
            ]

        if hasattr(value, "item"):
            try:
                return value.item()
            except (TypeError, ValueError):
                pass

        return value

    @staticmethod
    def _sha256(file_path):
        digest = hashlib.sha256()

        with Path(file_path).open("rb") as artifact_file:
            for block in iter(
                lambda: artifact_file.read(1024 * 1024),
                b"",
            ):
                digest.update(block)

        return digest.hexdigest()

    @staticmethod
    def _package_versions():
        result = {}

        for package_name in (
            "numpy",
            "pandas",
            "scikit-learn",
            "scipy",
            "skforecast",
            "statsmodels",
            "tensorflow",
        ):
            try:
                result[package_name] = package_version(package_name)
            except PackageNotFoundError:
                result[package_name] = None

        return result

    def _empty_registry(self):
        return {
            "schema_version": self.SCHEMA_VERSION,
            "updated_at": self._utc_now(),
            "active": {},
            "models": {},
        }

    def _initialize_registry(self):
        with self._lock:
            if not self._registry_path.exists():
                self._write_json_atomic(
                    self._registry_path,
                    self._empty_registry(),
                )
                return

            registry = self._read_json(self._registry_path)

            if registry.get("schema_version") != self.SCHEMA_VERSION:
                raise ValueError(
                    "Unsupported model registry schema version: "
                    f"{registry.get('schema_version')}"
                )

    @staticmethod
    def _read_json(file_path):
        with Path(file_path).open(
            "r",
            encoding="utf-8",
        ) as json_file:
            return json.load(json_file)

    @staticmethod
    def _write_json_atomic(file_path, value):
        destination = Path(file_path)
        destination.parent.mkdir(parents=True, exist_ok=True)

        temporary_path = destination.with_name(
            destination.name + ".tmp"
        )

        with temporary_path.open(
            "w",
            encoding="utf-8",
            newline="\n",
        ) as json_file:
            json.dump(
                ModelRegistry._json_safe(value),
                json_file,
                indent=2,
                ensure_ascii=False,
                sort_keys=True,
            )
            json_file.write("\n")
            json_file.flush()
            os.fsync(json_file.fileno())

        os.replace(temporary_path, destination)

    def _load_registry(self):
        registry = self._read_json(self._registry_path)

        if registry.get("schema_version") != self.SCHEMA_VERSION:
            raise ValueError(
                "Unsupported model registry schema version"
            )

        return registry

    def _save_registry(self, registry):
        registry["updated_at"] = self._utc_now()
        self._write_json_atomic(self._registry_path, registry)

    def _version_directory(
        self,
        feature_name,
        model_type,
        model_version,
    ):
        safe_feature = self._safe_segment(
            feature_name,
            "feature_name",
        )
        safe_model_type = self._safe_segment(
            model_type,
            "model_type",
        )
        safe_version = self._safe_segment(
            model_version,
            "model_version",
        )

        return (
            self._store_directory
            / "features"
            / safe_feature
            / safe_model_type
            / safe_version
        )

    def register_artifact(
        self,
        source_path,
        feature_name,
        model_type,
        model_version=None,
        metadata=None,
        metrics=None,
        preprocessing=None,
        activate=False,
    ):
        source = Path(source_path).expanduser().resolve()

        if not source.is_file():
            raise FileNotFoundError(
                f"Model artifact does not exist: {source}"
            )

        extension = source.suffix.lower()

        if extension not in self.SUPPORTED_ARTIFACT_EXTENSIONS:
            raise ValueError(
                f"Unsupported model artifact extension: {extension}"
            )

        version = model_version or self._new_version()

        safe_feature = self._safe_segment(
            feature_name,
            "feature_name",
        )
        safe_model_type = self._safe_segment(
            model_type,
            "model_type",
        )
        safe_version = self._safe_segment(
            version,
            "model_version",
        )

        version_directory = self._version_directory(
            safe_feature,
            safe_model_type,
            safe_version,
        )

        if version_directory.exists():
            raise FileExistsError(
                "Model version already exists: "
                f"{safe_feature}/{safe_model_type}/{safe_version}"
            )

        version_directory.mkdir(parents=True, exist_ok=False)

        artifact_name = f"model{extension}"
        artifact_path = version_directory / artifact_name
        temporary_artifact_path = version_directory / (
            artifact_name + ".tmp"
        )

        try:
            shutil.copy2(source, temporary_artifact_path)
            os.replace(temporary_artifact_path, artifact_path)

            checksum = self._sha256(artifact_path)
            created_at = self._utc_now()

            artifact_relative_path = artifact_path.relative_to(
                self._store_directory
            ).as_posix()

            model_metadata = {
                "schema_version": self.SCHEMA_VERSION,
                "feature_name": str(feature_name),
                "feature_key": safe_feature,
                "model_type": str(model_type),
                "model_type_key": safe_model_type,
                "model_version": safe_version,
                "artifact_path": artifact_relative_path,
                "artifact_format": extension.lstrip("."),
                "artifact_sha256": checksum,
                "created_at": created_at,
                "python_version": platform.python_version(),
                "platform": platform.platform(),
                "package_versions": self._package_versions(),
                "metadata": metadata or {},
            }

            self._write_json_atomic(
                version_directory / "metadata.json",
                model_metadata,
            )
            self._write_json_atomic(
                version_directory / "metrics.json",
                metrics or {},
            )
            self._write_json_atomic(
                version_directory / "preprocessing.json",
                preprocessing or {},
            )

            registry_entry = {
                "artifact_path": artifact_relative_path,
                "artifact_sha256": checksum,
                "artifact_format": extension.lstrip("."),
                "created_at": created_at,
                "metadata_path": (
                    version_directory / "metadata.json"
                ).relative_to(
                    self._store_directory
                ).as_posix(),
                "metrics_path": (
                    version_directory / "metrics.json"
                ).relative_to(
                    self._store_directory
                ).as_posix(),
                "preprocessing_path": (
                    version_directory / "preprocessing.json"
                ).relative_to(
                    self._store_directory
                ).as_posix(),
            }

            with self._lock:
                registry = self._load_registry()

                registry.setdefault(
                    "models",
                    {},
                ).setdefault(
                    safe_feature,
                    {},
                ).setdefault(
                    safe_model_type,
                    {},
                )[safe_version] = registry_entry

                if activate:
                    registry.setdefault(
                        "active",
                        {},
                    ).setdefault(
                        safe_feature,
                        {},
                    )[safe_model_type] = safe_version

                self._save_registry(registry)

            return {
                "feature_name": safe_feature,
                "model_type": safe_model_type,
                "model_version": safe_version,
                **registry_entry,
            }

        except Exception:
            shutil.rmtree(
                version_directory,
                ignore_errors=True,
            )
            raise

    def set_active(
        self,
        feature_name,
        model_type,
        model_version,
    ):
        safe_feature = self._safe_segment(
            feature_name,
            "feature_name",
        )
        safe_model_type = self._safe_segment(
            model_type,
            "model_type",
        )
        safe_version = self._safe_segment(
            model_version,
            "model_version",
        )

        with self._lock:
            registry = self._load_registry()

            model_entry = (
                registry.get("models", {})
                .get(safe_feature, {})
                .get(safe_model_type, {})
                .get(safe_version)
            )

            if model_entry is None:
                raise KeyError(
                    "Model version was not found: "
                    f"{safe_feature}/{safe_model_type}/{safe_version}"
                )

            self._verify_entry(model_entry)

            registry.setdefault(
                "active",
                {},
            ).setdefault(
                safe_feature,
                {},
            )[safe_model_type] = safe_version

            self._save_registry(registry)

    def clear_active(
        self,
        feature_name,
        model_type,
        model_version=None,
    ):
        safe_feature = self._safe_segment(
            feature_name,
            "feature_name",
        )
        safe_model_type = self._safe_segment(
            model_type,
            "model_type",
        )
        safe_version = (
            self._safe_segment(model_version, "model_version")
            if model_version is not None else None
        )

        with self._lock:
            registry = self._load_registry()
            active = registry.get("active", {})
            feature_active = active.get(safe_feature, {})
            current_version = feature_active.get(safe_model_type)

            if current_version is None or (
                safe_version is not None
                and current_version != safe_version
            ):
                return False

            del feature_active[safe_model_type]

            if not feature_active:
                del active[safe_feature]

            self._save_registry(registry)
            return True

    def get_active(
        self,
        feature_name,
        model_type,
    ):
        safe_feature = self._safe_segment(
            feature_name,
            "feature_name",
        )
        safe_model_type = self._safe_segment(
            model_type,
            "model_type",
        )

        with self._lock:
            registry = self._load_registry()

            active_version = (
                registry.get("active", {})
                .get(safe_feature, {})
                .get(safe_model_type)
            )

            if active_version is None:
                return None

            model_entry = (
                registry.get("models", {})
                .get(safe_feature, {})
                .get(safe_model_type, {})
                .get(active_version)
            )

            if model_entry is None:
                raise KeyError(
                    "The active model points to a missing registry entry"
                )

            artifact_path = self._verify_entry(model_entry)

            return {
                "feature_name": safe_feature,
                "model_type": safe_model_type,
                "model_version": active_version,
                **model_entry,
                "artifact_path": str(artifact_path),
            }

    def _verify_entry(self, model_entry):
        relative_artifact_path = model_entry.get("artifact_path")

        if not relative_artifact_path:
            raise ValueError(
                "Model registry entry has no artifact path"
            )

        artifact_path = (
            self._store_directory / relative_artifact_path
        ).resolve()

        try:
            artifact_path.relative_to(self._store_directory)
        except ValueError as error:
            raise ValueError(
                "Model artifact path escapes the model store"
            ) from error

        if not artifact_path.is_file():
            raise FileNotFoundError(
                f"Model artifact does not exist: {artifact_path}"
            )

        expected_checksum = model_entry.get("artifact_sha256")
        actual_checksum = self._sha256(artifact_path)

        if expected_checksum != actual_checksum:
            raise ValueError(
                "Model artifact checksum verification failed"
            )

        return artifact_path

    def verify_active(
        self,
        feature_name,
        model_type,
    ):
        active_model = self.get_active(
            feature_name,
            model_type,
        )

        return active_model is not None


def main():
    if len(sys.argv) < 4:
        raise SystemExit(
            "Usage: model_registry.py activate|deactivate "
            "<feature_name> <model_type> [model_version]"
        )

    action = sys.argv[1]
    feature_name = sys.argv[2]
    model_type = sys.argv[3]
    registry = ModelRegistry()

    if action == "activate" and len(sys.argv) == 5:
        model_version = sys.argv[4]
        registry.set_active(
            feature_name,
            model_type,
            model_version,
        )
        result = {
            "feature_name": feature_name,
            "model_type": model_type,
            "model_version": model_version,
            "active": True,
        }
    elif action == "deactivate" and len(sys.argv) in (4, 5):
        model_version = sys.argv[4] if len(sys.argv) == 5 else None
        changed = registry.clear_active(
            feature_name,
            model_type,
            model_version,
        )
        result = {
            "feature_name": feature_name,
            "model_type": model_type,
            "model_version": model_version,
            "active": False,
            "changed": changed,
        }
    else:
        raise SystemExit(
            "Usage: model_registry.py activate|deactivate "
            "<feature_name> <model_type> [model_version]"
        )

    print(json.dumps(result))


if __name__ == "__main__":
    main()