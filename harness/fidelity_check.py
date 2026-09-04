"""ERQ1 — the fidelity gate (§9). Runs Track B against a released cell and
writes the F1–F4 criteria INTO that cell's artefact directory.

    py -3.12 harness/fidelity_check.py --cell simulator/results/paper/V/cell/seed_11

Outputs, all under ``<cell>/trackb/``::

    bridge/round_XXXX/{request,response}.json   the online handshake
    calib/calibration.json                      the fitted compute-delay model
    fidelity.json                               the F1-F4 verdict

WHAT MAKES THIS FALSIFIABLE. Track B is launched with ``--independent-selection``:
it derives its own peer sets each round from its own evolving signature cache,
rather than being handed Track A's decisions. If the two sides were driven from
one decision list they would be the same deterministic function of the same
inputs, their agreement would be guaranteed by construction, and the comparison
would restate determinism instead of testing fidelity. With independent
selection, any divergence between the batch trajectory (Track A) and the
round-driven one (Track B) — a mishandled dynamic edge schedule, a desynchronised
RNG stream, a cache updated at the wrong point, a restart that corrupts model
state — shows up as F1 < 1 and, downstream, as a non-zero F3.

Criteria:
  F1  selection-agreement rate, Track A vs independently-deciding Track B  == 1.0
  F2  signature-space vs full-weight pairwise distance ratio               in [0.9, 1.1]
  F3  held-out accuracy MAE between the two tracks                         <= 0.01
  F4  held-out relative error of the calibrated compute-delay model        <= 0.10
"""
from __future__ import annotations

import argparse
import csv
import json
import subprocess
import sys
import time
from pathlib import Path
from typing import Dict, List

import numpy as np

REPO = Path(__file__).resolve().parent.parent
HARNESS = REPO / "harness"
ANALYSIS = REPO / "analysis"

F2_TOL = (0.9, 1.1)
F3_TOL = 0.01
F4_TOL = 0.10


def _py() -> List[str]:
    """The interpreter used for the child processes (this one, by default)."""
    return [sys.executable]


def _wait(marker: Path, proc: subprocess.Popen, timeout_s: float) -> None:
    deadline = time.time() + timeout_s
    while not marker.exists():
        if proc.poll() is not None:
            raise RuntimeError("Track-B worker exited early:\n" + (proc.stdout.read() or ""))
        if time.time() > deadline:
            raise TimeoutError(f"timed out waiting for {marker}")
        time.sleep(0.05)


