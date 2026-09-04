"""Track-B online worker (§8.4 Track B).

A long-running watcher over a bridge directory. Per round it waits for the
simulator's ``request.READY``, executes one genuine training round, and
publishes ``response.json`` + a signatures sidecar + ``response.READY``. The
simulator owns timing/energy; this worker supplies only the learning result.

**Peer-set provenance and what ERQ1 can actually falsify.** By default the
worker uses the peer sets carried in the request. If the driver copies those
from the Track-A trace, then Track A and Track B become the same deterministic
function of the same inputs and their agreement is guaranteed by construction —
the comparison then measures determinism, not fidelity, and cannot fail.

With ``--independent-selection`` the worker instead derives its own peer sets
each round from *its own* evolving signature cache, using the shared selection
library. Track B is then a genuinely independent, round-driven realisation of
the protocol: it exercises the online state machine (per-round cache updates,
dynamic-edge handling, restart/idempotency, RNG stream derivation across the
process boundary) rather than replaying a decision list. Any divergence in that
state machine shows up as a sub-1.0 selection-agreement rate and, downstream, as
a non-zero accuracy MAE. That is the version ERQ1 should be evaluated with; the
request-driven mode remains for smoke tests and for replaying a fixed schedule.
Either way the chosen peer sets are echoed back in ``response.json`` so the
driver can compare them against Track A's.

Training backend: plain PyTorch via the shared ``local_train`` (the same inner
loop as Track A). The handshake, idempotency, and response contract are
framework-agnostic: any backend that serves them could replace this worker,
but the reference implementation is PyTorch only.

Idempotency / restart: the worker is stateful (holds the per-node models). On
(re)start it replays rounds from 0 deterministically to rebuild model state;
for any round whose ``response.READY`` already exists it advances state by
re-training but does **not** rewrite the response — so a mid-round kill is
re-served identically (the learning result is deterministic; only the measured
``train_time_ms`` differs, as it is a wall-clock measurement).
"""
from __future__ import annotations

import argparse
import json
import time
from pathlib import Path
from typing import Dict, List

import numpy as np
import torch

import flrng
import merge as merge_rules
import selection
import signatures as sigmod
from data import Dataset, load_workload
from models import build_model, flatten_params, load_flat_params, param_count
from partition import dirichlet_partition
from selection import CostView
from system_trace import load as load_system_trace
from train_step import evaluate, local_train, round_train_seed, set_determinism

POLL_S = 0.05


