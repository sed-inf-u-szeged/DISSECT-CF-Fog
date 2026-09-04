"""TID-gap (§8.3, Eq. tid-gap): the accuracy shortfall of a decentralized run
on topology G relative to an equivalent centralized run at a matched local
computation budget,

    TID_gap(G) = A_cen − A_dec(G).

A_dec(G) is primarily the mean final per-node test accuracy of the gossip
cell; as a secondary view the accuracy of the uniformly averaged model w̄ is
reported. A_cen is the final global-model accuracy of the centralized cell
produced by the same harness on the same partitions (same seed ⇒ common
random numbers). Both values come from the cells' ``pass2/summary.json``
(written by ``run.py``); this module only combines them — it is a cross-cell
quantity, not a runtime one.

CLI:
    python tid_gap.py --dec <gossip pass2 dir> --cen <centralized pass2 dir> \
        [--out tid_gap.json]
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Dict


def tid_gap(dec_summary: Dict, cen_summary: Dict) -> Dict:
    """Eq. tid-gap from the two cells' §8.3 summaries.

    :param dec_summary: gossip cell summary — needs ``acc_mean_over_nodes``
                        (primary A_dec) and optionally ``acc_mean_model`` (w̄).
    :param cen_summary: centralized cell summary — needs ``acc_global_model``
                        (A_cen).
    :return: dict with ``a_cen``, ``a_dec_mean_over_nodes``, the primary
             ``tid_gap``, and (when w̄ is present) ``a_dec_mean_model`` +
             ``tid_gap_mean_model``.
    """
    a_cen = float(cen_summary["acc_global_model"])
    a_dec = float(dec_summary["acc_mean_over_nodes"])
    out = {
        "a_cen": a_cen,
        "a_dec_mean_over_nodes": a_dec,
        "tid_gap": a_cen - a_dec,
    }
    if "acc_mean_model" in dec_summary:
        a_mean_model = float(dec_summary["acc_mean_model"])
        out["a_dec_mean_model"] = a_mean_model
        out["tid_gap_mean_model"] = a_cen - a_mean_model
    if "acc_worst_node" in dec_summary:
        out["a_dec_worst_node"] = float(dec_summary["acc_worst_node"])
    return out


def _load(pass2_dir: str | Path) -> Dict:
    p = Path(pass2_dir) / "summary.json"
    if not p.exists():
        raise FileNotFoundError(
            f"{p} not found — re-run Pass 2 with the current harness "
            "(run.py writes summary.json alongside the learning trace)")
    return json.loads(p.read_text(encoding="utf-8"))


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description="TID-gap (Eq. tid-gap, §8.3)")
    ap.add_argument("--dec", required=True, help="gossip cell pass2 dir")
    ap.add_argument("--cen", required=True, help="centralized cell pass2 dir")
    ap.add_argument("--out", default=None, help="optional output JSON path")
    args = ap.parse_args(argv)

    result = tid_gap(_load(args.dec), _load(args.cen))
    text = json.dumps(result, indent=2)
    print(text)
    if args.out:
        Path(args.out).write_text(text, encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
