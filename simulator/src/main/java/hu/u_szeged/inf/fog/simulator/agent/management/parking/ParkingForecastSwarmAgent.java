package hu.u_szeged.inf.fog.simulator.agent.management.parking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import hu.u_szeged.inf.fog.simulator.agent.AgentApplication;
import hu.u_szeged.inf.fog.simulator.agent.application.parking.ParkingSensor;
import hu.u_szeged.inf.fog.simulator.agent.demo.Config;
import hu.u_szeged.inf.fog.simulator.agent.forecast.ForecasterManager;
import hu.u_szeged.inf.fog.simulator.common.util.ScenarioBase;
import hu.u_szeged.inf.fog.simulator.common.util.SimLogger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ParkingForecastSwarmAgent extends ParkingSwarmAgent {

    public ParkingForecastSwarmAgent(AgentApplication app) {
        super(app);
    }

    private final ObjectMapper objectMapper = new ObjectMapper();

    private boolean forecastRequestSaved = false;

    @Override
    protected void evaluateReconfiguration(long simulationTimeMs) {
        Map<String, Double> predictedEvents;
        String requestJson = null;

        try {
            requestJson = buildForecastRequest(simulationTimeMs);
            if (!forecastRequestSaved) {
                JsonNode requestNode = objectMapper.readTree(requestJson);
                String formattedRequestJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(requestNode);
                Files.writeString(Path.of(ScenarioBase.RESULT_DIRECTORY, "parking-forecast-request.json"), formattedRequestJson);
                forecastRequestSaved = true;
            }

            String responseJson = ForecasterManager.getInstance().predict(requestJson);
            predictedEvents = parseFirstSlotPredictions(responseJson);
            SimLogger.logRun("Parking forecast received at " + simulationTimeMs / 60_000.0 + " min for " + predictedEvents.size() + " sensors.");
        } catch (Exception e) {
            throw new RuntimeException("Parking forecast request failed", e);
        } finally {
            deleteForecastFiles(requestJson);
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

    protected void deleteForecastFiles(String requestJson) {
        if (requestJson == null) {
            return;
        }

        try {
            JsonNode root = objectMapper.readTree(requestJson);
            Files.deleteIfExists(Path.of(root.get("state_path").asText()));
            Files.deleteIfExists(Path.of(root.get("messages_path").asText()));
            Files.deleteIfExists(Path.of(root.get("output_path").asText()));
        } catch (Exception e) {
            SimLogger.logError("Failed to delete temporary parking forecast files: " + e.getMessage());
        }
    }

    protected List<Map<String, Double>> parseAllSlotPredictions(String responseJson) throws Exception {
        JsonNode root = objectMapper.readTree(responseJson);
        JsonNode outputPathNode = root.get("output_path");

        if (outputPathNode == null) {
            throw new IllegalStateException("Invalid forecast response: missing output_path");
        }

        Path outputPath = Path.of(outputPathNode.asText());
        List<String> lines = Files.readAllLines(outputPath);

        if (lines.size() < 2) {
            throw new IllegalStateException("Forecast output is empty: " + outputPath);
        }

        List<Map<String, Double>> slots = new ArrayList<>();
        String currentTime = null;
        Map<String, Double> currentSlot = null;

        for (int i = 1; i < lines.size(); i++) {
            String[] cells = lines.get(i).split(",", -1);

            if (cells.length < 4) {
                throw new IllegalStateException("Invalid forecast CSV row: " + lines.get(i));
            }

            String predictionTime = cells[0];
            String sensorId = cells[1];
            double events = Double.parseDouble(cells[2]);

            if (!predictionTime.equals(currentTime)) {
                currentTime = predictionTime;
                currentSlot = new HashMap<>();
                slots.add(currentSlot);
            }

            currentSlot.put(sensorId, events);
        }

        return slots;
    }

    protected Map<String, Double> parseFirstSlotPredictions(String responseJson) throws Exception {
        JsonNode root = objectMapper.readTree(responseJson);
        JsonNode outputPathNode = root.get("output_path");

        if (outputPathNode == null) {
            throw new IllegalStateException("Invalid forecast response: missing output_path");
        }

        Path outputPath = Path.of(outputPathNode.asText());
        List<String> lines = Files.readAllLines(outputPath);

        if (lines.size() < 2) {
            throw new IllegalStateException("Forecast output is empty: " + outputPath);
        }

        Map<String, Double> predictedEvents = new HashMap<>();
        String firstPredictionTime = null;

        for (int i = 1; i < lines.size(); i++) {
            String[] cells = lines.get(i).split(",", -1);

            if (cells.length < 4) {
                throw new IllegalStateException("Invalid forecast CSV row: " + lines.get(i));
            }

            String predictionTime = cells[0];

            if (firstPredictionTime == null) {
                firstPredictionTime = predictionTime;
            } else if (!predictionTime.equals(firstPredictionTime)) {
                break;
            }

            String sensorId = cells[1];
            double events = Double.parseDouble(cells[2]);
            predictedEvents.put(sensorId, events);
        }

        return predictedEvents;
    }

    protected String buildForecastRequest(long simulationTimeMs) throws Exception {
        long windowStartTimeMs = simulationTimeMs - (long) Config.PARKING_CONFIGURATION.get("forecastHistoryWindowMs");
        OffsetDateTime simulationStartTime = OffsetDateTime.parse((String) Config.PARKING_CONFIGURATION.get("traceStartTime"));
        long windowStartEpochSecond = simulationStartTime.plus(Duration.ofMillis(windowStartTimeMs)).toEpochSecond();

        Path resultDirectory = Path.of(ScenarioBase.RESULT_DIRECTORY);
        Files.createDirectories(resultDirectory);

        Path statePath = Files.createTempFile(resultDirectory, "parking-forecast-state-", ".csv");
        Path messagesPath = Files.createTempFile(resultDirectory, "parking-forecast-messages-", ".csv");
        Path outputPath = Files.createTempFile(resultDirectory, "parking-forecast-output-", ".csv");

        StringBuilder stateCsv = new StringBuilder("id,oc\n");
        StringBuilder messagesCsv = new StringBuilder("id,ts,oc\n");

        for (Object component : observedAppComponents) {
            if (!(component instanceof ParkingSensor sensor)) {
                continue;
            }

            stateCsv.append(sensor.id)
                    .append(",")
                    .append(sensor.getOccupancyAt(windowStartTimeMs))
                    .append("\n");

            for (ParkingSensor.ParkingObservation observation : sensor.getObservationsBetween(windowStartTimeMs, simulationTimeMs)) {
                long observationEpochSecond = simulationStartTime.plus(Duration.ofMillis(observation.simulationTimeMs())).toEpochSecond();

                messagesCsv.append(sensor.id)
                        .append(",")
                        .append(observationEpochSecond)
                        .append(",")
                        .append(observation.occupied())
                        .append("\n");
            }
        }

        Files.writeString(statePath, stateCsv.toString());
        Files.writeString(messagesPath, messagesCsv.toString());

        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", (String) Config.PARKING_CONFIGURATION.get("predictorModel"));
        root.put("time", windowStartEpochSecond);
        root.put("state_path", statePath.toAbsolutePath().normalize().toString());
        root.put("messages_path", messagesPath.toAbsolutePath().normalize().toString());
        root.put("output_path", outputPath.toAbsolutePath().normalize().toString());

        return root.toString();
    }
}