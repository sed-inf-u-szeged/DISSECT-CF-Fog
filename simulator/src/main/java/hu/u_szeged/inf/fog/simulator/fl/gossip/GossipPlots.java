package hu.u_szeged.inf.fog.simulator.fl.gossip;

import hu.u_szeged.inf.fog.simulator.fl.FLTelemetry;
import java.nio.file.Path;

/**
 * PNG figure rendering for the gossip artefacts, shared by {@code FLGossipDemo}
 * and {@code FLScenarioRunner}. Each method reads one CSV and writes one PNG via
 * {@link FLTelemetry#runMatplotlib} (transient script, python3 → python
 * fallback) and <b>degrades gracefully</b>: without Python/matplotlib a one-line
 * note is printed and the CSV remains — the simulation never depends on Python
 * for its data artefacts.
 */
public final class GossipPlots {

    private GossipPlots() {
    }

    /** TID mixing curves (global / inter-cluster / consensus) vs round. */
    public static void plotTid(Path csv, Path png) {
        plot(TID_PLOT, "fl_gossip_tid_plot.py", csv, png);
    }

    /** Per-round total node energy (mJ) bars, from {@code energy_per_round.csv}. */
    public static void plotEnergy(Path csv, Path png) {
        plot(ENERGY_PLOT, "fl_gossip_energy_plot.py", csv, png);
    }

    /** Per-round traffic (KB) bars + idle-ticks line, from the gossip telemetry CSV. */
    public static void plotTrafficIdle(Path csv, Path png) {
        plot(TRAFFIC_IDLE_PLOT, "fl_gossip_traffic_plot.py", csv, png);
    }

    /**
     * Real test accuracy vs round from a Pass-2 {@code learning_trace.csv}: one
     * thin line per node plus the bold node-mean. Only meaningful for real
     * training (Track A/B) — the synthetic demo has no accuracy to plot.
     */
    public static void plotAccuracy(Path csv, Path png) {
        plot(ACCURACY_PLOT, "fl_gossip_accuracy_plot.py", csv, png);
    }

    private static void plot(String pyBody, String scriptName, Path csv, Path png) {
        boolean ok = FLTelemetry.runMatplotlib(pyBody, scriptName, false,
                csv.toString(), png.toString());
        System.out.println(ok ? "  saved " + png.getFileName()
                : "  (skipped " + png.getFileName() + " — no Python/matplotlib; CSV is at " + csv + ")");
    }

    // ------------------------------------------------------------------
    // matplotlib scripts (read a CSV from argv[1], write a PNG to argv[2]).
    // ------------------------------------------------------------------

    private static final String TID_PLOT = ""
            + "import csv, sys\n"
            + "import matplotlib\n"
            + "matplotlib.use('Agg')\n"
            + "import matplotlib.pyplot as plt\n"
            + "from collections import defaultdict\n"
            + "p, out = sys.argv[1], sys.argv[2]\n"
            + "data = defaultdict(lambda: ([], []))\n"
            + "with open(p, newline='') as f:\n"
            + "    for row in csv.DictReader(f):\n"
            + "        sc = row['scope']\n"
            + "        data[sc][0].append(int(row['round'])); data[sc][1].append(float(row['value']))\n"
            + "plt.figure(figsize=(8,5))\n"
            + "for sc in ('global','inter_cluster','consensus'):\n"
            + "    if sc in data: plt.plot(data[sc][0], data[sc][1], marker='o', label=sc)\n"
            + "plt.xlabel('Round'); plt.ylabel('TID (signature L2 distance)')\n"
            + "plt.title('Topology-Induced Divergence vs round (lower = better mixing)')\n"
            + "plt.legend(); plt.grid(True, linestyle='--', alpha=0.3)\n"
            + "plt.tight_layout(); plt.savefig(out, dpi=150)\n";

