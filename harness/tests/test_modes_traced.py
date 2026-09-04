"""F3: the S0 baseline modes (centralized / hierarchical) emit the same trace
schema as the gossip mode — rows with empty ``peers``/``merge_weights`` and the
T+1 entry-model signature convention — and are deterministic. Uses the same
committed Pass-1 fixture as ``test_system_trace_loader``."""
from __future__ import annotations

from pathlib import Path

import numpy as np
import pytest

import data as datamod
import partition as partmod
import system_trace
from modes.centralized import run_centralized_traced
from modes.gossip import run_gossip
from modes.hierarchical import run_hierarchical_traced
from train_step import set_determinism

PROJECT_ROOT = Path(__file__).resolve().parent.parent.parent
FIXTURE = (PROJECT_ROOT / "simulator" / "src" / "test" / "resources" / "fl"
           / "sample_system_trace.json")

COMMON = dict(model_name="lenet5", in_channels=1, num_classes=4,
              epochs=1, lr=0.05, batch_size=16, init_seed=777)


@pytest.fixture(scope="module")
def trace():
    return system_trace.load(FIXTURE)


@pytest.fixture(scope="module")
def workload(trace):
    """Training pool plus the disjoint held-out pool every mode now requires."""
    return datamod.load_workload("synthetic", root="", samples=120, num_classes=4,
                                 in_channels=1, seed=trace.seed, eval_samples=40)


@pytest.fixture(scope="module")
def dataset(workload):
    return workload[0]


@pytest.fixture(scope="module")
def eval_set(workload):
    return workload[1]


@pytest.fixture(scope="module")
def partitions(trace, dataset):
    return partmod.dirichlet_partition(dataset.y.numpy(), trace.n,
                                       trace.hyper.dirichlet_rho, trace.seed)


def _check_schema(result, trace):
    n, rounds, d = trace.n, trace.hyper.rounds, trace.hyper.signature_dim
    assert result.signatures.shape == (n, rounds + 1, d)
    assert result.signatures.dtype == np.float32
    assert len(result.rows) == n * rounds
    for row in result.rows:
        assert row["peers"] == [], "S0 baselines have no peer sets"
        assert row["merge_weights"] == [], "S0 baselines have no merge weights"
        assert row["staleness_max"] == 0
        assert 0.0 <= row["acc"] <= 1.0


def test_centralized_traced_schema_and_broadcast(trace, dataset, partitions, eval_set):
    set_determinism(trace.seed)
    res = run_centralized_traced(trace, partitions, dataset, **COMMON, eval_set=eval_set)
    _check_schema(res, trace)
    # Centralized: every node enters every round with the broadcast global
    # model, so per-slot entry signatures are identical across nodes.
    for t in range(trace.hyper.rounds + 1):
        for i in range(1, trace.n):
            np.testing.assert_array_equal(res.signatures[0, t], res.signatures[i, t])
    # A_cen reference (Eq. tid-gap) is emitted with the run.
    assert 0.0 <= res.summary["acc_global_model"] <= 1.0


def test_gossip_summary_mean_model(trace, dataset, partitions, eval_set):
    """§8.3: gossip emits A_dec (mean + worst node) and the w̄ secondary view."""
    set_determinism(trace.seed)
    res = run_gossip(trace, partitions, dataset, **COMMON, eval_set=eval_set)
    s = res.summary
    for key in ("acc_mean_over_nodes", "acc_worst_node", "acc_mean_model"):
        assert 0.0 <= s[key] <= 1.0, key
    assert s["acc_worst_node"] <= s["acc_mean_over_nodes"] + 1e-12


def test_gossip_dynamic_schedule_restricts_peers(trace, dataset, partitions, eval_set):
    """F1 (dynamic cells): a peer selected in round t must be an *active*
    neighbour under the Pass-1 edge schedule for t — not merely a union-graph
    neighbour — and the merge member count stays consistent with the round's
    peers. Guards the Java replay's applyDynamicRound semantics."""
    import dataclasses
    short = dataclasses.replace(trace, hyper=dataclasses.replace(trace.hyper, rounds=3))
    assert short.topology.dynamic_schedule, "fixture must carry a dynamic schedule"
    set_determinism(short.seed)
    res = run_gossip(short, partitions, dataset, **COMMON, eval_set=eval_set)
    for row in res.rows:
        t = row["round"]
        active = short.topology.active_at(t)
        allowed = set(short.topology.neighbours(row["node"], active_edges=active))
        assert set(row["peers"]) <= allowed, (
            f"round {t} node {row['node']}: peers {row['peers']} outside the "
            f"active neighbourhood {sorted(allowed)}")
        assert len(row["merge_weights"]) == 1 + len(row["peers"])
    # Round 0 drops edge (3,5): node 5's only active neighbour is 4, so its
    # peer set is exactly [4] regardless of policy (k=2 > active degree 1).
    row_5_0 = next(r for r in res.rows if r["node"] == 5 and r["round"] == 0)
    assert row_5_0["peers"] == [4]


