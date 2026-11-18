import argparse
import json
from datetime import datetime
from pathlib import Path

import numpy as np
import pandas as pd

from app_utils.preprocessor import Preprocessor
from predictor_models.arima_model import ArimaModel


def metrics(actual, predicted, policy):
    predicted = np.asarray(predicted, dtype=float)
    if predicted.shape != actual.shape or not np.all(np.isfinite(predicted)):
        raise ValueError("Invalid prediction shape or non-finite values")

    error = np.abs(predicted - actual)
    tolerance = np.maximum(
        float(policy["absoluteTolerance"]),
        float(policy["accuracyTolerance"]) * np.abs(actual),
    )
    return {
        "matched": len(actual),
        "MAE64": float(error.mean()),
        "RMSE64": float(np.sqrt(np.mean(error ** 2))),
        "accuracy64_pct": float(100 * np.mean(error <= tolerance)),
        "mean10_MAE": float(
            abs(predicted[:10].mean() - actual[:10].mean())
        ),
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-dir", type=Path, required=True)
    args = parser.parse_args()
    source = args.source_dir.resolve()

    configs = list(source.parent.glob("simulation_settings_*.json"))
    if len(configs) != 1:
        raise ValueError("Expected one simulation settings file")

    config = json.loads(configs[0].read_text(encoding="utf-8-sig"))
    candidates = [
        item for item in config["predictionSettings"]
        if item["predictor"]["predictor"] == "ARIMA"
    ]
    if len(candidates) != 1:
        raise ValueError("Expected exactly one ARIMA configuration")

    arima_settings = candidates[0]
    prediction_settings = arima_settings["prediction"]
    batch_size = int(prediction_settings["batchSize"])
    if batch_size < 1 or int(prediction_settings["length"]) != 64:
        raise ValueError("Expected positive ARIMA history and 64 outputs")

    metadata = json.loads(
        (source / "metadata.json").read_text(encoding="utf-8-sig")
    )
    policy = metadata["lifecycleSettings"]
    files = sorted(source.glob("node-*.npz"))
    if not files:
        raise ValueError("No saved node predictions found")

    output = source.parent / (
        "quality-arima-first-"
        + datetime.now().strftime("%Y%m%d-%H%M%S-%f")
    )
    output.mkdir()
    (output / "metadata.json").write_text(
        json.dumps({
            "source_dir": str(source),
            "arima_settings": arima_settings,
            "lifecycle_policy": policy,
            "note": (
                "One common forecast origin per node; 64 unique targets. "
                "Saved LSTM_small forecasts are from the original "
                "per-node diagnostic, not the production shared model."
            ),
        }, indent=2),
        encoding="utf-8",
    )

    model = ArimaModel(arima_settings)
    results = []

    for index, path in enumerate(files, 1):
        with np.load(path, allow_pickle=False) as data:
            feature = str(data["feature"].item())
            raw = data["raw"].copy()
            start = int(data["starts"][0])
            actual = data["actual"][0].copy()
            predictions = {
                name: data[name][0].copy()
                for name in ["LR", "LastValue", "LSTM_small"]
            }

        if start < max(batch_size, 10):
            raise ValueError(f"{feature}: insufficient history")
        if actual.shape != (64,) or not np.array_equal(
            actual, raw[start:start + 64]
        ):
            raise ValueError(f"{feature}: target mismatch")

        print(f"ARIMA {index}/{len(files)} {feature}", flush=True)
        processed, scaler = Preprocessor.process(
            raw[start - batch_size:start],
            prediction_settings.get("smoothing"),
            prediction_settings["scale"],
        )
        forecast = model.predict(
            feature_name=feature,
            dataframe=processed,
            prediction_length=64,
            is_test_data=False,
        )
        predictions["ARIMA"] = np.asarray(
            Preprocessor.get_data_inverse(forecast, scaler),
            dtype=float,
        )
        predictions["PastMean10"] = np.full(
            64, raw[start - 10:start].mean()
        )

        for name, predicted in predictions.items():
            row = {
                "feature": feature,
                "model": name,
                "origin": start,
                **metrics(actual, predicted, policy),
            }
            results.append(row)
            print(
                f"  {name:<12} "
                f"accuracy={row['accuracy64_pct']:.2f}%  "
                f"MAE64={row['MAE64']:.2f}  "
                f"mean10_MAE={row['mean10_MAE']:.2f}",
                flush=True,
            )

        np.savez_compressed(
            output / path.name,
            feature=np.asarray(feature),
            start=np.asarray(start),
            actual=actual,
            **predictions,
        )
        pd.DataFrame(results).to_csv(output / "summary.csv", index=False)

    columns = ["MAE64", "RMSE64", "accuracy64_pct", "mean10_MAE"]
    print("\nEqual-weight averages across nodes:")
    print(
        pd.DataFrame(results).groupby("model")[columns]
        .mean().round(2).to_string()
    )
    print(f"\nRESULT_DIR: {output}")
    print("ARIMA_FIRST_BATCH_DONE")


if __name__ == "__main__":
    main()