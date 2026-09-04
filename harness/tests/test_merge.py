"""Merge rules — cross-language contract: mirrors MergeRuleTest.java's exact
numeric cases (shared ground truth ⇒ Java and Python agree)."""
from __future__ import annotations

import numpy as np
import pytest

import merge

EPS = 1e-12


def test_all_rules_sum_to_one():
    div = [0.0, 0.2, 1.5, 0.7]
    n = [10, 5, 8, 3]
    deg = [3, 2, 4, 2]
    for rule in ("UNIFORM", "SAMPLE_WEIGHTED", "DRIFT_SUPPRESSED", "METROPOLIS_HASTINGS"):
        w = merge.weights(rule, div, n, deg)
        assert abs(w.sum() - 1.0) < EPS
        assert (w >= -EPS).all()


def test_sample_weighted_is_the_drift_free_control():
    """ERQ4's one-factor control: SAMPLE_WEIGHTED holds the n_j factor and drops
    the 1/(1+D) factor, so SAMPLE_WEIGHTED → DRIFT_SUPPRESSED isolates drift
    suppression. Contrasting UNIFORM → DRIFT_SUPPRESSED alone changes both."""
    n = [2, 3, 5]
    deg = [2, 2, 2]
    # Pure sample weighting, independent of divergence.
    w_flat = merge.weights("SAMPLE_WEIGHTED", [0.0, 0.0, 0.0], n, deg)
    w_div = merge.weights("SAMPLE_WEIGHTED", [0.0, 1.0, 9.0], n, deg)
    assert np.allclose(w_flat, [0.2, 0.3, 0.5], atol=1e-12)
    assert np.allclose(w_flat, w_div, atol=1e-12), "SAMPLE_WEIGHTED must ignore divergence"
    # At equal divergence DRIFT_SUPPRESSED collapses onto it — the two differ
    # only through the drift factor, which is exactly the isolated lever.
    assert np.allclose(
        merge.weights("DRIFT_SUPPRESSED", [0.0, 0.0, 0.0], n, deg), w_flat, atol=1e-12)
    assert not np.allclose(
        merge.weights("DRIFT_SUPPRESSED", [0.0, 1.0, 9.0], n, deg), w_flat, atol=1e-6)


def test_uniform():
    w = merge.weights("UNIFORM", [0.0, 0.2, 1.5], [10, 5, 8], [2, 2, 2])
    assert np.allclose(w, 1.0 / 3.0, atol=EPS)


def test_drift_suppressed_equal_divergence_recovers_sample_weighting():
    # Mirrors MergeRuleTest.driftSuppressedFormula: n={2,3,5}, equal D ⇒ {0.2,0.3,0.5}.
    w = merge.weights("DRIFT_SUPPRESSED", [0.0, 0.0, 0.0], [2, 3, 5], [2, 2, 2])
    assert np.allclose(w, [0.2, 0.3, 0.5], atol=1e-9)


def test_drift_suppressed_monotone_in_d():
    n = [4, 4, 4]
    deg = [2, 2, 2]
    last = float("inf")
    for d in (0.0, 0.5, 1.0, 2.0, 5.0):
        w = merge.weights("DRIFT_SUPPRESSED", [0.0, d, 0.0], n, deg)
        assert w[1] < last
        last = w[1]


def test_metropolis_hastings_values():
    # Mirrors MergeRuleTest.metropolisHastingsRowStochastic: self deg 2, peers deg 3,2.
    w = merge.weights("METROPOLIS_HASTINGS", [0.0, 0.3, 0.9], [1, 1, 1], [2, 3, 2])
    assert abs(w[1] - 1.0 / 4.0) < 1e-12
    assert abs(w[2] - 1.0 / 3.0) < 1e-12
    assert abs(w[0] - (1.0 - (1.0 / 4.0 + 1.0 / 3.0))) < 1e-12
    assert abs(w.sum() - 1.0) < EPS


def test_metropolis_hastings_ring():
    w = merge.weights("METROPOLIS_HASTINGS", [0.0, 0.1, 0.4], [1, 1, 1], [2, 2, 2])
    assert np.allclose(w, 1.0 / 3.0, atol=1e-12)


def test_fixed_uniform_dpsgd():
    # self deg 3 + 3 neighbours ⇒ uniform 1/4; a k-subset must be rejected.
    w = merge.weights("FIXED_UNIFORM", [0.0, 0.2, 0.4, 0.6], [1, 1, 1, 1], [3, 2, 4, 2])
    assert np.allclose(w, 0.25, atol=EPS)
    with pytest.raises(ValueError):
        merge.weights("FIXED_UNIFORM", [0.0, 0.2], [1, 1], [3, 2])
