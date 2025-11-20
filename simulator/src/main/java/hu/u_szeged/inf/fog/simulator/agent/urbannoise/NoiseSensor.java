package hu.u_szeged.inf.fog.simulator.agent.urbannoise;

import hu.mta.sztaki.lpds.cloud.simulator.DeferredEvent;
import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ConsumptionEventAdapter;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ResourceConsumption;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.io.StorageObject;
import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;
import hu.u_szeged.inf.fog.simulator.agent.AgentApplication;
import hu.u_szeged.inf.fog.simulator.agent.Capacity.Utilisation;
import hu.u_szeged.inf.fog.simulator.agent.SwarmAgent;
import hu.u_szeged.inf.fog.simulator.util.SimLogger;
import java.util.ArrayList;

public class NoiseSensor extends Timed {
    
    public static long totalGeneratedFileSize;
    
    public static long totalGeneratedFiles;

    public static long totalOffloadedFiles;
    
    public static long totalSoundEventsToProcess;
    
    public static long totalProcessedFiles;
    
    public static long totalTimeOnNetwork;
    
    public int noOfmigratedFiles;
    
    public int noOfprocessedFiles;
    
    SwarmAgent sa;
    
    public Utilisation util;
    
    public PhysicalMachine pm;
    
    int threshold;
    
    AgentApplication app;
    
    public boolean isClassificationRunning;
    
    public double cpuTemp;
    
    public ArrayList<StorageObject> filesToBeProcessed;
            
    public boolean inside;
    
    public boolean sunExposed;
    
    private RemoteServer remoteServer;
    
    public int prevSoundValue; 
    
    public NoiseSensor(AgentApplication app, SwarmAgent sa, 
            Utilisation util, long freq, int threshold, boolean inside, boolean sunExposed) {
        this.sa = sa;
        this.util = util;
        this.pm = util.vm.getResourceAllocation().getHost();
        this.threshold = threshold;
        this.app = app;
        this.cpuTemp = this.app.configuration.get("minCpuTemp").doubleValue();
        this.filesToBeProcessed = new ArrayList<>();
        this.inside = inside;
        this.sunExposed = sunExposed;
        sa.registerComponent(this);  
        prevSoundValue = -1;
       
        new DeferredEvent((SeedSyncer.centralRnd.nextInt(10) + 1) * 1000L) {

            @Override
            protected void eventAction() {
                remoteServer = findRemoteServer();
                subscribe(freq);
            }
        };
    }

    @Override
    public void tick(long fires) {
        this.adjustTemperatureByEnv();
        
        String filename = Timed.getFireCount() + "-" + app.getComponentName(util.resource.name);
        long fileSize = this.app.configuration.get("soundFileSize").longValue();
        StorageObject so = new StorageObject(filename, fileSize, false);
        this.pm.localDisk.registerObject(so);
        totalGeneratedFileSize += fileSize;
        totalGeneratedFiles++;
        
        int value = lazySoundValue();
        if (value > threshold) {
            totalSoundEventsToProcess++;
            this.filesToBeProcessed.add(so);
        } else {
            sendResultToDatabase(pm.localDisk, remoteServer.util.vm.getResourceAllocation().getHost().localDisk, so); 
        }
        
        // manage the queue
        if (this.isClassificationRunning) {
            runSoundClassification();
        } else {
            offload();
        }  
    }
        
    private void offload() {
        ArrayList<StorageObject> successfullyTransferred = new ArrayList<>();
        for (StorageObject so : this.filesToBeProcessed) {
            NoiseSensor ns = sa.getNextNoiseSensorToOffload();
            
            if (ns != null) {
                try {
                    successfullyTransferred.add(so);
                    long actualTime = Timed.getFireCount();
                    this.noOfmigratedFiles++;
                    this.pm.localDisk.requestContentDelivery(so.id, ns.pm.localDisk, new ConsumptionEventAdapter() {
                        
                        @Override
                        public void conComplete() {
                            totalOffloadedFiles++;
                            NoiseSensor.totalTimeOnNetwork += Timed.getFireCount() - actualTime;
                            ns.filesToBeProcessed.add(so);
                            pm.localDisk.deregisterObject(so);
                        }
                    });
                } catch (NetworkException e) {
                    e.printStackTrace();
                }
            }             
        }

        this.filesToBeProcessed.removeAll(successfullyTransferred);
    }

    private RemoteServer findRemoteServer() {
        for (Object o : sa.components) {
            if (o.getClass().equals(RemoteServer.class)) {
                return (RemoteServer) o;
            }
        }
        return null;
    }

