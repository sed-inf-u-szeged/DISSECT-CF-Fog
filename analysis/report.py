"""Regenerates every §9 number and figure from the released artefact tree.

    python analysis/report.py --all
    python analysis/report.py --scenario S1 --results-root /path/to/results

Emits, under ``<results>/paper/report/``:
  * ``results.json``  — every reported quantity, machine-readable;
  * ``results.md``    — the §9 tables in the order they appear in the paper;
  * ``figures/*.png`` — R1-R4.

Design rules this module follows, because each corresponds to a way the earlier
analysis misreported a result:

1. **Accuracy comes from the held-out pool.** The ``acc`` column of a learning
   trace is a node's fit on its own training shard; only ``heldout_acc`` and the
   ``summary.json`` figures are reportable.
2. **Traffic conventions are named.** A per-endpoint sum double-counts every
   byte; the congestion claim compares the busiest gossip endpoint against the
   *aggregator's own* endpoint load, and the model payload is read from Pass 1
   rather than inferred from a telemetry row.
3. **Families of comparisons are Holm-corrected and the adjusted p is what gets
   printed.**
4. **The TID–λ₂ claim is tested at the family level**, since seeds within a
   topology share (often literally) the same λ₂.
"""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Dict, List

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))

import campaign as camp                                      # noqa: E402
from stats import (Contrast, holm_family, mean_ci,           # noqa: E402
                   tid_lambda2_monotonicity)

SEEDS_NOTE = "mean ± 95% CI (bootstrap percentile) over seeds"


def _ci(values: List[float]) -> Dict:
    c = mean_ci(values)
    return {"mean": c.mean, "lo": c.lo, "hi": c.hi, "n": c.n, "method": c.method,
            "raw": [float(v) for v in values]}


def _contrast_dict(c: Contrast) -> Dict:
    return {"label": c.label, "mean_a": c.mean_a, "mean_b": c.mean_b, "delta": c.delta,
            "test": c.test, "p_raw": c.p_value, "p_holm": c.p_adjusted,
            c.effect_name: c.effect_size, "reject_at_0.05": c.reject}


# ----------------------------------------------------------------------
# ERQ1 — the fidelity gate
# ----------------------------------------------------------------------
def erq1(root, campaign_name) -> Dict:
    cells = list(camp.iter_cells("V", root=root, campaign=campaign_name))
    if not cells:
        return {"status": "missing"}
    cell = cells[0]
    fidelity_path = cell.path / "trackb" / "fidelity.json"
    out: Dict = {"cell": str(cell.path.relative_to(camp.results_root(root)))}
    if fidelity_path.exists():
        out["criteria"] = json.loads(fidelity_path.read_text(encoding="utf-8"))
    else:
        out["criteria"] = None
        out["note"] = ("trackb/fidelity.json absent — run harness/fidelity_check.py "
                       "against this cell. ERQ1 gates the study, so its evidence must "
                       "live in the released tree.")
    meta = camp.run_metadata(cell)
    out["selection_agreement_rate"] = meta["selection_agreement_rate"]
    out["compute_delay_source"] = meta.get("compute_delay_source")
    s = camp.summary(cell)
    out["heldout"] = {"split": s.get("eval_split"), "samples": s.get("eval_samples"),
                      "acc_mean_over_nodes": s.get("acc_mean_over_nodes")}
    return out


# ----------------------------------------------------------------------
# ERQ2 — coordination-mode competitiveness
# ----------------------------------------------------------------------
def erq2(root, campaign_name) -> Dict:
    cells = list(camp.iter_cells("S1", root=root, campaign=campaign_name))
    if not cells:
        return {"status": "missing"}

    dec = camp.collect(cells, lambda c: camp.summary(c)["acc_mean_over_nodes"])
    wbar = camp.collect(cells, lambda c: camp.summary(c)["acc_mean_model"])
    worst = camp.collect(cells, lambda c: camp.summary(c)["acc_worst_node"])
    cen = camp.collect(cells, lambda c: camp.summary(c, "pass2_centralized")["acc_global_model"])
    hier = camp.collect(cells, lambda c: camp.summary(c, "pass2_hierarchical")["acc_global_model"])
    gap = camp.collect(cells, lambda c: camp.tid_gap(c)["tid_gap"])

    # System cost, per seed — not a single-seed figure dressed as a mean.
    traf = [camp.traffic(c) for c in cells]
    energy = camp.collect(cells, camp.energy_joules)

    contrasts = holm_family([
        ("centralized vs decentralized", cen, dec),
        ("hierarchical vs decentralized", hier, dec),
    ])

    return {
        "n_seeds": len(cells),
        "accuracy": {
            "A_dec_mean_over_nodes": _ci(dec),
            "A_dec_mean_model_wbar": _ci(wbar),
            "A_dec_worst_node": _ci(worst),
            "A_cen": _ci(cen),
            "A_hier": _ci(hier),
            "tid_gap": _ci(gap),
        },
        "contrasts": [_contrast_dict(c) for c in contrasts],
        "system": {
            "gossip_endpoint_total_mb": _ci([t.endpoint_total_mb for t in traf]),
            "gossip_on_wire_mb": _ci([t.on_wire_mb for t in traf]),
            "gossip_busiest_node_mb": _ci([t.busiest_node_mb for t in traf]),
            "centralized_aggregator_mb": _ci([t.centralized_aggregator_mb for t in traf]),
            "centralized_endpoint_total_mb": _ci([t.centralized_endpoint_total_mb for t in traf]),
            "busiest_vs_aggregator_pct": _ci([t.busiest_vs_aggregator_pct for t in traf]),
            "mean_idle_ticks": _ci([t.mean_idle_ticks for t in traf]),
            "energy_joules": _ci(energy),
            "model_payload_mb": traf[0].payload_mb,
            "convention": (
                "endpoint_total sums ul+dl at every node, so each byte is counted "
                "twice; on_wire counts each byte once. The congestion claim compares "
                "busiest_node against centralized_aggregator (2·n·p per round), which "
                "is the aggregator's OWN endpoint load."),
        },
    }


