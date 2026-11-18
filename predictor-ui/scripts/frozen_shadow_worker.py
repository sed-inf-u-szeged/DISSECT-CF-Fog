import contextlib
import hashlib
import json
import sys
import traceback
from pathlib import Path

OUT = sys.stdout
CUTOFF = 36_000_000
EXPECTED = {
    42: "0b5c8a95a4635c5b94daadeb5c29b2ab6a4f12ffa9520a63bab3bc31904b1951",
    43: "40fa45ca43dc68054b15b9843b942265316272c94ff783e77ac39f309f9fd267",
    44: "adf57c35f280a06ccac590ad60167bccc7cdf747c8592092c8b10ed1d90682b9",
}


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def save(path, value):
    path.write_text(
        json.dumps(value, indent=2, allow_nan=False),
        encoding="utf-8",
    )


def emit(file, value):
    file.write(json.dumps(value, allow_nan=False) + "\n")
    file.flush()


def ack(value):
    print(value, file=OUT, flush=True)


def run(export, output, seed):
    import numpy as np
    from predictor import Predictor

    predictors, settings, paths = {}, {}, {}

    settings["Arima128"] = {
        "predictor": {
            "predictor": "ARIMA",
            "hyperparameters": {
                "p_value": 3, "d_value": 0, "q_value": 0
            },
        },
        "prediction": {
            "length": 64,
            "testSize": 64,
            "batchSize": 128,
            "scale": True,
            "smoothing": {
                "windowSize": 48, "polynomialDegree": 5
            },
        },
    }

    for s, expected in EXPECTED.items():
        path = (
            export / f"combined_seed{s}" / "model.keras"
        ).resolve()
        if sha(path) != expected:
            raise ValueError(f"Frozen model hash mismatch: {path}")

        name = f"Combined{s}"
        paths[name] = path
        settings[name] = {
            "predictor": {
                "predictor": "LSTM",
                "hyperparameters": {
                    "future_model_location": str(path),
                    "test_model_location": str(path),
                },
            },
            "prediction": {
                "length": 64,
                "testSize": 64,
                "batchSize": 256,
                "scale": False,
                "smoothing": None,
            },
        }

    manifest = {
        "status": "running",
        "simulation_seed": seed,
        "mode": "live_shadow_only",
        "decision_policy": "Pliant without forecast input",
        "training": False,
        "promotion": False,
        "feedback": False,
        "first_origin": 256,
        "origin_stride": 64,
        "horizon": 64,
        "target_time_cutoff_ms": CUTOFF,
        "accuracy_rule": (
            "abs(pred-actual) <= 0.20*abs(actual); "
            "zero requires exact zero"
        ),
        "mean10_rule": (
            "abs(mean(pred[:10])-mean(actual[:10]))"
        ),
        "arima_nonconverged": (
            "included when finite; status retained"
        ),
        "model_hashes_before": {
            k: sha(p) for k, p in paths.items()
        },
        "settings": settings,
    }

    scripts = Path(__file__).resolve().parent
    sources = [
        Path(__file__),
        scripts / "predictor.py",
        scripts / "app_utils/preprocessor.py",
        scripts / "predictor_models/lstm_model.py",
        scripts / "predictor_models/arima_model.py",
    ]
    manifest["source_hashes"] = {
        str(p): sha(p) for p in sources
    }
    save(output / "shadow_manifest.json", manifest)

    for name, config in settings.items():
        predictors[name] = Predictor(config)

    histories, times, pending = {}, {}, {}
    stats = {
        k: {
            "windows": 0,
            "absolute_error_sum": 0.0,
            "mean10_error_sum": 0.0,
            "hits": 0,
            "nonzero_hits": 0,
            "nonzero_count": 0,
            "future_not_converged": 0,
        }
        for k in settings
    }
    scheduled, completed, excluded = 0, 0, 0

    with (output / "issued.jsonl").open(
        "w", encoding="utf-8"
    ) as issued, (output / "resolved.jsonl").open(
        "w", encoding="utf-8"
    ) as resolved:
        ack("READY")

        for line in sys.stdin:
            if line.rstrip("\r\n") == "END":
                break

            name, index, time_ms, value = (
                line.rstrip("\r\n").split("\t")
            )
            index = int(index)
            time_ms = int(time_ms)
            value = float(value)

            values = histories.setdefault(name, [])
            stamps = times.setdefault(name, [])

            if index != len(values) or not np.isfinite(value):
                raise ValueError(f"Invalid sample: {name}/{index}")
            if stamps and time_ms <= stamps[-1]:
                raise ValueError(
                    "Non-increasing simulation timestamps"
                )

            values.append(value)
            stamps.append(time_ms)
            n = len(values)

            old = pending.get(name)
            if old is not None and n == old["origin"] + 64:
                del pending[name]

                if time_ms > CUTOFF:
                    excluded += 1
                    emit(resolved, {
                        "feature": name,
                        "origin": old["origin"],
                        "status": "excluded_after_cutoff",
                    })
                else:
                    actual = np.array(
                        values[old["origin"]:n], dtype=float
                    )
                    record = {
                        "feature": name,
                        "origin": old["origin"],
                        "status": "complete",
                        "actual": actual.tolist(),
                        "target_times_ms": stamps[old["origin"]:n],
                        "metrics": {},
                    }

                    for model, pred in old["predictions"].items():
                        pred = np.array(pred, dtype=float)
                        error = np.abs(pred - actual)
                        hits = error <= 0.20 * np.abs(actual)
                        nz = actual != 0
                        m10 = float(abs(
                            pred[:10].mean() - actual[:10].mean()
                        ))

                        record["metrics"][model] = {
                            "mae64": float(error.mean()),
                            "mean10_error": m10,
                            "accuracy20_pct": float(
                                100 * hits.mean()
                            ),
                        }

                        st = stats[model]
                        st["windows"] += 1
                        st["absolute_error_sum"] += float(
                            error.sum()
                        )
                        st["mean10_error_sum"] += m10
                        st["hits"] += int(hits.sum())
                        st["nonzero_hits"] += int(hits[nz].sum())
                        st["nonzero_count"] += int(nz.sum())
                        status = old["statuses"][model] or {}
                        st["future_not_converged"] += int(
                            status.get("converged") is False
                        )

                    emit(resolved, record)
                    completed += 1

            if (
                n >= 256
                and (n - 256) % 64 == 0
                and time_ms < CUTOFF
            ):
                record = {
                    "feature": name,
                    "origin": n,
                    "issued_at_ms": time_ms,
                    "predictions": {},
                    "statuses": {},
                    "responses": {},
                }

                for model, predictor in predictors.items():
                    batch = settings[model]["prediction"]["batchSize"]
                    response = predictor.compute({
                        "name": name,
                        "values": values[-batch:],
                    })
                    future = response["prediction_future"]
                    pred = np.asarray(
                        future["data"], dtype=float
                    )

                    if (
                        response["feature_name"] != name
                        or pred.shape != (64,)
                        or not np.isfinite(pred).all()
                        or future["timestamp"]
                        != list(range(batch, batch + 64))
                    ):
                        raise ValueError(
                            f"Bad prediction or timestamp: "
                            f"{model}/{name}/{n}"
                        )

                    if model != "Arima128" and (pred < 0).any():
                        raise ValueError(
                            "Exported LSTM produced negative backlog"
                        )

                    record["predictions"][model] = pred.tolist()
                    record["statuses"][model] = (
                        response["prediction_status"]["future"]
                    )
                    record["responses"][model] = response

                emit(issued, record)
                pending[name] = record
                scheduled += 1

            ack("OK")
        else:
            raise RuntimeError("Java input closed without END")

        for name, old in pending.items():
            emit(resolved, {
                "feature": name,
                "origin": old["origin"],
                "status": "incomplete",
                "matched_points": (
                    len(histories[name]) - old["origin"]
                ),
            })

    if len(histories) != 9 or completed == 0:
        raise RuntimeError(
            "Expected 9 fog features and completed windows: "
            f"{len(histories)}, {completed}"
        )

    if scheduled != completed + excluded + len(pending):
        raise RuntimeError("Window accounting mismatch")

    for st in stats.values():
        w = st["windows"]
        st["mae64"] = st["absolute_error_sum"] / (64 * w)
        st["mean10_mae"] = st["mean10_error_sum"] / w
        st["accuracy20_pct"] = 100 * st["hits"] / (64 * w)
        st["nonzero_accuracy20_pct"] = (
            100 * st["nonzero_hits"] / st["nonzero_count"]
            if st["nonzero_count"] else None
        )

    manifest["model_hashes_after"] = {
        k: sha(p) for k, p in paths.items()
    }
    if (
        manifest["model_hashes_before"]
        != manifest["model_hashes_after"]
    ):
        raise RuntimeError("Frozen model changed during run")

    save(output / "summary.json", {
        "scheduled_windows": scheduled,
        "complete_windows": completed,
        "excluded_after_cutoff": excluded,
        "incomplete_windows": len(pending),
        "features": sorted(histories),
        "models": stats,
    })
    manifest["status"] = "completed"
    save(output / "shadow_manifest.json", manifest)
    ack("DONE")


if __name__ == "__main__":
    export = Path(sys.argv[1])
    output = Path(sys.argv[2])
    try:
        with contextlib.redirect_stdout(sys.stderr):
            run(export, output, int(sys.argv[3]))
    except Exception:
        save(output / "failure.json", {
            "status": "failed",
            "traceback": traceback.format_exc(),
        })
        traceback.print_exc(file=sys.stderr)
        sys.exit(1)