"""Peer-selection policies (§8.2) — exact mirror of the Java ``fl/selection``.

The harness (Pass 2) makes the authoritative peer-selection decisions; the Java
Pass-3 replay re-derives them for the ``selection_agreement_rate``. For that
rate to be 100% by construction, this module must reproduce the Java logic
bit-for-bit: the min–max normalisation with the degenerate guard and the
divergence cold-start (0.5, excluded from min/max), the γ schedule, the
composite score, and — critically — the RNG draw order (one ``next_double`` per
candidate in ascending peer-id order for the top-k tie-break; Fisher–Yates for
random-k).
"""
from __future__ import annotations

from dataclasses import dataclass
from typing import Callable, List, Sequence

import numpy as np

from flrng import SplitMix64

COLD_START_VALUE = 0.5
GAMMA_DEFAULT_MAGNITUDE = 0.25


# --------------------------------------------------------------------------
# Normalizer (Eq. norm + guards) — mirrors fl/selection/Normalizer.java
# --------------------------------------------------------------------------
def normalize(raw: Sequence[float], present: Sequence[bool]) -> np.ndarray:
    raw = np.asarray(raw, dtype=np.float64)
    present = np.asarray(present, dtype=bool)
    out = np.empty(raw.shape[0], dtype=np.float64)
    if not present.any():
        out[:] = COLD_START_VALUE
        return out
    lo = raw[present].min()
    hi = raw[present].max()
    degenerate = (hi == lo)
    for i in range(raw.shape[0]):
        if not present[i]:
            out[i] = COLD_START_VALUE
        elif degenerate:
            out[i] = 0.0
        else:
            out[i] = (raw[i] - lo) / (hi - lo)
    return out


# --------------------------------------------------------------------------
# γ schedule — mirrors fl/selection/GammaSchedule.java
# --------------------------------------------------------------------------
def gamma(schedule: str, round_: int, total_rounds: int,
          magnitude: float = GAMMA_DEFAULT_MAGNITUDE) -> float:
    if schedule == "ALWAYS_EXPLOIT":
        return +magnitude
    if schedule == "ALWAYS_EXPLORE":
        return -magnitude
    # EXPLORE_THEN_EXPLOIT (default)
    return -magnitude if round_ < (total_rounds // 2) else +magnitude


# --------------------------------------------------------------------------
# Cost view over a node's neighbours.
# --------------------------------------------------------------------------
@dataclass
class CostView:
    """Per-neighbour raw cost terms, keyed by neighbour id (mirror of CostView.java)."""
    latency: Callable[[int], float]
    load: Callable[[int], float]
    divergence: Callable[[int], float]
    has_divergence: Callable[[int], bool]
    bandwidth_cost: Callable[[int], float]
    degree: Callable[[int], int]


def _top_k_by_score(sorted_neighbours: List[int], score: np.ndarray,
                    rng: SplitMix64, k: int) -> List[int]:
    """Top-k lowest score; tie-break = one ``next_double`` per candidate in
    ascending id order, then by id. Mirrors PeerSelectionPolicy.topKByScore."""
    n = len(sorted_neighbours)
    tie = np.array([rng.next_double() for _ in range(n)], dtype=np.float64)
    order = sorted(range(n), key=lambda i: (score[i], tie[i], sorted_neighbours[i]))
    take = min(k, n)
    chosen = [sorted_neighbours[order[i]] for i in range(take)]
    return sorted(chosen)


def _terms(sorted_neighbours: List[int], costs: CostView):
    lat = np.array([costs.latency(j) for j in sorted_neighbours], dtype=np.float64)
    load = np.array([costs.load(j) for j in sorted_neighbours], dtype=np.float64)
    bw = np.array([costs.bandwidth_cost(j) for j in sorted_neighbours], dtype=np.float64)
    present = np.array([costs.has_divergence(j) for j in sorted_neighbours], dtype=bool)
    div = np.array([costs.divergence(j) if costs.has_divergence(j) else 0.0
                    for j in sorted_neighbours], dtype=np.float64)
    return lat, load, bw, div, present


def select_peers(policy_id: str, node: int, round_: int, total_rounds: int,
                 neighbours: Sequence[int], costs: CostView, rng: SplitMix64, k: int,
                 gamma_schedule: str = "EXPLORE_THEN_EXPLOIT",
                 alpha: float = 0.25, beta: float = 0.25, delta: float = 0.25,
                 gamma_magnitude: float = 0.25) -> List[int]:
    """Dispatches to the named selector; returns selected peer ids (ascending)."""
    sorted_nb = sorted(neighbours)
    n = len(sorted_nb)
    if n == 0:
        return []

    if policy_id == "ALL_NEIGHBORS":
        return list(sorted_nb)

    if policy_id == "RANDOM":
        shuffled = list(sorted_nb)
        for i in range(n - 1, 0, -1):
            j = rng.next_int(i + 1)
            shuffled[i], shuffled[j] = shuffled[j], shuffled[i]
        return sorted(shuffled[:min(k, n)])

    if policy_id == "DEGREE":
        score = np.array([-float(costs.degree(j)) for j in sorted_nb], dtype=np.float64)
        return _top_k_by_score(sorted_nb, score, rng, k)

    lat, load, bw, div, present = _terms(sorted_nb, costs)
    if policy_id == "SINGLE_LATENCY":
        score = normalize(lat, np.ones(n, dtype=bool))
    elif policy_id == "SINGLE_LOAD":
        score = normalize(load, np.ones(n, dtype=bool))
    elif policy_id == "SINGLE_BANDWIDTH":
        score = normalize(bw, np.ones(n, dtype=bool))
    elif policy_id == "SINGLE_DIVERGENCE":
        score = normalize(div, present)
    elif policy_id == "COMPOSITE":
        l_t = normalize(lat, np.ones(n, dtype=bool))
        c_t = normalize(load, np.ones(n, dtype=bool))
        d_t = normalize(div, present)
        b_t = normalize(bw, np.ones(n, dtype=bool))
        # Renormalise weights so |α|+|β|+|γ|+|δ| = 1 (mirror CompositeScore ctor).
        s = abs(alpha) + abs(beta) + abs(delta) + abs(gamma_magnitude)
        a, b, dl, gm = alpha / s, beta / s, delta / s, gamma_magnitude / s
        g = gamma(gamma_schedule, round_, total_rounds, gm)
        score = a * l_t + b * c_t + g * d_t + dl * b_t
    else:
        raise ValueError(f"unknown policy_id: {policy_id}")
    return _top_k_by_score(sorted_nb, score, rng, k)
