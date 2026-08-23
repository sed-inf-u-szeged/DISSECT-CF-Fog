package hu.u_szeged.inf.fog.simulator.agent;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.u_szeged.inf.fog.simulator.agent.util.ResourceAgentCsvExporter;

public class ResourceAgentManager extends Timed {

    private static final ResourceAgentManager instance = new ResourceAgentManager();

    private boolean running;
    private boolean csvLogging;

    private ResourceAgentManager() {
    }

    public static ResourceAgentManager getInstance() {
        return instance;
    }

    public void start(long frequency, boolean csvLogging) {
        if (running) {
            return;
        }

        this.csvLogging = csvLogging;
        this.running = true;

        if (csvLogging) {
            ResourceAgentCsvExporter.getInstance().log();
        }

        subscribe(frequency);
    }

    @Override
    public void tick(long fires) {
        updateHourlyPrices();

        if (csvLogging) {
            ResourceAgentCsvExporter.getInstance().log();
        }
    }

    public void stop() {
        if (!running) {
            return;
        }

        updateHourlyPrices();

        if (csvLogging) {
            ResourceAgentCsvExporter.getInstance().log();
        }

        unsubscribe();
        running = false;
    }

    private void updateHourlyPrices() {
        for (ResourceAgent resourceAgent : ResourceAgent.allResourceAgents.values()) {
            resourceAgent.updateHourlyPrice();
        }
    }
}