def _read_track_a(pass2: Path):
    """Peer sets and held-out accuracy per (round, node) from the Track-A trace."""
    peers: Dict[int, Dict[int, List[int]]] = {}
    acc: Dict[tuple, float] = {}
    with open(pass2 / "learning_trace.csv", newline="", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        if "heldout_acc" not in (reader.fieldnames or []):
            raise SystemExit(
                f"{pass2/'learning_trace.csv'} has no heldout_acc column.\n"
                "This trace predates the held-out split, so its accuracies are "
                "training-pool figures and cannot be compared against Track B.\n"
                "It is almost certainly a leftover from a previous campaign: delete or "
                "archive the cell and re-run the scenario before the fidelity gate.")
        for row in reader:
            r, node = int(row["round"]), int(row["node"])
            peers.setdefault(r, {})[node] = [int(x) for x in (row.get("peers") or "").split(";") if x]
            acc[(r, node)] = float(row["heldout_acc"])
    return peers, acc


#: Workload keys the gate must know to configure Track B, and the flag that
#: overrides each one.
_WORKLOAD_KEYS = ("dataset", "samples", "eval_samples", "model", "num_classes",
                  "in_channels", "epochs", "lr", "batch", "init_seed")


def _read_workload(pass2: Path, args) -> dict:
    """Resolves Track B's workload from the cell, with CLI flags as overrides.

    The gate compares Track A against Track B, so Track B must train on the
    workload Track A *actually used* — recorded in ``pass2/summary.json`` by
    ``run.py``. Reading it from the cell rather than defaulting to constants is
    what stops the gate from validating a workload nobody ran: previously these
    fell back to a hardcoded ``mnist/12000/0``, so editing ``V.yaml`` would leave
    the gate silently measuring the old workload and still reporting a pass.
    """
    summary_path = pass2 / "summary.json"
    if not summary_path.exists():
        raise SystemExit(f"no Track-A summary at {summary_path} — run the V scenario first")
    recorded = json.loads(summary_path.read_text(encoding="utf-8")).get("workload")

    overrides = {k: getattr(args, k) for k in _WORKLOAD_KEYS
                 if getattr(args, k, None) is not None}
    if recorded is None:
        missing = [k for k in _WORKLOAD_KEYS if k not in overrides]
        if missing:
            cell_dir = pass2.parent
            raise SystemExit(
                f"{summary_path} records no 'workload' block, so the gate cannot tell "
                f"what Track A trained on.\n"
                f"This cell predates workload recording. Note that simply re-running "
                f"the scenario will NOT fix it: the runner resumes any leg whose "
                f"pass2/ already exists, so the summary is never rewritten. Delete the "
                f"cell so Pass 2 re-trains:\n"
                f"    rm -rf '{cell_dir}'\n"
                f"    sh tools/run_campaign.sh V\n"
                f"Alternatively pass every workload flag explicitly — currently "
                f"missing: {', '.join(missing)}.\n"
                f"Guessing is not an option here: the gate exists to detect divergence "
                f"between the two tracks, and a wrong guess would hide it.")
        return dict(overrides)

    spec = {k: recorded[k] for k in _WORKLOAD_KEYS if k in recorded}
    absent = [k for k in _WORKLOAD_KEYS if k not in spec and k not in overrides]
    if absent:
        raise SystemExit(
            f"{summary_path} has a 'workload' block missing {', '.join(absent)}. "
            f"Re-run the V scenario, or supply those flags explicitly.")
    spec.update(overrides)
    if overrides:
        shown = ", ".join(f"{k}={overrides[k]}" for k in sorted(overrides))
        print(f"[fidelity] WARNING: overriding the cell's recorded workload ({shown}). "
              f"Track B will not train on what Track A trained on.")
    return spec


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description="ERQ1 fidelity gate")
    ap.add_argument("--cell", required=True, help="path to the V cell (…/V/cell/seed_11)")
    # Every workload flag defaults to None: the value is read from the cell's own
    # pass2/summary.json, so Track B trains on what Track A trained on. Passing one
    # explicitly overrides it, which is only meaningful for probing the gate itself.
    ap.add_argument("--dataset", default=None, help="override; else read from the cell")
    ap.add_argument("--samples", type=int, default=None)
    ap.add_argument("--eval-samples", type=int, default=None)
    ap.add_argument("--num-classes", type=int, default=None)
    ap.add_argument("--in-channels", type=int, default=None)
    ap.add_argument("--model", default=None)
    ap.add_argument("--epochs", type=int, default=None)
    ap.add_argument("--lr", type=float, default=None)
    ap.add_argument("--batch", type=int, default=None)
    ap.add_argument("--init-seed", type=int, default=None)
    ap.add_argument("--round-timeout-s", type=float, default=3600.0)
    args = ap.parse_args(argv)

    cell = Path(args.cell).resolve()
    trace_path = cell / "pass1" / "system_trace.json"
    pass2 = cell / "pass2"
    if not trace_path.exists():
        raise SystemExit(f"no Pass-1 trace at {trace_path} — run the V scenario first")
    if not (pass2 / "learning_trace.csv").exists():
        raise SystemExit(f"no Track-A trace at {pass2} — run the V scenario first")

    meta = json.loads(trace_path.read_text(encoding="utf-8"))
    rounds, n = meta["hyper"]["rounds"], meta["n"]

    work = cell / "trackb"
    bridge, calib = work / "bridge", work / "calib"
    bridge.mkdir(parents=True, exist_ok=True)
    calib.mkdir(parents=True, exist_ok=True)

    work_spec = _read_workload(pass2, args)
    dataset, samples = work_spec["dataset"], work_spec["samples"]
    eval_samples = work_spec["eval_samples"]

    data_args = ["--dataset", dataset, "--samples", str(samples),
                 "--eval-samples", str(eval_samples),
                 "--num-classes", str(work_spec["num_classes"]),
                 "--in-channels", str(work_spec["in_channels"]),
                 "--model", work_spec["model"],
                 "--epochs", str(work_spec["epochs"]), "--lr", str(work_spec["lr"]),
                 "--batch", str(work_spec["batch"]),
                 "--init-seed", str(work_spec["init_seed"])]

    peers_a, acc_a = _read_track_a(pass2)
    print(f"[fidelity] cell={cell.name} rounds={rounds} n={n} dataset={dataset} "
          f"samples={samples} eval_samples={eval_samples} (read from the cell)")

    # Launch the worker with INDEPENDENT selection — the whole point of the gate.
    worker = subprocess.Popen(
        _py() + [str(HARNESS / "trackb_worker.py"), "--bridge", str(bridge),
                 "--system-trace", str(trace_path), "--rounds", str(rounds),
                 "--independent-selection"] + data_args,
        cwd=HARNESS, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)

    acc_b: Dict[tuple, float] = {}
    peers_b: Dict[int, Dict[int, List[int]]] = {}
    distortions: List[float] = []
    try:
        for t in range(rounds):
            rd = bridge / f"round_{t:04d}"
            rd.mkdir(parents=True, exist_ok=True)
            # peerSets left empty: the worker decides for itself.
            (rd / "request.json").write_text(json.dumps({
                "round": t, "mode": "gossip",
                "mergeRule": meta["hyper"]["mergeRule"],
                "configHash": "fidelity", "timeoutS": args.round_timeout_s,
                "participants": list(range(n)), "peerSets": {},
            }), encoding="utf-8")
            (rd / "request.READY").write_bytes(b"")
            _wait(rd / "response.READY", worker, args.round_timeout_s)
            resp = json.loads((rd / "response.json").read_text(encoding="utf-8"))
            for i in range(n):
                m = resp["perNode"][str(i)]
                acc_b[(t, i)] = float(m["heldoutAcc"])
            peers_b[t] = {int(k): list(v) for k, v in (resp.get("peerSets") or {}).items()}
            if resp.get("signatureDistortion"):
                distortions.append(resp["signatureDistortion"]["meanRatio"])
            if (t + 1) % 5 == 0 or t == rounds - 1:
                print(f"[fidelity] round {t + 1}/{rounds}")
        worker.wait(timeout=300)
    finally:
        if worker.poll() is None:
            worker.kill()

    # F1 — selection agreement between the two independently-deciding tracks.
    matches = total = 0
    for t in range(rounds):
        for i in range(n):
            total += 1
            if set(peers_a.get(t, {}).get(i, [])) == set(peers_b.get(t, {}).get(i, [])):
                matches += 1
    f1 = matches / total if total else 1.0

    # F2 — signature-space distortion (Track B holds the full-precision weights).
    f2_mean = float(np.mean(distortions)) if distortions else float("nan")
    f2_std = float(np.std(distortions)) if distortions else float("nan")

    # F3 — held-out accuracy MAE between the tracks.
    pairs = [(acc_a[k], acc_b[k]) for k in acc_b
             if k in acc_a and not np.isnan(acc_a[k]) and not np.isnan(acc_b[k])]
    f3 = float(np.mean([abs(a - b) for a, b in pairs])) if pairs else float("nan")

    # F4 — calibrated compute-delay model, validated on held-out rounds.
    subprocess.run(_py() + [str(ANALYSIS / "calibration.py"), "--bridge", str(bridge),
                            "--system-trace", str(trace_path), "--out", str(calib)],
                   cwd=ANALYSIS, check=True)
    cal = json.loads((calib / "calibration.json").read_text(encoding="utf-8"))
    f4 = float(cal["validation_mean_rel_error"])

    criteria = [
        {"id": "F1", "criterion": "Selection-agreement rate (independent Track B)",
         "tolerance": "= 1.0 exact", "measured": round(f1, 6), "pass": f1 == 1.0},
        {"id": "F2", "criterion": "Signature-distortion ratio",
         "tolerance": f"[{F2_TOL[0]}, {F2_TOL[1]}]",
         "measured": f"{f2_mean:.4f} ± {f2_std:.4f}",
         "pass": bool(F2_TOL[0] <= f2_mean <= F2_TOL[1])},
        {"id": "F3", "criterion": "Track A vs B held-out accuracy MAE",
         "tolerance": f"<= {F3_TOL}", "measured": round(f3, 6), "pass": bool(f3 <= F3_TOL)},
        {"id": "F4", "criterion": "Held-out compute-delay relative error",
         "tolerance": f"<= {int(F4_TOL * 100)}%", "measured": f"{f4 * 100:.1f}%",
         "pass": bool(f4 <= F4_TOL)},
    ]
    out = {
        "cell": str(cell),
        "rounds": rounds, "n": n, "node_rounds_compared": total,
        "accuracy_pairs_compared": len(pairs),
        "selection_source_track_b": "independent",
        # The workload both tracks ran on, so the verdict states what it validated
        # rather than leaving a reader to assume it matched the cell.
        "workload": work_spec,
        "criteria": criteria,
        "all_pass": all(c["pass"] for c in criteria),
        "calibration": {k: cal[k] for k in
                        ("model", "instrument", "warmup_rounds_excluded",
                         "validation_mean_rel_error", "validation_median_rel_error",
                         "validation_pairs")},
        "note": ("Track B derived its own peer sets, so F1 and F3 are measurements "
                 "of the online coupling rather than restatements of determinism."),
    }
    (work / "fidelity.json").write_text(json.dumps(out, indent=2), encoding="utf-8")

    print("\n===== ERQ1 fidelity =====")
    for c in criteria:
        print(f"  {c['id']}  {c['criterion']:<52} {str(c['measured']):>16}  "
              f"tol {c['tolerance']:<14} {'PASS' if c['pass'] else 'FAIL'}")
    print(f"\n  wrote {work / 'fidelity.json'}")
    return 0 if out["all_pass"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
