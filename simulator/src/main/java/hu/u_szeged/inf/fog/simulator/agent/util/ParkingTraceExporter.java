package hu.u_szeged.inf.fog.simulator.agent.util;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.u_szeged.inf.fog.simulator.agent.application.parking.ParkingSensor;
import hu.u_szeged.inf.fog.simulator.common.util.ScenarioBase;

import java.io.Closeable;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.OffsetDateTime;

public class ParkingTraceExporter implements Closeable {

    private final OffsetDateTime traceStartTime;
    private final PrintWriter messagesWriter;
    private final PrintWriter metadataWriter;

    public ParkingTraceExporter(OffsetDateTime traceStartTime) {
        this.traceStartTime = traceStartTime;

        try {
            Path outputDirectory = Path.of(ScenarioBase.RESULT_DIRECTORY);
            Files.createDirectories(outputDirectory);

            Path messagesPath = outputDirectory.resolve("messages.csv");
            Path metadataPath = outputDirectory.resolve("metadata.csv");

            messagesWriter = new PrintWriter(
                    Files.newBufferedWriter(
                            messagesPath,
                            StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.TRUNCATE_EXISTING
                    ),
                    true
            );

            metadataWriter = new PrintWriter(
                    Files.newBufferedWriter(
                            metadataPath,
                            StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.TRUNCATE_EXISTING
                    ),
                    true
            );

            messagesWriter.println("id,ts,oc");
            metadataWriter.println("id,type,latitude,longitude");
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize parking trace exporter", e);
        }
    }

    public void registerSensor(ParkingSensor sensor) {
        metadataWriter.println(
                sensor.id + ","
                        + sensor.zone + ",,"
        );
    }

    public void logEvent(ParkingSensor sensor) {
        long timestamp = traceStartTime.plus(Duration.ofMillis(Timed.getFireCount())).toEpochSecond();

        messagesWriter.println(
                sensor.id + ","
                        + timestamp + ","
                        + sensor.isTaken
        );
    }

    @Override
    public void close() {
        messagesWriter.close();
        metadataWriter.close();
    }
}