"""Cross-language round-trip test for the Pass-1 ``system_trace.json``.

Loads the committed sample fixture produced by the Java writer
(``SystemTraceRoundTripTest`` with ``-Dregen.fixtures=true``) and asserts the
Python loader (``system_trace.py``) parses every part of the schema and exposes
it with the right types and semantics. This is the Python half of the P1.5
round-trip DoD; the Java half proves the fixture matches fresh writer output.
"""
from __future__ import annotations

from pathlib import Path

import pytest

import system_trace

PROJECT_ROOT = Path(__file__).resolve().parent.parent.parent
FIXTURE = (
    PROJECT_ROOT
    / "simulator"
    / "src"
    / "test"
    / "resources"
    / "fl"
    / "sample_system_trace.json"
)


@pytest.fixture(scope="module")
def trace():
    if not FIXTURE.exists():
        raise FileNotFoundError(
            f"fixture {FIXTURE} missing -- regenerate via "
            "mvn -Dtest=SystemTraceRoundTripTest -Dregen.fixtures=true test"
        )
    return system_trace.load(FIXTURE)


def test_envelope(trace):
    assert trace.schema_version == 1
    assert trace.scenario_id == "S_sample_dynamic"
    assert trace.seed == 42
    assert trace.n == 6


def test_topology_structure(trace):
    t = trace.topology
    assert t.type == "dynamic"
    assert len(t.hash) == 64  # SHA-256 hex
    # §9 base graph: 2×3 cliques (3+3) + 2 bridges = 8 edges.
    assert len(t.edges) == 8
    assert len(t.nodes) == 6
    # All edges canonical u < v with positive costs.
    for e in t.edges:
        assert e.u < e.v
        assert e.latency_ticks >= 0
        assert e.bandwidth_bytes_per_tick > 0


def test_lambda2_metadata(trace):
    t = trace.topology
    # Static λ₂ of the clustered base ≈ 1.0.
    assert t.lambda2 == pytest.approx(1.0, abs=1e-9)
    # Dynamic metadata present and ordered λ̄₂ ≤ union λ₂ ≤ static (here union==static).
    assert t.lambda2_expected is not None
    assert t.lambda2_union is not None
    assert 0.0 <= t.lambda2_expected <= t.lambda2_union + 1e-9


def test_dynamic_schedule(trace):
    t = trace.topology
    assert t.dynamic_schedule is not None
    assert len(t.dynamic_schedule) == 3
    rounds = [dr.round for dr in t.dynamic_schedule]
    assert rounds == [0, 1, 2]
    # active_at(round) = all edges minus the round's inactive set.
    all_pairs = {e.pair for e in t.edges}
    for dr in t.dynamic_schedule:
        active = t.active_at(dr.round)
        inactive = set(dr.inactive_edges)
        assert active == (all_pairs - inactive)


def test_neighbours_and_edge_lookup(trace):
    t = trace.topology
    # Node 0 is in cluster 0 (clique with 1,2) plus a bridge to 3.
    nbrs0 = t.neighbours(0)
    assert nbrs0 == sorted(nbrs0)
    assert 1 in nbrs0 and 2 in nbrs0
    # Edge lookup is order-independent.
    e = t.edge(2, 0)
    assert e is not None and e.pair == (0, 2)
    assert t.edge(0, 99) is None


def test_clusters(trace):
    clusters = [node.cluster for node in trace.nodes]
    assert clusters == [0, 0, 0, 1, 1, 1]


def test_hyper_and_model(trace):
    h = trace.hyper
    assert h.k == 2
    assert h.rounds == 100
    assert h.local_epochs == 1
    assert h.gamma_schedule == "EXPLORE_THEN_EXPLOIT"
    assert h.merge_rule == "DRIFT_SUPPRESSED"
    assert h.policy == "COMPOSITE"
    assert h.dirichlet_rho == pytest.approx(0.5)
    assert h.signature_dim == 256
    assert h.signature_seed == 1234

    m = trace.model
    assert m.name == "cifar10_cnn_v1"
    assert m.param_count == 1_000_000
    assert m.payload_bytes_float32 == 4_000_000


def test_load_profiles(trace):
    # Keys parsed back to ints; values are float lists.
    assert set(trace.load_profiles.keys()) == {0, 1}
    assert trace.load_profiles[0] == pytest.approx([0.31, 0.28, 0.30])
    assert trace.load_profiles[1] == pytest.approx([0.10, 0.12, 0.11])
