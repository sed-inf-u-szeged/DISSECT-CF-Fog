"""Peer selection — cross-language contract: mirrors PeerSelectionTest.java's
exact cases, including the ring degeneracy and explore/exploit behaviour, using
the same derived RNG stream so Java and Python agree."""
from __future__ import annotations

import flrng
import selection
from selection import CostView


def _cv(latency=None, load=None, divergence=None, bandwidth=None, degree=None):
    latency = latency or {}
    load = load or {}
    divergence = divergence or {}
    bandwidth = bandwidth or {}
    degree = degree or {}
    return CostView(
        latency=lambda j: latency.get(j, 0.0),
        load=lambda j: load.get(j, 0.0),
        divergence=lambda j: divergence.get(j, 0.0),
        has_divergence=lambda j: j in divergence,
        bandwidth_cost=lambda j: bandwidth.get(j, 0.0),
        degree=lambda j: degree.get(j, 0),
    )


ALL_SELECTORS = [
    "RANDOM", "DEGREE", "SINGLE_LATENCY", "SINGLE_LOAD",
    "SINGLE_DIVERGENCE", "SINGLE_BANDWIDTH", "COMPOSITE", "ALL_NEIGHBORS",
]


def test_ring_degeneracy_all_selectors():
    cv = _cv(latency={1: 10, 5: 20}, load={1: 0.3, 5: 0.7},
             divergence={1: 0.5, 5: 1.2}, bandwidth={1: 100, 5: 200},
             degree={1: 2, 5: 2})
    for pid in ALL_SELECTORS:
        rng = flrng.derive(42, 0, 0)
        chosen = selection.select_peers(pid, 0, 0, 100, [1, 5], cv, rng, 2)
        assert chosen == [1, 5], f"{pid} must return both ring neighbours when k=2=degree"


def test_single_factor_latency():
    cv = _cv(latency={1: 50, 2: 10, 3: 30, 4: 99})
    rng = flrng.derive(1, 0, 0)
    assert selection.select_peers("SINGLE_LATENCY", 0, 0, 100, [1, 2, 3, 4], cv, rng, 2) == [2, 3]


def test_degree_based():
    cv = _cv(degree={1: 2, 2: 9, 3: 5, 4: 1})
    rng = flrng.derive(1, 0, 0)
    assert selection.select_peers("DEGREE", 0, 0, 100, [1, 2, 3, 4], cv, rng, 2) == [2, 3]


def test_composite_explore_then_exploit():
    cv = _cv(latency={1: 5, 2: 5, 3: 5}, load={1: 0.1, 2: 0.1, 3: 0.1},
             bandwidth={1: 7, 2: 7, 3: 7}, divergence={1: 0.0, 2: 0.5, 3: 1.0})
    # explore (round 0 < T/2): prefer the most divergent peer (3).
    rng = flrng.derive(9, 0, 0)
    assert selection.select_peers("COMPOSITE", 0, 0, 100, [1, 2, 3], cv, rng, 1) == [3]
    # exploit (round 80 >= T/2): prefer the most similar peer (1).
    rng2 = flrng.derive(9, 80, 0)
    assert selection.select_peers("COMPOSITE", 0, 80, 100, [1, 2, 3], cv, rng2, 1) == [1]


def test_random_k_deterministic():
    cv = _cv()
    a = selection.select_peers("RANDOM", 0, 3, 100, [1, 2, 3, 4, 5, 6], cv, flrng.derive(7, 3, 0), 2)
    b = selection.select_peers("RANDOM", 0, 3, 100, [1, 2, 3, 4, 5, 6], cv, flrng.derive(7, 3, 0), 2)
    assert a == b
    assert len(a) == 2 and a[0] < a[1]


def test_all_neighbors():
    cv = _cv()
    assert selection.select_peers("ALL_NEIGHBORS", 0, 0, 100, [4, 1, 9, 2], cv, flrng.derive(1, 0, 0), 2) == [1, 2, 4, 9]
