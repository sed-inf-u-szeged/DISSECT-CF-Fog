"""Regenerate the SplitMix64/derive golden-vector CSV used by both the Java and
the Python tests.

The CSV is the *single source of truth* for the cross-language PRNG contract
(P0.3). To regenerate after any change to the algorithm or the derive constants:

    py harness/tools/generate_golden.py

The output is written to:

    simulator/src/test/resources/fl/splitmix64_golden.csv

Both ``simulator/src/test/java/.../util/SplitMix64GoldenTest.java`` and
``harness/tests/test_flrng_golden.py`` read this CSV and assert exact
equality on bit patterns (longs and doubles via raw bits, ints via decimal).
Any drift on either side breaks the test.

Each row records, for one ``(seed, round, node)`` triple:

* 3 successive ``next_long()`` outputs from a fresh derived stream;
* 3 successive ``next_double()`` outputs from a fresh derived stream;
* one ``next_int(7)`` from a fresh derived stream (the rejection-sampled int
  that the random-k selector and the top-k tie-break consume).
"""
from __future__ import annotations

import csv
import os
import struct
import sys
from pathlib import Path

HARNESS_DIR = Path(__file__).resolve().parent.parent
PROJECT_ROOT = HARNESS_DIR.parent
sys.path.insert(0, str(HARNESS_DIR))

from flrng import derive  # noqa: E402  (import after sys.path tweak)

# 20 fixed triples chosen to exercise edges of the input domain:
#   - zero seed/round/node
#   - node = -1 (graph-level decisions, e.g. dynamic edge toggling)
#   - the SplitMix64 gamma constant as a seed (a notorious self-cycle for
#     unmixed seeds, kept here as a regression guard)
#   - large/small nodes and rounds
#   - all-ones seed
TRIPLES: list[tuple[int, int, int]] = [
    (0x0000000000000000, 0, 0),
    (0x0000000000000000, 0, 1),
    (0x0000000000000000, 1, 0),
    (0x0000000000000001, 0, 0),
    (0x000000000000002A, 0, 0),       # seed=42, the scenario default
    (0x000000000000002A, 0, -1),      # graph-level edge toggle
    (0x000000000000002A, 1, 0),
    (0x000000000000002A, 99, 7),
    (0x9E3779B97F4A7C15, 0, 0),       # SplitMix64 gamma as seed
    (0xCAFEBABEDEADBEEF, 5, 12),
    (0x112210F47DE98115, 0, 0),       # 1234567890123456789
    (0x112210F47DE98115, 0, -1),
    (0x0000000000000001, 1, 1),
    (0x0000000000000002, 3, 5),
    (0x0000000000000008, 13, 21),
    (0x0000000000000022, 55, 89),
    (0xFFFFFFFFFFFFFFFF, 0, 0),
    (0xFFFFFFFFFFFFFFFF, 100, 500),
    (0x0000000000000007, 0, -1),
    (0x0000000000000007, 0, 0),
]
assert len(TRIPLES) == 20


def _double_bits_hex(x: float) -> str:
    """Return the 16-hex-digit IEEE-754 big-endian bit pattern of ``x``."""
    (bits,) = struct.unpack(">Q", struct.pack(">d", x))
    return f"0x{bits:016X}"


def main() -> int:
    out_path = PROJECT_ROOT / "simulator" / "src" / "test" / "resources" / "fl" / "splitmix64_golden.csv"
    out_path.parent.mkdir(parents=True, exist_ok=True)

    rows: list[dict[str, str]] = []
    for seed, round_, node in TRIPLES:
        rng_long = derive(seed, round_, node)
        longs = [rng_long.next_long() for _ in range(3)]
        rng_dbl = derive(seed, round_, node)
        doubles = [rng_dbl.next_double() for _ in range(3)]
        rng_int = derive(seed, round_, node)
        ni7 = rng_int.next_int(7)

        rows.append({
            "seed_hex":       f"0x{seed:016X}",
            "round":          str(round_),
            "node":           str(node),
            "long0_hex":      f"0x{longs[0]:016X}",
            "long1_hex":      f"0x{longs[1]:016X}",
            "long2_hex":      f"0x{longs[2]:016X}",
            "double0_bits":   _double_bits_hex(doubles[0]),
            "double1_bits":   _double_bits_hex(doubles[1]),
            "double2_bits":   _double_bits_hex(doubles[2]),
            "nextint7":       str(ni7),
        })

    fieldnames = list(rows[0].keys())
    with open(out_path, "w", newline="", encoding="utf-8") as f:
        f.write(
            "# SplitMix64 + derive(seed, round, node) golden vectors.\n"
            "# Regenerate via: py harness/tools/generate_golden.py\n"
            "# Loaded by simulator JUnit (SplitMix64GoldenTest) AND harness pytest\n"
            "# (test_flrng_golden.py). Drift on either side breaks CI.\n"
            "# Columns: seed_hex, round, node, long{0,1,2}_hex, double{0,1,2}_bits, nextint7.\n"
        )
        w = csv.DictWriter(f, fieldnames=fieldnames)
        w.writeheader()
        for r in rows:
            w.writerow(r)

    rel = os.path.relpath(out_path, PROJECT_ROOT)
    print(f"Wrote {len(rows)} golden vectors to {rel}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