# ----------------------------------------------------------------------
# ERQ3 — topology effect
# ----------------------------------------------------------------------
def erq3(root, campaign_name) -> Dict:
    grouped = camp.cells_by_factor("S2", root=root, campaign=campaign_name)
    if not grouped:
        return {"status": "missing"}

    families: Dict[str, Dict] = {}
    mono_input: Dict[str, tuple] = {}
    for cell_id, cells in grouped.items():
        # The family key must carry EVERY varied factor, not just the topology
        # name: the two Watts-Strogatz cells share topo=small_world and differ
        # only in lattice degree, so keying on the topology alone would merge
        # them into one family and silently reduce the H2 test from six
        # independent units to five.
        f = cells[0].factors
        family = f.get("topo", cell_id)
        if "deg" in f:
            family = f"{family}(k={f['deg']})"
        lam = camp.collect(cells, camp.lambda2)
        tid = camp.collect(cells, camp.final_tid)
        tid0 = camp.collect(cells, lambda c: camp.tid_series(c).value.iloc[0])
        acc = camp.collect(cells, lambda c: camp.summary(c)["acc_mean_over_nodes"])
        families[family] = {
            "lambda2": _ci(lam),
            "final_tid_global": _ci(tid),
            "initial_tid_global": _ci(tid0),
            "heldout_accuracy": _ci(acc),
        }
        mono_input[family] = (lam, tid)

    mono = tid_lambda2_monotonicity(mono_input)
    return {
        "families": families,
        "monotonicity": {
            "family_level": {"rho": mono.family.rho, "p_asymptotic": mono.family.p_value,
                             "p_permutation": mono.family_permutation_p, "n": mono.family.n},
            "pooled_descriptive": {"rho": mono.pooled.rho, "p": mono.pooled.p_value,
                                   "n": mono.pooled.n,
                                   "distinct_lambda2_levels": mono.distinct_levels},
            "note": ("The family-level test carries the claim: seeds within a topology "
                     "share essentially the same λ₂ (identical for ring and mesh), so a "
                     "pooled test over all (family, seed) points is pseudo-replicated "
                     "and its p-value is not interpretable as evidence for monotonicity."),
        },
    }


# ----------------------------------------------------------------------
# ERQ4 — merge-rule efficacy
# ----------------------------------------------------------------------
def erq4(root, campaign_name) -> Dict:
    grouped = camp.cells_by_factor("S3", root=root, campaign=campaign_name)
    if not grouped:
        return {"status": "missing"}

    table: Dict[str, Dict[str, Dict]] = {}
    raw: Dict[tuple, List[float]] = {}
    for cell_id, cells in grouped.items():
        f = cells[0].factors
        sel, mrg = f.get("pol", "?"), f.get("mrg", "?")
        acc = camp.collect(cells, lambda c: camp.summary(c)["acc_mean_over_nodes"])
        worst = camp.collect(cells, lambda c: camp.summary(c)["acc_worst_node"])
        table.setdefault(sel, {})[mrg] = {"accuracy": _ci(acc), "worst_node": _ci(worst)}
        raw[(sel, mrg)] = acc

    # Two families of contrasts, each Holm-corrected within itself.
    #   (a) the headline claim vs plain uniform averaging;
    #   (b) the ISOLATED drift factor: sample-weighted → drift-suppressed.
    selectors = sorted(table)
    fam_uniform, fam_drift = [], []
    for sel in selectors:
        if ("DRIFT_SUPPRESSED" in table[sel]) and ("UNIFORM" in table[sel]):
            fam_uniform.append((f"{sel}: drift-suppressed vs uniform",
                                raw[(sel, "DRIFT_SUPPRESSED")], raw[(sel, "UNIFORM")]))
        if ("DRIFT_SUPPRESSED" in table[sel]) and ("SAMPLE_WEIGHTED" in table[sel]):
            fam_drift.append((f"{sel}: drift-suppressed vs sample-weighted",
                              raw[(sel, "DRIFT_SUPPRESSED")], raw[(sel, "SAMPLE_WEIGHTED")]))

    return {
        "cells": table,
        "contrasts_vs_uniform": [_contrast_dict(c) for c in holm_family(fam_uniform)],
        "contrasts_drift_isolated": [_contrast_dict(c) for c in holm_family(fam_drift)],
        "note": ("drift-suppressed vs uniform changes sample weighting AND drift "
                 "suppression together; drift-suppressed vs sample-weighted isolates "
                 "the drift factor and is what supports a claim about drift suppression."),
    }


