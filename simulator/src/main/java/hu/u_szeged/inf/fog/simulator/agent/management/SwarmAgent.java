package hu.u_szeged.inf.fog.simulator.agent.management;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.u_szeged.inf.fog.simulator.agent.AgentApplication;
import hu.u_szeged.inf.fog.simulator.agent.Capacity;
import hu.u_szeged.inf.fog.simulator.agent.ResourceAgent;
import hu.u_szeged.inf.fog.simulator.agent.application.dummy.DummyServer;
import hu.u_szeged.inf.fog.simulator.agent.demo.Config;
import hu.u_szeged.inf.fog.simulator.common.util.EnergyDataCollector;
import hu.u_szeged.inf.fog.simulator.common.util.ScenarioBase;
import hu.u_szeged.inf.fog.simulator.common.util.SimLogger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SwarmAgent extends Timed {

    public static Set<SwarmAgent> allSwarmAgents = new HashSet<>();

    public List<Object> observedAppComponents = new ArrayList<>();

    public AgentApplication app;

    public long totalGeneratedFiles;

    //could greedy one inherit this from here? would it work?
    private static int shutdownCounter;

    public SwarmAgent(AgentApplication app) {
        //subscribe(1 * 60 * 1000L); // every one hour!
        subscribe(10 * 1000L); // every ten minutes! (more events, but at least we can shut agent application down in intervals of 10 minutes)

        this.app = app;
        allSwarmAgents.add(this);
    }

    //maybe make dummy Swarm Agent child class?
    public void shutdown(long fires) {
        // Default runtime
        long targetRuntime = (long) Config.DUMMY_CONFIGURATION.get("appSimLength");

        // If app has a runtime setting set, it overrides the default
        if (app.runtimeInMinutes != -1) {
            targetRuntime = app.runtimeInMinutes * ScenarioBase.MINUTE_IN_MILLISECONDS;
        }

        // It should be stopped at deploymentFinished + how long it should be running; if fire >= that time, shut it down
        if ((app.deploymentAlgorithmFinishedTime + targetRuntime) <= fires) {
            SimLogger.logRun("SwarmAgent shutdown started...");
            //total generated files?? (Not used in dummy scenario)
            //if(totalGeneratedFiles == this.filesSentToDatabase){
                for(Object component : this.observedAppComponents){
                    if(component instanceof DummyServer ds){
                        SimLogger.logRun("Shutting down DummyServer...");
                        ds.shutdown();
                    }
                }

                this.unsubscribe();

                app.terminationTime = fires;

                shutdownCounter++;

                for (ResourceAgent agent : ResourceAgent.allResourceAgents.values()) {
                    for (Capacity capacity : agent.capacities.values()) {
                        for (AgentApplication.Component component : this.app.components) {
                            capacity.releaseAllocatedCapacityForShutdown(component);
                        }
                    }
                }

                SimLogger.logRun(app.name + " application finished at: "
                        + Timed.getFireCount() / (double) ScenarioBase.MINUTE_IN_MILLISECONDS + " min.");
            //}

            // If all apps have been shut down, also stop the energy data collectors (if exists; just keep it for safety to avoid infinite events)
            if (shutdownCounter == ((List<Integer>) Config.DUMMY_CONFIGURATION.get("submissionDelay")).size()){
                for (EnergyDataCollector edc : EnergyDataCollector.allEnergyCollectors.values()) {
                    edc.stop();
                }
            }

            //System.out.println(SwarmAgent.totalGeneratedFiles == GreedyNoiseSwarmAgent.filesSentToDatabase);
            //System.out.println(shutdownCounter + " " + ((List<Integer>) Config.NOISE_CLASS_ONFIGURATION.get("submissionDelay")).size());
        }
    }

    @Override
    public void tick(long fires) {
        shutdown(fires);
    }
}
