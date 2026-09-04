package hu.u_szeged.inf.fog.simulator.fl;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Telemetry-export helpers extracted from {@link FLAggregator} so the aggregator
 * stays focused on round-lifecycle logic. Currently hosts the matplotlib-based
 * plotting utilities; further extractions (CSV writers, percentile helpers,
 * cumulative counters) can land here over time without touching the aggregator's
 * state machine.
 *
 * All methods are static — this class holds no state.
 */
public final class FLTelemetry {

    private FLTelemetry() {
        // utility class
    }

    /**
     * Plots the per-round telemetry CSV via a transient matplotlib script.
     * Tries {@code python3} first, falls back to {@code python}. Failures degrade
     * gracefully: the CSV is left in place so the user can plot manually.
     *
     * @param csvPath       path to the telemetry CSV written by the aggregator
     * @param outPngPath    desired PNG output path
     * @param aggregatorId  aggregator id, used only for log message prefixing
     */
    public static void plotTelemetry(String csvPath, String outPngPath, String aggregatorId) {
        String py = ""
            + "import csv, sys\n"
            + "import matplotlib.pyplot as plt\n"
            + "csv_path=sys.argv[1]; out_path=sys.argv[2]\n"
            + "R=[]; ACC=[]; DL=[]; UL=[]\n"
            + "with open(csv_path, newline='') as f:\n"
            + "    r=csv.DictReader(f)\n"
            + "    for row in r:\n"
            + "        R.append(int(row['round']))\n"
            + "        acc=row.get('accuracy','')\n"
            + "        ACC.append(float(acc) if acc not in ('', None) else float('nan'))\n"
            + "        DL.append((float(row['down_bytes']))/1e6)\n"
            + "        UL.append((float(row['up_model_bytes'])+float(row['up_sec_overhead_bytes']))/1e6)\n"
            + "plt.figure(figsize=(8,5))\n"
            + "ax1=plt.gca()\n"
            + "ax1.plot(R, ACC, marker='o', label='Accuracy')\n"
            + "ax1.set_xlabel('Round'); ax1.set_ylabel('Accuracy'); ax1.set_ylim(0,1)\n"
            + "ax2=ax1.twinx()\n"
            + "ax2.plot(R, DL, linestyle='--', label='Down MB')\n"
            + "ax2.plot(R, UL, linestyle=':', label='Up MB (model+sec)')\n"
            + "ax2.set_ylabel('Traffic (MB)')\n"
            + "lines1, labels1 = ax1.get_legend_handles_labels()\n"
            + "lines2, labels2 = ax2.get_legend_handles_labels()\n"
            + "ax2.legend(lines1+lines2, labels1+labels2, loc='best')\n"
            + "ax1.grid(True, linestyle='--', alpha=0.3)\n"
            + "plt.title('FL Telemetry: Accuracy and Traffic')\n"
            + "plt.tight_layout(); plt.savefig(out_path, dpi=150)\n";

        runMatplotlibScript(py, "fl_plot_tmp.py", csvPath, outPngPath, aggregatorId,
                "Plot saved to", "Python plotting failed or PNG not created. CSV is available at");
    }