# ----------------------------------------------------------------------
# ERQ5 — selector ablation
# ----------------------------------------------------------------------
def erq5(root, campaign_name) -> Dict:
    grouped = camp.cells_by_factor("S4", root=root, campaign=campaign_name)
    if not grouped:
        return {"status": "missing"}

    cells_out: Dict[str, Dict] = {}
    raw: Dict[str, List[float]] = {}
    for cell_id, cells in grouped.items():
        sel = cells[0].factors.get("pol", cell_id)
        acc = camp.collect(cells, lambda c: camp.summary(c)["acc_mean_over_nodes"])
        traf = [camp.traffic(c) for c in cells]
        cells_out[sel] = {
            "heldout_accuracy": _ci(acc),
            "final_tid_global": _ci(camp.collect(cells, camp.final_tid)),
            "endpoint_total_mb": _ci([t.endpoint_total_mb for t in traf]),
            "mean_idle_ticks": _ci([t.mean_idle_ticks for t in traf]),
            "energy_joules": _ci(camp.collect(cells, camp.energy_joules)),
            "mean_round_ticks": _ci(camp.collect(cells, camp.round_duration_ticks)),
        }
        raw[sel] = acc

    baseline = "RANDOM"
    fam = [(f"{sel} vs {baseline}", raw[sel], raw[baseline])
           for sel in sorted(raw) if sel != baseline and baseline in raw]
    return {"cells": cells_out,
            "contrasts_vs_random": [_contrast_dict(c) for c in holm_family(fam)]}


# ----------------------------------------------------------------------
# ERQ5b — which half of the γ schedule costs the composite selector?
# ----------------------------------------------------------------------
def erq5b(root, campaign_name) -> Dict:
    grouped = camp.cells_by_factor("S5", root=root, campaign=campaign_name)
    if not grouped:
        return {"status": "missing"}

    cells_out: Dict[str, Dict] = {}
    raw: Dict[str, List[float]] = {}
    for cell_id, cells in grouped.items():
        sched = cells[0].factors.get("gam", cell_id)
        acc = camp.collect(cells, lambda c: camp.summary(c)["acc_mean_over_nodes"])
        cells_out[sched] = {
            "heldout_accuracy": _ci(acc),
            "final_tid_global": _ci(camp.collect(cells, camp.final_tid)),
            "energy_joules": _ci(camp.collect(cells, camp.energy_joules)),
        }
        raw[sched] = acc

    # The mixing account predicts ALWAYS_EXPLORE > EXPLORE_THEN_EXPLOIT >
    # ALWAYS_EXPLOIT on accuracy and the reverse on TID. Both contrasts are
    # taken against the mixed schedule, which is the one S4 actually ran.
    baseline = "EXPLORE_THEN_EXPLOIT"
    fam = [(f"{s} vs {baseline}", raw[s], raw[baseline])
           for s in sorted(raw) if s != baseline and baseline in raw]

    ordered = [s for s in ("ALWAYS_EXPLORE", "EXPLORE_THEN_EXPLOIT", "ALWAYS_EXPLOIT")
               if s in cells_out]
    acc_seq = [cells_out[s]["heldout_accuracy"]["mean"] for s in ordered]
    tid_seq = [cells_out[s]["final_tid_global"]["mean"] for s in ordered]
    return {
        "cells": cells_out,
        "contrasts_vs_mixed": [_contrast_dict(c) for c in holm_family(fam)],
        "predicted_order": ordered,
        "accuracy_monotone_decreasing": all(
            a >= b - 1e-12 for a, b in zip(acc_seq, acc_seq[1:])),
        "tid_monotone_increasing": all(
            a <= b + 1e-12 for a, b in zip(tid_seq, tid_seq[1:])),
        "note": ("The mixing account predicts accuracy falling and TID rising as the "
                 "schedule shifts from exploring (reward divergence, mix widely) to "
                 "exploiting (penalise divergence, mix narrowly). Both flags false "
                 "would refute it."),
    }


