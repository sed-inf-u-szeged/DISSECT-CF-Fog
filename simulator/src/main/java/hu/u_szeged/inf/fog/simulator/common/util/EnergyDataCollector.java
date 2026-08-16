package hu.u_szeged.inf.fog.simulator.common.util;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.energy.specialized.IaaSEnergyMeter;
import hu.mta.sztaki.lpds.cloud.simulator.energy.specialized.PhysicalMachineEnergyMeter;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.BooleanSupplier;

/**
 * Collects and optionally logs energy consumption data during a simulation.
 */
public class EnergyDataCollector extends Timed {

    public static final Map<String, EnergyDataCollector> allEnergyCollectors = new HashMap<>();

    public static final NavigableMap<String, NavigableMap<Long, Double>> readings = new TreeMap<>();

    private PhysicalMachineEnergyMeter pmEnergyMeter;

    private IaaSEnergyMeter iaasEnergyMeter;

    private boolean logging;

    private String name;

    private boolean deltaMode;

    private BooleanSupplier measurementCondition;

    private double lastTotalConsumption;

    private boolean hasLast;

    public double accumulatedEnergy;

    /**
     * Creates an unconditional energy data collector for an IaaS service.
     *
     * @param name unique identifier of the collector
     * @param iaas observed IaaS service
     * @param deltaMode true if interval consumption should be recorded
     * @param logging true if readings should be stored for CSV export
     */
    public EnergyDataCollector(String name, IaaSService iaas, boolean deltaMode, boolean logging) {
        this(name, iaas, deltaMode, logging, () -> true);
    }

    /**
     * Creates a conditional energy data collector for an IaaS service.
     *
     * @param name unique identifier of the collector
     * @param iaas observed IaaS service
     * @param deltaMode true if interval consumption should be recorded
     * @param logging true if readings should be stored for CSV export
     * @param measurementCondition condition evaluated at every collector tick
     */
    public EnergyDataCollector(
            String name,
            IaaSService iaas,
            boolean deltaMode,
            boolean logging,
            BooleanSupplier measurementCondition) {

        this.name = name;
        this.deltaMode = deltaMode;
        this.logging = logging;
        this.measurementCondition = Objects.requireNonNull(
                measurementCondition,
                "The energy measurement condition cannot be null.");

        this.iaasEnergyMeter = new IaaSEnergyMeter(iaas);
        this.iaasEnergyMeter.startMeter(ScenarioBase.MINUTE_IN_MILLISECONDS, false);

        init();
    }

    /**
     * Creates an unconditional energy data collector for a physical machine.
     *
     * @param name unique identifier of the collector
     * @param pm observed physical machine
     * @param deltaMode true if interval consumption should be recorded
     * @param logging true if readings should be stored for CSV export
     */
    public EnergyDataCollector(String name, PhysicalMachine pm, boolean deltaMode, boolean logging) {
        this(name, pm, deltaMode, logging, () -> true);
    }

    /**
     * Creates a conditional energy data collector for a physical machine.
     *
     * @param name unique identifier of the collector
     * @param pm observed physical machine
     * @param deltaMode true if interval consumption should be recorded
     * @param logging true if readings should be stored for CSV export
     * @param measurementCondition condition evaluated at every collector tick
     */
    public EnergyDataCollector(
            String name,
            PhysicalMachine pm,
            boolean deltaMode,
            boolean logging,
            BooleanSupplier measurementCondition) {

        this.name = name;
        this.deltaMode = deltaMode;
        this.logging = logging;
        this.measurementCondition = Objects.requireNonNull(
                measurementCondition,
                "The energy measurement condition cannot be null.");

        this.pmEnergyMeter = new PhysicalMachineEnergyMeter(pm);
        this.pmEnergyMeter.startMeter(ScenarioBase.MINUTE_IN_MILLISECONDS, false);

        init();
    }

    private void init() {
        subscribe(ScenarioBase.MINUTE_IN_MILLISECONDS);

        if (allEnergyCollectors.containsKey(name)) {
            SimLogger.logError("EnergyDataCollector with name '" + name + "' already exists.");
        }

        allEnergyCollectors.put(name, this);

        if (logging) {
            readings.put(name, new TreeMap<>());
        }
    }

    /**
     * Stops the energy meter and unsubscribes this collector from the simulation.
     */
    public void stop() {
        unsubscribe();

        if (pmEnergyMeter != null) {
            pmEnergyMeter.stopMeter();
        } else {
            iaasEnergyMeter.stopMeter();
        }
    }

    @Override
    public void tick(long fires) {
        double total = pmEnergyMeter != null
                ? pmEnergyMeter.getTotalConsumption()
                : iaasEnergyMeter.getTotalConsumption();

        double energyConsumption;

        if (!deltaMode) {
            energyConsumption = total;
            accumulatedEnergy = total;
        } else if (!hasLast) {
            energyConsumption = 0.0;
            hasLast = true;
            lastTotalConsumption = total;
        } else {
            energyConsumption = total - lastTotalConsumption;
            lastTotalConsumption = total;

            if (!measurementCondition.getAsBoolean()) {
                energyConsumption = 0.0;
            }

            accumulatedEnergy += energyConsumption;
        }

        if (logging) {
            readings.get(name).put(fires, energyConsumption);
        }
    }

    /**
     * Writes all collected energy consumption data into a CSV file.
     *
     * @param resultDirectory directory where the file is created
     * @return path of the generated CSV file
     */
    public static Path writeToFile(String resultDirectory) {
        File outputFile = new File(resultDirectory, "energy.csv");

        List<String> seriesNames = new ArrayList<>(readings.keySet());
        TreeSet<Long> allTimestamps = new TreeSet<>();

        for (NavigableMap<Long, Double> series : readings.values()) {
            allTimestamps.addAll(series.keySet());
        }

        try (FileWriter fileWriter = new FileWriter(outputFile)) {
            fileWriter.write("Timestamp");

            for (String seriesName : seriesNames) {
                fileWriter.write("," + seriesName);
            }

            fileWriter.write("\n");

            for (Long timestamp : allTimestamps) {
                double hours = timestamp / (double) ScenarioBase.HOUR_IN_MILLISECONDS;
                fileWriter.write(Double.toString(hours));

                for (String seriesName : seriesNames) {
                    NavigableMap<Long, Double> series = readings.get(seriesName);
                    Double consumption = series.get(timestamp);
                    double energyKwh = consumption == null ? 0.0 : consumption / ScenarioBase.TO_KWH;

                    fileWriter.write("," + String.format(java.util.Locale.US, "%.6f", energyKwh));
                }

                fileWriter.write("\n");
            }
        } catch (IOException exception) {
            SimLogger.logError("Failed to set file logging: " + exception);
        }

        return outputFile.toPath();
    }
}