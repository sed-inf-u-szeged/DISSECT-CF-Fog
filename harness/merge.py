"""Merge rules (§8.2, Eq. merge) — exact mirror of the Java ``fl/merge``.

Inputs and output are aligned over the member array ``[self, peer_0, …]``
(self at index 0; the trace records the self weight first). All rules return
convex weights summing to 1. Divergence is computed in signature space (D5), so
the Java replay reproduces these weights from the same logged signatures.
"""
from __future__ import annotations

from typing import Sequence

import numpy as np

__all__ = ["uniform", "sample_weighted", "drift_suppressed", "metropolis_hastings",
           "fixed_uniform", "weights"]


def uniform(divergence: Sequence[float], sample_count: Sequence[int],
            degree: Sequence[int]) -> np.ndarray:
    m = len(divergence)
    return np.full(m, 1.0 / m, dtype=np.float64)


def sample_weighted(divergence: Sequence[float], sample_count: Sequence[int],
                    degree: Sequence[int]) -> np.ndarray:
    """FedAvg-style sample weighting, ``ω ∝ n_j`` — the ERQ4 control.

    ``drift_suppressed`` changes *two* things relative to ``uniform``: it
    weights by the local sample count AND it damps divergent peers by
    ``1/(1+D)``. Contrasting those two rules alone therefore cannot attribute
    the gain to drift suppression, because under a skewed Dirichlet draw the
    shard sizes span orders of magnitude and sample weighting by itself is a
    large effect. This rule isolates the sample-count factor so the drift factor
    is the only difference between it and ``drift_suppressed``.
    """
    n = np.asarray(sample_count, dtype=np.float64)
    return (n / n.sum()).astype(np.float64)


def drift_suppressed(divergence: Sequence[float], sample_count: Sequence[int],
                     degree: Sequence[int]) -> np.ndarray:
    div = np.asarray(divergence, dtype=np.float64)
    n = np.asarray(sample_count, dtype=np.float64)
    w = n / (1.0 + div)
    return (w / w.sum()).astype(np.float64)


def metropolis_hastings(divergence: Sequence[float], sample_count: Sequence[int],
                        degree: Sequence[int]) -> np.ndarray:
    deg = np.asarray(degree, dtype=np.int64)
    m = len(deg)
    w = np.empty(m, dtype=np.float64)
    deg_self = deg[0]
    peer_sum = 0.0
    for j in range(1, m):
        w[j] = 1.0 / (1.0 + max(deg_self, deg[j]))
        peer_sum += w[j]
    w[0] = 1.0 - peer_sum
    if w[0] < -1e-12:
        raise ValueError(f"MH self weight negative ({w[0]}): peers inconsistent with self degree")
    if w[0] < 0.0:
        w[0] = 0.0
    return w


def fixed_uniform(divergence: Sequence[float], sample_count: Sequence[int],
                  degree: Sequence[int]) -> np.ndarray:
    deg = np.asarray(degree, dtype=np.int64)
    m = len(deg)
    deg_self = int(deg[0])
    if m != deg_self + 1:
        raise ValueError("FixedUniform (D-PSGD) requires self + all neighbours "
                         f"(member count {m} != deg_i+1 {deg_self + 1})")
    return np.full(m, 1.0 / (deg_self + 1), dtype=np.float64)


_RULES = {
    "UNIFORM": uniform,
    "SAMPLE_WEIGHTED": sample_weighted,
    "DRIFT_SUPPRESSED": drift_suppressed,
    "METROPOLIS_HASTINGS": metropolis_hastings,
    "FIXED_UNIFORM": fixed_uniform,
}


def weights(rule_id: str, divergence: Sequence[float], sample_count: Sequence[int],
            degree: Sequence[int]) -> np.ndarray:
    if rule_id not in _RULES:
        raise ValueError(f"unknown merge rule: {rule_id}")
    return _RULES[rule_id](divergence, sample_count, degree)
