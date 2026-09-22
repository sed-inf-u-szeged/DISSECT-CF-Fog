package hu.u_szeged.inf.fog.simulator.agent.management.parking;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.u_szeged.inf.fog.simulator.agent.AgentApplication;
import hu.u_szeged.inf.fog.simulator.agent.application.parking.ParkingSensor;
import hu.u_szeged.inf.fog.simulator.agent.demo.Config;
import hu.u_szeged.inf.fog.simulator.agent.management.SwarmAgent;
import hu.u_szeged.inf.fog.simulator.agent.util.ParkingAppCsvExporter;
import hu.u_szeged.inf.fog.simulator.common.util.ScenarioBase;

public class ParkingTimeBasedSwarmAgent extends SwarmAgent {

    ParkingAppCsvExporter parkingAppCsvExporter;

    public ParkingTimeBasedSwarmAgent(AgentApplication app) {
        super(app);

        if ((boolean) Config.PARKING_CONFIGURATION.get("csvLogging")) {
            this.parkingAppCsvExporter = new ParkingAppCsvExporter(this);
        }
    }

    @Override
    public void tick(long fires) {
        if ((boolean) Config.PARKING_CONFIGURATION.get("orchestrationEnabled")) {
            evaluateTimeBasedReconfiguration(Timed.getFireCount());
        }

        if (this.parkingAppCsvExporter != null){
            this.parkingAppCsvExporter.log();
        }
    }

    public void start(long cooldownFreq) {
        subscribe(cooldownFreq);
    }

    private void evaluateTimeBasedReconfiguration(long simulationTimeMs) {
        long timeOfDay = simulationTimeMs % (24 * ScenarioBase.HOUR_IN_MILLISECONDS);

        long eightAm = 8 * ScenarioBase.HOUR_IN_MILLISECONDS;
        long sixPm = 18 * ScenarioBase.HOUR_IN_MILLISECONDS;

        ParkingSensor.ParkingMode requestedMode =
                (timeOfDay >= eightAm && timeOfDay < sixPm)
                        ? ParkingSensor.ParkingMode.BLE_POLL
                        : ParkingSensor.ParkingMode.NBIOT_PUSH;

        for (Object component : observedAppComponents) {
            if (component instanceof ParkingSensor sensor) {
                requestSensorReconfiguration(sensor, requestedMode);
            }
        }
    }

    private void requestSensorReconfiguration(ParkingSensor sensor, ParkingSensor.ParkingMode requestedMode) {
        sensor.pendingMode = sensor.mode != requestedMode ? requestedMode : null;
    }
}