def _wait_ready(marker: Path, timeout_s: float) -> bool:
    deadline = time.time() + timeout_s
    while not marker.exists():
        if time.time() > deadline:
            return False
        time.sleep(POLL_S)
    return True


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description="Track-B online training worker")
    ap.add_argument("--bridge", required=True)
    ap.add_argument("--system-trace", required=True)
    ap.add_argument("--rounds", type=int, required=True)
    ap.add_argument("--dataset", default="synthetic", choices=["synthetic", "mnist"])
    ap.add_argument("--data-root", default="./.data")
    ap.add_argument("--samples", type=int, default=240)
    ap.add_argument("--model", default="lenet5")
    ap.add_argument("--in-channels", type=int, default=1)
    ap.add_argument("--num-classes", type=int, default=10)
    ap.add_argument("--epochs", type=int, default=1)
    ap.add_argument("--lr", type=float, default=0.05)
    ap.add_argument("--batch", type=int, default=16)
    ap.add_argument("--init-seed", type=int, default=777)
    ap.add_argument("--round-timeout-s", type=float, default=600.0)
    ap.add_argument("--eval-samples", type=int, default=0)
    ap.add_argument("--independent-selection", action="store_true",
                    help="derive peer sets from this worker's own state instead of "
                         "using the request's, so ERQ1 measures fidelity rather than "
                         "restating determinism")
    args = ap.parse_args(argv)

    bridge = Path(args.bridge)
    bridge.mkdir(parents=True, exist_ok=True)
    trace = load_system_trace(args.system_trace)
    set_determinism(trace.seed)

    n = trace.n
    d = trace.hyper.signature_dim
    rule_id = trace.hyper.merge_rule
    payload_bytes = trace.model.payload_bytes_float32

    full, eval_set = load_workload(args.dataset, root=args.data_root, samples=args.samples,
                                   num_classes=args.num_classes, in_channels=args.in_channels,
                                   seed=trace.seed, eval_samples=args.eval_samples)
    partitions = dirichlet_partition(full.y.numpy(), n, trace.hyper.dirichlet_rho, trace.seed)
    node_data: List[Dataset] = [Dataset(full.x[torch.as_tensor(ix)], full.y[torch.as_tensor(ix)],
                                        full.num_classes) for ix in partitions]
    sample_counts = [len(p) for p in partitions]
    dynamic = bool(trace.topology.dynamic_schedule)

    # Shared initialisation, matching every other mode (§9 internal validity).
    models = []
    for i in range(n):
        torch.manual_seed(args.init_seed)
        models.append(build_model(args.model, args.num_classes, args.in_channels))
    proj = sigmod.make_projection(param_count(models[0]), d, trace.hyper.signature_seed)

    def node_sig(i):
        return sigmod.signature(flatten_params(models[i]).numpy(), proj)

    def edge(i, j):
        return trace.topology.edge(i, j)

    # Independent-selection state: this worker's own view of what it last saw
    # from each peer. Evolving it here (rather than reading Track A's decisions)
    # is what makes the selection-agreement rate a measurement.
    cache: List[Dict[int, tuple]] = [dict() for _ in range(n)]

    def derive_peer_sets(t: int, entry_sig, nbrs, deg) -> Dict[int, List[int]]:
        out: Dict[int, List[int]] = {}
        for i in range(n):
            cache_i = cache[i]

            def load_j(j, _t=t):
                prof = trace.load_profiles.get(j)
                return float(prof[_t]) if prof is not None and _t < len(prof) else 0.0

            cv = CostView(
                latency=lambda j, _i=i: float(edge(_i, j).latency_ticks),
                load=load_j,
                divergence=lambda j, _c=cache_i, _s=entry_sig[i]: (
                    0.0 if _c.get(j) is None else sigmod.l2(_s, _c[j][0])),
                has_divergence=lambda j, _c=cache_i: j in _c,
                bandwidth_cost=lambda j, _i=i: payload_bytes / float(edge(_i, j).bandwidth_bytes_per_tick),
                degree=lambda j: deg[j],
            )
            out[i] = selection.select_peers(
                trace.hyper.policy, i, t, args.rounds, nbrs[i], cv,
                flrng.derive(trace.seed, t, i), trace.hyper.k,
                gamma_schedule=trace.hyper.gamma_schedule)
        return out

    mode_label = "independent" if args.independent_selection else "request-driven"
    print(f"[trackb] worker up: bridge={bridge} rounds={args.rounds} n={n} "
          f"selection={mode_label} heldout={len(eval_set)}", flush=True)

    for t in range(args.rounds):
        round_dir = bridge / f"round_{t:04d}"
        round_dir.mkdir(parents=True, exist_ok=True)
        if not _wait_ready(round_dir / "request.READY", args.round_timeout_s):
            print(f"[trackb] timed out waiting for round {t} request", flush=True)
            return 2
        req = json.loads((round_dir / "request.json").read_text(encoding="utf-8"))
        requested: Dict[int, List[int]] = {int(k): list(v) for k, v in (req.get("peerSets") or {}).items()}

        # Merge degree terms must reflect this round's active topology in
        # dynamic mode (FLGossipOrchestrator.mergeNode uses topology.degree
        # after applyDynamicRound), so both sides compute identical weights.
        active = trace.topology.active_at(t) if dynamic else None
        nbrs = [trace.topology.neighbours(i, active_edges=active) for i in range(n)]
        degree = [len(nb) for nb in nbrs]

        # Entry snapshots (for merge from a consistent state).
        entry_sig = [node_sig(i) for i in range(n)]
        entry_flat = [flatten_params(models[i]).clone() for i in range(n)]

        # Peer sets: either the request's, or derived from this worker's own
        # state (the mode under which ERQ1's agreement rate is a measurement).
        if args.independent_selection:
            peer_sets = derive_peer_sets(t, entry_sig, nbrs, degree)
        else:
            peer_sets = requested

        per_node = {}
        new_sigs = np.zeros((n, d), dtype=np.float32)
        for i in range(n):
            peers = peer_sets.get(i, [])
            members = [i] + peers
            div = [0.0] + [sigmod.l2(entry_sig[i], entry_sig[j]) for j in peers]
            scnt = [sample_counts[m] for m in members]
            deg = [degree[m] for m in members]
            w = merge_rules.weights(rule_id, div, scnt, deg)
            merged = torch.zeros_like(entry_flat[i])
            for slot, m in enumerate(members):
                merged += float(w[slot]) * entry_flat[m]
            load_flat_params(models[i], merged)
            res = local_train(models[i], node_data[i], args.epochs, args.lr, args.batch,
                              round_train_seed(trace.seed, t, i))
            per_node[str(i)] = {
                "acc": res.acc,
                "heldoutAcc": evaluate(models[i], eval_set)[1],
                "loss": res.loss, "deltaNorm": res.delta_norm,
                "trainTimeMs": res.train_time_ms, "payloadBytes": int(payload_bytes),
                "nSamples": sample_counts[i],
                "peers": list(peers),
                "mergeWeights": [float(x) for x in w],
            }
            new_sigs[i] = node_sig(i)

        # Cache update at merge completion for selected peers only (P0.3b) —
        # the same contract as modes/gossip.py and FLGossipNode.
        for i in range(n):
            for j in peer_sets.get(i, []):
                cache[i][j] = (entry_sig[j].copy(), t)

        response_ready = round_dir / "response.READY"
        if not response_ready.exists():
            sig_name = f"signatures_{t:04d}.bin"
            np.ascontiguousarray(new_sigs.astype("<f4")).tofile(round_dir / sig_name)
            # §8.4 distortion check (D5): Track B is the only place the FULL
            # weights exist, so the residual distortion of signature-space
            # distances relative to full-weight distances is measured here and
            # reported per round (mean ratio ≈ 1 ⇒ the linear projection
            # preserves the pairwise geometry the divergence decisions use).
            post_flat = [flatten_params(models[i]).numpy() for i in range(n)]
            ratios = []
            for i in range(n):
                for j in range(i + 1, n):
                    dw = sigmod.l2(post_flat[i], post_flat[j])
                    if dw > 1e-12:
                        ratios.append(sigmod.l2(new_sigs[i], new_sigs[j]) / dw)
            distortion = None if not ratios else {
                "pairs": len(ratios),
                "meanRatio": float(np.mean(ratios)),
                "minRatio": float(min(ratios)),
                "maxRatio": float(max(ratios)),
            }
            # Echo the peer sets actually used and their provenance, so the
            # driver can compute the selection-agreement rate against Track A.
            resp = {"round": t, "perNode": per_node, "signaturesFile": sig_name,
                    "signatureDim": d, "signatureDistortion": distortion,
                    "selectionSource": "independent" if args.independent_selection else "request",
                    "peerSets": {str(i): list(peer_sets.get(i, [])) for i in range(n)}}
            (round_dir / "response.json").write_text(json.dumps(resp), encoding="utf-8")
            response_ready.write_bytes(b"")
            print(f"[trackb] served round {t}", flush=True)
        else:
            print(f"[trackb] round {t} already served — re-served idempotently", flush=True)

    print("[trackb] worker done", flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