# ----------------------------------------------------------------------
# ERQ5c — the scale-invariance check (S6), not a hypothesis test
# ----------------------------------------------------------------------
def erq5c(root, campaign_name) -> Dict:
    """Measures the invariance that Eq. (norm) implies, on S6 against S4.

    S6 repeats S4 with every link capacity scaled to one tenth. Because the
    selection score min--max normalises each cost term *within* a neighbourhood,
    it is unchanged by any positive affine rescaling of a term, so no cost regime
    can alter a selector's decisions. That is a property of the score, not a
    conjecture about the deployment, so S6 is reported as a confirmation rather
    than tested: there is no hypothesis here to accept or reject, and no
    contrast to correct for.

    Two things must therefore hold together, and both are measured:

    * the **cost regime genuinely changed** — metered energy and round duration
      move, which is what rules out the trivial explanation that the scenario
      simply failed to take effect; and
    * the **decisions did not** — every ``(round, node)`` exchange set, and
      hence every accuracy, is identical to the corresponding S4 run.

    Reporting a p-value over S6-vs-S4 accuracy would be meaningless: the two
    vectors are the same numbers, seed for seed.
    """
    grouped = camp.cells_by_factor("S6", root=root, campaign=campaign_name)
    if not grouped:
        return {"status": "missing"}

    by_selector: Dict[str, List] = {}
    for cell_id, cells in grouped.items():
        by_selector[cells[0].factors.get("pol", cell_id)] = cells

    try:
        s4 = camp.cells_by_factor("S4", root=root, campaign=campaign_name)
    except FileNotFoundError:
        s4 = {}
    s4_by_selector = {cells[0].factors.get("pol", cid): cells
                      for cid, cells in s4.items()}

    cells_out: Dict[str, Dict] = {}
    for sel, cells in by_selector.items():
        acc = camp.collect(cells, lambda c: camp.summary(c)["acc_mean_over_nodes"])
        entry = {
            "heldout_accuracy": _ci(acc),
            "energy_joules": _ci(camp.collect(cells, camp.energy_joules)),
            "mean_round_ticks": _ci(camp.collect(cells, camp.round_duration_ticks)),
            "final_tid_global": _ci(camp.collect(cells, camp.final_tid)),
        }

        ref = s4_by_selector.get(sel)
        if ref:
            by_seed = {c.seed: c for c in ref}
            matched = agreed = events = 0
            worst_acc_delta = 0.0
            for c in cells:
                peer = by_seed.get(c.seed)
                if peer is None:
                    continue
                matched += 1
                here, there = camp.exchange_sets(c), camp.exchange_sets(peer)
                events += len(here)
                agreed += sum(1 for k, v in here.items() if there.get(k) == v)
                worst_acc_delta = max(worst_acc_delta, abs(
                    camp.summary(c)["acc_mean_over_nodes"]
                    - camp.summary(peer)["acc_mean_over_nodes"]))
            s4_acc = camp.collect(ref, lambda c: camp.summary(c)["acc_mean_over_nodes"])
            s4_energy = camp.collect(ref, camp.energy_joules)
            entry["vs_s4"] = {
                "seeds_matched": matched,
                "decision_events": events,
                "decision_events_identical": agreed,
                "decision_agreement": (agreed / events) if events else None,
                "max_abs_accuracy_delta": worst_acc_delta,
                "s4_heldout_accuracy": _ci(s4_acc),
                "s4_energy_joules": _ci(s4_energy),
                "s4_mean_round_ticks": _ci(camp.collect(ref, camp.round_duration_ticks)),
                "energy_change_pct": 100.0 * (np.mean(
                    camp.collect(cells, camp.energy_joules)) / np.mean(s4_energy) - 1.0),
            }
        cells_out[sel] = entry

    out: Dict = {"cells": cells_out}
    agreements = [d["vs_s4"]["decision_agreement"] for d in cells_out.values()
                  if "vs_s4" in d and d["vs_s4"]["decision_agreement"] is not None]
    deltas = [d["vs_s4"]["max_abs_accuracy_delta"] for d in cells_out.values()
              if "vs_s4" in d]
    if agreements:
        out["invariance_holds"] = (min(agreements) == 1.0 and max(deltas) == 0.0)
        out["min_decision_agreement"] = min(agreements)
        out["max_abs_accuracy_delta"] = max(deltas)
    out["note"] = ("Confirmation, not a test. The score's positive-affine invariance is a "
                   "property of Eq. (norm); S6 checks that the implementation exhibits it "
                   "under a cost regime that did move the system state. Consequently the "
                   "S4 selector refutation is not specific to the bandwidth of that "
                   "scenario: no deployment can make a cost-aware selector more "
                   "cost-aware, because only the term weights can.")
    return out


# ----------------------------------------------------------------------
# Rendering
# ----------------------------------------------------------------------
def _fmt(ci: Dict, places: int = 3) -> str:
    return f"{ci['mean']:.{places}f} [{ci['lo']:.{places}f}, {ci['hi']:.{places}f}]"