    private static final String ENERGY_PLOT = ""
            + "import csv, sys\n"
            + "import matplotlib\n"
            + "matplotlib.use('Agg')\n"
            + "import matplotlib.pyplot as plt\n"
            + "from collections import defaultdict\n"
            + "p, out = sys.argv[1], sys.argv[2]\n"
            + "tot = defaultdict(float)\n"
            + "with open(p, newline='') as f:\n"
            + "    for row in csv.DictReader(f):\n"
            + "        v = row['energy_mj']\n"
            + "        tot[int(row['round'])] += (float(v) if v not in ('','nan','NaN') else 0.0)\n"
            + "R = sorted(tot); E = [tot[r] for r in R]\n"
            + "plt.figure(figsize=(8,5))\n"
            + "plt.bar(R, E)\n"
            + "plt.xlabel('Round'); plt.ylabel('Energy (mJ)')\n"
            + "plt.title('Per-round total node energy (all nodes)')\n"
            + "plt.grid(True, axis='y', linestyle='--', alpha=0.3)\n"
            + "plt.tight_layout(); plt.savefig(out, dpi=150)\n";

    private static final String TRAFFIC_IDLE_PLOT = ""
            + "import csv, sys\n"
            + "import matplotlib\n"
            + "matplotlib.use('Agg')\n"
            + "import matplotlib.pyplot as plt\n"
            + "from collections import defaultdict\n"
            + "p, out = sys.argv[1], sys.argv[2]\n"
            + "by = defaultdict(float); idle = defaultdict(float)\n"
            + "with open(p, newline='') as f:\n"
            + "    for row in csv.DictReader(f):\n"
            + "        r = int(row['round'])\n"
            + "        by[r] += (float(row['ul_bytes']) + float(row['dl_bytes'])) / 1000.0\n"
            + "        idle[r] += float(row['idle_ticks'])\n"
            + "R = sorted(by)\n"
            + "fig = plt.figure(figsize=(8,5)); ax1 = plt.gca()\n"
            + "ax1.bar(R, [by[r] for r in R], alpha=0.6, label='traffic (KB)')\n"
            + "ax1.set_xlabel('Round'); ax1.set_ylabel('Traffic (KB)')\n"
            + "ax2 = ax1.twinx(); ax2.plot(R, [idle[r] for r in R], color='red', marker='o', label='idle ticks')\n"
            + "ax2.set_ylabel('Idle time (ticks)')\n"
            + "l1,la1 = ax1.get_legend_handles_labels(); l2,la2 = ax2.get_legend_handles_labels()\n"
            + "ax1.legend(l1+l2, la1+la2, loc='best')\n"
            + "plt.title('Per-round traffic and idle time (cost of synchrony)')\n"
            + "plt.tight_layout(); plt.savefig(out, dpi=150)\n";

    private static final String ACCURACY_PLOT = ""
            + "import csv, sys\n"
            + "import matplotlib\n"
            + "matplotlib.use('Agg')\n"
            + "import matplotlib.pyplot as plt\n"
            + "from collections import defaultdict\n"
            + "p, out = sys.argv[1], sys.argv[2]\n"
            + "acc = defaultdict(dict)\n"
            + "with open(p, newline='') as f:\n"
            + "    for row in csv.DictReader(f):\n"
            + "        acc[int(row['node'])][int(row['round'])] = float(row['acc'])\n"
            + "plt.figure(figsize=(8,5))\n"
            + "rounds = sorted({r for d in acc.values() for r in d})\n"
            + "for n in sorted(acc):\n"
            + "    R = sorted(acc[n]); plt.plot(R, [acc[n][r] for r in R], alpha=0.35, linewidth=1)\n"
            + "mean = [sum(acc[n][r] for n in acc if r in acc[n]) / sum(1 for n in acc if r in acc[n])\n"
            + "        for r in rounds]\n"
            + "plt.plot(rounds, mean, color='black', marker='o', linewidth=2.2, label='mean over nodes')\n"
            + "plt.xlabel('Round'); plt.ylabel('Local test accuracy')\n"
            + "plt.ylim(0.0, 1.0)\n"
            + "plt.title('Real per-node test accuracy vs round (thin = nodes, bold = mean)')\n"
            + "plt.legend(); plt.grid(True, linestyle='--', alpha=0.3)\n"
            + "plt.tight_layout(); plt.savefig(out, dpi=150)\n";
}
