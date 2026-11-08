"""Read-only validation of mobility-pilot-v1. Does not run Java or train models."""

import argparse
from collections import Counter, defaultdict
import hashlib
import json
import math
from pathlib import Path
import re
import sys


def require(condition, message):
    if not condition:
        raise ValueError(message)


def text_hash(text):
    return hashlib.sha256(text.replace("\r\n", "\n").rstrip("\n").encode("utf-8")).hexdigest()


def canonical_route(points):
    """Exact geometry, independent of start vertex or traversal direction."""
    vertices = [tuple(float(v) for v in p) for p in points[:-1]]
    candidates = []
    for sequence in (vertices, list(reversed(vertices))):
        candidates.extend(tuple(sequence[i:] + sequence[:i]) for i in range(len(sequence)))
    return min(candidates)


def shape_signature(points):
    """Pairwise distances: detect translated/rotated/reflected/scaled copies."""
    vertices = [tuple(float(v) for v in p) for p in points[:-1]]
    scale = max(math.dist(a, b) for a in vertices for b in vertices)
    candidates = []
    for sequence in (vertices, list(reversed(vertices))):
        for i in range(len(sequence)):
            ordered = sequence[i:] + sequence[:i]
            candidates.append(tuple(round(math.dist(a, b) / scale, 8) for a in ordered for b in ordered))
    return len(vertices), min(candidates)


def cross(a, b, c):
    return (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0])


def intersects(a, b, c, d):
    def on_segment(x, y, z):
        return cross(x, y, z) == 0 and min(x[0], y[0]) <= z[0] <= max(x[0], y[0]) and min(x[1], y[1]) <= z[1] <= max(x[1], y[1])
    return (cross(a, b, c) * cross(a, b, d) < 0 and cross(c, d, a) * cross(c, d, b) < 0) or any((on_segment(a, b, c), on_segment(a, b, d), on_segment(c, d, a), on_segment(c, d, b)))


def check_points(points, label):
    require(isinstance(points, list) and 4 <= len(points) <= 100, label + ": invalid waypoint count")
    for p in points:
        require(isinstance(p, list) and len(p) == 2 and all(type(v) in (int, float) and math.isfinite(v) and abs(v) <= 10000 for v in p), label + ": invalid coordinates")
    require(points[0] == points[-1] == [0, 0], label + ": route must start/end at 0,0")
    segments = list(zip(points, points[1:]))
    require(all(math.dist(a, b) >= 1 for a, b in segments), label + ": short segment")
    require(sum(math.dist(a, b) for a, b in segments) >= 100, label + ": route is too short")
    for i, (a, b) in enumerate(segments):
        for j in range(i + 1, len(segments)):
            if j == i + 1 or (i == 0 and j == len(segments) - 1):
                continue
            require(not intersects(a, b, *segments[j]), label + ": self-intersecting route")


def read_properties(path):
    values = {}
    for line in path.read_text(encoding="utf-8-sig").splitlines():
        line = line.strip()
        if not line or line.startswith(("#", "!")):
            continue
        require("=" in line and "\\" not in line, str(path) + ": unsupported property syntax")
        key, value = (v.strip() for v in line.split("=", 1))
        require(key not in values, str(path) + ": duplicate property " + key)
        values[key] = value
    require(set(values) == {"scenario_name", "seed", "speed_m_s", "waypoints_m"}, str(path) + ": unexpected/missing properties")
    return values