def render_markdown(res: Dict) -> str:
    L: List[str] = ["# §9 results — regenerated from the artefact tree", ""]

    e1 = res.get("ERQ1", {})
    L += ["## ERQ1 — fidelity gate", ""]
    if e1.get("criteria"):
        L += ["| ID | Criterion | Tolerance | Measured | Pass |",
              "|----|-----------|-----------|----------|------|"]
        for c in e1["criteria"].get("criteria", []):
            L.append(f"| {c['id']} | {c['criterion']} | {c['tolerance']} | "
                     f"{c['measured']} | {'yes' if c['pass'] else 'no'} |")
    else:
        L.append(f"_{e1.get('note', 'not run')}_")
    L.append("")

    e2 = res.get("ERQ2", {})
    if "accuracy" in e2:
        a, s = e2["accuracy"], e2["system"]
        L += ["## ERQ2 — coordination-mode competitiveness", "",
              f"Held-out accuracy, {SEEDS_NOTE} (n={e2['n_seeds']}).", "",
              "| Mode | Held-out accuracy | Endpoint traffic (MB) | Busiest endpoint (MB) | Energy (J) | Idle (ticks) |",
              "|------|-------------------|----------------------|----------------------|-----------|--------------|",
              f"| Centralized | {_fmt(a['A_cen'])} | {_fmt(s['centralized_endpoint_total_mb'],1)} | "
              f"{_fmt(s['centralized_aggregator_mb'],1)} (analytic) | — | — |",
              f"| Hierarchical | {_fmt(a['A_hier'])} | — | — | — | — |",
              f"| Decentralized | {_fmt(a['A_dec_mean_over_nodes'])} | {_fmt(s['gossip_endpoint_total_mb'],1)} | "
              f"**{_fmt(s['gossip_busiest_node_mb'],1)}** | {_fmt(s['energy_joules'],1)} | "
              f"{_fmt(s['mean_idle_ticks'],1)} |",
              "",
              f"- TID gap = {_fmt(a['tid_gap'])}; w̄-view {_fmt(a['A_dec_mean_model_wbar'])}; "
              f"worst node {_fmt(a['A_dec_worst_node'])}",
              f"- Busiest gossip endpoint = {_fmt(s['busiest_vs_aggregator_pct'],1)}% of the "
              f"central aggregator's own endpoint load",
              f"- On-wire (each byte once): gossip {_fmt(s['gossip_on_wire_mb'],1)} MB vs "
              f"centralized {_fmt(s['centralized_aggregator_mb'],1)} MB",
              ""]
        for c in e2["contrasts"]:
            eff = c.get("cohens_d", c.get("cliffs_delta"))
            L.append(f"- {c['label']}: Δ={c['delta']:+.4f}, {c['test']}, "
                     f"p_raw={c['p_raw']:.4g}, p_Holm={c['p_holm']:.4g}, effect={eff:.2f}")
        L.append("")

    e3 = res.get("ERQ3", {})
    if "families" in e3:
        L += ["## ERQ3 — topology effect", "",
              "| Topology | λ₂ | Initial TID | Final TID | Held-out accuracy |",
              "|----------|-----|-------------|-----------|-------------------|"]
        for fam in sorted(e3["families"], key=lambda f: e3["families"][f]["lambda2"]["mean"]):
            d = e3["families"][fam]
            L.append(f"| {fam} | {d['lambda2']['mean']:.3f} | {_fmt(d['initial_tid_global'],2)} | "
                     f"{_fmt(d['final_tid_global'],2)} | {_fmt(d['heldout_accuracy'])} |")
        m = e3["monotonicity"]
        L += ["",
              f"- Family-level Spearman (n={m['family_level']['n']}): ρ={m['family_level']['rho']:.2f}, "
              f"permutation p={m['family_level']['p_permutation']:.3f}",
              f"- Pooled over all (family, seed) points — descriptive only, "
              f"{m['pooled_descriptive']['distinct_lambda2_levels']} distinct λ₂ levels among "
              f"{m['pooled_descriptive']['n']} points: ρ={m['pooled_descriptive']['rho']:.2f}",
              ""]

    e4 = res.get("ERQ4", {})
    if "cells" in e4:
        selectors = sorted(e4["cells"])
        merges = sorted({m for sel in selectors for m in e4["cells"][sel]})
        L += ["## ERQ4 — merge-rule efficacy", "",
              "| Merge rule | " + " | ".join(selectors) + " |",
              "|---|" + "|".join(["---"] * len(selectors)) + "|"]
        for mrg in merges:
            row = [f"{_fmt(e4['cells'][sel][mrg]['accuracy'])} "
                   f"({e4['cells'][sel][mrg]['worst_node']['mean']:.2f})"
                   if mrg in e4["cells"][sel] else "—" for sel in selectors]
            L.append(f"| {mrg} | " + " | ".join(row) + " |")
        L += ["", "Contrasts vs uniform (confounds sample weighting with drift suppression):"]
        for c in e4["contrasts_vs_uniform"]:
            eff = c.get("cohens_d", c.get("cliffs_delta"))
            L.append(f"- {c['label']}: Δ={c['delta']:+.3f}, p_Holm={c['p_holm']:.4g}, effect={eff:.2f}")
        L += ["", "Contrasts isolating the drift factor (vs sample-weighted):"]
        for c in e4["contrasts_drift_isolated"]:
            eff = c.get("cohens_d", c.get("cliffs_delta"))
            L.append(f"- {c['label']}: Δ={c['delta']:+.3f}, p_Holm={c['p_holm']:.4g}, effect={eff:.2f}")
        L.append("")

    e5 = res.get("ERQ5", {})
    if "cells" in e5:
        L += ["## ERQ5 — selector ablation", "",
              "| Selector | Held-out accuracy | Final TID | Traffic (MB) | Energy (J) | Idle (ticks) |",
              "|---|---|---|---|---|---|"]
        for sel in sorted(e5["cells"]):
            d = e5["cells"][sel]
            L.append(f"| {sel} | {_fmt(d['heldout_accuracy'])} | {_fmt(d['final_tid_global'],2)} | "
                     f"{_fmt(d['endpoint_total_mb'],1)} | {_fmt(d['energy_joules'],1)} | "
                     f"{_fmt(d['mean_idle_ticks'],1)} |")
        L += [""]
        for c in e5["contrasts_vs_random"]:
            eff = c.get("cohens_d", c.get("cliffs_delta"))
            L.append(f"- {c['label']}: Δ={c['delta']:+.4f}, p_Holm={c['p_holm']:.4g}, effect={eff:.2f}")
        L.append("")

    e5b = res.get("ERQ5b", {})
    if "cells" in e5b:
        L += ["## ERQ5b — γ schedule: which half costs the composite selector?", "",
              "| γ schedule | Held-out accuracy | Final TID | Energy (J) |",
              "|---|---|---|---|"]
        for s in e5b["predicted_order"]:
            d = e5b["cells"][s]
            L.append(f"| {s} | {_fmt(d['heldout_accuracy'])} | {_fmt(d['final_tid_global'],2)} | "
                     f"{_fmt(d['energy_joules'],1)} |")
        L += ["",
              f"- accuracy decreasing explore→exploit: **{e5b['accuracy_monotone_decreasing']}**",
              f"- TID increasing explore→exploit: **{e5b['tid_monotone_increasing']}**",
              "  (the mixing account predicts both true; both false would refute it)", ""]
        for c in e5b["contrasts_vs_mixed"]:
            eff = c.get("cohens_d", c.get("cliffs_delta"))
            L.append(f"- {c['label']}: Δ={c['delta']:+.4f}, p_Holm={c['p_holm']:.4g}, effect={eff:.2f}")
        L.append("")

    e5c = res.get("ERQ5c", {})
    if "cells" in e5c:
        L += ["## ERQ5c — scale invariance of the selection score (S6 vs S4)", "",
              "S6 = S4 with every link capacity scaled to 0.1. Not a hypothesis test: the "
              "score's positive-affine invariance is a property of Eq. (norm), and this "
              "cell confirms the implementation exhibits it while the cost regime moved.",
              "",
              "| Selector | Energy (J) | vs S4 | Round (ticks) | vs S4 | Decisions identical | max \\|Δacc\\| |",
              "|---|---|---|---|---|---|---|"]
        for sel in sorted(e5c["cells"]):
            d = e5c["cells"][sel]
            v = d.get("vs_s4")
            if v:
                L.append(f"| {sel} | {_fmt(d['energy_joules'],1)} | "
                         f"{_fmt(v['s4_energy_joules'],1)} ({v['energy_change_pct']:+.1f}%) | "
                         f"{_fmt(d['mean_round_ticks'],1)} | {_fmt(v['s4_mean_round_ticks'],1)} | "
                         f"{v['decision_events_identical']}/{v['decision_events']} "
                         f"({v['decision_agreement']:.3f}) | "
                         f"{v['max_abs_accuracy_delta']:.2e} |")
            else:
                L.append(f"| {sel} | {_fmt(d['energy_joules'],1)} | — | "
                         f"{_fmt(d['mean_round_ticks'],1)} | — | — | — |")
        L += ["",
              "| Selector | Held-out accuracy (S6) | Held-out accuracy (S4) | Final TID |",
              "|---|---|---|---|"]
        for sel in sorted(e5c["cells"]):
            d = e5c["cells"][sel]
            v = d.get("vs_s4")
            L.append(f"| {sel} | {_fmt(d['heldout_accuracy'])} | "
                     f"{_fmt(v['s4_heldout_accuracy']) if v else '—'} | "
                     f"{_fmt(d['final_tid_global'],2)} |")
        L.append("")
        if "invariance_holds" in e5c:
            L.append(f"- Invariance holds: **{'yes' if e5c['invariance_holds'] else 'NO'}** "
                     f"(min decision agreement {e5c['min_decision_agreement']:.4f}, "
                     f"max |Δaccuracy| {e5c['max_abs_accuracy_delta']:.2e})")
        L += [f"- {e5c['note']}", ""]

    return "\n".join(L)


