package hu.u_szeged.inf.fog.simulator.agent.management.parking;

import hu.u_szeged.inf.fog.simulator.agent.AgentApplication;
import hu.u_szeged.inf.fog.simulator.agent.application.parking.ParkingGateway;
import hu.u_szeged.inf.fog.simulator.agent.application.parking.ParkingSensor;
import hu.u_szeged.inf.fog.simulator.agent.demo.Config;

public class ParkingGroupLevelSwarmAgent extends ParkingSwarmAgent {
    public ParkingGroupLevelSwarmAgent(AgentApplication app) {
        super(app);
    }

    @Override
    protected void evaluateReconfiguration(long simulationTimeMs) {
        long blePollingIntervalMs = (long) Config.PARKING_CONFIGURATION.get("gatewayFreq");
        long blePollCount = (orchestrationIntervalMs + blePollingIntervalMs - 1) / blePollingIntervalMs;
        for (ParkingGateway gateway : ParkingGateway.allParkingGateways) {
            long eventCount = 0;
            for (ParkingSensor sensor : gateway.observedSensors) {
                eventCount += sensor.orchestrationIntervalEventCount;
            }
            long nbiotCost = eventCount * ParkingSensor.ParkingMode.NBIOT_PUSH.batteryCost;
            long bleCost = blePollCount * ParkingSensor.ParkingMode.BLE_POLL.batteryCost * gateway.observedSensors.size();
            if (nbiotCost > bleCost) {
                for (ParkingSensor sensor : gateway.observedSensors) {
                    requestSensorReconfiguration(sensor, ParkingSensor.ParkingMode.BLE_POLL);
                }
            } else if (nbiotCost < bleCost) {
                for (ParkingSensor sensor : gateway.observedSensors) {
                    requestSensorReconfiguration(sensor, ParkingSensor.ParkingMode.NBIOT_PUSH);
                }
            }
        }
    }
}