    /**
     * Plots the per-round energy CSV via a transient matplotlib script with an
     * auto-scaled secondary axis for the participants series when it is small
     * compared to the server series (rendered in mJ instead of J in that case).
     *
     * @param csvPath       path to the energy CSV written by the aggregator
     * @param outPngPath    desired PNG output path
     * @param aggregatorId  aggregator id, used only for log message prefixing
     */
    public static void plotEnergy(String csvPath, String outPngPath, String aggregatorId) {
        String py = ""
            + "import csv, sys\n"
            + "import matplotlib.pyplot as plt\n"
            + "csv_path=sys.argv[1]; out_path=sys.argv[2]\n"
            + "R=[]; SRVJ=[]; PARJ=[]\n"
            + "with open(csv_path, newline='') as f:\n"
            + "    r=csv.DictReader(f)\n"
            + "    for row in r:\n"
            + "        R.append(int(row['round']))\n"
            + "        SRVJ.append(float(row['server_joules']))\n"
            + "        PARJ.append(float(row['participants_joules']))\n"
            + "fig = plt.figure(figsize=(8,5))\n"
            + "ax1 = plt.gca()\n"
            + "ax1.plot(R, SRVJ, marker='o', label='Server (J)', color='tab:blue')\n"
            + "ax1.set_xlabel('Round')\n"
            + "ax1.set_ylabel('Server energy (J)', color='tab:blue')\n"
            + "ax1.tick_params(axis='y', labelcolor='tab:blue')\n"
            + "ax1.grid(True, linestyle='--', alpha=0.3)\n"
            + "use_secondary = False\n"
            + "if len(PARJ) and max(PARJ) > 0:\n"
            + "    if (max(SRVJ) > 0) and (max(PARJ) < 0.1 * max(SRVJ)):\n"
            + "        use_secondary = True\n"
            + "if use_secondary:\n"
            + "    ax2 = ax1.twinx()\n"
            + "    par_mJ = [x*1000.0 for x in PARJ]\n"
            + "    ax2.plot(R, par_mJ, marker='s', label='Participants (mJ)', color='tab:orange')\n"
            + "    ax2.set_ylabel('Participants energy (mJ)', color='tab:orange')\n"
            + "    ax2.tick_params(axis='y', labelcolor='tab:orange')\n"
            + "    lines, labels = ax1.get_legend_handles_labels()\n"
            + "    lines2, labels2 = ax2.get_legend_handles_labels()\n"
            + "    ax1.legend(lines+lines2, labels+labels2, loc='best')\n"
            + "else:\n"
            + "    ax1.plot(R, PARJ, marker='s', label='Participants (J)', color='tab:orange')\n"
            + "    ax1.legend(loc='best')\n"
            + "plt.title('FL Energy per Round')\n"
            + "plt.tight_layout(); plt.savefig(out_path, dpi=150)\n";

        runMatplotlibScript(py, "fl_energy_plot_tmp.py", csvPath, outPngPath, aggregatorId,
                "Energy plot saved to", "Python plotting failed or PNG not created. Energy CSV at");
    }

    /**
     * Plots the time-series energy CSV produced by
     * {@link hu.u_szeged.inf.fog.simulator.util.EnergyDataCollectorFL#writeToFile(String)}.
     *
     * The input CSV is semicolon-separated with one row per sampling tick (default
     * every 60 s) and one column per registered collector:
     * <pre>
     *   Timestamp; aggregator; device-0; device-1; ...
     *   60000;     0.064636;   0.000000;  0.000000; ...
     * </pre>
     * Values are cumulative energy in kWh. The aggregator column (if present) is
     * highlighted in red bold; device columns are drawn thinner with the default
     * matplotlib palette. X-axis is simulation time in seconds (ticks/1000).
     *
     * @param csvPath       path to the {@code energy.csv} written by EnergyDataCollectorFL
     * @param outPngPath    desired PNG output path
     * @param aggregatorId  aggregator id, used only for log message prefixing
     */
    public static void plotEnergyTimeseries(String csvPath, String outPngPath, String aggregatorId) {
        String py = ""
            + "import csv, sys\n"
            + "import matplotlib.pyplot as plt\n"
            + "csv_path=sys.argv[1]; out_path=sys.argv[2]\n"
            + "T=[]; series={}; order=[]\n"
            + "with open(csv_path, newline='') as f:\n"
            + "    rdr=csv.reader(f, delimiter=';')\n"
            + "    header=[h.strip() for h in next(rdr)]\n"
            + "    cols=header[1:]\n"
            + "    for c in cols:\n"
            + "        series[c]=[]\n"
            + "        order.append(c)\n"
            + "    for row in rdr:\n"
            + "        if not row:\n"
            + "            continue\n"
            + "        try:\n"
            + "            ts=int(row[0])/1000.0\n"
            + "        except (ValueError, IndexError):\n"
            + "            continue\n"
            + "        T.append(ts)\n"
            + "        for i,c in enumerate(cols):\n"
            + "            v=row[i+1].strip() if (i+1)<len(row) else ''\n"
            + "            series[c].append(float(v) if v not in ('','nan','NaN') else float('nan'))\n"
            + "plt.figure(figsize=(9,5))\n"
            + "for c in order:\n"
            + "    if c=='aggregator':\n"
            + "        plt.plot(T, series[c], label=c, linewidth=2.5, color='tab:red', zorder=3)\n"
            + "    else:\n"
            + "        plt.plot(T, series[c], label=c, linewidth=1.0, alpha=0.7)\n"
            + "plt.xlabel('Simulation time (s)')\n"
            + "plt.ylabel('Cumulative energy (kWh)')\n"
            + "plt.title('FL Energy Time-Series (per collector, cumulative)')\n"
            + "plt.grid(True, linestyle='--', alpha=0.3)\n"
            + "ncol = 2 if len(order) > 6 else 1\n"
            + "plt.legend(loc='upper left', fontsize=8, ncol=ncol)\n"
            + "plt.tight_layout(); plt.savefig(out_path, dpi=150)\n";

        runMatplotlibScript(py, "fl_energy_ts_plot_tmp.py", csvPath, outPngPath, aggregatorId,
                "Energy time-series plot saved to",
                "Python plotting failed or PNG not created. Energy time-series CSV at");
    }

