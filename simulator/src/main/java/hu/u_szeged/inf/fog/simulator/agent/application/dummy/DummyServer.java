package hu.u_szeged.inf.fog.simulator.agent.application.dummy;

import hu.mta.sztaki.lpds.cloud.simulator.DeferredEvent;
import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ConsumptionEventAdapter;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ResourceConsumption;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.io.StorageObject;
import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;
import hu.u_szeged.inf.fog.simulator.agent.Capacity.Utilisation;
import hu.u_szeged.inf.fog.simulator.agent.demo.Config;
import hu.u_szeged.inf.fog.simulator.agent.management.SwarmAgent;
import hu.u_szeged.inf.fog.simulator.common.util.RepoFileManager;
import hu.u_szeged.inf.fog.simulator.common.util.ScenarioBase;
import hu.u_szeged.inf.fog.simulator.common.util.SimLogger;

public class DummyServer extends Timed  {

    private SwarmAgent swarmAgent;

    public Utilisation util;

    public DummyServer(SwarmAgent swarmAgent, Utilisation util) {
        this.swarmAgent = swarmAgent;
        this.util = util;
        swarmAgent.observedAppComponents.add(this);
        new DeferredEvent(SeedSyncer.centralRnd.nextInt(20) * 1000L){

            @Override
            protected void eventAction() {
                subscribe((long) Config.DUMMY_CONFIGURATION.get("samplingFreq")+ SeedSyncer.centralRnd.nextInt(-5,11));
            }
        };
    }

    public void stop() {
        unsubscribe();
    }
    
    @Override
    public void tick(long fires) {
        DummyServer server = (DummyServer) swarmAgent.observedAppComponents.get(SeedSyncer.centralRnd.nextInt(swarmAgent.observedAppComponents.size()));
        if (server != this) {
            String name = util.component.id + "-" + fires;
            StorageObject dummyData = new StorageObject(name,
                    (long) Config.DUMMY_CONFIGURATION.get("resFileSize"), true);
            swarmAgent.totalGeneratedFiles++;
            swarmAgent.totalGeneratedDataSize += dummyData.size;

            Repository sourceRepo = util.vm.getResourceAllocation().getHost().localDisk;
            Repository targetRepo = server.util.vm.getResourceAllocation().getHost().localDisk;
            sourceRepo.registerObject(dummyData);
            try {
                if (sourceRepo == targetRepo) {
                    Integer intraNodeLatency = sourceRepo.getLatencies().get(targetRepo.getName());

                    if (intraNodeLatency == null) {
                        throw new IllegalStateException("No latency is configured for repository: " + targetRepo.getName());
                    }

                    long intraNodeBandwidth = Math.min(sourceRepo.getOutputbw(), targetRepo.getInputbw());

                    long transmissionTime = (long) Math.ceil((double) dummyData.size / intraNodeBandwidth);

                    long transferTime = intraNodeLatency + transmissionTime;

                    new DeferredEvent(transferTime) {

                        @Override
                        protected void eventAction() {
                            processReceivedFile(server, sourceRepo, targetRepo, dummyData, fires);
                        }
                    };
                } else {
                    sourceRepo.requestContentDelivery(
                            name,
                            targetRepo,
                            new ConsumptionEventAdapter() {

                                @Override
                                public void conComplete() {
                                    processReceivedFile(server, sourceRepo, targetRepo, dummyData, fires);
                                }
                            });
                }
            } catch (NetworkNode.NetworkException e) {
                SimLogger.logError("DummyServer data transfer failed: " + e);
            }
        }
    }

    private void processReceivedFile(DummyServer server, Repository sourceRepo, Repository targetRepo, StorageObject dummyData, long creationTime) {
        long deliveryTime = Timed.getFireCount() - creationTime;
        swarmAgent.totalFileDeliveryTime += deliveryTime;

        sourceRepo.deregisterObject(dummyData.id);
        RepoFileManager.mergeFiles(targetRepo, dummyData, "DummyApp-files");

        double computeTaskBase = (double) Config.DUMMY_CONFIGURATION.get("computeTaskBase");
        double computeTaskPerByte = (double) Config.DUMMY_CONFIGURATION.get("computeTaskPerByte");
        double computeTaskLength = computeTaskBase + computeTaskPerByte * dummyData.size;

        try {
            server.util.vm.newComputeTask(
                    computeTaskLength,
                    ResourceConsumption.unlimitedProcessing,
                    new ConsumptionEventAdapter() {

                        @Override
                        public void conComplete() {
                            SimLogger.logRun(server.util.component.id + " processed a file (" + dummyData.size
                                    + " bytes, " + (Timed.getFireCount() - creationTime) / 1_000D
                                    + " sec.) sent by " + util.component.id + " at: "
                                    + Timed.getFireCount() / (double) ScenarioBase.MINUTE_IN_MILLISECONDS + " min.");
                        }
                    });
        } catch (NetworkNode.NetworkException exception) {
            SimLogger.logError("DummyServer compute task failed: " + exception);
        }
    }
}