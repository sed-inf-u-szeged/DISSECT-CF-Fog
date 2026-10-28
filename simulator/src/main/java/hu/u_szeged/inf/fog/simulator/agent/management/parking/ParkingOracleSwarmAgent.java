package hu.u_szeged.inf.fog.simulator.agent.management.parking;

import hu.u_szeged.inf.fog.simulator.agent.AgentApplication;
import hu.u_szeged.inf.fog.simulator.agent.application.parking.ParkingSensor;
import hu.u_szeged.inf.fog.simulator.agent.demo.Config;

public class ParkingOracleSwarmAgent extends ParkingSwarmAgent {

    public ParkingOracleSwarmAgent(AgentApplication app) {
        super(app);
    }

    @Override
    protected void evaluateReconfiguration(long simulationTimeMs) {
        long intervalEndTimeMs = simulationTimeMs + orchestrationIntervalMs;
        long blePollingIntervalMs = (long) Config.PARKING_CONFIGURATION.get("gatewayFreq");
        long blePollCount = (orchestrationIntervalMs + blePollingIntervalMs - 1) / blePollingIntervalMs;
        long bleCost = blePollCount * ParkingSensor.ParkingMode.BLE_POLL.batteryCost;

        for (Object component : observedAppComponents) {
            if (component instanceof ParkingSensor sensor) {
                int actualEventCount = sensor.getTraceEventCountBetween(simulationTimeMs, intervalEndTimeMs);
                double nbiotCost = actualEventCount * ParkingSensor.ParkingMode.NBIOT_PUSH.batteryCost;

                if (nbiotCost > bleCost) {
                    requestSensorReconfiguration(sensor, ParkingSensor.ParkingMode.BLE_POLL);
                } else if (nbiotCost < bleCost) {
                    requestSensorReconfiguration(sensor, ParkingSensor.ParkingMode.NBIOT_PUSH);
                }
            }
        }
    }
}