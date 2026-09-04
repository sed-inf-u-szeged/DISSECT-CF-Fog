package hu.u_szeged.inf.fog.simulator.util;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.energy.AggregatedEnergyMeter;
import hu.mta.sztaki.lpds.cloud.simulator.energy.EnergyMeter;
import hu.mta.sztaki.lpds.cloud.simulator.energy.specialized.IaaSEnergyMeter;
import hu.mta.sztaki.lpds.cloud.simulator.energy.specialized.PhysicalMachineEnergyMeter;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

/**
 * Periodically samples DISSECT-CF's native energy meters and writes a CSV.
 * Units:
 *  - Internal meters report energy as W·tick. With the default tick=ms this equals mJ.
 *  - CSV exports values in kWh (mJ → J → kWh).
 */
public class EnergyDataCollectorFL extends Timed {

    // frequency explicit and stable across runs
    private static final long FREQ_TICKS = 60_000L; // 60s in ticks (assuming 1 tick = 1 ms)

    // Static registry for lookup by IaaSService/PhysicalMachine
    // allow other components (e.g., FLAggregator) to fetch the right collector
    private static final Map<Object, EnergyDataCollectorFL> REGISTRY = new IdentityHashMap<>();
    private static final Map<String, TreeMap<Long, Double>> READINGS = new TreeMap<>();
    private static final List<EnergyDataCollectorFL> ALL = new ArrayList<>();

    // Instance fields
    public final String name;
    public volatile double energyConsumption; // mJ with default tick=ms

    private final boolean logging;

    private PhysicalMachineEnergyMeter pmEnergyMeter;
    private IaaSEnergyMeter iaasEnergyMeter;

    // =========================================
    // Constructors
    // =========================================

    /**
     * Meters an entire IaaS stack (aggregates host, net, disk of all PMs).
     */
    public EnergyDataCollectorFL(String name, IaaSService iaas, boolean logging) {
        subscribe(FREQ_TICKS); // Ensure recurring sampling happens
        ALL.add(this);

        this.name = name;
        this.logging = logging;

        this.iaasEnergyMeter = new IaaSEnergyMeter(iaas); 
        this.iaasEnergyMeter.startMeter(FREQ_TICKS, true);

        REGISTRY.put(iaas, this);
        if (logging) {
            READINGS.putIfAbsent(name, new TreeMap<>());
        }
    }

    /**
     * Meters a single PhysicalMachine (host + NIC + disk).
     */
    public EnergyDataCollectorFL(String name, PhysicalMachine pm, boolean logging) {
        subscribe(FREQ_TICKS); // Ensure recurring sampling happens
        ALL.add(this);

        this.name = name;
        this.logging = logging;

        this.pmEnergyMeter = new PhysicalMachineEnergyMeter(pm); 
        this.pmEnergyMeter.startMeter(FREQ_TICKS, true);

        REGISTRY.put(pm, this);
        if (logging) {
            READINGS.putIfAbsent(name, new TreeMap<>());
        }
    }

    // =========================================
    // Sampling
    // =========================================
    @Override
    public void tick(long fires) {
        double total;
        // Prefer the specific meter if present
        if (pmEnergyMeter != null) {
            total = pmEnergyMeter.getTotalConsumption(); // mJ (assuming tick=ms)
        } else if (iaasEnergyMeter != null) {
            total = iaasEnergyMeter.getTotalConsumption(); // mJ
        } else {
            total = 0.0;
        }
        energyConsumption = total;

        if (logging) {
            READINGS.get(name).put(fires, total);
        }
    }

    // Lookup helpers used by the FL module
    public static EnergyDataCollectorFL getEnergyCollector(IaaSService iaas) {
        return REGISTRY.get(iaas);
    }
    public static EnergyDataCollectorFL getEnergyCollector(PhysicalMachine pm) {
        return REGISTRY.get(pm);
    }

