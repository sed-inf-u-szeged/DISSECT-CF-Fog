import numpy as np

import diagnose_lstm_nodes as diagnostic


def horizon_report(actual, predicted, policy):
    actual = np.asarray(actual, dtype=float)
    predicted = np.asarray(predicted, dtype=float)

    if actual.shape != predicted.shape or actual.ndim != 1:
        raise ValueError("Expected matching one-dimensional arrays")
    if actual.size < 64:
        raise ValueError("Expected at least 64 future points")
    if not np.all(np.isfinite(actual)) or not np.all(np.isfinite(predicted)):
        raise ValueError("Non-finite evaluation values")

    print(
        "  Range       | Points | MAE(bytes) | RMSE(bytes) | Accuracy%",
        flush=True,
    )
    ranges = [
        ("First 1", 0, 1),
        ("First 10", 0, 10),
        ("Steps 11-64", 10, 64),
        ("All 64", 0, 64),
    ]

    for label, start, end in ranges:
        result = diagnostic.metrics(
            actual[start:end], predicted[start:end], policy
        )
        print(
            f"  {label:<11} | {result['points']:6d} | "
            f"{result['MAE']:10.2f} | {result['RMSE']:11.2f} | "
            f"{result['accuracy_pct']:9.2f}",
            flush=True,
        )

    actual_mean = float(np.mean(actual[:10]))
    predicted_mean = float(np.mean(predicted[:10]))
    mean_error = abs(predicted_mean - actual_mean)
    mean_tolerance = max(
        float(policy["absoluteTolerance"]),
        float(policy["accuracyTolerance"]) * abs(actual_mean),
    )

    print(
        "  First-10 average comparison (one aggregate per block):",
        flush=True,
    )
    print(
        f"    Actual average:    {actual_mean:.2f} bytes\n"
        f"    Predicted average: {predicted_mean:.2f} bytes\n"
        f"    Absolute error:    {mean_error:.2f} bytes\n"
        f"    Allowed error:     {mean_tolerance:.2f} bytes\n"
        f"    Within tolerance:  {mean_error <= mean_tolerance}",
        flush=True,
    )


if __name__ == "__main__":
    # Change diagnostic reporting only; reuse the existing training workflow.
    diagnostic.tolerance_report = horizon_report
    diagnostic.main()
    print("HORIZON_COMPARISON_DONE", flush=True)