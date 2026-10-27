package hu.u_szeged.inf.fog.simulator.agent.management.parking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import hu.u_szeged.inf.fog.simulator.agent.AgentApplication;
import hu.u_szeged.inf.fog.simulator.agent.application.parking.ParkingSensor;
import hu.u_szeged.inf.fog.simulator.agent.demo.Config;
import hu.u_szeged.inf.fog.simulator.agent.forecast.ForecasterManager;
import hu.u_szeged.inf.fog.simulator.common.util.SimLogger;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

public class ParkingForecastSwarmAgent extends ParkingSwarmAgent {

    public ParkingForecastSwarmAgent(AgentApplication app) {
        super(app);
    }

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void evaluateReconfiguration(long simulationTimeMs) {
        Map<String, Double> predictedEvents;

        try {
            String requestJson = buildForecastRequest(simulationTimeMs);
            String responseJson = ForecasterManager.getInstance().predict(requestJson);
            predictedEvents = parseFirstSlotPredictions(responseJson);
            SimLogger.logRun("Parking forecast received at " + simulationTimeMs / 60_000.0 + " min for " + predictedEvents.size() + " sensors.");
        } catch (Exception e) {
            throw new RuntimeException("Parking forecast request failed", e);
        }

        long blePollingIntervalMs = (long) Config.PARKING_CONFIGURATION.get("gatewayFreq");
        long blePollCount = (orchestrationIntervalMs + blePollingIntervalMs - 1) / blePollingIntervalMs;
        long bleCost = blePollCount * ParkingSensor.ParkingMode.BLE_POLL.batteryCost;

        for (Object component : observedAppComponents) {
            if (component instanceof ParkingSensor sensor) {
                Double predictedEventCount = predictedEvents.get(sensor.id);

                if (predictedEventCount == null) {
                    throw new IllegalStateException("Missing forecast for sensor " + sensor.id);
                }

                double nbiotCost = predictedEventCount * ParkingSensor.ParkingMode.NBIOT_PUSH.batteryCost;

                if (nbiotCost > bleCost) {
                    requestSensorReconfiguration(sensor, ParkingSensor.ParkingMode.BLE_POLL);
                } else if (nbiotCost < bleCost) {
                    requestSensorReconfiguration(sensor, ParkingSensor.ParkingMode.NBIOT_PUSH);
                }
            }
        }
    }

    private Map<String, Double> parseFirstSlotPredictions(String responseJson) throws Exception {
        JsonNode root = objectMapper.readTree(responseJson);
        JsonNode forecast = root.get("forecast");

        if (forecast == null || !forecast.isArray()) {
            throw new IllegalStateException("Invalid forecast response: missing forecast array");
        }

        Map<String, Double> predictedEvents = new HashMap<>();

        for (JsonNode prediction : forecast) {
            String sensorId = prediction.get("id").asText();
            double events = prediction.get("events").asDouble();
            predictedEvents.putIfAbsent(sensorId, events);
        }

        return predictedEvents;
    }

    private String buildForecastRequest(long simulationTimeMs) {
        long windowStartTimeMs = simulationTimeMs - (long) Config.PARKING_CONFIGURATION.get("forecastHistoryWindowMs");
        OffsetDateTime simulationStartTime = OffsetDateTime.parse((String) Config.PARKING_CONFIGURATION.get("traceStartTime"));

        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode state = root.putObject("state");
        state.put("time", simulationStartTime.plus(Duration.ofMillis(windowStartTimeMs)).toString());

        ArrayNode stateSensors = state.putArray("sensors");
        ArrayNode messages = root.putArray("messages");

        for (Object component : observedAppComponents) {
            if (!(component instanceof ParkingSensor sensor)) {
                continue;
            }

            ObjectNode stateSensor = stateSensors.addObject();
            stateSensor.put("id", sensor.id);
            stateSensor.putNull("type");
            stateSensor.putNull("latitude");
            stateSensor.putNull("longitude");
            stateSensor.put("oc", sensor.getOccupancyAt(windowStartTimeMs));

            for (ParkingSensor.ParkingObservation observation : sensor.getObservationsBetween(windowStartTimeMs, simulationTimeMs)) {
                ObjectNode message = messages.addObject();
                message.put("id", sensor.id);
                message.putNull("type");
                message.putNull("latitude");
                message.putNull("longitude");
                message.put("ts", simulationStartTime.plus(Duration.ofMillis(observation.simulationTimeMs())).toString());
                message.put("oc", observation.occupied());
            }
        }

        return root.toString();
    }
}