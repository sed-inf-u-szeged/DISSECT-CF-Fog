package hu.u_szeged.inf.fog.simulator.agent.dt;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;

public class ParkingCsvData {

    public final LocalDateTime simulationStartTime;
    public final long maxSimulationTimeMs;
    public final Map<String, Deque<ParkingEvent>> eventsBySensor;

    public ParkingCsvData(LocalDateTime simulationStartTime, long maxSimulationTimeMs,
                          Map<String, Deque<ParkingEvent>> eventsBySensor) {
        this.simulationStartTime = simulationStartTime;
        this.maxSimulationTimeMs = maxSimulationTimeMs;
        this.eventsBySensor = eventsBySensor;
    }

    public static ParkingCsvData load(Path csvPath) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(csvPath)) {
            String headerLine = reader.readLine();

            if (headerLine == null) {
                throw new IllegalArgumentException("Parking CSV is empty");
            }

            String[] header = headerLine.split(",", -1);

            if (header.length < 2 || !"timestamp".equals(header[0].trim())) {
                throw new IllegalArgumentException("Invalid parking CSV header");
            }

            Map<String, Deque<ParkingEvent>> eventsBySensor = new LinkedHashMap<>();

            for (int i = 1; i < header.length; i++) {
                eventsBySensor.put(header[i].trim(), new ArrayDeque<>());
            }

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");

            LocalDateTime simulationStartTime = null;
            LocalDateTime lastTimestamp = null;

            String line;

            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }

                String[] cells = line.split(",", -1);

                if (cells.length != header.length) {
                    throw new IllegalArgumentException("Invalid parking CSV row: " + line);
                }

                LocalDateTime timestamp = LocalDateTime.parse(cells[0].trim(), formatter);

                if (simulationStartTime == null) {
                    simulationStartTime = timestamp;
                }

                lastTimestamp = timestamp;

                long simulationTimeMs =
                        Duration.between(simulationStartTime, timestamp).toMillis();

                for (int i = 1; i < cells.length; i++) {
                    String value = cells[i].trim();

                    if (value.isEmpty()) {
                        continue;
                    }

                    String sensorId = header[i].trim();

                    eventsBySensor.get(sensorId).add(
                            new ParkingEvent(simulationTimeMs)
                    );
                }
            }

            if (simulationStartTime == null) {
                throw new IllegalArgumentException("Parking CSV contains no data rows");
            }

            long maxSimulationTimeMs =
                    Duration.between(simulationStartTime, lastTimestamp).toMillis();

            return new ParkingCsvData(
                    simulationStartTime,
                    maxSimulationTimeMs,
                    eventsBySensor
            );
        }
    }

    public record ParkingEvent(long simulationTimeMs) {
    }
}