"""Decentralized gossip mode (Pass 2, §8.1 Alg. 1) — the authoritative learning
side whose decisions the Java Pass-3 replay reproduces.

Per round t (matching FLGossipOrchestrator's convention):
  1. record slot t = signature of each node's current (entry) model;
  2. select peers (cached signature divergence + γ schedule), draws from
     flrng.derive(seed, t, node) in the documented order;
  3. merge real models with drift-suppressed (or configured) weights computed
     from the FRESH signature-space divergences (D5);
  4. shared local_train → the model entering round t+1;
  5. update each node's cache with the entry signatures of its selected peers.
Signatures for slots 0..T (T+1) are written so the replay can re-derive
selection and TID identically.
"""
from __future__ import annotations

from dataclasses import dataclass
from typing import Dict, List, Tuple

import numpy as np
import torch

import flrng
import merge as merge_rules
import selection
import signatures as sigmod
from data import Dataset
from models import build_model, flatten_params, load_flat_params, param_count
from selection import CostView
from system_trace import SystemTrace
from train_step import evaluate, local_train, round_train_seed


@dataclass
class GossipResult:
    rows: List[Dict]
    signatures: np.ndarray  # [n, T+1, d] float32
    #: §8.3 evaluation summary (written to ``pass2/summary.json``): for gossip,
    #: the primary A_dec (mean final per-node accuracy on the full set), the
    #: worst-node accuracy, and the secondary view — the accuracy of the
    #: uniformly averaged model w̄; for the baselines, the global-model
    #: accuracy (the Eq. tid-gap A_cen reference).
    summary: Dict = None


def _edge_lookup(trace: SystemTrace):
    by_pair = {}
    for e in trace.edges:
        by_pair[(e.u, e.v)] = e
    return by_pair


