"""Trade-off (Pareto) views and cost-to-target metrics (§9 ERQ2 and ERQ5).

Accuracy-versus-cumulative-bytes and accuracy-versus-Joules fronts, plus
rounds/bytes/energy-to-target-accuracy A*, so conclusions never hinge on a
single budget convention.
"""
from __future__ import annotations

from dataclasses import dataclass
from typing import List, Optional, Sequence, Tuple


@dataclass
class ToTarget:
    rounds: Optional[int]
    cumulative_bytes: Optional[int]
    cumulative_energy_j: Optional[float]
    reached: bool


def to_target(acc_per_round: Sequence[float], bytes_per_round: Sequence[float],
              energy_j_per_round: Sequence[float], target: float) -> ToTarget:
    """First round at which accuracy reaches ``target``, with the cumulative
    bytes and Joules spent up to and including that round."""
    cb = 0.0
    ce = 0.0
    for r, acc in enumerate(acc_per_round):
        cb += bytes_per_round[r] if r < len(bytes_per_round) else 0.0
        ce += energy_j_per_round[r] if r < len(energy_j_per_round) else 0.0
        if acc >= target:
            return ToTarget(r, int(cb), float(ce), True)
    return ToTarget(None, None, None, False)


def pareto_front(points: Sequence[Tuple[float, float]]) -> List[Tuple[float, float]]:
    """Lower-left Pareto front for (cost, −accuracy) style points where we want
    to MAXIMISE accuracy at MINIMUM cost. ``points`` = list of (cost, accuracy).
    Returns the non-dominated (cost, accuracy) points sorted by cost."""
    pts = sorted(points, key=lambda p: (p[0], -p[1]))
    front: List[Tuple[float, float]] = []
    best_acc = -float("inf")
    for cost, acc in pts:
        if acc > best_acc:
            front.append((cost, acc))
            best_acc = acc
    return front
