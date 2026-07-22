package hu.u_szeged.inf.fog.simulator.agent.management.dummy;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.u_szeged.inf.fog.simulator.agent.AgentApplication;
import hu.u_szeged.inf.fog.simulator.agent.Capacity;
import hu.u_szeged.inf.fog.simulator.agent.ResourceAgent;
import hu.u_szeged.inf.fog.simulator.agent.application.dummy.DummyServer;
import hu.u_szeged.inf.fog.simulator.agent.demo.Config;
import hu.u_szeged.inf.fog.simulator.agent.management.SwarmAgent;
import hu.u_szeged.inf.fog.simulator.agent.util.ResourceAgentCsvExporter;
import hu.u_szeged.inf.fog.simulator.common.util.EnergyDataCollector;
import hu.u_szeged.inf.fog.simulator.common.util.ScenarioBase;
import hu.u_szeged.inf.fog.simulator.common.util.SimLogger;

import java.util.List;

public class DummySwarmAgent extends SwarmAgent {

    public DummySwarmAgent(AgentApplication app, long freq) {
        super(app);
        subscribe(freq);
    }

    @Override
    public void tick(long fires) {

        if ((boolean) Config.DUMMY_CONFIGURATION.get("csvLogging")) {
            ResourceAgentCsvExporter.getInstance().log();
        }
        for (ResourceAgent agent : ResourceAgent.allResourceAgents.values()) {
            agent.updateHourlyPrice();
        }
        shutdown(fires);
    }

    @Override
    public void shutdown(long fires) {
        if ((app.submissionTime + (long) Config.DUMMY_CONFIGURATION.get("simLength")) < fires) {

            for(Object component : this.observedAppComponents){
                if(component instanceof DummyServer ds){
                    ds.stop();
                    ds.util.endTime = fires;
                }
            }
            this.unsubscribe();
            app.terminationTime = fires;
            SimLogger.logRun(app.name + " application finished at: "
                    + Timed.getFireCount() / (double) ScenarioBase.MINUTE_IN_MILLISECONDS + " min.");
            applicationShutdownCounter++;

            for (ResourceAgent agent : ResourceAgent.allResourceAgents.values()) {
                for (Capacity capacity : agent.capacities.values()) {
                    for (AgentApplication.Component component : this.app.components) {
                        capacity.releaseAllocatedCapacityForShutdown(component);
                    }
                }
            }

            if (applicationShutdownCounter + ResourceAgent.failedDeployments == ((List<Integer>) Config.NOISE_CLASS_CONFIGURATION.get("submissionDelay")).size()) {
                for (EnergyDataCollector edc : EnergyDataCollector.allEnergyCollectors.values()) {
                    edc.stop();
                }
            }

        }
    }
}
