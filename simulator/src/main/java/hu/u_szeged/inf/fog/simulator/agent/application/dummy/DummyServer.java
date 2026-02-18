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

    public static long totalGeneratedFileSize = 0;

    private SwarmAgent swarmAgent;

    Utilisation util;

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

        // TODO: SETTING UP UNSUBSCRIBE!!
    }
    
    @Override
    public void tick(long fires) {
        DummyServer server = (DummyServer) swarmAgent.observedAppComponents.get(SeedSyncer.centralRnd.nextInt(swarmAgent.observedAppComponents.size()));
        if (server != this) {
            String name = util.component.id + "-" + fires;
            StorageObject dummyData = new StorageObject(name,
                    (long) Config.DUMMY_CONFIGURATION.get("resFileSize"), true);
            totalGeneratedFileSize += dummyData.size;

            Repository sourceRepo = util.vm.getResourceAllocation().getHost().localDisk;
            Repository targetRepo = server.util.vm.getResourceAllocation().getHost().localDisk;
            sourceRepo.registerObject(dummyData);
            try {
                util.vm.getResourceAllocation().getHost().localDisk.requestContentDelivery(
                        name, targetRepo, new ConsumptionEventAdapter() {

                            @Override
                            public void conComplete() {
                                sourceRepo.deregisterObject(name);
                                RepoFileManager.mergeFiles(targetRepo, dummyData, "DummyApp-files");

                                try {

                                    server.util.vm.newComputeTask(8_000 * ((dummyData.size / 500.0) + 1),
                                            ResourceConsumption.unlimitedProcessing,
                                            new ConsumptionEventAdapter() {

                                                @Override
                                                public void conComplete() {
                                                    SimLogger.logRun(server.util.component.id + " processed a file (" + dummyData.size + " bytes, "
                                                            + (Timed.getFireCount() - fires) / 1_000D + " sec.) sent by "
                                                            + util.component.id + " at: " + Timed.getFireCount() / (double) ScenarioBase.MINUTE_IN_MILLISECONDS
                                                            + " min.");
                                                }
                                            });
                                } catch (NetworkNode.NetworkException e) {
                                    throw new RuntimeException(e);
                                }
                            }
                        });
            } catch (NetworkNode.NetworkException e) {
                SimLogger.logError("DummyServer data transfer failed: " + e);
            }
        }
    }
}