# ----------------------------------------------------------------------
# Figures
# ----------------------------------------------------------------------
def make_figures(root, campaign_name, res: Dict, out_dir: Path) -> List[Path]:
    """R1–R4, drawn from the same artefact tree as the tables."""
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt

    out_dir.mkdir(parents=True, exist_ok=True)
    made: List[Path] = []

    # R1 — held-out accuracy vs round, per coordination mode.
    try:
        cells = list(camp.iter_cells("S1", root=root, campaign=campaign_name))
        fig, ax = plt.subplots(figsize=(5.2, 3.6))
        for leg, label, mk in [("pass2_centralized", "centralized", "s"),
                               ("pass2_hierarchical", "hierarchical", "^"),
                               ("pass2", "decentralized (gossip)", "o")]:
            curves = []
            for c in cells:
                try:
                    curves.append(camp.heldout_curve(c, leg))
                except (FileNotFoundError, KeyError):
                    pass
            if not curves:
                continue
            frame = np.vstack([c.values for c in curves])
            rounds = curves[0].index.values
            mean = frame.mean(axis=0)
            lo, hi = np.percentile(frame, [2.5, 97.5], axis=0) if len(curves) > 1 else (mean, mean)
            ax.plot(rounds, mean, marker=mk, ms=3, label=label)
            ax.fill_between(rounds, lo, hi, alpha=0.15)
        ax.set_xlabel("round")
        ax.set_ylabel("held-out accuracy (mean over nodes)")
        ax.set_title("R1  Held-out accuracy vs round per coordination mode (S1)")
        ax.legend(fontsize=8)
        ax.grid(alpha=0.3)
        p = out_dir / "R1_accuracy_vs_round.png"
        fig.savefig(p, dpi=150, bbox_inches="tight")
        plt.close(fig)
        made.append(p)
    except FileNotFoundError:
        pass

    # R2 — final TID vs λ₂ across topology families.
    e3 = res.get("ERQ3", {})
    if "families" in e3:
        fig, ax = plt.subplots(figsize=(5.2, 3.6))
        for fam, d in sorted(e3["families"].items()):
            ax.scatter(d["lambda2"]["raw"], d["final_tid_global"]["raw"],
                       s=28, alpha=0.8, label=fam.replace("_", "-"))
        ax.set_xscale("log")
        ax.set_xlabel(r"algebraic connectivity $\lambda_2$")
        ax.set_ylabel("final TID dispersion (global)")
        m = e3["monotonicity"]["family_level"]
        ax.set_title(rf"R2  TID vs $\lambda_2$ ($\rho_s$={m['rho']:.2f}, "
                     rf"perm. $p$={m['p_permutation']:.2f}, n={m['n']} families)")
        ax.legend(fontsize=8)
        ax.grid(alpha=0.3, which="both")
        p = out_dir / "R2_tid_vs_lambda2.png"
        fig.savefig(p, dpi=150, bbox_inches="tight")
        plt.close(fig)
        made.append(p)

    # R3 — merge-rule ablation, with the sample-weighted control adjacent to
    # drift-suppressed so the isolated drift effect is readable off the figure.
    e4 = res.get("ERQ4", {})
    if "cells" in e4:
        order = ["UNIFORM", "SAMPLE_WEIGHTED", "DRIFT_SUPPRESSED", "METROPOLIS_HASTINGS"]
        selectors = sorted(e4["cells"])
        merges = [m for m in order if any(m in e4["cells"][s] for s in selectors)]
        fig, ax = plt.subplots(figsize=(6.0, 3.6))
        x = np.arange(len(merges))
        w = 0.8 / max(1, len(selectors))
        for i, sel in enumerate(selectors):
            means = [e4["cells"][sel].get(m, {}).get("accuracy", {}).get("mean", np.nan)
                     for m in merges]
            errs = [
                (e4["cells"][sel][m]["accuracy"]["hi"] - e4["cells"][sel][m]["accuracy"]["lo"]) / 2
                if m in e4["cells"][sel] else 0.0 for m in merges]
            ax.bar(x + (i - (len(selectors) - 1) / 2) * w, means, w, yerr=errs,
                   capsize=3, label=sel)
        ax.set_xticks(x)
        ax.set_xticklabels([m.replace("_", "\n").lower() for m in merges], fontsize=8)
        ax.set_ylabel("final held-out accuracy")
        ax.set_title(r"R3  Merge-rule ablation (S3, $\rho{=}0.1$)")
        ax.legend(fontsize=8)
        ax.grid(alpha=0.3, axis="y")
        p = out_dir / "R3_merge_ablation.png"
        fig.savefig(p, dpi=150, bbox_inches="tight")
        plt.close(fig)
        made.append(p)

    # R4 — selector ablation: accuracy against measured system cost.
    e5 = res.get("ERQ5", {})
    if "cells" in e5:
        fig, ax = plt.subplots(figsize=(5.6, 3.8))
        for sel, d in sorted(e5["cells"].items()):
            ax.errorbar(d["energy_joules"]["mean"], d["heldout_accuracy"]["mean"],
                        yerr=[[d["heldout_accuracy"]["mean"] - d["heldout_accuracy"]["lo"]],
                              [d["heldout_accuracy"]["hi"] - d["heldout_accuracy"]["mean"]]],
                        fmt="o", ms=6, capsize=3)
            ax.annotate(sel.replace("SINGLE_", "").lower(),
                        (d["energy_joules"]["mean"], d["heldout_accuracy"]["mean"]),
                        textcoords="offset points", xytext=(6, 4), fontsize=7)
        ax.set_xlabel("metered energy (J, both endpoints)")
        ax.set_ylabel("final held-out accuracy")
        ax.set_title("R4  Peer-selection policy: accuracy vs measured cost (S4)")
        ax.grid(alpha=0.3)
        p = out_dir / "R4_selector_ablation.png"
        fig.savefig(p, dpi=150, bbox_inches="tight")
        plt.close(fig)
        made.append(p)

    # R5 — γ schedule: accuracy and dispersion side by side, because the claim
    # under test is about their joint ordering, not either alone.
    e5b = res.get("ERQ5b", {})
    if "cells" in e5b:
        order = e5b["predicted_order"]
        fig, (ax, ax2) = plt.subplots(1, 2, figsize=(7.4, 3.4))
        x = np.arange(len(order))
        acc = [e5b["cells"][s]["heldout_accuracy"] for s in order]
        tid = [e5b["cells"][s]["final_tid_global"] for s in order]
        labels = [s.replace("_", "\n").lower() for s in order]

        ax.bar(x, [a["mean"] for a in acc],
               yerr=[[a["mean"] - a["lo"] for a in acc], [a["hi"] - a["mean"] for a in acc]],
               capsize=3, color="#4C72B0")
        # The reference the schedule has to beat is not the mixed schedule but
        # uniform-random selection, which no schedule reaches.
        e5 = res.get("ERQ5", {})
        if "cells" in e5 and "RANDOM" in e5["cells"]:
            r = e5["cells"]["RANDOM"]["heldout_accuracy"]["mean"]
            ax.axhline(r, ls="--", lw=1.2, color="crimson")
            ax.text(len(order) - 0.5, r, " random selection", va="bottom", ha="right",
                    fontsize=7, color="crimson")
        ax.set_xticks(x)
        ax.set_xticklabels(labels, fontsize=7)
        ax.set_ylabel("held-out accuracy")
        ax.grid(alpha=0.3, axis="y")

        ax2.bar(x, [t["mean"] for t in tid],
                yerr=[[t["mean"] - t["lo"] for t in tid], [t["hi"] - t["mean"] for t in tid]],
                capsize=3, color="#DD8452")
        ax2.set_xticks(x)
        ax2.set_xticklabels(labels, fontsize=7)
        ax2.set_ylabel("final TID dispersion")
        ax2.grid(alpha=0.3, axis="y")

        fig.suptitle(r"R5  $\gamma$ schedule: accuracy and dispersion (S5)", fontsize=10)
        fig.tight_layout()
        p = out_dir / "R5_gamma_schedule.png"
        fig.savefig(p, dpi=150, bbox_inches="tight")
        plt.close(fig)
        made.append(p)

    return made


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description="Regenerate the §9 results")
    ap.add_argument("--results-root", default=None)
    ap.add_argument("--campaign", default="paper")
    ap.add_argument("--scenario", action="append",
                    choices=["V", "S1", "S2", "S3", "S4", "S5", "S6"],
                    help="restrict to one scenario (repeatable); default is all")
    ap.add_argument("--all", action="store_true")
    args = ap.parse_args(argv)

    wanted = set(args.scenario or []) or {"V", "S1", "S2", "S3", "S4", "S5", "S6"}
    root, camp_name = args.results_root, args.campaign
    res: Dict = {}
    steps = [("V", "ERQ1", erq1), ("S1", "ERQ2", erq2), ("S2", "ERQ3", erq3),
             ("S3", "ERQ4", erq4), ("S4", "ERQ5", erq5), ("S5", "ERQ5b", erq5b), ("S6", "ERQ5c", erq5c)]
    for scen, key, fn in steps:
        if scen not in wanted:
            continue
        try:
            res[key] = fn(root, camp_name)
        except FileNotFoundError as e:
            res[key] = {"status": "missing", "detail": str(e)}
            print(f"[report] {key}: {e}")

    out_dir = camp.results_root(root) / camp_name / "report"
    out_dir.mkdir(parents=True, exist_ok=True)
    (out_dir / "results.json").write_text(json.dumps(res, indent=2, default=float),
                                          encoding="utf-8")
    md = render_markdown(res)
    (out_dir / "results.md").write_text(md, encoding="utf-8")
    figs = make_figures(root, camp_name, res, out_dir / "figures")
    print(md)
    print(f"\n[report] wrote {out_dir/'results.json'} and results.md")
    for f in figs:
        print(f"[report] figure {f.name}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
