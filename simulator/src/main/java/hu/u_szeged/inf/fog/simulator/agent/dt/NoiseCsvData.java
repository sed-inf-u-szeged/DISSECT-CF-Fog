package hu.u_szeged.inf.fog.simulator.agent.dt;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;

public class NoiseCsvData {

    public record SensorEvent(long simulationTimeMs, double value) {}

    public LocalDateTime simulationStartTime;

    public long maxSimulationTimeMs;

    public Map<String, Deque<SensorEvent>> eventsBySensor;

    public NoiseCsvData(LocalDateTime simulationStartTime, long maxSimulationTimeMs, Map<String, Deque<SensorEvent>> eventsBySensor) {
        this.simulationStartTime = simulationStartTime;
        this.maxSimulationTimeMs = maxSimulationTimeMs;
        this.eventsBySensor = eventsBySensor;
    }

    public Deque<SensorEvent> getEvents(String sensorName) {
        return eventsBySensor.get(sensorName);
    }

    public static NoiseCsvData load(Path csvPath) throws IOException {
        Map<String, Deque<SensorEvent>> eventsBySensor = new LinkedHashMap<>();
        LocalDateTime simulationStartTime = null;
        long maxSimulationTimeMs = 0L;

        try (BufferedReader reader = Files.newBufferedReader(csvPath)) {
            String headerLine = reader.readLine();
            if (headerLine == null || headerLine.isBlank()) {
                throw new RuntimeException("Invalid CSV header");
            }

            // Split header, keeping empty columns too
            String[] headers = headerLine.split(",", -1);

            if (headers.length < 2 || !headers[0].trim().equals("timestamp")) {
                throw new RuntimeException("Invalid CSV header");
            }

            // Create one event queue for every sensor column
            for (int i = 1; i < headers.length; i++) {
                String sensorName = headers[i].trim();
                if (sensorName.isEmpty()) {
                    throw new RuntimeException("Invalid CSV header");
                }

                eventsBySensor.put(sensorName, new ArrayDeque<>());
            }

            String line;

            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }

                String[] cells = line.split(",", -1);

                if (cells.length != headers.length) {
                    throw new RuntimeException("Invalid CSV row");
                }

                LocalDateTime realTimestamp =
                        LocalDateTime.parse(cells[0].trim());

                if (simulationStartTime == null) {
                    simulationStartTime = realTimestamp;
                }

                long simulationTimeMs =
                        Duration.between(simulationStartTime, realTimestamp)
                                .toMillis();

                maxSimulationTimeMs = simulationTimeMs;

                for (int i = 1; i < cells.length; i++) {
                    String rawValue = cells[i].trim();

                    if (rawValue.isEmpty()) {
                        continue;
                    }

                    double value = Double.parseDouble(rawValue);
                    String sensorName = headers[i].trim();

                    eventsBySensor.get(sensorName).addLast(
                            new SensorEvent(simulationTimeMs, value)
                    );
                }
            }
        }

        return new NoiseCsvData(simulationStartTime, maxSimulationTimeMs, eventsBySensor);
    }
}