def run_gossip(trace: SystemTrace, partitions: List[np.ndarray], full: Dataset,
               *, model_name: str, in_channels: int, num_classes: int,
               epochs: int, lr: float, batch_size: int, init_seed: int,
               eval_set: Dataset = None, eval_every: int = 1) -> GossipResult:
    #: ``eval_set`` is the held-out pool (§9 metrics). It is disjoint from
    #: ``full`` (the Dirichlet-partitioned training pool) and is the only basis
    #: for reported accuracy; ``full`` is used solely to size the partitions.
    if eval_set is None:
        raise ValueError("eval_set is required: reported accuracy must come from a "
                         "held-out pool, never from the training pool")
    seed = trace.seed
    n = trace.n
    rounds = trace.hyper.rounds
    d = trace.hyper.signature_dim
    k = trace.hyper.k
    policy_id = trace.hyper.policy
    rule_id = trace.hyper.merge_rule
    gamma_schedule = trace.hyper.gamma_schedule
    payload_bytes = trace.model.payload_bytes_float32

    edges = _edge_lookup(trace)

    def edge(i, j):
        return edges.get((i, j) if i < j else (j, i))

    dynamic = bool(trace.topology.dynamic_schedule)

    # Per-node models + projection + data.
    #
    # SHARED initialisation (§9 internal validity): every node starts from the
    # *same* weights, exactly as the centralized broadcast and the hierarchical
    # regional broadcast do. Seeding per node instead (init_seed + i) would give
    # gossip n distinct inits against centralized's one, so part of both the TID
    # gap and the TID dispersion would be residual initialisation disagreement
    # rather than a topology effect — the confound §9 claims to remove.
    models = []
    for i in range(n):
        torch.manual_seed(init_seed)
        models.append(build_model(model_name, num_classes, in_channels))
    pcount = param_count(models[0])
    proj = sigmod.make_projection(pcount, d, trace.hyper.signature_seed)
    node_data = [Dataset(full.x[torch.as_tensor(ix)], full.y[torch.as_tensor(ix)], num_classes)
                 for ix in partitions]
    sample_counts = [len(p) for p in partitions]

    cache: List[Dict[int, Tuple[np.ndarray, int]]] = [dict() for _ in range(n)]
    sigs = np.zeros((n, rounds + 1, d), dtype=np.float32)
    rows: List[Dict] = []

    def node_signature(i):
        return sigmod.signature(flatten_params(models[i]).numpy(), proj)

    def make_costview(i: int, t: int, cache_i: Dict[int, Tuple[np.ndarray, int]],
                      sig_i: np.ndarray, deg: List[int]) -> CostView:
        """Factory so ``i``/``t`` are bound per node (no late-binding closures)."""
        def load_j(j):
            prof = trace.load_profiles.get(j)
            return float(prof[t]) if prof is not None and t < len(prof) else 0.0
        return CostView(
            latency=lambda j: float(edge(i, j).latency_ticks),
            load=load_j,
            divergence=lambda j: (0.0 if cache_i.get(j) is None
                                  else sigmod.l2(sig_i, cache_i[j][0])),
            has_divergence=lambda j: j in cache_i,
            bandwidth_cost=lambda j: payload_bytes / float(edge(i, j).bandwidth_bytes_per_tick),
            degree=lambda j: deg[j],
        )

    for t in range(rounds):
        # Round-t neighbourhood: for dynamic topologies, restricted to the
        # Pass-1 edge schedule (the derive(seed, t, -1) toggle stream), exactly
        # as FLGossipOrchestrator.applyDynamicRound does before selection. The
        # candidate set (and hence the min–max normalisation) and the degree
        # terms (DEGREE selection, MH / D-PSGD merge) all see active edges
        # only; edge costs L/B still come from the union graph, matching
        # GossipCostView.
        active = trace.topology.active_at(t) if dynamic else None
        nbrs = [trace.topology.neighbours(i, active_edges=active) for i in range(n)]
        deg = [len(nb) for nb in nbrs]

        # 1) entry signatures (slot t)
        for i in range(n):
            sigs[i, t] = node_signature(i)

        # 2) selection (cached divergence)
        peer_sets: List[List[int]] = [None] * n
        staleness: List[int] = [0] * n
        for i in range(n):
            cache_i = cache[i]
            cv = make_costview(i, t, cache_i, sigs[i, t], deg)
            rng = flrng.derive(seed, t, i)
            peers = selection.select_peers(policy_id, i, t, rounds, nbrs[i], cv, rng, k,
                                           gamma_schedule=gamma_schedule)
            peer_sets[i] = peers
            staleness[i] = max((t - cache_i[j][1]) for j in cache_i) if cache_i else 0

        # 3) merge (fresh signature divergence) from snapshotted entry models, then 4) train
        entry_flat = [flatten_params(models[i]).clone() for i in range(n)]
        round_metrics = [None] * n
        merge_weights_row = [None] * n
        for i in range(n):
            peers = peer_sets[i]
            members = [i] + peers
            div = [0.0] + [sigmod.l2(sigs[i, t], sigs[j, t]) for j in peers]
            scnt = [sample_counts[m] for m in members]
            deg_m = [deg[m] for m in members]
            w = merge_rules.weights(rule_id, div, scnt, deg_m)
            merge_weights_row[i] = [float(x) for x in w]
            merged = torch.zeros_like(entry_flat[i])
            for slot, m in enumerate(members):
                merged += float(w[slot]) * entry_flat[m]
            load_flat_params(models[i], merged)
            res = local_train(models[i], node_data[i], epochs, lr, batch_size,
                              round_train_seed(seed, t, i))
            round_metrics[i] = res

        # 5) cache update (entry signatures of selected peers; P0.3b)
        for i in range(n):
            for j in peer_sets[i]:
                cache[i][j] = (sigs[j, t].copy(), t)

        # record rows. `acc` is the LOCAL fit accuracy on the node's own shard
        # (a training diagnostic — under a skewed Dirichlet draw a node with two
        # classes trivially reaches ~1.0). `heldout_acc` is the reportable
        # figure, measured on the disjoint eval pool; it is what §9's accuracy
        # tables and the accuracy-vs-round figure must use.
        measure_heldout = (t % eval_every == 0) or (t == rounds - 1)
        for i in range(n):
            res = round_metrics[i]
            heldout = evaluate(models[i], eval_set)[1] if measure_heldout else float("nan")
            rows.append({
                "node": i, "round": t, "acc": res.acc, "loss": res.loss,
                "heldout_acc": heldout,
                "delta_norm": res.delta_norm, "payload_bytes": payload_bytes,
                "train_time_ms": res.train_time_ms, "n_samples": sample_counts[i],
                "peers": peer_sets[i], "merge_weights": merge_weights_row[i],
                "staleness_max": staleness[i],
            })

    # final slot T
    for i in range(n):
        sigs[i, rounds] = node_signature(i)

    # §8.3 summary on the HELD-OUT pool: primary A_dec = mean (and worst) final
    # per-node accuracy; secondary view = accuracy of the uniformly averaged
    # model w̄ = (1/n) Σ w_i (evaluate per-node models FIRST — the w̄ evaluation
    # reuses models[0] as scratch).
    per_node_acc = [evaluate(models[i], eval_set)[1] for i in range(n)]
    mean_flat = torch.stack([flatten_params(models[i]) for i in range(n)]).mean(dim=0)
    load_flat_params(models[0], mean_flat)
    acc_mean_model = evaluate(models[0], eval_set)[1]
    summary = {
        "eval_split": "heldout",
        "eval_samples": len(eval_set),
        "acc_mean_over_nodes": float(np.mean(per_node_acc)),
        "acc_worst_node": float(min(per_node_acc)),
        "acc_mean_model": acc_mean_model,
        "acc_per_node": [float(a) for a in per_node_acc],
    }
    return GossipResult(rows=rows, signatures=sigs, summary=summary)
