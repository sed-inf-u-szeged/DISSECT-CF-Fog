package hu.u_szeged.inf.fog.simulator.util;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.energy.specialized.IaaSEnergyMeter;
import hu.mta.sztaki.lpds.cloud.simulator.energy.specialized.PhysicalMachineEnergyMeter;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.TreeMap;
import java.util.TreeSet;

public class EnergyDataCollector extends Timed {
    
    public static long freq = 60 * 1000;
    
    public static List<EnergyDataCollector> energyCollectors = new ArrayList<>();
    
    public static TreeMap<String, TreeMap<Long, Double>> readings = new TreeMap<String, TreeMap<Long, Double>>();
        
    public double energyConsumption;
    
    PhysicalMachineEnergyMeter pmEnergyMeter;
    
    IaaSEnergyMeter iaasEnergyMeter;
    
    boolean logging;
    
    String name;
    
    public EnergyDataCollector(String name, IaaSService iaas, boolean logging) {
        this.name = name;
        subscribe(freq);
        energyCollectors.add(this);
        this.logging = logging;
        this.iaasEnergyMeter = new IaaSEnergyMeter(iaas);
        this.iaasEnergyMeter.startMeter(freq, true);
    }
    
    public EnergyDataCollector(String name, PhysicalMachine pm, boolean logging) {
        this.name = name;
        subscribe(freq);
        energyCollectors.add(this);
        this.logging = logging;
        this.pmEnergyMeter = new PhysicalMachineEnergyMeter(pm);
        this.pmEnergyMeter.startMeter(freq, true);
    }

    public void stop() {
        unsubscribe();
        if (this.pmEnergyMeter != null) {
            this.pmEnergyMeter.stopMeter();
        }
        if (this.iaasEnergyMeter != null) {
            this.iaasEnergyMeter.stopMeter();
        }
    }
    
    @Override
    public void tick(long fires) {
        if (this.pmEnergyMeter != null) {
            energyConsumption = pmEnergyMeter.getTotalConsumption();
        }
        if (this.iaasEnergyMeter != null) {
            energyConsumption = iaasEnergyMeter.getTotalConsumption();
        }
        if (logging) {
            readings.computeIfAbsent(this.name, k -> new TreeMap<>()).put(Timed.getFireCount(), energyConsumption);
        }
      
    }
    
    public static void writeToFile(String resultDirectory) {
        try {            
            FileWriter fw = new FileWriter(resultDirectory + File.separator + "energy.csv");
            
            fw.write("Timestamp");
            for (String key : readings.keySet()) {
                fw.write("," + key);
            }
            fw.write("\n");

            TreeSet<Long> allTimestamps = new TreeSet<>();
            for (TreeMap<Long, Double> treeMap : readings.values()) {
                allTimestamps.addAll(treeMap.keySet());
            }

            for (Long timestamp : allTimestamps) {
                fw.write(timestamp.toString()); 
                for (String key : readings.keySet()) {
                    Double value = readings.get(key).get(timestamp);
                    fw.write("," + (value != null ? Math.round(value / 1000 / 3_600_000) : "")); 
                }
                fw.write("\n");
            }

            fw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
