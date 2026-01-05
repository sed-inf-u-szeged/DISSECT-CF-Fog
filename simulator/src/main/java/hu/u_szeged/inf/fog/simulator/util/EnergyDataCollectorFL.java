package hu.u_szeged.inf.fog.simulator.util;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
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
