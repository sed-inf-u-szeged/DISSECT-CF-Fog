package hu.u_szeged.inf.fog.simulator.agent;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.u_szeged.inf.fog.simulator.agent.urbannoise.NoiseSensor;
import hu.u_szeged.inf.fog.simulator.util.SimLogger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;

public class SwarmAgent extends Timed {

    public ArrayList<Object> components;
    
    public ArrayList<NoiseSensor> noiseSensorsWithClassification;
    
    private int currentIndex;
    
    public static ArrayList<SwarmAgent> allSwarmAgents = new ArrayList<>(); 
    
    private final Deque<CpuTempSample> cpuTempSamples;
    
    public AgentApplication app;

    public SwarmAgent(AgentApplication app) {
        this.app = app;
        this.components = new ArrayList<>();
        cpuTempSamples = new ArrayDeque<>();
        this.noiseSensorsWithClassification = new ArrayList<>();
        allSwarmAgents.add(this);
        subscribe(10_000);
    }
    
    public void registerComponent(Object component) {
        this.components.add(component);
    }

    @Override
    public void tick(long fires) {
        double currentCpuLoad = avgCpu();
        recordCpuLoad(currentCpuLoad);
        this.scale(currentCpuLoad);
    }
    
    public void recordCpuLoad(double currentCpuLoad) {
        long now = Timed.getFireCount();
        cpuTempSamples.addLast(new CpuTempSample(now, currentCpuLoad));

        while (!cpuTempSamples.isEmpty() && now - cpuTempSamples.peekFirst().timestamp > 600_000) {
            cpuTempSamples.removeFirst();
        }
    }

    public double getCpuLoadAvgLastMin() {        
        if (cpuTempSamples.isEmpty()) {
            return Double.MAX_VALUE;
        }
        
        long now = Timed.getFireCount();
        long elapsed = now - cpuTempSamples.peekFirst().timestamp;

        if (elapsed < 600_000) { 
            return Double.MAX_VALUE;
        }

        double sum = 0.0;
        int count = 0;
        for (CpuTempSample s : cpuTempSamples) {
            sum += s.cpuLoad;
            count++;
        }
        
        return (count > 0) ? (sum / count) : 0.0;
    }
    
    public double avgCpu() {
        int noiseSensors = 0;
        double load = 0.0;
        for (Object o : this.components) {
            if (o.getClass().equals(NoiseSensor.class)) {
                NoiseSensor ns = (NoiseSensor) o;
                if (ns.util.vm.isProcessing()) {
                    load += 100;
                } else {
                    load++;
                }
                noiseSensors++;
            }
        }
        return noiseSensors == 0 ? 0 : load / noiseSensors;
    }
    
    private NoiseSensor findSensorByCpuTemp(boolean minSearch) {
        double min = Double.MAX_VALUE;
        double max = Double.MIN_VALUE;
        NoiseSensor best = null;
        
        for (Object o : this.components) {
            if (o.getClass().equals(NoiseSensor.class)) {
                NoiseSensor ns = (NoiseSensor) o;
                
                if (minSearch) {
                    if (ns.cpuTemp < min && !ns.isClassificationRunning && ns.cpuTemp < 95.0) {
                        best = ns;
                        min = ns.cpuTemp;
                    }     
                } else {
                    if (ns.cpuTemp > max && ns.isClassificationRunning) {
                        best = ns;
                        max = ns.cpuTemp;
                    }
                }
                      
            }
        }
        return best;
    }
    
    private void scale(double currentCpuLoad) {      
        /*
        for (Object o : this.components) {
            if (o.getClass().equals(NoiseSensor.class)) {
                NoiseSensor ns = (NoiseSensor) o;
                SimLogger.logRun(Timed.getFireCount()/1000/60/60 + " " + ns.hashCode() + " " + ns.cpuTemp + " " 
                + ns.isClassificationRunning + " " + ns.filesToBeProcessed.size() + " " + noiseSensorsWithClassification.size());
            }
        }
        */
        
        if (noiseSensorsWithClassification.size() < 2) {
            NoiseSensor ns = findSensorByCpuTemp(true);
            if (ns != null) {
                SimLogger.logRun(this.app.getComponentName(ns.util.resource.name) + "'classifier was started at: " 
                        + Timed.getFireCount() / 1000.0 / 60.0 + " min. due to minimum requirement");
                noiseSensorsWithClassification.add(ns);
                ns.isClassificationRunning = true;
            } else {
                //SimLogger.logRun("SA did not find any suitables sensor to start
                
            }
        } else if (currentCpuLoad > 70) {
            NoiseSensor ns = findSensorByCpuTemp(true);
            if (ns != null) {
                SimLogger.logRun(this.app.getComponentName(ns.util.resource.name) + "'classifier was started at: " 
                    + Timed.getFireCount() / 1000 / 60 + " min. due to large load");
                noiseSensorsWithClassification.add(ns);
                ns.isClassificationRunning = true;
            }
           
        } else if (getCpuLoadAvgLastMin() < 30) {
            NoiseSensor ns = findSensorByCpuTemp(false);
            if (ns != null && noiseSensorsWithClassification.size() > 2) {
                SimLogger.logRun(this.app.getComponentName(ns.util.resource.name) + "'classifier was turned off at: "
                    + Timed.getFireCount() / 1000.0 / 60.0  + " min. due to small load");
                noiseSensorsWithClassification.remove(ns);
                ns.isClassificationRunning = false;    
            }
        }
    }
    
    public NoiseSensor getNextNoiseSensorToOffload() {
        if (noiseSensorsWithClassification.isEmpty()) {
            return null;
        }
        if (currentIndex >= noiseSensorsWithClassification.size()) {
            currentIndex = 0;
        }
        
        NoiseSensor item = noiseSensorsWithClassification.get(currentIndex);
        currentIndex = (currentIndex + 1) % noiseSensorsWithClassification.size();
        return item;
    }
    
    public void startNecesseryServices(int num) {
        int count = 0;
        for (Object component : components) {
            if (component instanceof NoiseSensor) {
                NoiseSensor ns = (NoiseSensor) component;
                noiseSensorsWithClassification.add(ns);
                ns.isClassificationRunning = true;
                
                SimLogger.logRun(this.app.getComponentName(ns.util.resource.name) + "'classifier was started at: "
                    + Timed.getFireCount() / 1000.0 / 60.0 + " min. due to minimum requirement");
                count++;
                if (count >= num) {
                    break; 
                }
            }
        }
    }

    private static class CpuTempSample {
        long timestamp;
        double cpuLoad;
        
        CpuTempSample(long ts, double load) {
            this.timestamp = ts;
            this.cpuLoad = load;
        }
    }
}