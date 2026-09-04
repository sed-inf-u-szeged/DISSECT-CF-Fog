"""Results-schema exhibits (§9 sec:results-schema): R1–R9 and Tables R1–R5,
produced **from the released artefact tree only** (no hidden state — every
figure is regenerable). This module renders the headline exhibits; identifiers
are placeholders until the campaign completes.

Matplotlib runs headless (Agg). Every renderer degrades gracefully: if an input
artefact is missing it emits a labelled placeholder PNG, so the pipeline always
runs end to end without manual steps (P5 DoD: R1/R4/R7 from the P3.7 output).
"""
from __future__ import annotations

import argparse
from pathlib import Path
from typing import Optional

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt  # noqa: E402
import numpy as np  # noqa: E402
import pandas as pd  # noqa: E402


def _placeholder(out: Path, title: str, note: str) -> Path:
    fig, ax = plt.subplots(figsize=(6, 4))
    ax.set_title(title)
    ax.text(0.5, 0.5, note, ha="center", va="center", wrap=True, fontsize=10)
    ax.set_axis_off()
    fig.savefig(out, dpi=110, bbox_inches="tight")
    plt.close(fig)
    return out


def fig_r1_accuracy(learning_trace: Optional[Path], out: Path) -> Path:
    """R1: held-out accuracy vs round (mean over nodes ± 95% CI).

    Plots ``heldout_acc``, never ``acc``: the latter is each node's fit on its
    own training shard, which under a skewed Dirichlet draw runs far above the
    held-out figure and would not match the summary tables.
    """
    if learning_trace is None or not learning_trace.exists():
        return _placeholder(out, "R1: accuracy vs round", "no learning_trace.csv (placeholder)")
    df = pd.read_csv(learning_trace)
    if "heldout_acc" not in df.columns:
        return _placeholder(out, "R1: accuracy vs round",
                            "trace predates the held-out split — its accuracies are "
                            "training-pool figures and are not plotted")
    df = df.dropna(subset=["heldout_acc"])
    grp = df.groupby("round")["heldout_acc"]
    rounds = sorted(df["round"].unique())
    means = grp.mean().reindex(rounds).values
    stds = grp.std(ddof=0).reindex(rounds).fillna(0).values
    counts = grp.count().reindex(rounds).values
    ci = 1.96 * stds / np.sqrt(np.maximum(counts, 1))

    fig, ax = plt.subplots(figsize=(6, 4))
    ax.plot(rounds, means, marker="o", label="decentralized (mean per-node)")
    ax.fill_between(rounds, means - ci, means + ci, alpha=0.2)
    ax.set_xlabel("round")
    ax.set_ylabel("held-out accuracy")
    ax.set_title("R1: held-out accuracy vs round (mean ± 95% CI)")
    ax.legend()
    fig.savefig(out, dpi=110, bbox_inches="tight")
    plt.close(fig)
    return out


def fig_r4_tid(tid_timeseries: Optional[Path], out: Path) -> Path:
    """R4: TID-dispersion time series (global + inter-cluster scopes)."""
    if tid_timeseries is None or not tid_timeseries.exists():
        return _placeholder(out, "R4: TID dispersion", "no tid_timeseries.csv (placeholder)")
    df = pd.read_csv(tid_timeseries)
    fig, ax = plt.subplots(figsize=(6, 4))
    for scope in ("global", "inter_cluster"):
        sub = df[df["scope"] == scope].sort_values("round")
        if not sub.empty:
            ax.plot(sub["round"], sub["value"], marker="o", label=scope)
    ax.set_xlabel("round")
    ax.set_ylabel("TID dispersion (signature ℓ₂)")
    ax.set_title("R4: TID-dispersion time series")
    ax.legend()
    fig.savefig(out, dpi=110, bbox_inches="tight")
    plt.close(fig)
    return out


def fig_r7_selectors(learning_trace: Optional[Path], out: Path) -> Path:
    """R7: adaptive selector vs baselines on final accuracy. The full sweep is a
    multi-cell exhibit (campaign); from a single cell this shows the one selector
    as a labelled placeholder bar."""
    if learning_trace is None or not learning_trace.exists():
        return _placeholder(out, "R7: selector comparison",
                            "selector sweep is a multi-cell campaign exhibit (placeholder)")
    df = pd.read_csv(learning_trace)
    last_round = df["round"].max()
    policy = str(df["policy_id"].iloc[0]) if "policy_id" in df.columns else "COMPOSITE"
    final_acc = df[df["round"] == last_round]["acc"].mean()
    fig, ax = plt.subplots(figsize=(6, 4))
    ax.bar([policy], [final_acc])
    ax.set_ylabel("final mean accuracy")
    ax.set_title("R7: selector comparison (single-cell placeholder)")
    ax.text(0.5, 0.95, "full 7-selector sweep is a released artefact",
            transform=ax.transAxes, ha="center", va="top", fontsize=8, color="gray")
    fig.savefig(out, dpi=110, bbox_inches="tight")
    plt.close(fig)
    return out


def generate(pass2: Optional[Path], pass3: Optional[Path], out_dir: Path) -> list[Path]:
    out_dir.mkdir(parents=True, exist_ok=True)
    learning_trace = (pass2 / "learning_trace.csv") if pass2 else None
    tid = (pass3 / "tid_timeseries.csv") if pass3 else None
    produced = [
        fig_r1_accuracy(learning_trace, out_dir / "R1_accuracy_vs_round.png"),
        fig_r4_tid(tid, out_dir / "R4_tid_dispersion.png"),
        fig_r7_selectors(learning_trace, out_dir / "R7_selector_comparison.png"),
    ]
    return produced


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description="Render Results-Schema exhibits from the artefact tree")
    ap.add_argument("--pass2", default=None, help="Pass-2 dir (learning_trace.csv)")
    ap.add_argument("--pass3", default=None, help="Pass-3 dir (tid_timeseries.csv, ...)")
    ap.add_argument("--out", required=True)
    args = ap.parse_args(argv)
    produced = generate(Path(args.pass2) if args.pass2 else None,
                        Path(args.pass3) if args.pass3 else None,
                        Path(args.out))
    for p in produced:
        print(f"[figures] wrote {p}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
