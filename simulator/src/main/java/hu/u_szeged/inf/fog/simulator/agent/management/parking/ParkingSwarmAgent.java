package hu.u_szeged.inf.fog.simulator.agent.management.parking;

import hu.u_szeged.inf.fog.simulator.agent.AgentApplication;
import hu.u_szeged.inf.fog.simulator.agent.application.parking.ParkingSensor;
import hu.u_szeged.inf.fog.simulator.agent.application.parking.PlatformService;
import hu.u_szeged.inf.fog.simulator.agent.demo.Config;
import hu.u_szeged.inf.fog.simulator.agent.management.SwarmAgent;
import hu.u_szeged.inf.fog.simulator.agent.util.NoiseAppCsvExporter;
import hu.u_szeged.inf.fog.simulator.agent.util.ParkingAppCsvExporter;

public class ParkingSwarmAgent extends SwarmAgent {

    ParkingAppCsvExporter parkingAppCsvExporter;

    public ParkingSwarmAgent(AgentApplication app) {
        super(app);

        if ((boolean) Config.PARKING_CONFIGURATION.get("csvLogging")) {
            this.parkingAppCsvExporter = new ParkingAppCsvExporter(this);
        }
    }

    @Override
    public void tick(long fires) {
        if (this.parkingAppCsvExporter != null){
            this.parkingAppCsvExporter.log();
        }
    }

    public void start(long cooldownFreq) {
        subscribe(cooldownFreq);
    }
}
