package hu.u_szeged.inf.fog.simulator.agent.management.parking;

import hu.u_szeged.inf.fog.simulator.agent.AgentApplication;
import hu.u_szeged.inf.fog.simulator.agent.application.parking.ParkingSensor;
import hu.u_szeged.inf.fog.simulator.agent.demo.Config;
import hu.u_szeged.inf.fog.simulator.agent.forecast.ForecasterManager;
import hu.u_szeged.inf.fog.simulator.common.util.SimLogger;

import java.util.List;
import java.util.Map;

public class ParkingForecast2hSwarmAgent extends ParkingForecastSwarmAgent {

    private List<Map<String, Double>> cachedForecastSlots;
    private int currentSlotIndex = 0;

    public ParkingForecast2hSwarmAgent(AgentApplication app) {
        super(app);
    }

    @Override
    protected void evaluateReconfiguration(long simulationTimeMs) {
        if (cachedForecastSlots == null || currentSlotIndex >= cachedForecastSlots.size()) {
            try {
                String requestJson = buildForecastRequest(simulationTimeMs);
                String responseJson = ForecasterManager.getInstance().predict(requestJson);
                cachedForecastSlots = parseAllSlotPredictions(responseJson);
                currentSlotIndex = 0;

                if (cachedForecastSlots.size() != 8) {
                    throw new IllegalStateException("Expected 8 forecast slots, got " + cachedForecastSlots.size());
                }

                SimLogger.logRun("Parking 2h forecast received at " + simulationTimeMs / 60_000.0 + " min.");
            } catch (Exception e) {
                throw new RuntimeException("Parking 2h forecast request failed", e);
            }
        }

        Map<String, Double> predictedEvents = cachedForecastSlots.get(currentSlotIndex);
        currentSlotIndex++;

        long blePollingIntervalMs = (long) Config.PARKING_CONFIGURATION.get("gatewayFreq");
        long blePollCount = (orchestrationIntervalMs + blePollingIntervalMs - 1) / blePollingIntervalMs;
        long bleCost = blePollCount * ParkingSensor.ParkingMode.BLE_POLL.batteryCost;

        for (Object component : observedAppComponents) {
            if (component instanceof ParkingSensor sensor) {
                Double predictedEventCount = predictedEvents.get(sensor.id);

                if (predictedEventCount == null) {
                    throw new IllegalStateException("Missing forecast for sensor " + sensor.id);
                }

                double forecastThresholdFactor = (double) Config.PARKING_CONFIGURATION.get("forecastThresholdFactor");
                double nbiotCost = predictedEventCount * ParkingSensor.ParkingMode.NBIOT_PUSH.batteryCost;

                if (nbiotCost > bleCost * forecastThresholdFactor) {
                    requestSensorReconfiguration(sensor, ParkingSensor.ParkingMode.BLE_POLL);
                } else if (nbiotCost < bleCost * forecastThresholdFactor) {
                    requestSensorReconfiguration(sensor, ParkingSensor.ParkingMode.NBIOT_PUSH);
                }
            }
        }
    }
}