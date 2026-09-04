"""Track-B calibration (§8.4, §9 metrics) — fits the analytic compute-delay and
power parameters to Track-B measurements, validated on held-out rounds so
calibration and validation never share data.

Inputs:
  * Track-B ``train_time_ms`` and ``nSamples`` per node per round (extracted
    from the bridge responses), and
  * host power samples (Watts) from the chosen instrument.

MODEL FORM. Local training cost is dominated by how much local data a node
holds, so the delay is fitted **affine in the shard size**::

    train_time_ms ≈ intercept_ms + ms_per_sample · n_samples

per device tier, by ordinary least squares. An earlier version fitted a
*constant* per tier; that ignores ``n_samples``, which under a skewed Dirichlet
partition is the single largest driver of training time (shard sizes span
orders of magnitude), and it consequently failed its held-out tolerance. The
affine form is both the physically sensible one and the one the simulator
actually consumes (``ComputeDelayModel``).

WARMUP. The first ``--warmup-rounds`` rounds are excluded from the fit: the
initial round carries interpreter/allocator/JIT warmup that is not part of
steady-state per-round cost, and leaving it in biases the intercept upward and
inflates the held-out error. They are excluded from validation too, so the
criterion measures the steady-state model rather than a transient.

Fit on the first half of the remaining rounds; validate on the held-out second
half (per-round relative error). Emits ``calibration.json`` (consumed by
``ComputeDelayModel`` on the Java side) + ``calibration_report.csv``.

HOST-POWER LIMITATION (R2/R7): real per-tier Watts require a physical
instrument (RAPL/powercap on Linux, IPMI/BMC, or a declared smart-plug). None
is available on a Windows dev box, so when no ``--power-csv`` is supplied this
module fits power from a clearly-labelled PLACEHOLDER trace and records
``instrument="PLACEHOLDER"`` — the compute-delay fit from real ``train_time_ms``
is exact; absolute Watts are indicative until calibrated on hardware.
"""
from __future__ import annotations

import argparse
import csv
import json
from pathlib import Path
from typing import Dict, List, Optional, Tuple

import numpy as np


#: One measurement: (node, round, train_time_ms, n_samples).
Sample = Tuple[int, int, float, int]


def extract_train_times(bridge_dir: str | Path) -> List[Sample]:
    """Reads ``(node, round, train_time_ms, n_samples)`` from the Track-B responses."""
    bridge = Path(bridge_dir)
    out: List[Sample] = []
    for rd in sorted(bridge.glob("round_*")):
        resp = rd / "response.json"
        if not resp.exists():
            continue
        data = json.loads(resp.read_text(encoding="utf-8"))
        rnd = int(data["round"])
        for node_s, m in data["perNode"].items():
            out.append((int(node_s), rnd, float(m["trainTimeMs"]), int(m.get("nSamples", 0))))
    return out


def _lstsq_affine(x: np.ndarray, y: np.ndarray) -> Tuple[float, float]:
    """OLS fit of ``y = intercept + slope·x``. Degenerate x (all equal, or a
    single point) falls back to a constant model, which is the best available
    predictor in that case."""
    if x.size == 0:
        return 0.0, 0.0
    if x.size < 2 or float(np.ptp(x)) == 0.0:
        return float(y.mean()), 0.0
    design = np.column_stack([np.ones_like(x, dtype=np.float64), x.astype(np.float64)])
    coef, *_ = np.linalg.lstsq(design, y.astype(np.float64), rcond=None)
    return float(coef[0]), float(coef[1])


