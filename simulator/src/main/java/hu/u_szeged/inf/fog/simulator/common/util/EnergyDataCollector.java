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
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Collects and optionally logs energy consumption data during a simulation.
 */
public class EnergyDataCollector extends Timed {
    
    public static final Map<String, EnergyDataCollector> allEnergyCollectors = new HashMap<>();

    public static final NavigableMap<String, NavigableMap<Long, Double>> readings = new TreeMap<>();

    PhysicalMachineEnergyMeter pmEnergyMeter;
    
    IaaSEnergyMeter iaasEnergyMeter;
    
    boolean logging;
    
    String name;

    private boolean deltaMode;

    private double lastTotalConsumption;

    private boolean hasLast;

    public double accumulatedEnergy;
    
    /**
     * Creates an energy data collector for an IaaS service.
     *
     * @param name unique identifier of the collector
     * @param iaas the IaaS service whose energy consumption is measured
     * @param logging true, energy readings are stored for later export
     */
    public EnergyDataCollector(String name, IaaSService iaas,  boolean deltaMode, boolean logging) {
        this.name = name;
        this.deltaMode = deltaMode;
        this.logging = logging;
        this.iaasEnergyMeter = new IaaSEnergyMeter(iaas);
        this.iaasEnergyMeter.startMeter(ScenarioBase.MINUTE_IN_MILLISECONDS, false);
        init();
    }
    
    /**
     * Creates an energy data collector for a physical machine.
     *
     * @param name unique identifier of the collector
     * @param pm the physical machine whose energy consumption is measured
     * @param logging true, energy readings are stored for later export
     */
    public EnergyDataCollector(String name, PhysicalMachine pm,  boolean deltaMode, boolean logging) {
        this.name = name;
        this.deltaMode = deltaMode;
        this.logging = logging;
        this.pmEnergyMeter = new PhysicalMachineEnergyMeter(pm);
        this.pmEnergyMeter.startMeter(ScenarioBase.MINUTE_IN_MILLISECONDS, false);
        init();
    }
    
    private void init() {
        subscribe(ScenarioBase.MINUTE_IN_MILLISECONDS);
        
        if (allEnergyCollectors.containsKey(name)) {
            SimLogger.logError("EnergyDataCollector with name '" + name + "' already exists");
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
        if (this.pmEnergyMeter != null) {
            this.pmEnergyMeter.stopMeter();
        } else {
            this.iaasEnergyMeter.stopMeter();
        }
    }

    @Override
    public void tick(long fires) {
        double total;
        if (this.pmEnergyMeter != null) {
            total = pmEnergyMeter.getTotalConsumption();
        } else {
            total = iaasEnergyMeter.getTotalConsumption();
        }

        double energyConsumption;

        if (!deltaMode) {
            energyConsumption = total;
            accumulatedEnergy = total;
        } else {
            if (!hasLast) {
                energyConsumption = 0.0;
                hasLast = true;
            } else {
                energyConsumption = total - lastTotalConsumption;
            }
            lastTotalConsumption = total;
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
     */
    public static Path writeToFile(String resultDirectory) {
        File outFile = new File(resultDirectory, "energy.csv");
        
        List<String> seriesNames = new ArrayList<>(readings.keySet());
        
        TreeSet<Long> allTimestamps = new TreeSet<>();
        for (NavigableMap<Long, Double> series : readings.values()) {
            allTimestamps.addAll(series.keySet());
        }
        
        try (FileWriter fw = new FileWriter(outFile)) {
            fw.write("Timestamp");
            for (String name : seriesNames) {
                fw.write("," + name);
            }
            fw.write("\n");
            
            for (Long time : allTimestamps) {
                
                double hours = time / (double) ScenarioBase.HOUR_IN_MILLISECONDS;
                fw.write(Double.toString(hours));

                for (String name : seriesNames) {
                    NavigableMap<Long, Double> series = readings.get(name);
                    Double energyKwh = series.get(time) / ScenarioBase.TO_KWH;
                    fw.write("," + String.format(java.util.Locale.US, "%.6f", energyKwh));
                }

                fw.write("\n");
            }
        } catch (IOException e) {
            SimLogger.logError("Failed to set file logging: " + e.toString());
        }
        return Path.of(outFile.getAbsolutePath());
    }
}