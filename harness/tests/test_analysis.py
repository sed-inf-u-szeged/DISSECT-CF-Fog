"""Analysis package tests (§9 stats plan + Results-Schema figures).

Covers the statistical primitives on known cases and verifies figures.py renders
the R1/R4/R7 exhibits from a minimal artefact tree without manual steps (P5 DoD).
"""
from __future__ import annotations

import sys
from pathlib import Path

import numpy as np
import pytest

# analysis/ is a sibling of harness/; add it to the path for these tests.
ANALYSIS = Path(__file__).resolve().parent.parent.parent / "analysis"
sys.path.insert(0, str(ANALYSIS))

import stats as st          # noqa: E402
import pareto               # noqa: E402
import figures              # noqa: E402


# ---------------------------------------------------------------- stats
def test_cohens_d_and_cliffs_delta():
    assert abs(st.cohens_d([1, 2, 3], [1, 2, 3])) < 1e-9
    assert st.cohens_d([10, 11, 12], [1, 2, 3]) > 3.0
    assert st.cliffs_delta([3, 4, 5], [1, 2]) == pytest.approx(1.0)
    assert st.cliffs_delta([1, 2], [3, 4]) == pytest.approx(-1.0)


def test_holm_bonferroni_step_down():
    # sorted p: 0.001 (≤0.05/3), 0.04 (≤0.05/2? no → stop), 0.5
    assert st.holm_bonferroni([0.001, 0.04, 0.5], 0.05) == [True, False, False]
    assert st.holm_bonferroni([0.001, 0.002, 0.003], 0.05) == [True, True, True]


def test_spearman_monotone():
    x = list(range(1, 11))
    y = [v * 2 + 1 for v in x]  # strictly increasing
    s = st.spearman(x, y)
    assert s.rho == pytest.approx(1.0)
    assert s.p_value < 0.05


def test_compare_detects_large_difference():
    rng = np.random.default_rng(0)
    a = rng.normal(0.0, 1.0, 40)
    b = rng.normal(4.0, 1.0, 40)
    res = st.compare(a, b)
    assert res.p_value < 0.05
    assert res.test in ("welch_t", "mann_whitney_u")
    assert abs(res.effect_size) > 0.5


def test_mean_ci_bootstrap_small_sample():
    ci = st.mean_ci([0.5, 0.6, 0.55], confidence=0.95)
    assert ci.method == "bootstrap"
    assert ci.lo <= ci.mean <= ci.hi


# ---------------------------------------------------------------- pareto
def test_to_target_and_front():
    tt = pareto.to_target([0.4, 0.7, 0.85], [100, 100, 100], [1.0, 1.0, 1.0], target=0.8)
    assert tt.reached and tt.rounds == 2 and tt.cumulative_bytes == 300
    front = pareto.pareto_front([(10, 0.5), (20, 0.6), (15, 0.55), (30, 0.55)])
    # (30,0.55) is dominated by (20,0.6); front is increasing-accuracy at min cost.
    assert (10, 0.5) in front and (20, 0.6) in front
    assert (30, 0.55) not in front


# ---------------------------------------------------------------- figures
def test_figures_generate_r1_r4_r7(tmp_path):
    pass2 = tmp_path / "pass2"
    pass3 = tmp_path / "pass3"
    pass2.mkdir()
    pass3.mkdir()
    (pass2 / "learning_trace.csv").write_text(
        "schema_version,scenario_id,topology_hash,policy_id,merge_rule,gamma_schedule,seed,"
        "node,round,acc,loss,delta_norm,payload_bytes,train_time_ms,n_samples,peers,merge_weights,staleness_max\n"
        "1,S,hash,COMPOSITE,DRIFT_SUPPRESSED,EXPLORE_THEN_EXPLOIT,42,0,0,0.5,1.0,0.1,4000,10.0,80,1;2,0.8;0.1;0.1,0\n"
        "1,S,hash,COMPOSITE,DRIFT_SUPPRESSED,EXPLORE_THEN_EXPLOIT,42,1,0,0.55,0.9,0.1,4000,10.0,46,0;2,0.8;0.1;0.1,0\n"
        "1,S,hash,COMPOSITE,DRIFT_SUPPRESSED,EXPLORE_THEN_EXPLOIT,42,0,1,0.6,0.8,0.1,4000,10.0,80,1;2,0.8;0.1;0.1,1\n"
        "1,S,hash,COMPOSITE,DRIFT_SUPPRESSED,EXPLORE_THEN_EXPLOIT,42,1,1,0.65,0.7,0.1,4000,10.0,46,0;2,0.8;0.1;0.1,1\n",
        encoding="utf-8")
    (pass3 / "tid_timeseries.csv").write_text(
        "round,scope,value\n0,global,1.5\n0,inter_cluster,2.0\n0,consensus,0.7\n"
        "1,global,1.1\n1,inter_cluster,1.6\n1,consensus,0.5\n", encoding="utf-8")

    produced = figures.generate(pass2, pass3, tmp_path / "figs")
    names = {p.name for p in produced}
    assert names == {"R1_accuracy_vs_round.png", "R4_tid_dispersion.png", "R7_selector_comparison.png"}
    for p in produced:
        assert p.exists() and p.stat().st_size > 0


def test_figures_placeholder_when_artefacts_missing(tmp_path):
    # No artefacts: still produces (placeholder) PNGs without manual steps.
    produced = figures.generate(None, None, tmp_path / "figs")
    for p in produced:
        assert p.exists() and p.stat().st_size > 0


# ---------------------------------------------------------------- tid_gap (§8.3 Eq. tid-gap)
import tid_gap as tg        # noqa: E402


def test_tid_gap_arithmetic():
    dec = {"acc_mean_over_nodes": 0.70, "acc_mean_model": 0.74, "acc_worst_node": 0.61}
    cen = {"acc_global_model": 0.82}
    out = tg.tid_gap(dec, cen)
    assert abs(out["tid_gap"] - 0.12) < 1e-12
    assert abs(out["tid_gap_mean_model"] - 0.08) < 1e-12
    assert out["a_cen"] == 0.82 and out["a_dec_worst_node"] == 0.61


def test_tid_gap_secondary_view_optional():
    out = tg.tid_gap({"acc_mean_over_nodes": 0.5}, {"acc_global_model": 0.6})
    assert abs(out["tid_gap"] - 0.1) < 1e-12
    assert "tid_gap_mean_model" not in out
