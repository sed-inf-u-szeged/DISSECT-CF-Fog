package hu.u_szeged.inf.fog.simulator.agent.util;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.u_szeged.inf.fog.simulator.agent.application.noise.NoiseSensor;
import hu.u_szeged.inf.fog.simulator.agent.application.noise.Sun;
import hu.u_szeged.inf.fog.simulator.agent.demo.Config;
import hu.u_szeged.inf.fog.simulator.agent.management.GreedyNoiseSwarmAgent;
import hu.u_szeged.inf.fog.simulator.agent.management.SwarmAgent;
import hu.u_szeged.inf.fog.simulator.common.util.ScenarioBase;
import org.apache.commons.lang3.tuple.Pair;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;

public class NoiseAppCsvExporter implements Closeable {

    public static Map<String, NoiseAppCsvExporter> allNoiseAppCsvExporters = new HashMap<>();

    private boolean headerWritten;
    public String appName;

    public final Path soundValuesPath;
    public final Path noiseSensorTemperaturePath;
    public final Path noiseSensorClassifierCountPath;
    public final Path processedFilePath;
    public final Path fileMigrationCountPath;
    public final Path sunIntensityPath;
    public final Path noiseSensorCpuLoadPath;

    public final PrintWriter soundValuesWriter;
    public final PrintWriter noiseSensorTemperatureWriter;
    public final PrintWriter noiseSensorClassifierCountWriter;
    public final PrintWriter processedFileWriter;
    public final PrintWriter fileMigrationCountWriter;
    public final PrintWriter sunIntensityWriter;
    public final PrintWriter noiseSensorCpuLoadWriter;


    //File cpuLoad;

