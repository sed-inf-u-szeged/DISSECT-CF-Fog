package hu.u_szeged.inf.fog.simulator.agent.management.parking;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.u_szeged.inf.fog.simulator.agent.AgentApplication;
import hu.u_szeged.inf.fog.simulator.agent.application.parking.ParkingSensor;
import hu.u_szeged.inf.fog.simulator.agent.demo.Config;
import hu.u_szeged.inf.fog.simulator.agent.management.SwarmAgent;
import hu.u_szeged.inf.fog.simulator.agent.util.ParkingAppCsvExporter;

public abstract class ParkingSwarmAgent extends SwarmAgent {

    private final ParkingAppCsvExporter parkingAppCsvExporter;

    protected long orchestrationIntervalMs;

    protected ParkingSwarmAgent(AgentApplication app) {
        super(app);
        this.parkingAppCsvExporter = (boolean) Config.PARKING_CONFIGURATION.get("csvLogging") ? new ParkingAppCsvExporter(this) : null;
    }

    @Override
    public final void tick(long fires) {
        if ((boolean) Config.PARKING_CONFIGURATION.get("orchestrationEnabled")) {
            evaluateReconfiguration(Timed.getFireCount());

            for (Object component : observedAppComponents) {
                if (component instanceof ParkingSensor sensor) {
                    sensor.orchestrationIntervalEventCount = 0;
                }
            }
        }
        if (parkingAppCsvExporter != null) {
            parkingAppCsvExporter.log();
        }
    }

    public void start(long orchestrationIntervalMs) {
        this.orchestrationIntervalMs = orchestrationIntervalMs;
        subscribe(orchestrationIntervalMs);
    }

    protected abstract void evaluateReconfiguration(long simulationTimeMs);

    protected void requestSensorReconfiguration(ParkingSensor sensor, ParkingSensor.ParkingMode requestedMode) {
        sensor.pendingMode = sensor.mode != requestedMode ? requestedMode : null;
    }
}