def fit(train_times: List[Sample], node_tier: Dict[int, str],
        power_samples: Optional[List[Tuple[int, int, float]]] = None,
        split_frac: float = 0.5, warmup_rounds: int = 1,
        device_scale: Optional[Dict[str, float]] = None) -> Tuple[dict, List[dict]]:
    """Fits the per-tier affine compute delay + power on the first
    ``split_frac`` of the post-warmup rounds; validates on the rest. Returns
    ``(calibration_dict, report_rows)``."""
    all_rounds = sorted({r for (_, r, _, _) in train_times})
    if not all_rounds:
        raise ValueError("no train-time samples to calibrate from")
    # Drop warmup rounds from BOTH fit and validation: they are a transient, not
    # part of the steady-state per-round cost the simulator models.
    warm = set(all_rounds[:max(0, warmup_rounds)])
    rounds = [r for r in all_rounds if r not in warm]
    if not rounds:
        rounds = all_rounds
        warm = set()
    n_fit = max(1, int(len(rounds) * split_frac))
    fit_rounds = set(rounds[:n_fit])
    val_rounds = set(rounds[n_fit:]) or set(rounds[:n_fit])  # tiny runs: validate on fit set

    # Per-tier affine fit (train_time_ms ~ n_samples) on the fit rounds.
    by_tier_x: Dict[str, List[float]] = {}
    by_tier_y: Dict[str, List[float]] = {}
    for (node, rnd, ms, ns) in train_times:
        if rnd in fit_rounds:
            tier = node_tier[node]
            by_tier_x.setdefault(tier, []).append(float(ns))
            by_tier_y.setdefault(tier, []).append(ms)
    tier_fit = {
        tier: _lstsq_affine(np.array(by_tier_x[tier]), np.array(by_tier_y[tier]))
        for tier in by_tier_x
    }

    scale = dict(device_scale or {})

    # Power fit.
    instrument = "MEASURED"
    if power_samples is None:
        instrument = "PLACEHOLDER"
        # Indicative placeholder: idle/active band scaled off the compute load.
        power = {tier: {"powerMinW": 5.0, "powerMaxW": 15.0} for tier in tier_fit}
    else:
        by_tier_pw: Dict[str, List[float]] = {}
        for (node, rnd, w) in power_samples:
            if rnd in fit_rounds:
                by_tier_pw.setdefault(node_tier[node], []).append(w)
        power = {tier: {"powerMinW": float(np.min(v)), "powerMaxW": float(np.max(v))}
                 for tier, v in by_tier_pw.items()}

    # Validate on held-out rounds: relative error per (node, round).
    report_rows: List[dict] = []
    rel_errs: List[float] = []
    for (node, rnd, ms, ns) in train_times:
        if rnd in val_rounds:
            tier = node_tier[node]
            intercept, slope = tier_fit.get(tier, (ms, 0.0))
            predicted = intercept + slope * ns
            rel = abs(predicted - ms) / ms if ms > 0 else 0.0
            rel_errs.append(rel)
            report_rows.append({"node": node, "round": rnd, "tier": tier,
                                "n_samples": ns,
                                "measured_ms": ms, "predicted_ms": predicted,
                                "rel_error": rel})

    calibration = {
        "instrument": instrument,
        "model": "affine_in_n_samples",
        "split_frac": split_frac,
        "warmup_rounds_excluded": sorted(warm),
        "fit_rounds": sorted(fit_rounds),
        "validation_rounds": sorted(val_rounds),
        "tiers": {
            tier: {
                "interceptMs": tier_fit[tier][0],
                "msPerSample": tier_fit[tier][1],
                "deviceScale": float(scale.get(tier, 1.0)),
                **power.get(tier, {}),
            }
            for tier in tier_fit
        },
        "validation_mean_rel_error": float(np.mean(rel_errs)) if rel_errs else 0.0,
        "validation_median_rel_error": float(np.median(rel_errs)) if rel_errs else 0.0,
        "validation_pairs": len(rel_errs),
    }
    return calibration, report_rows


def write_outputs(out_dir: str | Path, calibration: dict, report_rows: List[dict]) -> None:
    out = Path(out_dir)
    out.mkdir(parents=True, exist_ok=True)
    (out / "calibration.json").write_text(json.dumps(calibration, indent=2), encoding="utf-8")
    with open(out / "calibration_report.csv", "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["node", "round", "tier", "n_samples", "measured_ms", "predicted_ms", "rel_error"])
        for r in report_rows:
            w.writerow([r["node"], r["round"], r["tier"], r.get("n_samples", 0),
                        f"{r['measured_ms']:.6f}", f"{r['predicted_ms']:.6f}", f"{r['rel_error']:.6f}"])


def _node_tiers_from_trace(system_trace_path: str) -> Dict[int, str]:
    import sys
    sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "harness"))
    from system_trace import load as load_system_trace
    trace = load_system_trace(system_trace_path)
    return {node.id: node.profile for node in trace.nodes}


def _device_scale_from_trace(system_trace_path: str) -> Dict[str, float]:
    """Host→device multiplier per tier from the modelled device throughput.

    The harness measures wall-clock time on the calibration host; the simulated
    node is a fog device with its own capability. Scaling by the ratio of the
    fastest modelled tier's throughput to each tier's makes the reference tier
    1.0 and every slower tier proportionally slower, so the device model — not
    the calibration machine — sets relative round durations.
    """
    import sys
    sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "harness"))
    from system_trace import load as load_system_trace
    trace = load_system_trace(system_trace_path)
    throughput = {}
    for node in trace.nodes:
        throughput.setdefault(node.profile, node.cores * node.mips)
    if not throughput:
        return {}
    reference = max(throughput.values())
    return {tier: (reference / t if t > 0 else 1.0) for tier, t in throughput.items()}


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description="Track-B calibration")
    ap.add_argument("--bridge", required=True, help="Track-B bridge dir with round responses")
    ap.add_argument("--system-trace", required=True, help="for per-node device tiers")
    ap.add_argument("--power-csv", default=None, help="optional node,round,watts samples")
    ap.add_argument("--out", required=True)
    ap.add_argument("--split-frac", type=float, default=0.5)
    ap.add_argument("--warmup-rounds", type=int, default=1,
                    help="leading rounds excluded from both fit and validation")
    ap.add_argument("--no-device-scale", action="store_true",
                    help="keep deviceScale=1.0 for every tier (host-equivalent timing)")
    args = ap.parse_args(argv)

    train_times = extract_train_times(args.bridge)
    node_tier = _node_tiers_from_trace(args.system_trace)
    scale = {} if args.no_device_scale else _device_scale_from_trace(args.system_trace)
    power_samples = None
    if args.power_csv:
        power_samples = []
        with open(args.power_csv, newline="", encoding="utf-8") as f:
            for row in csv.DictReader(f):
                power_samples.append((int(row["node"]), int(row["round"]), float(row["watts"])))

    calibration, report = fit(train_times, node_tier, power_samples, args.split_frac,
                              warmup_rounds=args.warmup_rounds, device_scale=scale)
    write_outputs(args.out, calibration, report)
    print(f"[calibration] instrument={calibration['instrument']} "
          f"model={calibration['model']} tiers={list(calibration['tiers'])} "
          f"val_mean_rel_error={calibration['validation_mean_rel_error']:.4f} "
          f"(median {calibration['validation_median_rel_error']:.4f}, "
          f"n={calibration['validation_pairs']}) -> {args.out}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
