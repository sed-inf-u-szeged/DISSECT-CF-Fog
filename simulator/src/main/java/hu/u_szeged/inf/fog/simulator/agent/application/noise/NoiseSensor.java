package hu.u_szeged.inf.fog.simulator.agent.application.noise;

import hu.mta.sztaki.lpds.cloud.simulator.DeferredEvent;
import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ConsumptionEventAdapter;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ResourceConsumption;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.io.StorageObject;
import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;
import hu.u_szeged.inf.fog.simulator.agent.Capacity.Utilisation;
import hu.u_szeged.inf.fog.simulator.agent.demo.Config;
import hu.u_szeged.inf.fog.simulator.agent.management.ForecastBasedSwarmAgent;
import hu.u_szeged.inf.fog.simulator.agent.management.GreedyNoiseSwarmAgent;
import hu.u_szeged.inf.fog.simulator.common.util.RepoFileManager;
import hu.u_szeged.inf.fog.simulator.common.util.ScenarioBase;
import hu.u_szeged.inf.fog.simulator.common.util.SimLogger;

import java.util.*;

public class NoiseSensor extends Timed {

    GreedyNoiseSwarmAgent swarmAgent;

    public Utilisation util;

    public boolean inside;

    public boolean sunExposed;

    private RemoteServer remoteServer;

    public int prevSoundValue;

    public double cpuTemperature;

    PriorityQueue<StorageObject> filesToProcess = new PriorityQueue<>(
            Comparator
                    .comparingLong((StorageObject obj) ->
                            RemoteServer.networkTimePerFile.getOrDefault(obj.id, Long.MAX_VALUE)
                    )
                    .thenComparing(obj -> obj.id)
    );

    public int processedFilesLastMinute;

    public static long totalOffloadedFiles;

    public static long totalSoundEventsToProcess;

    public static long totalProcessedFiles;

    public int processedFileCounter;

    public int fileMigrationCounter;

    public NoiseSensor(GreedyNoiseSwarmAgent swarmAgent, Utilisation util, boolean inside, boolean sunExposed) {
        this.swarmAgent = swarmAgent;
        this.util = util;
        this.inside = inside;
        this.sunExposed = sunExposed;
        prevSoundValue = -1;
        this.swarmAgent.observedAppComponents.add(this);

        if(swarmAgent instanceof ForecastBasedSwarmAgent) {
            ((ForecastBasedSwarmAgent) swarmAgent).windowsForPrediction.putIfAbsent(util.component.id, new ArrayDeque<>());
        }

        new DeferredEvent(SeedSyncer.centralRnd.nextInt(20) * 1000L) {

            @Override
            protected void eventAction() {
                remoteServer = findRemoteServer();
                subscribe((long) Config.NOISE_CLASS_CONFIGURATION.get("samplingFreq"));
            }
        };
    }

    @Override
    public void tick(long fires) {
       this.adjustTemperatureByEnv();

        if ((swarmAgent.app.submissionTime + (long) Config.NOISE_CLASS_CONFIGURATION.get("simLength")) > fires) {
            String filename = Timed.getFireCount() + "-" + util.component.id;
            long fileSize = (long) Config.NOISE_CLASS_CONFIGURATION.get("soundFileSize");
            StorageObject so = new StorageObject(filename, fileSize, false);
            RemoteServer.networkTimePerFile.put(filename, Timed.getFireCount());

            PhysicalMachine pm = this.util.vm.getResourceAllocation().getHost();
            pm.localDisk.registerObject(so);

            int soundValue;
            if (Config.NOISE_CLASS_CONFIGURATION.get("samplingStrategy").equals("lazy")){
                soundValue = lazySoundValue();
            } else {
                soundValue = randomSoundValue();
            }
            swarmAgent.totalGeneratedFiles++;

            if (soundValue > (int) Config.NOISE_CLASS_CONFIGURATION.get("soundThreshold")) {
                totalSoundEventsToProcess++;
                this.filesToProcess.add(so);
            } else {
                sendResultToDatabase(pm.localDisk, remoteServer.util.vm.getResourceAllocation().getHost().localDisk, so);
            }
        }
        
        // manage the queue
        if (this.swarmAgent.noiseSensorsWithClassifier.contains(this)) {
            runSoundClassification();
        } else {
            int limit = 0;
            if (swarmAgent.noiseSensorsWithClassifier.size() < 2) {
                limit = (int) (filesToProcess.size() * (double) Config.NOISE_CLASS_CONFIGURATION.get("offloadLimitPerIteration"));
            } else{
                limit = filesToProcess.size();
            }
            offload(limit);
        }  
    }
        