    /**
     * Forces the underlying DISSECT-CF energy meter to sample <i>now</i> (at the
     * current simulation fire count), bypassing the {@link #FREQ_TICKS} sampling
     * cadence, and refreshes the cached {@link #energyConsumption} field with the
     * result. Returns the live consumption in mJ (same units as the cached field).
     *
     * Why this exists: the cached field is updated only when this collector's own
     * {@link #tick(long)} fires, i.e. every {@code FREQ_TICKS} ticks (default 60 s).
     * FL rounds are typically much shorter than 60 s, so per-round energy deltas
     * computed by the aggregator from the cached field would silently come out as
     * zero. Calling this method at both endpoints of a round lets the aggregator
     * compute a real per-round delta.
     *
     * Implementation note: both {@link PhysicalMachineEnergyMeter} and
     * {@link IaaSEnergyMeter} extend {@link AggregatedEnergyMeter}, whose own
     * {@code tick(long)} is an explicit no-op — the actual accumulation happens
     * in the leaf {@code DirectEnergyMeter}s inside its {@code supervised} list
     * (IaaS aggregates PMs, which aggregate per-spreader leaves). We therefore
     * recurse down the supervision tree and tick each leaf at the current sim
     * time, then read {@code getTotalConsumption()} which sums the now-refreshed
     * children on demand.
     *
     * Safety: this uses the same pattern that {@link
     * hu.mta.sztaki.lpds.cloud.simulator.energy.EnergyMeter#stopMeter()} uses to
     * force a final sample before unsubscribing — the leaf's own {@code tick} is
     * a no-op when {@code fires == lastMetered}, so repeated calls within the same
     * fire count do not double-count, and naturally-scheduled ticks fire after
     * ours with no interference (they compute their delta from the newly-updated
     * {@code lastMetered}).
     */
    public double forceSample() {
        long now = Timed.getFireCount();
        double total = 0.0;
        if (pmEnergyMeter != null) {
            forceTickLeaves(pmEnergyMeter, now);
            total = pmEnergyMeter.getTotalConsumption();
        } else if (iaasEnergyMeter != null) {
            forceTickLeaves(iaasEnergyMeter, now);
            total = iaasEnergyMeter.getTotalConsumption();
        }
        energyConsumption = total;
        return total;
    }

    /**
     * Recursively walks an {@link AggregatedEnergyMeter}'s supervised tree and
     * ticks the leaf {@code DirectEnergyMeter}s. Aggregator-level {@code tick}s
     * are no-ops, so calling tick on an aggregator achieves nothing — only the
     * leaves actually accumulate.
     */
    private static void forceTickLeaves(EnergyMeter meter, long now) {
        if (meter instanceof AggregatedEnergyMeter) {
            for (EnergyMeter child : ((AggregatedEnergyMeter) meter).supervised) {
                forceTickLeaves(child, now);
            }
        } else {
            meter.tick(now);
        }
    }

    /**
     * Releases the Timed subscription and the underlying energy meters, and drops
     * this collector from the static lookup registry. Idempotent: safe to call twice.
     * READINGS are preserved so {@link #writeToFile(String)} can still export them
     * after stopping.
     */
    public void stop() {
        unsubscribe();
        if (pmEnergyMeter != null) {
            pmEnergyMeter.stopMeter();
        }
        if (iaasEnergyMeter != null) {
            iaasEnergyMeter.stopMeter();
        }
        REGISTRY.values().remove(this);
        ALL.remove(this);
    }

    /**
     * Stops every live collector and clears all static state (registry, instance
     * list, and readings). Intended for test isolation and repeated runs in one JVM.
     */
    public static void clearAll() {
        for (EnergyDataCollectorFL c : new ArrayList<>(ALL)) {
            c.stop();
        }
        ALL.clear();
        REGISTRY.clear();
        READINGS.clear();
    }


    // Export
    public static void writeToFile(String resultDirectory) {
        try {
            File outDir = new File(resultDirectory);
            if (!outDir.exists()) {
                outDir.mkdirs();
            }

            FileWriter fw = new FileWriter(new File(outDir, "energy.csv"));

            // Header
            fw.write("Timestamp");
            for (String key : READINGS.keySet()) {
                fw.write("; " + key);
            }
            fw.write("\n");

            // Collect a consistent timestamp set
            TreeSet<Long> allTimestamps = new TreeSet<>();
            for (TreeMap<Long, Double> tm : READINGS.values()) {
                allTimestamps.addAll(tm.keySet());
            }

            // Body (values in kWh)
            for (Long ts : allTimestamps) {
                fw.write(ts.toString());
                for (String key : READINGS.keySet()) {
                    Double mJ = READINGS.get(key).get(ts);
                    double kWh = (mJ == null) ? Double.NaN : ((mJ / 1000.0) / 3_600_000.0); // mJ→J→kWh
                    fw.write(";" + (mJ == null ? "" : String.format(java.util.Locale.US, "%.6f", kWh)));
                }
                fw.write("\n");
            }

            fw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
