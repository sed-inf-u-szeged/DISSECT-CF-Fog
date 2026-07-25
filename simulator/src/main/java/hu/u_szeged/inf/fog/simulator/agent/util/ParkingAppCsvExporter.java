package hu.u_szeged.inf.fog.simulator.agent.util;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.u_szeged.inf.fog.simulator.agent.application.noise.NoiseSensor;
import hu.u_szeged.inf.fog.simulator.agent.application.parking.ParkingSensor;
import hu.u_szeged.inf.fog.simulator.agent.management.SwarmAgent;
import hu.u_szeged.inf.fog.simulator.common.util.ScenarioBase;
import org.apache.commons.lang3.tuple.Pair;
import java.io.Closeable;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;

public class ParkingAppCsvExporter implements Closeable {

    public static Map<String, ParkingAppCsvExporter> allParkingAppCsvExporters = new HashMap<>();

    private boolean headerWritten;
    public SwarmAgent swarmAgent;

    public final Path parkingSpotStatusPath;
    public final Path batteryStatusPath;

    public final PrintWriter parkingSpotStatusWriter;
    public final PrintWriter batteryStatusWriter;

    public ParkingAppCsvExporter(SwarmAgent swarmAgent) {
        this.swarmAgent = swarmAgent;
        allParkingAppCsvExporters.put(this.swarmAgent.app.name, this);

        try {
            parkingSpotStatusPath = Paths.get(
                    ScenarioBase.RESULT_DIRECTORY,
                    this.swarmAgent.app.name + "-parking-spot-status.csv"
            );
            batteryStatusPath = Paths.get(
                    ScenarioBase.RESULT_DIRECTORY,
                    this.swarmAgent.app.name + "-battery-status.csv"
            );

            parkingSpotStatusWriter = new PrintWriter(
                    Files.newBufferedWriter(parkingSpotStatusPath, StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE, StandardOpenOption.APPEND),
                    true
            );
            batteryStatusWriter = new PrintWriter(
                    Files.newBufferedWriter(batteryStatusPath, StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE, StandardOpenOption.APPEND),
                    true
            );
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String generateHeader() {
        List<String> names = new ArrayList<>();
        for (Object o : swarmAgent.observedAppComponents) {
            if (o instanceof ParkingSensor ps) {
                names.add(ps.id); // TODO: replace to util id later
            }
        }

        return "time" + "," + String.join(",", names);
    }

    @Override
    public void close() throws IOException {
        parkingSpotStatusWriter.close();
        batteryStatusWriter.close();
    }

    public void log() {
        double time = Timed.getFireCount() / (double) ScenarioBase.HOUR_IN_MILLISECONDS;

        if (!headerWritten) {
            String header = generateHeader();
            parkingSpotStatusWriter.println(header);
            batteryStatusWriter.println(header);
            headerWritten = true;
        }

        StringBuilder rowForParkingSpotStatus = new StringBuilder();
        StringBuilder rowForBatteryStatus = new StringBuilder();

        rowForParkingSpotStatus.append(String.format(Locale.ROOT, "%.3f", time));
        rowForBatteryStatus.append(String.format(Locale.ROOT, "%.3f", time));

        for (Object o : swarmAgent.observedAppComponents) {
            if (o instanceof ParkingSensor ps) {
                rowForParkingSpotStatus.append(",");
                rowForParkingSpotStatus.append(ps.isTaken ? 1 : 0);

                rowForBatteryStatus.append(",");
                rowForBatteryStatus.append(ps.batteryLevel);
            }
        }

        parkingSpotStatusWriter.println(rowForParkingSpotStatus);
        batteryStatusWriter.println(rowForBatteryStatus);
    }
}