    private void offload() {
        PhysicalMachine pm = this.util.vm.getResourceAllocation().getHost();

        int limit = 0;
        if (swarmAgent.noiseSensorsWithClassifier.size() < 2) {
            limit = (int) (filesToProcess.size() * (double) Config.NOISE_CLASS_CONFIGURATION.get("offloadLimitPerIteration"));
        } else{
            limit = filesToProcess.size();
        }

        for (int i = 0; i < limit; i++) {
            StorageObject so = filesToProcess.poll();
            if (so == null) {
                break;
            }

            NoiseSensor ns = swarmAgent.getNextClassifierForOffloading();
            if (ns == null) {
                filesToProcess.add(so);
                break;
            }

            try {
                this.fileMigrationCounter++;
                pm.localDisk.requestContentDelivery(so.id, ns.util.vm.getResourceAllocation().getHost().localDisk, new ConsumptionEventAdapter() {

                    @Override
                    public void conComplete() {
                        totalOffloadedFiles++;
                        //ns.filesToProcess.addFirst(so);
                        ns.filesToProcess.add(so);
                        pm.localDisk.deregisterObject(so);
                    }
                });
            } catch (NetworkException e) {
                SimLogger.logError("Offloading sound file " + so.id + " failed: " + e);
            }
        }
    }

    private void offload(int limit) {
        if (limit > 0 && !swarmAgent.noiseSensorsWithClassifier.contains(this)) {
            PhysicalMachine pm = this.util.vm.getResourceAllocation().getHost();
            StorageObject so = filesToProcess.poll();
            if (so == null) {
                return;
            }

            NoiseSensor ns = swarmAgent.getNextClassifierForOffloading();
            if (ns == null) {
                //filesToProcess.addFirst(so);
                filesToProcess.add(so);
                return;
            }

            try {
                this.fileMigrationCounter++;
                pm.localDisk.requestContentDelivery(so.id, ns.util.vm.getResourceAllocation().getHost().localDisk, new ConsumptionEventAdapter() {

                    @Override
                    public void conComplete() {
                        totalOffloadedFiles++;
                        //ns.filesToProcess.addFirst(so);
                        ns.filesToProcess.add(so);
                        pm.localDisk.deregisterObject(so);
                        int remainingLimit = limit - 1;
                        offload(remainingLimit);
                    }
                });
            } catch (NetworkException e) {
                SimLogger.logError("Offloading sound file " + so.id + " failed: " + e);
            }
        }

    }

    private RemoteServer findRemoteServer() {
        for (Object o : swarmAgent.observedAppComponents) {
            if (o.getClass().equals(RemoteServer.class)) {
                return (RemoteServer) o;
            }
        }
        return null;
    }

    private void sendResultToDatabase(Repository from, Repository to, StorageObject so) {
        from.deregisterObject(so);
        StorageObject resFile = new StorageObject(so.id, (long) Config.NOISE_CLASS_CONFIGURATION.get("resFileSize"), false);
        from.registerObject(resFile);

        try {
            from.requestContentDelivery(resFile.id, to,
                    new ConsumptionEventAdapter() {
                        
                    @Override
                    public void conComplete() {
                        from.deregisterObject(resFile);
                        to.deregisterObject(resFile);
                        RepoFileManager.mergeFiles(to, resFile, swarmAgent.app.name);
                        swarmAgent.filesSentToDatabase++;
                        long latency =  Timed.getFireCount() - RemoteServer.networkTimePerFile.remove(so.id);
                        remoteServer.latencies.add(latency);
                        RemoteServer.totalEndToEndLatency += latency;
                    }
                    
                });
        } catch (NetworkException e) {
            SimLogger.logError("Sending result file " + resFile.id + " to database failed: " + e);
        }    
        
    }
    
