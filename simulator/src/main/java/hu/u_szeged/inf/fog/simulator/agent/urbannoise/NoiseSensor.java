package hu.u_szeged.inf.fog.simulator.agent.urbannoise;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ConsumptionEventAdapter;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ResourceConsumption;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;
import hu.mta.sztaki.lpds.cloud.simulator.io.StorageObject;
import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;
import hu.u_szeged.inf.fog.simulator.agent.AgentApplication;
import hu.u_szeged.inf.fog.simulator.agent.Capacity.Utilisation;
import hu.u_szeged.inf.fog.simulator.agent.SwarmAgent;
import hu.u_szeged.inf.fog.simulator.util.SimLogger;
import java.util.ArrayList;

public class NoiseSensor extends Timed {
    
    public static long generatedFileSize;
    
    public static long generatedFiles;

    public static long offloadedFiles;
    
    public static long soundEventsReqProcessing;
    
    public static long processedFiles;
    
    public static long timeOnNetwork;
    
    SwarmAgent sa;
    
    public Utilisation util;
    
    public PhysicalMachine pm;
    
    int threshold;
    
    AgentApplication app;
    
    public boolean isClassificationRunning;
    
    public double cpuTemp;
    
    public ArrayList<StorageObject> filesToBeProcessed;
    
    public boolean inside;
    
    public boolean noDirectSun;
    
    
    public NoiseSensor(AgentApplication app, SwarmAgent sa, 
            Utilisation util, long freq, int threshold, boolean inside, boolean noDirectSun) {
        this.sa = sa;
        this.util = util;
        this.pm = util.vm.getResourceAllocation().getHost();
        this.threshold = threshold;
        this.app = app;
        this.cpuTemp = 70.0;
        this.filesToBeProcessed = new ArrayList<>();
        this.inside = inside;
        this.noDirectSun = noDirectSun;
        subscribe(freq);
        sa.registerComponent(this);        
    }

    @Override
    public void tick(long fires) {
        this.adjustTemperatureByEnv();
        
        String filename = Timed.getFireCount() + "-" + app.getComponentName(util.resource.name);
        StorageObject so = new StorageObject(filename, 655360, false);
        this.pm.localDisk.registerObject(so);
        generatedFileSize += 655_360;
        generatedFiles++;
        
        int value = randomSoundValue();
        if (value > threshold) {
            soundEventsReqProcessing++;
            this.filesToBeProcessed.add(so);
        } else {
            RemoteServer rs = findRemoteServer();
            this.pm.localDisk.deregisterObject(so);
            so.size = 100;
            this.pm.localDisk.registerObject(so);
            long actualTime = Timed.getFireCount();
            try {
                this.pm.localDisk.requestContentDelivery(filename, rs.util.vm.getResourceAllocation().getHost().localDisk,
                        new ConsumptionEventAdapter() {
                            
                        @Override
                        public void conComplete() {
                            NoiseSensor.timeOnNetwork += Timed.getFireCount() - actualTime;
                            pm.localDisk.deregisterObject(filename);
                        }
                        
                    });
            } catch (NetworkException e) {
                e.printStackTrace();
            }     
        }
        
        // manage the queue
        if (this.isClassificationRunning) {
            startSoundClassification();
        } else {
            offload();
        }  
    }
    
    private void adjustTemperatureByEnv() {
        final double sun = Sun.getInstance().getSunStrength(); 
        final double heatOutside = 0.03;
        final double heatInside  = 0.02;
        final double heatShade   = 0.01;
        final double nightCool   = 0.07;  
        final double noise       = 0.12; 
        final double heatingProbability = 0.9;

        double delta = 0;

        if (sun > 0.01) { 
            if (SeedSyncer.centralRnd.nextDouble() < heatingProbability) {
                if (this.noDirectSun) {
                    delta = heatShade * sun * sun;
                } else if (this.inside) {
                    delta = heatInside * sun * sun;
                } else {
                    delta = heatOutside * sun * sun;
                }
            } else {
                delta = 0.0; 
            }
        } else {
            delta = -nightCool; 
        }

        delta += (SeedSyncer.centralRnd.nextDouble() - 0.5) * noise;

        this.cpuTemp += delta * 0.5;

        if (this.cpuTemp < 70.0) {
            this.cpuTemp = 70.0;
        }
        if (this.cpuTemp > 100.0) {
            this.cpuTemp = 100.0;
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
                    this.pm.localDisk.requestContentDelivery(so.id, ns.pm.localDisk, new ConsumptionEventAdapter() {
                        
                        @Override
                        public void conComplete() {
                            offloadedFiles++;
                            NoiseSensor.timeOnNetwork += Timed.getFireCount() - actualTime;
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

    private void startSoundClassification() {
       
        if (this.cpuTemp <= 95.0 && this.filesToBeProcessed.size() > 0) {
            StorageObject so = this.filesToBeProcessed.remove(0);
            this.cpuTemp += 0.005;
           
            try {
                this.util.vm.newComputeTask(1_700 * util.utilisedCpu, ResourceConsumption.unlimitedProcessing, 
                           new ConsumptionEventAdapter() {
                                            
                            @Override
                            public void conComplete() {
                                startSoundClassification();
                                cpuTemp += 0.005;
                                RemoteServer rs = findRemoteServer();
                                pm.localDisk.deregisterObject(so);
                                so.size = 100;
                                pm.localDisk.registerObject(so);
                                long actualTime = Timed.getFireCount();
                                try {
                                    pm.localDisk.requestContentDelivery(so.id, rs.util.vm.getResourceAllocation().getHost().localDisk,
                                            new ConsumptionEventAdapter() {
                                                
                                            @Override
                                            public void conComplete() {
                                                processedFiles++;
                                                NoiseSensor.timeOnNetwork += Timed.getFireCount() - actualTime;
                                                pm.localDisk.deregisterObject(so.id);
                                                
                                                
                                            }
                                            
                                        });
                                        
                                } catch (NetworkException e) {
                                    e.printStackTrace();
                                }   
                            }
                        });
            } catch (NetworkException e) {
                e.printStackTrace();
            } 
        } else if (cpuTemp > 95.0 && this.isClassificationRunning) {
            SimLogger.logRun(this.sa.app.getComponentName(this.util.resource.name) + "'classifier was turned off at: "
                + Timed.getFireCount() / 1000.0 / 60.0  + " min. due to large temperature");
            this.isClassificationRunning = false;
            this.sa.noiseSensorsWithClassifier.remove(this);
        }
    }

    private int randomSoundValue() {
        return SeedSyncer.centralRnd.nextInt(130 - 30 + 1) + 30;
    }
    
    private int logNormalSoundValue() {
        double mean = 2.0;
        double stdDev = 0.5;

        double logNormal = Math.exp(mean + stdDev * SeedSyncer.centralRnd.nextGaussian());

        double scaled = Math.min(130, Math.max(30, logNormal));

        return (int) scaled;
    }
    
}
