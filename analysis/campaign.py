"""Loaders over the released campaign artefact tree (§9).

Everything the §9 tables and figures need is read from the per-run artefact
directories — no re-simulation, and no absolute paths: the results root is
resolved from the repository layout or overridden with ``--results-root``, so a
reviewer can regenerate every number after a plain clone.

Tree layout produced by ``FLScenarioRunner``::

    <results>/<campaign>/<scenario>/<cellId>/seed_<s>/
        pass1/system_trace.json
        pass2/{learning_trace.csv,signatures.bin,summary.json}
        pass2_centralized/summary.json        (S1 only)
        pass2_hierarchical/summary.json       (S1 only)
        pass3/{gossip_telemetry.csv,energy_per_round.csv,tid_timeseries.csv,
               per_exchange.csv,idle_time.csv,run_metadata.json}
        tid_gap.json
"""
from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path
from typing import Dict, Iterator, List, Optional, Tuple

import numpy as np
import pandas as pd

#: analysis/ lives directly under the repository root.
REPO_ROOT = Path(__file__).resolve().parent.parent
DEFAULT_RESULTS = REPO_ROOT / "simulator" / "results"


def results_root(override: Optional[str] = None) -> Path:
    """The campaign results root: explicit override, else the repo default."""
    return Path(override).resolve() if override else DEFAULT_RESULTS


@dataclass(frozen=True)
class Cell:
    """One (cell, seed) run directory plus its identifying factors."""
    scenario: str
    cell_id: str
    seed: int
    path: Path

    @property
    def factors(self) -> Dict[str, str]:
        """Parses a cell id into its factor map.

        The runner joins ``shortKey=value`` pairs with ``_``, but the *values*
        themselves contain underscores (``scale_free``, ``DRIFT_SUPPRESSED``,
        ``SINGLE_LATENCY``), so a naive split on ``_`` truncates them — and
        silently merges distinct cells whose truncated values collide, e.g. the
        two ``small_world_deg=*`` families or the four ``SINGLE_*`` selectors.
        A token without ``=`` is therefore a continuation of the preceding
        value, not a new key::

            topo=small_world_deg=6            → {'topo': 'small_world', 'deg': '6'}
            pol=COMPOSITE_mrg=DRIFT_SUPPRESSED → {'pol': 'COMPOSITE',
                                                  'mrg': 'DRIFT_SUPPRESSED'}
        """
        if self.cell_id == "cell":
            return {}
        out: Dict[str, str] = {}
        key = None
        for part in self.cell_id.split("_"):
            if "=" in part:
                key, value = part.split("=", 1)
                out[key] = value
            elif key is not None:
                out[key] = f"{out[key]}_{part}"
        return out


def iter_cells(scenario: str, *, root: Optional[str] = None,
               campaign: str = "paper") -> Iterator[Cell]:
    """Yields every ``seed_*`` run directory of a scenario, in sorted order."""
    base = results_root(root) / campaign / scenario
    if not base.is_dir():
        raise FileNotFoundError(
            f"scenario '{scenario}' not found under {base}. Run the campaign first, "
            f"or pass --results-root.")
    for cell_dir in sorted(p for p in base.iterdir() if p.is_dir()):
        for seed_dir in sorted(cell_dir.glob("seed_*")):
            yield Cell(scenario, cell_dir.name, int(seed_dir.name.split("_")[1]), seed_dir)


def cells_by_factor(scenario: str, **kw) -> Dict[str, List[Cell]]:
    """Groups a scenario's runs by cell id (seeds collected per cell)."""
    grouped: Dict[str, List[Cell]] = {}
    for c in iter_cells(scenario, **kw):
        grouped.setdefault(c.cell_id, []).append(c)
    return grouped


# ----------------------------------------------------------------------
# Per-run readers
# ----------------------------------------------------------------------

def summary(cell: Cell, leg: str = "pass2") -> Dict:
    """A Pass-2 leg's ``summary.json`` (held-out accuracies)."""
    p = cell.path / leg / "summary.json"
    if not p.exists():
        raise FileNotFoundError(f"{p} missing — was the '{leg}' leg run for this cell?")
    return json.loads(p.read_text(encoding="utf-8"))


def tid_gap(cell: Cell) -> Dict:
    return json.loads((cell.path / "tid_gap.json").read_text(encoding="utf-8"))


def run_metadata(cell: Cell) -> Dict:
    return json.loads((cell.path / "pass3" / "run_metadata.json").read_text(encoding="utf-8"))


def system_trace(cell: Cell) -> Dict:
    return json.loads((cell.path / "pass1" / "system_trace.json").read_text(encoding="utf-8"))


def lambda2(cell: Cell) -> float:
    """λ₂ of the cell's graph — λ̄₂ (expected over realised graphs) for dynamic."""
    topo = system_trace(cell)["topology"]
    return float(topo.get("lambda2_expected") or topo["lambda2"])


def telemetry(cell: Cell) -> pd.DataFrame:
    return pd.read_csv(cell.path / "pass3" / "gossip_telemetry.csv")


def tid_series(cell: Cell, scope: str = "global") -> pd.DataFrame:
    df = pd.read_csv(cell.path / "pass3" / "tid_timeseries.csv")
    return df[df.scope == scope].sort_values("round")


def final_tid(cell: Cell, scope: str = "global") -> float:
    return float(tid_series(cell, scope).value.iloc[-1])


