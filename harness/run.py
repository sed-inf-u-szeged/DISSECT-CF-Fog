"""Pass-2 learning-harness CLI (§8.4).

Consumes a Pass-1 ``system_trace.json`` and emits the Pass-3 inputs
(``learning_trace.csv`` + ``signatures.bin`` + ``signatures_index.json``) for
the chosen coordination mode — ``gossip`` (decentralized), ``centralized``
(FedAvg), or ``hierarchical`` (regional FedAvg + global sync every G rounds).
All three share the same ``local_train`` and write the same trace schema
(peers column empty for the centralized and hierarchical baselines), so ERQ2
compares coordination on real training. For the gossip mode the peer-selection
decisions made here are authoritative and reproduced by the Java replay.

Examples:
    python run.py --system-trace pass1/system_trace.json --mode gossip \
        --out pass2/ --epochs 1 --lr 0.05 --batch 16 --dataset synthetic \
        --samples 240 --model lenet5
    python run.py --system-trace pass1/system_trace.json --mode centralized --out pass2/
    python run.py --system-trace pass1/system_trace.json --mode hierarchical \
        --global-every 5 --out pass2/
"""
from __future__ import annotations

import argparse
from pathlib import Path

import data as datamod
import partition as partmod
from modes.centralized import run_centralized_traced
from modes.gossip import run_gossip
from modes.hierarchical import run_hierarchical_traced
from system_trace import load as load_system_trace
from train_step import set_determinism
from trace_writer import write_trace


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description="Pass-2 learning harness")
    ap.add_argument("--system-trace", required=True)
    ap.add_argument("--mode", default="gossip",
                    choices=["gossip", "centralized", "hierarchical"])
    ap.add_argument("--global-every", type=int, default=5,
                    help="hierarchical only: global reconciliation cadence G (rounds)")
    ap.add_argument("--out", required=True)
    ap.add_argument("--dataset", default="synthetic", choices=["synthetic", "mnist"])
    ap.add_argument("--data-root", default="./.data")
    ap.add_argument("--samples", type=int, default=240,
                    help="training-pool size; <=0 uses the full split (MNIST: 60k)")
    ap.add_argument("--eval-samples", type=int, default=0,
                    help="held-out pool cap; <=0 uses the full held-out split")
    ap.add_argument("--eval-every", type=int, default=1,
                    help="held-out evaluation cadence in rounds (final round always measured)")
    ap.add_argument("--model", default="lenet5")
    ap.add_argument("--in-channels", type=int, default=1)
    ap.add_argument("--num-classes", type=int, default=10)
    ap.add_argument("--epochs", type=int, default=1)
    ap.add_argument("--lr", type=float, default=0.05)
    ap.add_argument("--batch", type=int, default=16)
    ap.add_argument("--init-seed", type=int, default=777)
    args = ap.parse_args(argv)

    trace = load_system_trace(args.system_trace)
    set_determinism(trace.seed)

    # Workload: the Dirichlet-partitioned training pool and a DISJOINT held-out
    # pool. Every reported accuracy comes from the held-out pool — evaluating on
    # the training pool would be an optimistically biased figure that cannot be
    # called a test accuracy (§9 metrics).
    full, eval_set = datamod.load_workload(
        args.dataset, root=args.data_root, samples=args.samples,
        num_classes=args.num_classes, in_channels=args.in_channels,
        seed=trace.seed, eval_samples=args.eval_samples)
    print(f"[pass2] workload={args.dataset} train_pool={len(full)} heldout={len(eval_set)}")

    # Partition: Dirichlet(ρ) from the system_trace hyper, one shard per node.
    labels = full.y.numpy()
    partitions = partmod.dirichlet_partition(labels, trace.n, trace.hyper.dirichlet_rho, trace.seed)

    common = dict(model_name=args.model, in_channels=args.in_channels,
                  num_classes=args.num_classes, epochs=args.epochs, lr=args.lr,
                  batch_size=args.batch, init_seed=args.init_seed,
                  eval_set=eval_set, eval_every=max(1, args.eval_every))
    if args.mode == "gossip":
        result = run_gossip(trace, partitions, full, **common)
    elif args.mode == "centralized":
        result = run_centralized_traced(trace, partitions, full, **common)
    else:  # hierarchical (G reported per D6)
        print(f"[pass2] hierarchical: global reconciliation every G={args.global_every} rounds")
        result = run_hierarchical_traced(trace, partitions, full, **common,
                                         global_every=args.global_every)

    # For the centralized and hierarchical baselines the peer-selection policy is
    # undefined; record the coordination mode in the policy column instead of the
    # gossip selector id.
    policy_id = trace.hyper.policy if args.mode == "gossip" else args.mode.upper()
    write_trace(args.out, scenario_id=trace.scenario_id, topology_hash=trace.topology.hash,
                policy_id=policy_id, merge_rule=trace.hyper.merge_rule,
                gamma_schedule=trace.hyper.gamma_schedule, seed=trace.seed,
                n=trace.n, rounds=trace.hyper.rounds, d=trace.hyper.signature_dim,
                rows=result.rows, signatures=result.signatures)
    if result.summary:
        # §8.3 evaluation summary (A_dec / w̄ accuracy for gossip; the A_cen
        # global-model reference for the baselines) — input to analysis/tid_gap.py.
        import json
        # Record the workload this leg actually trained on. The ERQ1 fidelity gate
        # configures Track B from this block, so the two tracks are compared on the
        # same workload by construction rather than because both happened to read
        # the same config — which is how a gate can silently validate a workload
        # nobody ran.
        summary = dict(result.summary)
        summary["workload"] = {
            "dataset": args.dataset,
            "samples": args.samples,
            "eval_samples": args.eval_samples,
            "model": args.model,
            "num_classes": args.num_classes,
            "in_channels": args.in_channels,
            "epochs": args.epochs,
            "lr": args.lr,
            "batch": args.batch,
            "init_seed": args.init_seed,
        }
        (Path(args.out) / "summary.json").write_text(json.dumps(summary, indent=2),
                                                     encoding="utf-8")
    print(f"[pass2] wrote learning trace to {Path(args.out).resolve()}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
