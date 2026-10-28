package hu.u_szeged.inf.fog.simulator.agent.dt;
import hu.u_szeged.inf.fog.simulator.agent.application.parking.ParkingSensor;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;

public class ParkingTraceData {

    public final OffsetDateTime simulationStartTime;

    public final long maxSimulationTimeMs;

    public final Map<String, SensorState> sensors;

    public final Map<String, Deque<ParkingCsvData.ParkingEvent>> eventsBySensor;

    public final Map<String, Deque<ParkingSensor.ParkingObservation>> warmupObservationsBySensor;

    public ParkingTraceData(OffsetDateTime simulationStartTime, long maxSimulationTimeMs, Map<String, SensorState> sensors,
                            Map<String, Deque<ParkingCsvData.ParkingEvent>> eventsBySensor, Map<String, Deque<ParkingSensor.ParkingObservation>> warmupObservationsBySensor) {
        this.simulationStartTime = simulationStartTime;
        this.maxSimulationTimeMs = maxSimulationTimeMs;
        this.sensors = sensors;
        this.eventsBySensor = eventsBySensor;
        this.warmupObservationsBySensor = warmupObservationsBySensor;
    }

    public static ParkingTraceData load(Path statePath, Path tracePath, OffsetDateTime simulationStartTime, long historyWindowMs) throws IOException {

        Map<String, SensorState> sensors = loadInitialState(statePath);
        Map<String, Boolean> currentOccupancy = new LinkedHashMap<>();
        Map<String, Boolean> simulationStartOccupancy = new LinkedHashMap<>();
        Map<String, Deque<ParkingCsvData.ParkingEvent>> eventsBySensor = new LinkedHashMap<>();
        Map<String, Deque<ParkingSensor.ParkingObservation>> warmupObservationsBySensor = new LinkedHashMap<>();

        for (SensorState sensor : sensors.values()) {
            currentOccupancy.put(sensor.id(), sensor.occupied());
            simulationStartOccupancy.put(sensor.id(), sensor.occupied());
            eventsBySensor.put(sensor.id(), new ArrayDeque<>());
            warmupObservationsBySensor.put(sensor.id(), new ArrayDeque<>());
            warmupObservationsBySensor.get(sensor.id()).add(new ParkingSensor.ParkingObservation(-historyWindowMs, sensor.occupied()));
        }

        long maxSimulationTimeMs = 0;
        try (BufferedReader reader = Files.newBufferedReader(tracePath)) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw new IllegalArgumentException("Parking trace CSV is empty");
            }
            String[] header = headerLine.split(",", -1);
            int idIndex = findColumn(header, "id");
            int timestampIndex = findColumn(header, "ts");
            int occupancyIndex = findColumn(header, "oc");

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] cells = line.split(",", -1);
                String sensorId = cells[idIndex].trim();
                OffsetDateTime timestamp = OffsetDateTime.parse(cells[timestampIndex].trim(), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ssXXX"));
                boolean occupied = Boolean.parseBoolean(cells[occupancyIndex].trim());
                if (!currentOccupancy.containsKey(sensorId)) {
                    throw new IllegalArgumentException("Sensor not found in initial state: " + sensorId);
                }
                long simulationTimeMs = Duration.between(simulationStartTime, timestamp).toMillis();
                boolean previousOccupancy = currentOccupancy.get(sensorId);

                if (simulationTimeMs < 0) {
                    if (occupied != previousOccupancy) {
                        warmupObservationsBySensor.get(sensorId).add(new ParkingSensor.ParkingObservation(simulationTimeMs, occupied));
                        currentOccupancy.put(sensorId, occupied);
                    }
                    simulationStartOccupancy.put(sensorId, currentOccupancy.get(sensorId));
                    continue;
                }

                maxSimulationTimeMs = Math.max(maxSimulationTimeMs, simulationTimeMs);

                if (occupied != previousOccupancy) {
                    eventsBySensor.get(sensorId).add(new ParkingCsvData.ParkingEvent(simulationTimeMs));
                    currentOccupancy.put(sensorId, occupied);
                }
            }
        }
        sensors.replaceAll((id, sensor) -> new SensorState(sensor.id(), sensor.type(), sensor.latitude(), sensor.longitude(), simulationStartOccupancy.get(id), sensor.failed()));
        return new ParkingTraceData(simulationStartTime, maxSimulationTimeMs, sensors, eventsBySensor, warmupObservationsBySensor);
    }

    private static Map<String, SensorState> loadInitialState(Path statePath) throws IOException {

        Map<String, SensorState> sensors = new LinkedHashMap<>();

        try (BufferedReader reader = Files.newBufferedReader(statePath)) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw new IllegalArgumentException("Parking state CSV is empty");
            }
            String[] header = headerLine.split(",", -1);
            int idIndex = findColumn(header, "id");
            int typeIndex = findColumn(header, "type");
            int latitudeIndex = findColumn(header, "latitude");
            int longitudeIndex = findColumn(header, "longitude");
            int occupancyIndex = findColumn(header, "oc");
            int failedIndex = findColumn(header, "failed");

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] cells = line.split(",", -1);
                SensorState sensor = new SensorState(cells[idIndex].trim(), cells[typeIndex].trim(), cells[latitudeIndex].isBlank() ?
                        Double.NaN : Double.parseDouble(cells[latitudeIndex].trim()),
                        cells[longitudeIndex].isBlank() ?
                        Double.NaN : Double.parseDouble(cells[longitudeIndex].trim()), Boolean.parseBoolean(cells[occupancyIndex].trim()), Boolean.parseBoolean(cells[failedIndex].trim()));
                sensors.put(sensor.id(), sensor);
            }
        }

        return sensors;
    }

    private static int findColumn(String[] header, String columnName) {
        for (int i = 0; i < header.length; i++) {
            if (columnName.equals(header[i].trim())) {
                return i;
            }
        }
        throw new IllegalArgumentException("Missing column in parking CSV: " + columnName);
    }

    public record SensorState(String id, String type, double latitude, double longitude, boolean occupied, boolean failed) {
    }

}