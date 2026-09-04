"""The ERQ1 gate must take its workload from the cell, never from a default.

The gate's whole job is to detect divergence between Track A (batch replay) and
Track B (online coupling). If it configures Track B from hardcoded constants
while Track A ran whatever the scenario file said, the two tracks can differ in
what they trained on and the gate will still report a pass — it would be
validating a workload nobody ran. These tests pin the resolution order.
"""
from __future__ import annotations

import argparse
import json

import pytest

from fidelity_check import _WORKLOAD_KEYS, _read_workload

FULL = {
    "dataset": "mnist", "samples": 12000, "eval_samples": 0, "model": "lenet5",
    "num_classes": 10, "in_channels": 1, "epochs": 1, "lr": 0.05, "batch": 16,
    "init_seed": 777,
}


def _args(**over):
    ns = argparse.Namespace(**{k: None for k in _WORKLOAD_KEYS})
    for k, v in over.items():
        setattr(ns, k, v)
    return ns


def _cell(tmp_path, summary: dict | None):
    pass2 = tmp_path / "pass2"
    pass2.mkdir()
    if summary is not None:
        (pass2 / "summary.json").write_text(json.dumps(summary), encoding="utf-8")
    return pass2


def test_reads_the_workload_the_cell_recorded(tmp_path):
    pass2 = _cell(tmp_path, {"eval_split": "heldout", "workload": FULL})
    assert _read_workload(pass2, _args()) == FULL


def test_a_scenario_edit_is_followed_not_ignored(tmp_path):
    """The regression this guards: editing V.yaml used to leave the gate on
    mnist/12000/0 regardless, so it measured the previous workload."""
    edited = {**FULL, "dataset": "synthetic", "samples": 240}
    pass2 = _cell(tmp_path, {"workload": edited})
    got = _read_workload(pass2, _args())
    assert (got["dataset"], got["samples"]) == ("synthetic", 240)


def test_explicit_flag_overrides_and_is_announced(tmp_path, capsys):
    pass2 = _cell(tmp_path, {"workload": FULL})
    got = _read_workload(pass2, _args(samples=999))
    assert got["samples"] == 999
    assert got["dataset"] == "mnist"          # untouched keys still come from the cell
    warning = capsys.readouterr().out
    assert "WARNING" in warning and "samples=999" in warning


def test_missing_workload_block_fails_loudly(tmp_path):
    pass2 = _cell(tmp_path, {"eval_split": "heldout"})   # pre-fix cell
    with pytest.raises(SystemExit) as e:
        _read_workload(pass2, _args())
    assert "no 'workload' block" in str(e.value)


def test_missing_block_is_usable_if_every_flag_is_supplied(tmp_path):
    pass2 = _cell(tmp_path, {"eval_split": "heldout"})
    assert _read_workload(pass2, _args(**FULL)) == FULL


def test_partial_workload_block_fails_loudly(tmp_path):
    partial = {k: v for k, v in FULL.items() if k != "batch"}
    pass2 = _cell(tmp_path, {"workload": partial})
    with pytest.raises(SystemExit) as e:
        _read_workload(pass2, _args())
    assert "batch" in str(e.value)


def test_absent_summary_names_the_remedy(tmp_path):
    pass2 = _cell(tmp_path, None)
    with pytest.raises(SystemExit) as e:
        _read_workload(pass2, _args())
    assert "run the V scenario first" in str(e.value)
