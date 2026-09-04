"""Statistical analysis plan (§9 sec:stats).

Each scenario cell is summarised as a mean with a 95% CI (Student-t, or
bootstrap when n_rep < 10). Pairwise comparisons use Welch's t-test, replaced by
Mann–Whitney U when normality is rejected (Shapiro–Wilk, α = 0.05); families of
comparisons are corrected with Holm–Bonferroni; effect sizes (Cliff's δ, or
Cohen's d where parametric) are reported alongside p-values. The TID–λ₂ relation
(H2) is assessed with Spearman rank correlation over the topology-family means,
with an exact permutation p-value.
"""
from __future__ import annotations

from dataclasses import dataclass
from typing import Dict, List, Sequence, Tuple

import numpy as np
from scipy import stats

ALPHA = 0.05


@dataclass
class CI:
    mean: float
    lo: float
    hi: float
    n: int
    method: str


def mean_ci(values: Sequence[float], confidence: float = 0.95) -> CI:
    """Mean with a 95% CI — Student-t for n ≥ 10, else a bootstrap percentile CI."""
    x = np.asarray(values, dtype=float)
    n = x.size
    if n == 0:
        return CI(float("nan"), float("nan"), float("nan"), 0, "none")
    m = float(x.mean())
    if n >= 10:
        se = stats.sem(x)
        h = se * stats.t.ppf(0.5 + confidence / 2, n - 1)
        return CI(m, m - h, m + h, n, "t")
    # bootstrap percentile CI for small samples
    rng = np.random.default_rng(0)
    boots = np.array([rng.choice(x, size=n, replace=True).mean() for _ in range(2000)])
    lo, hi = np.percentile(boots, [(1 - confidence) / 2 * 100, (0.5 + confidence / 2) * 100])
    return CI(m, float(lo), float(hi), n, "bootstrap")


def cohens_d(a: Sequence[float], b: Sequence[float]) -> float:
    a = np.asarray(a, float)
    b = np.asarray(b, float)
    na, nb = a.size, b.size
    if na < 2 or nb < 2:
        return float("nan")
    sp = np.sqrt(((na - 1) * a.var(ddof=1) + (nb - 1) * b.var(ddof=1)) / (na + nb - 2))
    return float((a.mean() - b.mean()) / sp) if sp > 0 else 0.0


def cliffs_delta(a: Sequence[float], b: Sequence[float]) -> float:
    """Cliff's δ — non-parametric effect size in [-1, 1]."""
    a = np.asarray(a, float)
    b = np.asarray(b, float)
    if a.size == 0 or b.size == 0:
        return float("nan")
    gt = sum(1 for x in a for y in b if x > y)
    lt = sum(1 for x in a for y in b if x < y)
    return (gt - lt) / (a.size * b.size)


@dataclass
class Comparison:
    test: str
    statistic: float
    p_value: float
    effect_name: str
    effect_size: float
    normal: bool


def compare(a: Sequence[float], b: Sequence[float]) -> Comparison:
    """Shapiro–Wilk normality gate → Welch's t (parametric) or Mann–Whitney U,
    with the matching effect size. Falls back gracefully on tiny samples."""
    a = np.asarray(a, float)
    b = np.asarray(b, float)
    normal = True
    for s in (a, b):
        if s.size >= 3:
            try:
                if stats.shapiro(s).pvalue < ALPHA:
                    normal = False
            except ValueError:
                normal = False
        else:
            normal = False
    if normal:
        res = stats.ttest_ind(a, b, equal_var=False)  # Welch
        return Comparison("welch_t", float(res.statistic), float(res.pvalue),
                          "cohens_d", cohens_d(a, b), True)
    res = stats.mannwhitneyu(a, b, alternative="two-sided")
    return Comparison("mann_whitney_u", float(res.statistic), float(res.pvalue),
                      "cliffs_delta", cliffs_delta(a, b), False)


def holm_bonferroni(p_values: Sequence[float], alpha: float = ALPHA) -> List[bool]:
    """Holm–Bonferroni step-down: returns per-comparison reject decisions, in the
    original input order."""
    p = list(p_values)
    m = len(p)
    order = sorted(range(m), key=lambda i: p[i])
    reject = [False] * m
    for rank, i in enumerate(order):
        if p[i] <= alpha / (m - rank):
            reject[i] = True
        else:
            break  # step-down stops at the first non-rejection
    return reject


