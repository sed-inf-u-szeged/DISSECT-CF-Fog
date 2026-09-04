"""Python loader for the Pass-1 ``system_trace.json`` artefact (§8.4 Pass 1).

Mirrors the Java POJO tree in
``simulator/src/main/java/hu/u_szeged/inf/fog/simulator/fl/cosim/SystemTrace.java``.
The Pass-2 learning harness consumes this to obtain the topology, the per-link
cost tables (latency L, bandwidth B), the per-round dynamic edge schedule, and
the per-node load profiles C_j — without regenerating any of them, so the
simulator (Java) is the single source of truth for the system pre-run.

The round-trip is locked by ``SystemTraceRoundTripTest`` (Java) and
``tests/test_system_trace_loader.py`` (pytest) against the committed sample
fixture ``simulator/src/test/resources/fl/sample_system_trace.json``.
"""
from __future__ import annotations

import json
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple

__all__ = [
    "Edge",
    "Node",
    "DynamicRound",
    "Hyper",
    "Model",
    "Topology",
    "SystemTrace",
    "load",
]


@dataclass(frozen=True)
class Edge:
    u: int
    v: int
    latency_ticks: int
    bandwidth_bytes_per_tick: int

    @property
    def pair(self) -> Tuple[int, int]:
        return (self.u, self.v)


@dataclass(frozen=True)
class Node:
    id: int
    profile: str
    cores: int
    mips: float
    ram_gb: float
    lat: float
    lon: float
    cluster: int


@dataclass(frozen=True)
class DynamicRound:
    round: int
    inactive_edges: Tuple[Tuple[int, int], ...]


@dataclass(frozen=True)
class Hyper:
    k: int
    rounds: int
    local_epochs: int
    gamma_schedule: str
    merge_rule: str
    policy: str
    dirichlet_rho: float
    signature_dim: int
    signature_seed: int


@dataclass(frozen=True)
class Model:
    name: str
    param_count: int
    payload_bytes_float32: int


@dataclass
class Topology:
    type: str
    hash: str
    lambda2: float
    nodes: List[Node]
    edges: List[Edge]
    params: Dict[str, Any] = field(default_factory=dict)
    lambda2_expected: Optional[float] = None
    lambda2_union: Optional[float] = None
    dynamic_schedule: Optional[List[DynamicRound]] = None

    def neighbours(self, node: int, active_edges: Optional[set] = None) -> List[int]:
        """Active neighbours of ``node`` in ascending id order.

        ``active_edges`` is an optional set of ``(u, v)`` pairs to restrict to
        (e.g. for a given dynamic round); when ``None`` the full edge set is
        used. Matches ``FLTopology.neighbours`` semantics.
        """
        out = []
        for e in self.edges:
            if active_edges is not None and e.pair not in active_edges:
                continue
            if e.u == node:
                out.append(e.v)
            elif e.v == node:
                out.append(e.u)
        return sorted(out)

    def edge(self, i: int, j: int) -> Optional[Edge]:
        lo, hi = (i, j) if i < j else (j, i)
        for e in self.edges:
            if e.u == lo and e.v == hi:
                return e
        return None

    def inactive_at(self, round_: int) -> set:
        """The set of inactive ``(u, v)`` pairs at ``round_`` from the dynamic
        schedule, or an empty set if there is no schedule / round."""
        if not self.dynamic_schedule:
            return set()
        for dr in self.dynamic_schedule:
            if dr.round == round_:
                return set(dr.inactive_edges)
        return set()

    def active_at(self, round_: int) -> set:
        """The set of active ``(u, v)`` pairs at ``round_`` (all edges minus the
        round's inactive set)."""
        inactive = self.inactive_at(round_)
        return {e.pair for e in self.edges if e.pair not in inactive}


@dataclass
class SystemTrace:
    schema_version: int
    scenario_id: str
    seed: int
    n: int
    topology: Topology
    hyper: Hyper
    model: Model
    load_profiles: Dict[int, List[float]] = field(default_factory=dict)
    cost_tables_note: str = ""

    @property
    def edges(self) -> List[Edge]:
        return self.topology.edges

    @property
    def nodes(self) -> List[Node]:
        return self.topology.nodes


def _parse_node(d: Dict[str, Any]) -> Node:
    geo = d.get("geo") or {}
    return Node(
        id=int(d["id"]),
        profile=str(d.get("profile", "")),
        cores=int(d.get("cores", 0)),
        mips=float(d.get("mips", 0.0)),
        ram_gb=float(d.get("ramGB", 0.0)),
        lat=float(geo.get("lat", 0.0)),
        lon=float(geo.get("lon", 0.0)),
        cluster=int(d.get("cluster", 0)),
    )


def _parse_edge(d: Dict[str, Any]) -> Edge:
    return Edge(
        u=int(d["u"]),
        v=int(d["v"]),
        latency_ticks=int(d["latencyTicks"]),
        bandwidth_bytes_per_tick=int(d["bandwidthBytesPerTick"]),
    )


def _parse_dynamic(d: Dict[str, Any]) -> DynamicRound:
    inactive = tuple(
        (int(pair[0]), int(pair[1])) for pair in (d.get("inactive_edges") or [])
    )
    return DynamicRound(round=int(d["round"]), inactive_edges=inactive)


def _parse_topology(d: Dict[str, Any]) -> Topology:
    sched = d.get("dynamic_schedule")
    return Topology(
        type=str(d["type"]),
        hash=str(d["hash"]),
        lambda2=float(d["lambda2"]),
        nodes=[_parse_node(x) for x in d.get("nodes", [])],
        edges=[_parse_edge(x) for x in d.get("edges", [])],
        params=dict(d.get("params") or {}),
        lambda2_expected=d.get("lambda2_expected"),
        lambda2_union=d.get("lambda2_union"),
        dynamic_schedule=[_parse_dynamic(x) for x in sched] if sched is not None else None,
    )


def _parse_hyper(d: Dict[str, Any]) -> Hyper:
    return Hyper(
        k=int(d["k"]),
        rounds=int(d["rounds"]),
        local_epochs=int(d["localEpochs"]),
        gamma_schedule=str(d["gammaSchedule"]),
        merge_rule=str(d["mergeRule"]),
        policy=str(d["policy"]),
        dirichlet_rho=float(d["dirichletRho"]),
        signature_dim=int(d["signatureDim"]),
        signature_seed=int(d["signatureSeed"]),
    )


def _parse_model(d: Dict[str, Any]) -> Model:
    return Model(
        name=str(d["name"]),
        param_count=int(d["paramCount"]),
        payload_bytes_float32=int(d["payloadBytesFloat32"]),
    )


def load(path: str | Path) -> SystemTrace:
    """Load and parse a ``system_trace.json`` file into a :class:`SystemTrace`."""
    with open(path, "r", encoding="utf-8") as f:
        raw = json.load(f)

    load_profiles = {
        int(k): [float(x) for x in v] for k, v in (raw.get("load_profiles") or {}).items()
    }

    return SystemTrace(
        schema_version=int(raw["schema_version"]),
        scenario_id=str(raw["scenario_id"]),
        seed=int(raw["seed"]),
        n=int(raw["n"]),
        topology=_parse_topology(raw["topology"]),
        hyper=_parse_hyper(raw["hyper"]),
        model=_parse_model(raw["model"]),
        load_profiles=load_profiles,
        cost_tables_note=str(raw.get("cost_tables_note", "")),
    )
