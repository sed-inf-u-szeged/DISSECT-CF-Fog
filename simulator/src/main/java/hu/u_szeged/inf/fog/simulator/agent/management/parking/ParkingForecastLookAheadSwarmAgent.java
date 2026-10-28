package hu.u_szeged.inf.fog.simulator.agent.management.parking;

import hu.u_szeged.inf.fog.simulator.agent.AgentApplication;
import hu.u_szeged.inf.fog.simulator.agent.application.parking.ParkingSensor;
import hu.u_szeged.inf.fog.simulator.agent.demo.Config;
import hu.u_szeged.inf.fog.simulator.agent.forecast.ForecasterManager;
import hu.u_szeged.inf.fog.simulator.common.util.SimLogger;

import java.util.List;
import java.util.Map;

public class ParkingForecastLookAheadSwarmAgent extends ParkingForecastSwarmAgent {

    public ParkingForecastLookAheadSwarmAgent(AgentApplication app) {
        super(app);
    }

    @Override
    protected void evaluateReconfiguration(long simulationTimeMs) {
        List<Map<String, Double>> forecastSlots;

        try {
            String requestJson = buildForecastRequest(simulationTimeMs);
            String responseJson = ForecasterManager.getInstance().predict(requestJson);
            forecastSlots = parseAllSlotPredictions(responseJson);
            SimLogger.logRun("Parking look-ahead forecast received at " + simulationTimeMs / 60_000.0 + " min for " + forecastSlots.get(0).size() + " sensors.");
        } catch (Exception e) {
            throw new RuntimeException("Parking look-ahead forecast request failed", e);
        }

        if (forecastSlots.size() < 2) {
            throw new IllegalStateException("Look-ahead forecast requires at least 2 forecast slots");
        }

        Map<String, Double> currentSlot = forecastSlots.get(0);
        Map<String, Double> nextSlot = forecastSlots.get(1);

        long blePollingIntervalMs = (long) Config.PARKING_CONFIGURATION.get("gatewayFreq");
        long blePollCount = (orchestrationIntervalMs + blePollingIntervalMs - 1) / blePollingIntervalMs;
        long bleCost = blePollCount * ParkingSensor.ParkingMode.BLE_POLL.batteryCost;
        double forecastThresholdFactor = (double) Config.PARKING_CONFIGURATION.get("forecastThresholdFactor");
        double thresholdCost = bleCost * forecastThresholdFactor;

        for (Object component : observedAppComponents) {
            if (component instanceof ParkingSensor sensor) {
                Double currentPredictedEvents = currentSlot.get(sensor.id);
                Double nextPredictedEvents = nextSlot.get(sensor.id);

                if (currentPredictedEvents == null || nextPredictedEvents == null) {
                    throw new IllegalStateException("Missing look-ahead forecast for sensor " + sensor.id);
                }

                double currentNbiotCost = currentPredictedEvents * ParkingSensor.ParkingMode.NBIOT_PUSH.batteryCost;
                double nextNbiotCost = nextPredictedEvents * ParkingSensor.ParkingMode.NBIOT_PUSH.batteryCost;
                boolean currentSuggestsBle = currentNbiotCost > thresholdCost;
                boolean nextSuggestsBle = nextNbiotCost > thresholdCost;

                if (currentSuggestsBle || (sensor.mode == ParkingSensor.ParkingMode.BLE_POLL && nextSuggestsBle)) {
                    requestSensorReconfiguration(sensor, ParkingSensor.ParkingMode.BLE_POLL);
                } else if (currentNbiotCost < thresholdCost) {
                    requestSensorReconfiguration(sensor, ParkingSensor.ParkingMode.NBIOT_PUSH);
                }
            }
        }
    }
}