    /** Aggregator-labelled wrapper over {@link #runMatplotlib}; python output echoes. */
    private static void runMatplotlibScript(String pyBody,
                                            String scriptFileName,
                                            String csvPath,
                                            String outPngPath,
                                            String aggregatorId,
                                            String successPrefix,
                                            String failureSuffix) {
        boolean success = runMatplotlib(pyBody, scriptFileName, true, csvPath, outPngPath);
        if (success) {
            System.out.println("Aggregator " + aggregatorId + ": " + successPrefix + " " + outPngPath);
        } else {
            System.out.println("Aggregator " + aggregatorId + ": " + failureSuffix + " " + csvPath);
        }
    }

    /**
     * Writes {@code pyBody} to a transient script file, runs it with
     * {@code python3} (then {@code python} as fallback) passing {@code argv},
     * and deletes the script afterwards. Success requires exit code 0 and, by
     * convention, that the <b>last</b> argv entry exists as a file afterwards
     * (the output PNG). Failures degrade gracefully (never throws) — the
     * simulation itself must never depend on Python. Shared by the FL demos.
     *
     * @param pyBody the matplotlib script source.
     * @param scriptFileName transient script file name (written to the CWD).
     * @param echoPythonOutput {@code true} inherits stdio (python output shows on
     *                         the console); {@code false} discards it silently.
     * @param argv script arguments; the last one is the expected output file.
     * @return {@code true} iff the script succeeded and the output file exists.
     */
    public static boolean runMatplotlib(String pyBody, String scriptFileName,
                                        boolean echoPythonOutput, String... argv) {
        Path script = Paths.get(scriptFileName);
        try {
            Files.write(script, pyBody.getBytes(StandardCharsets.UTF_8));
            List<String> cmd = new ArrayList<>();
            cmd.add("python3");
            cmd.add(script.toString());
            for (String a : argv) {
                cmd.add(a);
            }
            boolean success = runOnce(cmd, echoPythonOutput, argv);
            if (!success) {
                // Fall back to `python` on systems where `python3` is not on PATH.
                cmd.set(0, "python");
                success = runOnce(cmd, echoPythonOutput, argv);
            }
            return success;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            try { Files.deleteIfExists(script); } catch (Exception ignore) {}
        }
    }

    private static boolean runOnce(List<String> cmd, boolean echoPythonOutput, String[] argv)
            throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        Process p;
        if (echoPythonOutput) {
            p = pb.inheritIO().start();
        } else {
            p = pb.redirectErrorStream(true).start();
            try (java.io.InputStream in = p.getInputStream()) {
                byte[] buf = new byte[4096];
                while (in.read(buf) != -1) {
                    // drain so the process never blocks on a full pipe; not echoed
                }
            }
        }
        int exit = p.waitFor();
        return exit == 0
                && (argv.length == 0 || Files.exists(Paths.get(argv[argv.length - 1])));
    }
}