def holm_adjusted(p_values: Sequence[float]) -> List[float]:
    """Holm-adjusted p-values, in the original input order.

    Reporting a *raw* p-value while claiming a Holm correction understates the
    evidence threshold, so the adjusted number is what belongs in the text:
    ``p_adj[i] = max over the step-down prefix of (m - rank)·p``, clipped to 1
    and made monotone non-decreasing in rank.
    """
    p = list(p_values)
    m = len(p)
    if m == 0:
        return []
    order = sorted(range(m), key=lambda i: p[i])
    adjusted = [0.0] * m
    running = 0.0
    for rank, i in enumerate(order):
        running = max(running, (m - rank) * p[i])
        adjusted[i] = min(1.0, running)
    return adjusted


@dataclass
class Contrast:
    """One pairwise comparison plus its family-corrected p-value."""
    label: str
    mean_a: float
    mean_b: float
    delta: float
    test: str
    p_value: float
    p_adjusted: float
    effect_name: str
    effect_size: float
    reject: bool


def holm_family(comparisons: Sequence[tuple], alpha: float = ALPHA) -> List[Contrast]:
    """Runs a family of pairwise comparisons and Holm-corrects them together.

    :param comparisons: ``(label, sample_a, sample_b)`` triples; the reported
                        delta is ``mean(a) − mean(b)``.
    :return: one :class:`Contrast` per input, in input order, carrying both the
             raw and the Holm-adjusted p-value.
    """
    results = [(label, compare(a, b), float(np.mean(a)), float(np.mean(b)))
               for (label, a, b) in comparisons]
    adjusted = holm_adjusted([r[1].p_value for r in results])
    out: List[Contrast] = []
    for (label, cmp_, mean_a, mean_b), p_adj in zip(results, adjusted):
        out.append(Contrast(
            label=label, mean_a=mean_a, mean_b=mean_b, delta=mean_a - mean_b,
            test=cmp_.test, p_value=cmp_.p_value, p_adjusted=p_adj,
            effect_name=cmp_.effect_name, effect_size=cmp_.effect_size,
            reject=p_adj <= alpha))
    return out


@dataclass
class Spearman:
    rho: float
    p_value: float
    n: int


def spearman(x: Sequence[float], y: Sequence[float]) -> Spearman:
    """Spearman rank correlation — used for the TID–λ₂ monotonicity claim (H2)."""
    x = np.asarray(x, float)
    y = np.asarray(y, float)
    if x.size < 3:
        return Spearman(float("nan"), float("nan"), int(x.size))
    res = stats.spearmanr(x, y)
    return Spearman(float(res.statistic), float(res.pvalue), int(x.size))


@dataclass
class MonotonicityResult:
    """The TID–λ₂ relation, reported at both levels of analysis."""
    #: Over topology means — one point per family. This is the honest test.
    family: Spearman
    #: Over every (family, seed) point, retained for completeness.
    pooled: Spearman
    #: Distinct λ₂ levels among the pooled points.
    distinct_levels: int
    #: Permutation p-value for the family-level ρ (exact for small family counts).
    family_permutation_p: float


def tid_lambda2_monotonicity(groups: Dict[str, Tuple[List[float], List[float]]],
                             rng_seed: int = 0) -> MonotonicityResult:
    """Assesses H2 without pseudo-replication.

    ``groups`` maps a topology family to ``(lambda2_per_seed, tid_per_seed)``.

    Pooling all (family, seed) points inflates significance: seeds within a
    family are repeated measurements of essentially the *same* λ₂ — for a ring
    or a full mesh λ₂ is a deterministic function of n and is literally
    identical across seeds — so the pooled test behaves as if there were far
    more independent levels of the predictor than there are. The number of
    independent units is the number of families, so the family-level ρ over the
    per-family means is the test that carries the claim, with an exact
    permutation p-value (the asymptotic p-value is meaningless at n≈4). The
    pooled statistic is still returned, but must be labelled as descriptive.
    """
    families = sorted(groups)
    lam_means = [float(np.mean(groups[f][0])) for f in families]
    tid_means = [float(np.mean(groups[f][1])) for f in families]

    pooled_lam = [v for f in families for v in groups[f][0]]
    pooled_tid = [v for f in families for v in groups[f][1]]
    distinct = len({round(v, 6) for v in pooled_lam})

    if len(families) >= 3:
        fam = spearman(lam_means, tid_means)
        # Exact permutation over the family orderings.
        perms = list(_permutations(range(len(families))))
        observed = fam.rho
        extreme = sum(
            1 for p in perms
            if stats.spearmanr(lam_means, [tid_means[i] for i in p]).statistic <= observed + 1e-12)
        perm_p = extreme / len(perms)
    else:
        fam = Spearman(float("nan"), float("nan"), len(families))
        perm_p = float("nan")

    return MonotonicityResult(family=fam, pooled=spearman(pooled_lam, pooled_tid),
                              distinct_levels=distinct, family_permutation_p=perm_p)


def _permutations(seq):
    from itertools import permutations
    return permutations(seq)
