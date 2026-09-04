"""Writes the Pass-2 learning trace (§8.4) in the exact wire format the Java
``TraceReader`` consumes: ``learning_trace.csv`` + ``signatures.bin`` (raw
little-endian float32) + ``signatures_index.json``.

Signature convention (mirrors FLGossipOrchestrator): ``signatures[node, t]`` is
the model entering round ``t`` (slot 0 = initial model, slot T = final model),
so there are ``T+1`` slots per node. The CSV has one metrics row per
``(node, round)`` for rounds ``0..T-1``.
"""
from __future__ import annotations

import json
from pathlib import Path
from typing import Dict, List

import numpy as np

#: ``acc`` is the local fit accuracy on the node's own training shard (a
#: training diagnostic); ``heldout_acc`` is the accuracy on the disjoint
#: held-out pool and is the only reportable figure (§9 metrics). Both are
#: carried so the replay can report the held-out value while the local one stays
#: available for diagnosing straggler/overfit behaviour. ``heldout_acc`` is NaN
#: on rounds outside the evaluation cadence.
CSV_COLUMNS = [
    "schema_version", "scenario_id", "topology_hash", "policy_id", "merge_rule",
    "gamma_schedule", "seed", "node", "round", "acc", "heldout_acc", "loss", "delta_norm",
    "payload_bytes", "train_time_ms", "n_samples", "peers", "merge_weights", "staleness_max",
]


def _fmt_float(v: float) -> str:
    # repr() round-trips a float exactly; the Java side parses with Double.parseDouble.
    return repr(float(v))


def write_trace(out_dir: str | Path, *, scenario_id: str, topology_hash: str,
                policy_id: str, merge_rule: str, gamma_schedule: str, seed: int,
                n: int, rounds: int, d: int,
                rows: List[Dict], signatures: np.ndarray) -> None:
    """
    :param rows: one dict per (node, round) with keys node, round, acc, loss,
                 delta_norm, payload_bytes, train_time_ms, n_samples, peers
                 (list[int]), merge_weights (list[float]), staleness_max.
    :param signatures: float32 array [n, rounds+1, d] of entry-model signatures.
    """
    out = Path(out_dir)
    out.mkdir(parents=True, exist_ok=True)

    if signatures.shape != (n, rounds + 1, d):
        raise ValueError(f"signatures shape {signatures.shape} != ({n},{rounds + 1},{d})")

    # learning_trace.csv
    lines = [",".join(CSV_COLUMNS)]
    for r in rows:
        peers = ";".join(str(p) for p in r["peers"])
        mw = ";".join(_fmt_float(w) for w in r["merge_weights"])
        lines.append(",".join([
            "1", scenario_id, topology_hash, policy_id, merge_rule, gamma_schedule,
            str(seed), str(r["node"]), str(r["round"]),
            _fmt_float(r["acc"]), _fmt_float(r.get("heldout_acc", float("nan"))),
            _fmt_float(r["loss"]), _fmt_float(r["delta_norm"]),
            str(int(r["payload_bytes"])), _fmt_float(r["train_time_ms"]),
            str(int(r["n_samples"])), peers, mw, str(int(r["staleness_max"])),
        ]))
    (out / "learning_trace.csv").write_text("\n".join(lines) + "\n", encoding="utf-8")

    # signatures.bin : little-endian float32, layout (node*(rounds+1)+slot)*d
    sig = np.ascontiguousarray(signatures.astype("<f4"))
    sig.tofile(out / "signatures.bin")

    # signatures_index.json
    index = {
        "dtype": "float32",
        "d": d,
        "n": n,
        "rounds": rounds + 1,   # T+1 entry-model slots
        "node_order": list(range(n)),
    }
    (out / "signatures_index.json").write_text(json.dumps(index), encoding="utf-8")
