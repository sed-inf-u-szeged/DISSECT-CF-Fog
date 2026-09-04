"""Unit tests for the artefact-tree loaders.

    py -3.12 -m pytest analysis/test_campaign.py -q
"""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from campaign import Cell  # noqa: E402


def _factors(cell_id: str):
    return Cell(scenario="S", cell_id=cell_id, seed=11, path=Path(".")).factors


def test_values_containing_underscores_are_not_truncated():
    """The runner joins factors with '_' and the values contain '_' too, so a
    naive split truncates them AND silently merges distinct cells whose
    truncations collide — which is exactly how two small-world families became
    one and four single-factor selectors became one."""
    assert _factors("topo=scale_free") == {"topo": "scale_free"}
    assert _factors("topo=small_world_deg=6") == {"topo": "small_world", "deg": "6"}
    assert _factors("topo=small_world_deg=14") == {"topo": "small_world", "deg": "14"}
    assert _factors("pol=SINGLE_LATENCY") == {"pol": "SINGLE_LATENCY"}
    assert _factors("pol=COMPOSITE_mrg=DRIFT_SUPPRESSED") == {
        "pol": "COMPOSITE", "mrg": "DRIFT_SUPPRESSED"}
    assert _factors("pol=RANDOM_mrg=METROPOLIS_HASTINGS") == {
        "pol": "RANDOM", "mrg": "METROPOLIS_HASTINGS"}


def test_distinct_cells_stay_distinct():
    """The failure mode that matters is collision, not truncation per se."""
    ids = ["pol=SINGLE_LATENCY", "pol=SINGLE_LOAD",
           "pol=SINGLE_DIVERGENCE", "pol=SINGLE_BANDWIDTH"]
    assert len({_factors(i)["pol"] for i in ids}) == 4

    sw = ["topo=small_world_deg=6", "topo=small_world_deg=14"]
    assert len({(_factors(i)["topo"], _factors(i)["deg"]) for i in sw}) == 2


def test_unvaried_cell_has_no_factors():
    assert _factors("cell") == {}