def learning_trace(cell: Cell, leg: str = "pass2") -> pd.DataFrame:
    return pd.read_csv(cell.path / leg / "learning_trace.csv")


def heldout_curve(cell: Cell, leg: str = "pass2") -> pd.Series:
    """Mean held-out accuracy per round, over nodes.

    This is the reportable accuracy curve. The ``acc`` column is the local fit
    on each node's own training shard — under a skewed Dirichlet draw a node
    holding two classes trivially reaches ≈1.0 there, so plotting it as a test
    accuracy overstates every mode and disagrees with the summary tables.
    """
    df = learning_trace(cell, leg)
    if "heldout_acc" not in df.columns:
        raise KeyError(
            f"{cell.path/leg}/learning_trace.csv has no heldout_acc column — it predates "
            f"the held-out split and its accuracies are training-pool figures.")
    return df.dropna(subset=["heldout_acc"]).groupby("round")["heldout_acc"].mean()


# ----------------------------------------------------------------------
# System metrics
# ----------------------------------------------------------------------

def model_payload_bytes(cell: Cell) -> int:
    """The size of ONE model payload.

    Read from the Pass-1 model block (or the per-exchange ledger), never
    inferred from a telemetry row: ``gossip_telemetry.csv``'s ``ul_bytes`` is a
    node's *per-round* upload, which at k=2 is two payloads and can be more when
    other nodes also select that node. Treating it as a single payload doubles
    every analytic aggregator figure derived from it.
    """
    return int(system_trace(cell)["model"]["payloadBytesFloat32"])


@dataclass(frozen=True)
class TrafficSummary:
    """Traffic in both conventions, because they answer different questions.

    ``endpoint_total_mb`` sums ul+dl over every node, so each transferred byte
    is counted twice (once at each endpoint). ``on_wire_mb`` counts each byte
    once. ``busiest_node_mb`` is the single busiest endpoint — the congestion
    figure — and must be compared against the *aggregator's own* endpoint load,
    not against a whole-federation total.
    """
    endpoint_total_mb: float
    on_wire_mb: float
    busiest_node_mb: float
    busiest_node: int
    mean_idle_ticks: float
    rounds: int
    n: int
    payload_mb: float

    @property
    def centralized_aggregator_mb(self) -> float:
        """Analytic aggregator endpoint load: 2·n·p per round (n uploads in,
        n broadcasts out), i.e. what a single central aggregator would carry."""
        return 2.0 * self.n * self.payload_mb * self.rounds

    @property
    def centralized_endpoint_total_mb(self) -> float:
        """Same traffic counted at BOTH endpoints, to compare like-for-like
        against ``endpoint_total_mb``."""
        return 2.0 * self.centralized_aggregator_mb

    @property
    def busiest_vs_aggregator_pct(self) -> float:
        """The decongestion figure: busiest gossip endpoint as a percentage of
        the central aggregator's endpoint load."""
        return 100.0 * self.busiest_node_mb / self.centralized_aggregator_mb


def traffic(cell: Cell) -> TrafficSummary:
    tel = telemetry(cell)
    per_node = tel.assign(b=tel.ul_bytes + tel.dl_bytes).groupby("node")["b"].sum()
    payload = model_payload_bytes(cell) / 1e6
    return TrafficSummary(
        endpoint_total_mb=float(per_node.sum()) / 1e6,
        on_wire_mb=float(tel.ul_bytes.sum()) / 1e6,
        busiest_node_mb=float(per_node.max()) / 1e6,
        busiest_node=int(per_node.idxmax()),
        mean_idle_ticks=float(tel.idle_ticks.mean()),
        rounds=int(tel["round"].nunique()),
        n=int(tel.node.nunique()),
        payload_mb=payload,
    )


def energy_joules(cell: Cell) -> float:
    """Total metered energy over the run, in joules (both endpoints, §8.1)."""
    df = pd.read_csv(cell.path / "pass3" / "energy_per_round.csv")
    return float(np.nansum(df.energy_mj.values)) / 1000.0


def energy_per_node_joules(cell: Cell) -> pd.Series:
    df = pd.read_csv(cell.path / "pass3" / "energy_per_round.csv")
    return df.groupby("node")["energy_mj"].sum() / 1000.0


def round_duration_ticks(cell: Cell) -> float:
    df = pd.read_csv(cell.path / "pass3" / "gossip_round.csv")
    return float(df.round_duration_ticks.mean())


def exchange_sets(cell: Cell) -> Dict[Tuple[int, int], Tuple[int, ...]]:
    """Maps each ``(round, node)`` to the set of peers that node exchanged with.

    Read from ``pass3/per_exchange.csv``, which logs one row per *directed*
    transfer; a symmetric exchange therefore appears twice, so a node's
    outgoing rows in a round are the union of the peers it selected and the
    peers that selected it. That union is the right object for comparing two
    runs' selection behaviour: it is identical across runs if and only if every
    node's own choice is, and it needs no assumption about which side initiated.
    """
    df = pd.read_csv(cell.path / "pass3" / "per_exchange.csv")
    return {(int(rnd), int(src)): tuple(sorted(int(d) for d in grp["dst"]))
            for (rnd, src), grp in df.groupby(["round", "src"])}


def collect(cells: List[Cell], fn) -> List[float]:
    """Applies ``fn`` across a cell's seeds, returning the per-seed values."""
    return [float(fn(c)) for c in cells]