    public NoiseAppCsvExporter(SwarmAgent swarmAgent) {
        this.appName = swarmAgent.app.name;
        allNoiseAppCsvExporters.put(appName, this);

        try {
            soundValuesPath = Paths.get(
                    ScenarioBase.RESULT_DIRECTORY,
                    appName + "-sound-values.csv"
            );
            noiseSensorTemperaturePath = Paths.get(
                    ScenarioBase.RESULT_DIRECTORY,
                    appName + "-noise-sensor-temperature.csv"
            );
            noiseSensorClassifierCountPath = Paths.get(
                    ScenarioBase.RESULT_DIRECTORY,
                    appName + "-noise-sensor-classifier-count.csv"
            );
            processedFilePath = Paths.get(
                    ScenarioBase.RESULT_DIRECTORY,
                    appName + "-processed-files-count.csv"
            );
            fileMigrationCountPath = Paths.get(
                    ScenarioBase.RESULT_DIRECTORY,
                    appName + "-file-migration-count.csv"
            );
            sunIntensityPath = Paths.get(
                    ScenarioBase.RESULT_DIRECTORY,
                    appName + "-sun-intensity.csv"
            );
            noiseSensorCpuLoadPath = Paths.get(
                    ScenarioBase.RESULT_DIRECTORY,
                    appName + "-noise-sensor-cpu-load.csv"
            );

            soundValuesWriter = new PrintWriter(
                    Files.newBufferedWriter(soundValuesPath, StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE, StandardOpenOption.APPEND),
                    true
            );
            noiseSensorTemperatureWriter = new PrintWriter(
                    Files.newBufferedWriter(noiseSensorTemperaturePath, StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE, StandardOpenOption.APPEND),
                    true
            );
            noiseSensorClassifierCountWriter = new PrintWriter(
                    Files.newBufferedWriter(noiseSensorClassifierCountPath, StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE, StandardOpenOption.APPEND),
                    true
            );
            processedFileWriter = new PrintWriter(
                    Files.newBufferedWriter(processedFilePath, StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE, StandardOpenOption.APPEND),
                    true
            );
            fileMigrationCountWriter = new PrintWriter(
                    Files.newBufferedWriter(fileMigrationCountPath, StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE, StandardOpenOption.APPEND),
                    true
            );
            sunIntensityWriter = new PrintWriter(
                    Files.newBufferedWriter(sunIntensityPath, StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE, StandardOpenOption.APPEND),
                    true
            );
            noiseSensorCpuLoadWriter = new PrintWriter(
                    Files.newBufferedWriter(noiseSensorCpuLoadPath, StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE, StandardOpenOption.APPEND),
                    true
            );

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String generateHeader(SwarmAgent swarmAgent) {
        List<String> names = new ArrayList<>();
        for (Object o : swarmAgent.observedAppComponents) {
            if (o instanceof NoiseSensor ns) {
                names.add(ns.util.component.id);
            }
        }

        return "time" + "," + String.join(",", names);
    }

    @Override
    public void close() throws IOException {
        soundValuesWriter.close();
        noiseSensorTemperatureWriter.close();
        noiseSensorClassifierCountWriter.close();
        processedFileWriter.close();
        fileMigrationCountWriter.close();
        sunIntensityWriter.close();
        noiseSensorCpuLoadWriter.close();
    }

    public void log(SwarmAgent swarmAgent, Pair<Map<NoiseSensor, Double>, Double> noiseSensorCpuLoads) {
        double time = Timed.getFireCount() / (double) ScenarioBase.HOUR_IN_MILLISECONDS;

        if (!headerWritten) {
            String header = generateHeader(swarmAgent);
            soundValuesWriter.println(header);
            noiseSensorTemperatureWriter.println(header + ",avg-cpu-temperature");
            noiseSensorClassifierCountWriter.println("time,classifier-count,available-sensor-count");
            processedFileWriter.println(header + ",sum-of-processed-files");
            fileMigrationCountWriter.println(header + ",sum-of-file-migrations");
            sunIntensityWriter.println("time,sun_intensity");
            noiseSensorCpuLoadWriter.println(header + ",avg-cpu-load-of-classifiers");
            headerWritten = true;
        }

        StringBuilder rowForSoundValues = new StringBuilder();
        StringBuilder rowForCpuTemperatures = new StringBuilder();
        StringBuilder rowForClassifierCount = new StringBuilder();
        StringBuilder rowForProcessedFileCount = new StringBuilder();
        StringBuilder rowForFileMigrationCount = new StringBuilder();
        StringBuilder rowForSunIntensity = new StringBuilder();
        StringBuilder rowForCpuLoads = new StringBuilder();

        rowForSoundValues.append(String.format(Locale.ROOT, "%.3f", time));
        rowForCpuTemperatures.append(String.format(Locale.ROOT, "%.3f", time));
        rowForClassifierCount.append(String.format(Locale.ROOT, "%.3f", time));
        rowForProcessedFileCount.append(String.format(Locale.ROOT, "%.3f", time));
        rowForFileMigrationCount.append(String.format(Locale.ROOT, "%.3f", time));
        rowForSunIntensity.append(String.format(Locale.ROOT, "%.3f", time)).append(",").append(Sun.getInstance().getSunStrength());
        rowForCpuLoads.append(String.format(Locale.ROOT, "%.3f", time));

        int availableSensors = 0;
        int processedFileCount = 0;
        int summedProcessedFileCount = 0;
        int fileMigrationCount = 0;
        int summedFileMigrationCount = 0;

        double sumOfCpuTemperatures = 0;
        int totalSensors = 0;

        var loadsMap = noiseSensorCpuLoads.getLeft();
        double avgLoad = noiseSensorCpuLoads.getRight();

        for (Object o : swarmAgent.observedAppComponents) {
            if (o instanceof NoiseSensor ns) {
                rowForSoundValues.append(",");
                rowForSoundValues.append(ns.prevSoundValue);

                rowForCpuTemperatures.append(",");
                rowForCpuTemperatures.append(ns.cpuTemperature);
                sumOfCpuTemperatures += ns.cpuTemperature;
                totalSensors++;

                if (ns.cpuTemperature < (double) Config.NOISE_CLASS_CONFIGURATION.get("cpuTempTreshold")) {
                    availableSensors++;
                }

                rowForProcessedFileCount.append(",");
                rowForProcessedFileCount.append(String.format(Locale.ROOT, "%d", ns.processedFileCounter));
                summedProcessedFileCount += ns.processedFileCounter;
                ns.processedFileCounter = 0;

                rowForFileMigrationCount.append(",");
                rowForFileMigrationCount.append(String.format(Locale.ROOT, "%d", ns.fileMigrationCounter));
                summedFileMigrationCount += ns.fileMigrationCounter;
                ns.fileMigrationCounter = 0;

                rowForCpuLoads.append(",");
                rowForCpuLoads.append(String.format(Locale.ROOT, "%.3f", loadsMap.get(ns)));
            }
        }




        GreedyNoiseSwarmAgent gsa = (GreedyNoiseSwarmAgent) swarmAgent;
        rowForClassifierCount.append(",");
        rowForClassifierCount.append(String.format(Locale.ROOT, "%d", gsa.noiseSensorsWithClassifier.size()));
        rowForClassifierCount.append(",");
        rowForClassifierCount.append(String.format(Locale.ROOT, "%d", availableSensors));
        rowForCpuLoads.append(",");
        rowForCpuLoads.append(String.format(Locale.ROOT, "%.3f", avgLoad));
        rowForCpuTemperatures.append(",");
        rowForCpuTemperatures.append(String.format(Locale.ROOT, "%.3f", sumOfCpuTemperatures / totalSensors));

        soundValuesWriter.println(rowForSoundValues);
        noiseSensorTemperatureWriter.println(rowForCpuTemperatures);
        noiseSensorClassifierCountWriter.println(rowForClassifierCount);
        processedFileWriter.println(rowForProcessedFileCount + "," + String.format(Locale.ROOT, "%d", summedProcessedFileCount));
        fileMigrationCountWriter.println(rowForFileMigrationCount + "," + String.format(Locale.ROOT, "%d", summedFileMigrationCount));
        sunIntensityWriter.println(rowForSunIntensity);
        noiseSensorCpuLoadWriter.println(rowForCpuLoads);
    }
}
