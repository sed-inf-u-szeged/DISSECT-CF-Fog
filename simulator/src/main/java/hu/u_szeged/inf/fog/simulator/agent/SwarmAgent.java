package hu.u_szeged.inf.fog.simulator.agent;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.u_szeged.inf.fog.simulator.agent.urbannoise.NoiseSensor;
import hu.u_szeged.inf.fog.simulator.util.SimLogger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;

public class SwarmAgent extends Timed {

    public ArrayList<Object> components;
    
    public ArrayList<NoiseSensor> noiseSensorsWithClassifier;
    
    private int currentIndex;
    
    public static ArrayList<SwarmAgent> allSwarmAgents = new ArrayList<>(); 
    
    private final Deque<CpuTempSample> cpuTempSamples;
    
    public AgentApplication app;

    public SwarmAgent(AgentApplication app) {
        this.app = app;
        this.components = new ArrayList<>();
        cpuTempSamples = new ArrayDeque<>();
        this.noiseSensorsWithClassifier = new ArrayList<>();
        allSwarmAgents.add(this);
        subscribe(this.app.configuration.get("samplingFreq").longValue());
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
        while (!cpuTempSamples.isEmpty() 
                && now - cpuTempSamples.peekFirst().timestamp > this.app.configuration.get("cpuTimeWindow").longValue()) {
            cpuTempSamples.removeFirst();
        }
    }

    public double getCpuLoadAvgLastMin() {        
        if (cpuTempSamples.isEmpty()) {
            return Double.MAX_VALUE;
        }
        
        long now = Timed.getFireCount();
        long elapsed = now - cpuTempSamples.peekFirst().timestamp;

        if (elapsed < this.app.configuration.get("cpuTimeWindow").longValue()) { 
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
        double noiseSensors = 0.0;
        double load = 0.0;
        for (Object o : this.components) {
            if (o.getClass().equals(NoiseSensor.class)) {
                NoiseSensor ns = (NoiseSensor) o;
                if (ns.util.vm.isProcessing()) {
                    load += 100;
                    noiseSensors++;
                } else if (ns.isClassificationRunning) {
                    load++;
                    noiseSensors++;
                }
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
                    if (ns.cpuTemp < min && !ns.isClassificationRunning 
                            && ns.cpuTemp < this.app.configuration.get("cpuTempTreshold").doubleValue()) {
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
        if (noiseSensorsWithClassifier.size() < this.app.configuration.get("minContainerCount").intValue()) {
            NoiseSensor ns = findSensorByCpuTemp(true);
            if (ns != null) {
                SimLogger.logRun(this.app.getComponentName(ns.util.resource.name) + "'classifier was started at: " 
                        + Timed.getFireCount() / 1000.0 / 60.0 + " min. due to minimum requirement");
                noiseSensorsWithClassifier.add(ns);
                ns.isClassificationRunning = true;
            } 
        } else if (currentCpuLoad > this.app.configuration.get("cpuLoadScaleUp").doubleValue()) {
            NoiseSensor ns = findSensorByCpuTemp(true);
            if (ns != null) {
                SimLogger.logRun(this.app.getComponentName(ns.util.resource.name) + "'classifier was started at: " 
                    + Timed.getFireCount() / 1000 / 60 + " min. due to large load");
                noiseSensorsWithClassifier.add(ns);
                ns.isClassificationRunning = true;
            }
        } else if (getCpuLoadAvgLastMin() < this.app.configuration.get("cpuLoadScaleDown").doubleValue()) {
            NoiseSensor ns = findSensorByCpuTemp(false);
            if (ns != null && noiseSensorsWithClassifier.size() > this.app.configuration.get("minContainerCount").intValue()) {
                SimLogger.logRun(this.app.getComponentName(ns.util.resource.name) + "'classifier was turned off at: "
                    + Timed.getFireCount() / 1000.0 / 60.0  + " min. due to small load");
                noiseSensorsWithClassifier.remove(ns);
                ns.isClassificationRunning = false;    
            }
        }
    }
    
    public NoiseSensor getNextNoiseSensorToOffload() {
        if (noiseSensorsWithClassifier.isEmpty()) {
            return null;
        }
        if (currentIndex >= noiseSensorsWithClassifier.size()) {
            currentIndex = 0;
        }
        
        NoiseSensor item = noiseSensorsWithClassifier.get(currentIndex);
        currentIndex = (currentIndex + 1) % noiseSensorsWithClassifier.size();
        return item;
    }
    
    public void startNecesseryServices(int num) {
        int count = 0;
        for (Object component : components) {
            if (component instanceof NoiseSensor) {
                NoiseSensor ns = (NoiseSensor) component;
                noiseSensorsWithClassifier.add(ns);
                ns.isClassificationRunning = true;
                
                SimLogger.logRun(this.app.getComponentName(ns.util.resource.name) + "'classifier was started first at: "
                    + Timed.getFireCount() / 1000.0 / 60.0 + " min.");
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