def validate(plan_path):
    root = plan_path.resolve().parent
    plan = json.loads(plan_path.read_text(encoding="utf-8-sig"))
    require(plan["dataset_id"] == "mobility-pilot-v1" and plan["schema_version"] == 1, "Unsupported dataset plan")
    require(plan["status"] == "configuration_only_not_generated", "This checker validates a configuration plan, not generated data")
    require(plan["speeds_m_s"] == [2.0, 4.1, 6.0] and plan["seed"] == 42, "Unexpected pilot speed/seed settings")
    require(plan["protocol"] == {"history_points": 8, "horizon_ms": 60000, "interval_ms": 60000,
            "nominal_generation_duration_ms": 7200000, "device_count": 1, "node_count": 2,
            "node_separation_m": 1600, "node_radius_m": 1000,
            "start_latitude_deg": 47.73, "start_longitude_deg": 18.27}, "Pilot protocol changed")
    expected_families = {"rectangle": "train", "triangle": "train", "zigzag": "validation", "irregular_polygon": "test"}
    routes = {}
    geometries = set()
    shapes = defaultdict(set)
    excluded = {canonical_route(p) for p in plan["excluded_development_routes_m"]}
    for route in plan["routes"]:
        rid = route["route_id"]
        require(isinstance(rid, str) and re.fullmatch(r"[a-z0-9_]+", rid), "Invalid route_id")
        require(rid not in routes, "Duplicate route_id: " + rid)
        require(expected_families.get(route["family"]) == route["split"], rid + ": family split violation")
        points = route["waypoints_m"]
        check_points(points, rid)
        geometry = canonical_route(points)
        require(geometry not in geometries and geometry not in excluded, rid + ": duplicate or development geometry")
        geometries.add(geometry)
        shapes[shape_signature(points)].add(route["split"])
        routes[rid] = route
    require(len(routes) == 12, "Expected 12 routes")
    require(Counter(r["split"] for r in routes.values()) == {"train": 8, "validation": 2, "test": 2}, "Incorrect route split counts")
    require(Counter(r["family"] for r in routes.values()) == {"rectangle": 4, "triangle": 4, "zigzag": 2, "irregular_polygon": 2}, "Incorrect route family counts")
    require(all(len(splits) == 1 for splits in shapes.values()), "Equivalent shapes span multiple splits")
    cases = set()
    config_paths = set()
    route_speeds = defaultdict(list)
    split_counts = Counter()
    for run in plan["runs"]:
        case = run["case_id"]
        require(isinstance(case, str) and re.fullmatch(r"[a-z0-9_]+", case), "Invalid case_id")
        require(case not in cases, "Duplicate case_id: " + case)
        cases.add(case)
        require(run["route_id"] in routes, case + ": unknown route")
        route = routes[run["route_id"]]
        require(run["split"] == route["split"], case + ": a route spans multiple splits")
        require(run["seed"] == 42 and type(run["speed_m_s"]) in (int, float) and run["speed_m_s"] in plan["speeds_m_s"], case + ": unexpected speed/seed")
        relative = Path(run["config_path"])
        require(not relative.is_absolute() and ".." not in relative.parts and relative.parts[0] == run["split"] and relative.suffix == ".properties", case + ": invalid config path")
        path = (root / relative).resolve()
        require(root in path.parents and path not in config_paths, case + ": unsafe/duplicate config path")
        config_paths.add(path)
        values = read_properties(path)
        require(values["scenario_name"] == case and int(values["seed"]) == run["seed"] and float(values["speed_m_s"]) == run["speed_m_s"], case + ": config parameters differ from manifest")
        points = [[float(v) for v in row.split(",")] for row in values["waypoints_m"].split(";")]
        require(points == route["waypoints_m"], case + ": config route differs from manifest")
        require(text_hash(path.read_text(encoding="utf-8-sig")) == run["config_text_sha256"], case + ": config checksum failed")
        route_speeds[run["route_id"]].append(run["speed_m_s"])
        split_counts[run["split"]] += 1
    require(len(cases) == 36 and split_counts == {"train": 24, "validation": 6, "test": 6}, "Expected 36 cases: 24/6/6")
    require(set(route_speeds) == set(routes) and all(sorted(v) == plan["speeds_m_s"] for v in route_speeds.values()), "Each route must have all three speed variants exactly once")
    require(config_paths == {p.resolve() for p in root.rglob("*.properties")}, "Extra/unlisted scenario configurations found")
    return split_counts


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    default = Path(__file__).resolve().parents[3] / "simulator/src/main/resources/demo/mobility/dataset-v1/dataset-plan.json"
    parser.add_argument("--manifest", type=Path, default=default)
    args = parser.parse_args()
    try:
        counts = validate(args.manifest)
    except (OSError, ValueError, KeyError, TypeError, IndexError) as error:
        print("DATASET_PLAN_INVALID: " + str(error), file=sys.stderr)
        return 1
    print("dataset=mobility-pilot-v1")
    print("routes=12 cases=36 train=24 validation=6 test=6")
    print("speed_m_s=2.0,4.1,6.0 seed=42")
    print("route/config/split/duplicate checks=passed")
    print("simulation=NOT_RUN training=NOT_STARTED")
    print("MOBILITY_DATASET_PLAN_OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
