"""Hierarchical FL mode (Pass 2 baseline, §9): per-round regional FedAvg within
each cluster, with a global reconciliation every ``G`` rounds. Shares the same
``local_train``; on a single isolated node it reduces to ``local_train`` (the
one-node equivalence basis).

``run_hierarchical`` is the bare coordination core (pinned by the equivalence
test); ``run_hierarchical_traced`` adds the trace-schema recording (rows +
T+1 signature slots, peers column empty) so the S0 cells emit the same
artefacts as the gossip mode. Cluster membership comes from the Pass-1 trace's
node labels; ``G`` is the ``global_every`` argument (reported per D6)."""
from __future__ import annotations

from typing import List

import numpy as np
import torch

import signatures as sigmod
from data import Dataset
from models import build_model, flatten_params, load_flat_params, param_count
from train_step import evaluate, local_train, round_train_seed


def run_hierarchical(models: List[torch.nn.Module], node_data: List[Dataset],
                     sample_counts: List[int], cluster_labels: List[int],
                     rounds: int, global_every: int, epochs: int, lr: float,
                     batch_size: int, seed: int) -> torch.Tensor:
    """Runs two-tier FL; returns the final global flattened parameter vector."""
    n = len(models)
    clusters = sorted(set(cluster_labels))
    members = {c: [i for i in range(n) if cluster_labels[i] == c] for c in clusters}
    regional = {c: flatten_params(models[members[c][0]]).clone() for c in clusters}

    for t in range(rounds):
        for c in clusters:
            mem = members[c]
            ctotal = float(sum(sample_counts[i] for i in mem))
            for i in mem:
                load_flat_params(models[i], regional[c])      # regional broadcast
                local_train(models[i], node_data[i], epochs, lr, batch_size,
                            round_train_seed(seed, t, i))
            agg = torch.zeros_like(regional[c])
            for i in mem:
                agg += (sample_counts[i] / ctotal) * flatten_params(models[i])
            regional[c] = agg
        # Global reconciliation every G rounds (sample-weighted across clusters).
        if (t + 1) % global_every == 0:
            gtotal = float(sum(sample_counts))
            gagg = torch.zeros_like(regional[clusters[0]])
            for c in clusters:
                cw = sum(sample_counts[i] for i in members[c]) / gtotal
                gagg += cw * regional[c]
            for c in clusters:
                regional[c] = gagg.clone()

    final = regional[clusters[0]]
    load_flat_params(models[0], final)
    return final


def run_hierarchical_traced(trace, partitions: List[np.ndarray], full: Dataset,
                            *, model_name: str, in_channels: int, num_classes: int,
                            epochs: int, lr: float, batch_size: int, init_seed: int,
                            global_every: int, eval_set: Dataset = None,
                            eval_every: int = 1):
    """Two-tier FL with trace-schema recording; mirrors ``run_gossip``'s
    interface. Slot ``t`` = the model *entering* round ``t``: every node of
    cluster ``c`` enters with the regional model of ``c``, so entry signatures
    are identical within a cluster and differ across clusters between global
    reconciliations — the inter-cluster TID scope shows exactly the two-tier
    sawtooth. ``peers``/``merge_weights`` are empty (§8.4 / plan P3.3).

    ``eval_set`` is the held-out pool; all reported accuracy comes from it."""
    from modes.gossip import GossipResult  # rows + signatures container

    if eval_set is None:
        raise ValueError("eval_set is required: reported accuracy must come from a "
                         "held-out pool, never from the training pool")

    seed = trace.seed
    n = trace.n
    rounds = trace.hyper.rounds
    d = trace.hyper.signature_dim
    payload_bytes = trace.model.payload_bytes_float32
    cluster_labels = [trace.nodes[i].cluster for i in range(n)]

    # Shared initialisation across all modes (§9 internal validity).
    models = []
    for i in range(n):
        torch.manual_seed(init_seed)
        models.append(build_model(model_name, num_classes, in_channels))
    pcount = param_count(models[0])
    proj = sigmod.make_projection(pcount, d, trace.hyper.signature_seed)
    node_data = [Dataset(full.x[torch.as_tensor(ix)], full.y[torch.as_tensor(ix)], num_classes)
                 for ix in partitions]
    sample_counts = [len(p) for p in partitions]

    clusters = sorted(set(cluster_labels))
    members = {c: [i for i in range(n) if cluster_labels[i] == c] for c in clusters}
    regional = {c: flatten_params(models[members[c][0]]).clone() for c in clusters}

    sigs = np.zeros((n, rounds + 1, d), dtype=np.float32)
    rows = []
    for t in range(rounds):
        for c in clusters:
            csig = sigmod.signature(regional[c].numpy(), proj)
            for i in members[c]:
                sigs[i, t] = csig
        measure_heldout = (t % eval_every == 0) or (t == rounds - 1)
        for c in clusters:
            mem = members[c]
            ctotal = float(sum(sample_counts[i] for i in mem))
            for i in mem:
                load_flat_params(models[i], regional[c])      # regional broadcast
                res = local_train(models[i], node_data[i], epochs, lr, batch_size,
                                  round_train_seed(seed, t, i))
                heldout = evaluate(models[i], eval_set)[1] if measure_heldout else float("nan")
                rows.append({
                    "node": i, "round": t, "acc": res.acc, "loss": res.loss,
                    "heldout_acc": heldout,
                    "delta_norm": res.delta_norm, "payload_bytes": payload_bytes,
                    "train_time_ms": res.train_time_ms, "n_samples": sample_counts[i],
                    "peers": [], "merge_weights": [], "staleness_max": 0,
                })
            agg = torch.zeros_like(regional[c])
            for i in mem:
                agg += (sample_counts[i] / ctotal) * flatten_params(models[i])
            regional[c] = agg
        # Global reconciliation every G rounds (sample-weighted across clusters).
        if (t + 1) % global_every == 0:
            gtotal = float(sum(sample_counts))
            gagg = torch.zeros_like(regional[clusters[0]])
            for c in clusters:
                cw = sum(sample_counts[i] for i in members[c]) / gtotal
                gagg += cw * regional[c]
            for c in clusters:
                regional[c] = gagg.clone()
    for c in clusters:
        csig = sigmod.signature(regional[c].numpy(), proj)
        for i in members[c]:
            sigs[i, rounds] = csig
    # Global reference: the sample-weighted reconciliation of the final regional
    # models, evaluated on the held-out pool.
    gtotal = float(sum(sample_counts))
    gagg = torch.zeros_like(regional[clusters[0]])
    for c in clusters:
        cw = sum(sample_counts[i] for i in members[c]) / gtotal
        gagg += cw * regional[c]
    load_flat_params(models[0], gagg)
    acc_global = evaluate(models[0], eval_set)[1]
    return GossipResult(rows=rows, signatures=sigs,
                        summary={"eval_split": "heldout",
                                 "eval_samples": len(eval_set),
                                 "acc_global_model": acc_global})
