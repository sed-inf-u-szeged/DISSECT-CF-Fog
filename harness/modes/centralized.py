"""Centralized FedAvg mode (Pass 2 baseline, §9). Each round every client
trains from the broadcast global model via the shared ``local_train``; the
server averages the client models (sample-weighted FedAvg). On a single
isolated client this reduces to ``local_train`` — the basis for the one-node
equivalence test.

``run_centralized`` is the bare coordination core (kept as-is — it is pinned
by the equivalence test); ``run_centralized_traced`` wraps the same loop with
the trace-schema recording (rows + T+1 signature slots, peers column empty)
so the S0 cells emit ``learning_trace.csv`` + ``signatures.bin`` exactly like
the gossip mode."""
from __future__ import annotations

from typing import List

import numpy as np
import torch

import signatures as sigmod
from data import Dataset
from models import build_model, flatten_params, load_flat_params, param_count
from train_step import evaluate, local_train, round_train_seed


def run_centralized(models: List[torch.nn.Module], node_data: List[Dataset],
                    sample_counts: List[int], rounds: int, epochs: int, lr: float,
                    batch_size: int, seed: int) -> torch.Tensor:
    """Runs FedAvg; returns the final global flattened parameter vector. The
    global model is ``models[0]``; all clients start each round from it."""
    n = len(models)
    total = float(sum(sample_counts))
    global_flat = flatten_params(models[0]).clone()
    for t in range(rounds):
        client_flats = []
        for i in range(n):
            load_flat_params(models[i], global_flat)   # broadcast
            local_train(models[i], node_data[i], epochs, lr, batch_size,
                        round_train_seed(seed, t, i))
            client_flats.append(flatten_params(models[i]).clone())
        # Sample-weighted average.
        agg = torch.zeros_like(global_flat)
        for i in range(n):
            agg += (sample_counts[i] / total) * client_flats[i]
        global_flat = agg
    load_flat_params(models[0], global_flat)
    return global_flat


def run_centralized_traced(trace, partitions: List[np.ndarray], full: Dataset,
                           *, model_name: str, in_channels: int, num_classes: int,
                           epochs: int, lr: float, batch_size: int, init_seed: int,
                           eval_set: Dataset = None, eval_every: int = 1):
    """FedAvg with trace-schema recording; mirrors ``run_gossip``'s interface
    and conventions. Slot ``t`` = the model *entering* round ``t``; in the
    centralized mode every node enters with the broadcast global model, so all
    per-node entry signatures of a round are identical (TID = 0 by construction
    — no topology). ``peers``/``merge_weights`` are empty (§8.4 / plan P3.3).

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

    # Shared initialisation, identical to the gossip and hierarchical modes: the
    # broadcast below would overwrite per-node inits anyway, but seeding
    # identically keeps the three modes provably matched on initialization.
    models = []
    for i in range(n):
        torch.manual_seed(init_seed)
        models.append(build_model(model_name, num_classes, in_channels))
    pcount = param_count(models[0])
    proj = sigmod.make_projection(pcount, d, trace.hyper.signature_seed)
    node_data = [Dataset(full.x[torch.as_tensor(ix)], full.y[torch.as_tensor(ix)], num_classes)
                 for ix in partitions]
    sample_counts = [len(p) for p in partitions]
    total = float(sum(sample_counts))

    global_flat = flatten_params(models[0]).clone()  # global = models[0] init
    sigs = np.zeros((n, rounds + 1, d), dtype=np.float32)
    rows = []
    for t in range(rounds):
        gsig = sigmod.signature(global_flat.numpy(), proj)
        for i in range(n):
            sigs[i, t] = gsig
        agg = torch.zeros_like(global_flat)
        measure_heldout = (t % eval_every == 0) or (t == rounds - 1)
        for i in range(n):
            load_flat_params(models[i], global_flat)          # broadcast
            res = local_train(models[i], node_data[i], epochs, lr, batch_size,
                              round_train_seed(seed, t, i))
            agg += (sample_counts[i] / total) * flatten_params(models[i])
            heldout = evaluate(models[i], eval_set)[1] if measure_heldout else float("nan")
            rows.append({
                "node": i, "round": t, "acc": res.acc, "loss": res.loss,
                "heldout_acc": heldout,
                "delta_norm": res.delta_norm, "payload_bytes": payload_bytes,
                "train_time_ms": res.train_time_ms, "n_samples": sample_counts[i],
                "peers": [], "merge_weights": [], "staleness_max": 0,
            })
        global_flat = agg
    final_sig = sigmod.signature(global_flat.numpy(), proj)
    for i in range(n):
        sigs[i, rounds] = final_sig
    # A_cen reference for Eq. tid-gap: the final global model on the held-out pool.
    load_flat_params(models[0], global_flat)
    acc_global = evaluate(models[0], eval_set)[1]
    return GossipResult(rows=rows, signatures=sigs,
                        summary={"eval_split": "heldout",
                                 "eval_samples": len(eval_set),
                                 "acc_global_model": acc_global})
