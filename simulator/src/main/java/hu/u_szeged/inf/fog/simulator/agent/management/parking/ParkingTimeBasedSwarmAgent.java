package hu.u_szeged.inf.fog.simulator.agent.management.parking;

import hu.u_szeged.inf.fog.simulator.agent.AgentApplication;
import hu.u_szeged.inf.fog.simulator.agent.application.parking.ParkingSensor;
import hu.u_szeged.inf.fog.simulator.common.util.ScenarioBase;

public class ParkingTimeBasedSwarmAgent extends ParkingSwarmAgent {
    private final long startOffsetMs;

    public ParkingTimeBasedSwarmAgent(AgentApplication app) {
        this(app, 0L);
    }

    public ParkingTimeBasedSwarmAgent(AgentApplication app, long startOffsetMs) {
        super(app);
        this.startOffsetMs = startOffsetMs;
    }

    @Override
    protected void evaluateReconfiguration(long simulationTimeMs) {
        long timeOfDay = (startOffsetMs + simulationTimeMs) % (24 * ScenarioBase.HOUR_IN_MILLISECONDS);
        long eightAm = 8 * ScenarioBase.HOUR_IN_MILLISECONDS;
        long sixPm = 18 * ScenarioBase.HOUR_IN_MILLISECONDS;
        ParkingSensor.ParkingMode requestedMode = timeOfDay >= eightAm && timeOfDay < sixPm ? ParkingSensor.ParkingMode.BLE_POLL : ParkingSensor.ParkingMode.NBIOT_PUSH;
        for (Object component : observedAppComponents) {
            if (component instanceof ParkingSensor sensor) {
                requestSensorReconfiguration(sensor, requestedMode);
            }
        }
    }
}