def test_hierarchical_traced_regional_then_global(trace, dataset, partitions, eval_set):
    set_determinism(trace.seed)
    res = run_hierarchical_traced(trace, partitions, dataset, **COMMON,
                                  eval_set=eval_set, global_every=1)
    _check_schema(res, trace)
    labels = [trace.nodes[i].cluster for i in range(trace.n)]
    # Entry signatures are identical within a cluster at every slot…
    for t in range(trace.hyper.rounds + 1):
        for c in set(labels):
            mem = [i for i in range(trace.n) if labels[i] == c]
            for i in mem[1:]:
                np.testing.assert_array_equal(res.signatures[mem[0], t],
                                              res.signatures[i, t])
    # …and with G=1 every round ends in a global sync, so from slot 1 on the
    # entry model is identical across *all* nodes.
    for t in range(1, trace.hyper.rounds + 1):
        for i in range(1, trace.n):
            np.testing.assert_array_equal(res.signatures[0, t], res.signatures[i, t])


def test_all_modes_share_the_initial_model(trace, dataset, partitions, eval_set):
    """§9 internal validity: every coordination mode must start from the SAME
    weights. Seeding gossip per node would give it n distinct inits against
    centralized's one, so part of the TID gap and of the TID dispersion would be
    residual initialisation disagreement rather than a topology effect."""
    set_determinism(trace.seed)
    gos = run_gossip(trace, partitions, dataset, **COMMON, eval_set=eval_set)
    set_determinism(trace.seed)
    cen = run_centralized_traced(trace, partitions, dataset, **COMMON, eval_set=eval_set)

    # Slot 0 is the model entering round 0, i.e. the initialisation itself.
    for i in range(1, trace.n):
        np.testing.assert_array_equal(
            gos.signatures[0, 0], gos.signatures[i, 0],
            err_msg="gossip nodes do not share the initial model")
    np.testing.assert_allclose(
        gos.signatures[0, 0], cen.signatures[0, 0], rtol=0, atol=0,
        err_msg="gossip and centralized do not share the initial model")
    # ⇒ TID dispersion at round 0 is exactly zero, so any later dispersion is
    # attributable to training and topology, not to the starting point.
    assert float(np.abs(gos.signatures[:, 0] - gos.signatures[0, 0]).max()) == 0.0


def test_heldout_accuracy_is_recorded_and_distinct(trace, dataset, partitions, eval_set):
    """Reported accuracy must come from the held-out pool, and the local-shard
    ``acc`` column must be carried separately — conflating them is what turns a
    training accuracy into a claimed test accuracy."""
    set_determinism(trace.seed)
    res = run_gossip(trace, partitions, dataset, **COMMON, eval_set=eval_set)
    assert res.summary["eval_split"] == "heldout"
    assert res.summary["eval_samples"] == len(eval_set)
    for row in res.rows:
        assert 0.0 <= row["heldout_acc"] <= 1.0
    # The two columns measure different things, so on a skewed partition they
    # must not be the same number for every node-round.
    assert any(abs(r["acc"] - r["heldout_acc"]) > 1e-9 for r in res.rows)


def test_traced_modes_deterministic(trace, dataset, partitions, eval_set):
    set_determinism(trace.seed)
    a = run_centralized_traced(trace, partitions, dataset, **COMMON, eval_set=eval_set)
    set_determinism(trace.seed)
    b = run_centralized_traced(trace, partitions, dataset, **COMMON, eval_set=eval_set)
    np.testing.assert_array_equal(a.signatures, b.signatures)
    # train_time_ms is measured wall-clock — non-deterministic by design (the
    # CI canary masks the same column); everything else must match exactly.
    strip = lambda rows: [{k: v for k, v in r.items() if k != "train_time_ms"} for r in rows]
    assert strip(a.rows) == strip(b.rows)