    private void sendResultToDatabase(Repository from, Repository to, StorageObject so) {
        this.pm.localDisk.deregisterObject(so);
        so.size = this.app.configuration.get("resFileSize").longValue();
        this.pm.localDisk.registerObject(so);
        long actualTime = Timed.getFireCount();
        try {
            from.requestContentDelivery(so.id, to,
                    new ConsumptionEventAdapter() {
                        
                    @Override
                    public void conComplete() {
                        NoiseSensor.totalTimeOnNetwork += Timed.getFireCount() - actualTime;
                        from.deregisterObject(so);
                        to.deregisterObject(so);
                        StorageObject resFile = to.lookup(app.name);
                        to.deregisterObject(resFile);
                        resFile.size += app.configuration.get("resFileSize").longValue();
                        to.registerObject(resFile);                            
                    }
                    
                });
        } catch (NetworkException e) {
            e.printStackTrace();
        }    
        
    }
    
    private void adjustTemperatureByEnv() {
        final double sun = Sun.getInstance().getSunStrength();

        final double minCpuTemp = this.app.configuration.get("minCpuTemp").doubleValue();
        final double maxCpuTemp = this.app.configuration.get("maxCpuTemp").doubleValue(); 

        // base active cooling 
        double delta = (minCpuTemp - cpuTemp) * 0.02;

        if (this.inside && this.sunExposed) {
            // weak heating
            delta += 0.35 * sun;

        } else if (!this.inside && !this.sunExposed) {
            // medium heating
            delta += 0.75 * sun;

        } else if (!this.inside && this.sunExposed) {
            // strong heating
            delta += 1.5 * sun;
            
        }
        
        // noise
        delta += (SeedSyncer.centralRnd.nextDouble() - 0.5) * 0.1;
        cpuTemp += delta;
        
        if (cpuTemp < minCpuTemp) {
            cpuTemp = minCpuTemp;
        }
        if (cpuTemp > maxCpuTemp) {
            cpuTemp = maxCpuTemp;
        }
    }
    
    private void runSoundClassification() {
       
        if (this.cpuTemp <= this.app.configuration.get("cpuTempTreshold").doubleValue() && this.filesToBeProcessed.size() > 0) {
            StorageObject so = this.filesToBeProcessed.remove(0);

            double delta = (this.app.configuration.get("maxCpuTemp").doubleValue() - cpuTemp) * 0.016;
            cpuTemp += delta;
           
            try {
                this.util.vm.newComputeTask(this.app.configuration.get("lengthOfProcessing").doubleValue() * util.utilisedCpu, 
                        ResourceConsumption.unlimitedProcessing, 
                           new ConsumptionEventAdapter() {
                                            
                            @Override
                            public void conComplete() {
                                totalProcessedFiles++;
                                noOfprocessedFiles++;
                                NoiseSensor.totalTimeOnNetwork += app.configuration.get("lengthOfProcessing").longValue();
                                runSoundClassification();
                                sendResultToDatabase(pm.localDisk, remoteServer.util.vm.getResourceAllocation().getHost().localDisk, so); 
                            }
                        });
            } catch (NetworkException e) {
                e.printStackTrace();
            } 
        } else if (cpuTemp > this.app.configuration.get("cpuTempTreshold").doubleValue() && this.isClassificationRunning) {
            SimLogger.logRun(this.sa.app.getComponentName(this.util.resource.name) + "'classifier was turned off at: "
                + Timed.getFireCount() / 1000.0 / 60.0  + " min. due to large temperature");
            this.isClassificationRunning = false;
            this.sa.noiseSensorsWithClassifier.remove(this);
        }
    }

    private int randomSoundValue() {
        this.prevSoundValue = SeedSyncer.centralRnd.nextInt(
            this.app.configuration.get("maxSoundLevel").intValue() 
            - this.app.configuration.get("minSoundLevel").intValue() + 1) 
            + this.app.configuration.get("minSoundLevel").intValue();
        return prevSoundValue;
    }
    
    private int lazySoundValue() {
        int min = this.app.configuration.get("minSoundLevel").intValue();
        int max = this.app.configuration.get("maxSoundLevel").intValue();

        if (prevSoundValue == -1) {
            prevSoundValue = min + SeedSyncer.centralRnd.nextInt(max - min + 1);
        }
        
        int delta = SeedSyncer.centralRnd.nextInt(7) - 3;
        prevSoundValue += delta;

        if (SeedSyncer.centralRnd.nextDouble() < 0.1) {
            prevSoundValue += SeedSyncer.centralRnd.nextInt(21) - 10;
        }

        prevSoundValue = Math.max(min, Math.min(max, prevSoundValue));

        return prevSoundValue;
    }
}