    private void adjustTemperatureByEnv() {
        final double sun = Sun.getInstance().getSunStrength();

        final double minCpuTemp = (double) Config.NOISE_CLASS_CONFIGURATION.get("minCpuTemp");
        final double maxCpuTemp = (double) Config.NOISE_CLASS_CONFIGURATION.get("maxCpuTemp");

        // base active cooling 
        double delta = (minCpuTemp - cpuTemperature) * 0.02;

        // heating based on the conditions
        if (this.inside) {
            if (this.sunExposed) {
                delta += 0.2 * sun;
            }
        } else {
            if (this.sunExposed) {
                delta += 0.8 * sun; // 1.5
            } else {
                delta += 0.4 * sun; // 0.75
            }
        }

        // noise
        delta += (SeedSyncer.centralRnd.nextDouble() - 0.5) * 0.1;
        cpuTemperature += delta;
        
        if (cpuTemperature < minCpuTemp) {
            cpuTemperature = minCpuTemp;
        }
        if (cpuTemperature > maxCpuTemp) {
            cpuTemperature = maxCpuTemp;
        }
    }
    
    private void runSoundClassification() {
       
        if (this.cpuTemperature < (double) Config.NOISE_CLASS_CONFIGURATION.get("cpuTempTreshold") && !this.filesToProcess.isEmpty()) {
            StorageObject so = filesToProcess.poll();

            double delta = ((double) Config.NOISE_CLASS_CONFIGURATION.get("maxCpuTemp") - cpuTemperature) * 0.016;
            cpuTemperature += delta;
           
            try {
                this.util.vm.newComputeTask((double) Config.NOISE_CLASS_CONFIGURATION.get("lengthOfProcessing") * util.utilisedCpu,
                        ResourceConsumption.unlimitedProcessing, 
                           new ConsumptionEventAdapter() {
                                            
                            @Override
                            public void conComplete() {
                                processedFileCounter++;
                                totalProcessedFiles++;
                                processedFilesLastMinute++;
                                runSoundClassification();
                                sendResultToDatabase(util.vm.getResourceAllocation().getHost().localDisk,
                                        remoteServer.util.vm.getResourceAllocation().getHost().localDisk, so);
                            }
                        });
            } catch (NetworkException e) {
                SimLogger.logError("Sound classification of file " + so.id + " failed: " + e);
            } 
        } else if (cpuTemperature >= (double) Config.NOISE_CLASS_CONFIGURATION.get("cpuTempTreshold") &&
                this.swarmAgent.noiseSensorsWithClassifier.contains(this)) {
            this.swarmAgent.noiseSensorsWithClassifier.remove(this);
            this.swarmAgent.decisionType.compute("scale-down-temperature", (k, v) -> v + 1);
            SimLogger.logRun(util.component.id + "'classifier was turned off at: "
                    + Timed.getFireCount() / (double) ScenarioBase.MINUTE_IN_MILLISECONDS + " min. due to high temperature");
        }
    }

    private int randomSoundValue() {
        this.prevSoundValue = SeedSyncer.centralRnd.nextInt(
                (int) Config.NOISE_CLASS_CONFIGURATION.get("maxSoundLevel")
            - (int) Config.NOISE_CLASS_CONFIGURATION.get("minSoundLevel") + 1)
            + (int) Config.NOISE_CLASS_CONFIGURATION.get("minSoundLevel");
        return prevSoundValue;
    }
    
    private int lazySoundValue() {
        int min = (int) Config.NOISE_CLASS_CONFIGURATION.get("minSoundLevel");
        int max = (int) Config.NOISE_CLASS_CONFIGURATION.get("maxSoundLevel");

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

    public void stop() {
        unsubscribe();
    }
}
