package hu.u_szeged.inf.fog.simulator.agent;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.u_szeged.inf.fog.simulator.agent.demo.Config;
import hu.u_szeged.inf.fog.simulator.agent.strategy.mapping.pareto.ExhaustiveMappingStrategy;
import hu.u_szeged.inf.fog.simulator.agent.strategy.message.FloodingMessagingStrategy;
import hu.u_szeged.inf.fog.simulator.agent.util.ResourceAgentCsvExporter;
import hu.u_szeged.inf.fog.simulator.common.node.ComputingAppliance;
import hu.u_szeged.inf.fog.simulator.common.util.GeoLocation;
import hu.u_szeged.inf.fog.simulator.common.util.ScenarioBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceAgentManagerAndCsvExporterTest {

    private static final double EPSILON = 1e-9;

    @BeforeEach
    void resetGlobalState() throws Exception {
        ResourceAgent.allResourceAgents.clear();
        ComputingAppliance.allComputingAppliances.clear();
        AgentApplication.allAgentApplications.clear();
        resetExporterSingleton();
        deleteCsvFiles();
        ResourceAgentManager.getInstance().stop();
    }

    @AfterEach
    void cleanUp() throws Exception {
        ResourceAgentManager.getInstance().stop();
        closeExporterIfPresent();
        ResourceAgent.allResourceAgents.clear();
        ComputingAppliance.allComputingAppliances.clear();
        AgentApplication.allAgentApplications.clear();
        deleteCsvFiles();
    }

    @Test
    void start_withCsvLogging_startsTimedSubscriptionAndWritesHeader() throws Exception {
        ResourceAgent first = createAgent("RA1", 8.0);
        ResourceAgent second = createAgent("RA2", 12.0);

        ResourceAgentManager manager = ResourceAgentManager.getInstance();
        manager.start(10_000L, true);

        assertTrue(manager.isSubscribed());

        Path hourlyPricePath = Path.of(ScenarioBase.RESULT_DIRECTORY, "ra-hourly-price.csv");
        assertTrue(Files.exists(hourlyPricePath));

        List<String> lines = Files.readAllLines(hourlyPricePath);
        assertTrue(lines.size() >= 2);
        assertTrue(lines.get(0).startsWith("time,"));
        assertTrue(lines.get(0).contains("RA1"));
        assertTrue(lines.get(0).contains("RA2"));
        assertTrue(lines.get(1).startsWith(String.format(java.util.Locale.ROOT, "%.3f", Timed.getFireCount() / (double) ScenarioBase.HOUR_IN_MILLISECONDS)));

        manager.stop();
    }

    @Test
    void tick_updatesHourlyPriceForEachResourceAgent() {
        ResourceAgent first = createAgent("RA1", 10.0);
        ResourceAgent second = createAgent("RA2", 20.0);

        first.capacities.put("NodeA", createCapacity("NodeA", 10.0, 100L, 100L, 1000L, 10));
        second.capacities.put("NodeB", createCapacity("NodeB", 20.0, 200L, 200L, 2000L, 20));

        ResourceAgentManager.getInstance().tick(0L);

        assertEquals(8.5, first.hourlyPrice, EPSILON);
        assertEquals(17.0, second.hourlyPrice, EPSILON);

        ResourceAgentManager.getInstance().stop();
    }

    @Test
    void log_usesAgentsSnapshotAtExporterConstruction() throws Exception {
        ResourceAgent first = createAgent("RA1", 10.0);

        ResourceAgentCsvExporter exporter = ResourceAgentCsvExporter.getInstance();
        ResourceAgent second = createAgent("RA2", 20.0);
        exporter.log();

        Path hourlyPricePath = exporter.hourlyPricePath;
        List<String> lines = Files.readAllLines(hourlyPricePath);

        assertEquals("time,RA1", lines.get(0));
        assertFalse(lines.get(0).contains("RA2"));
        assertTrue(lines.size() >= 2);
        assertEquals(2, ResourceAgent.allResourceAgents.size());
    }

    private static void resetExporterSingleton() throws Exception {
        Field field = ResourceAgentCsvExporter.class.getDeclaredField("instance");
        field.setAccessible(true);
        field.set(null, null);
    }

    private static void closeExporterIfPresent() throws Exception {
        Field field = ResourceAgentCsvExporter.class.getDeclaredField("instance");
        field.setAccessible(true);
        Object instance = field.get(null);
        if (instance instanceof ResourceAgentCsvExporter exporter) {
            exporter.close();
            field.set(null, null);
        }
    }

    private static void deleteCsvFiles() throws IOException {
        Path hourlyPricePath = Path.of(ScenarioBase.RESULT_DIRECTORY, "ra-hourly-price.csv");
        Path resourceMetricsPath = Path.of(ScenarioBase.RESULT_DIRECTORY, "ra-resource-metrics.csv");
        Files.deleteIfExists(hourlyPricePath);
        Files.deleteIfExists(resourceMetricsPath);
    }

    private static ResourceAgent createAgent(String name, double hourlyPrice) {
        return new ResourceAgent(name, hourlyPrice, new ExhaustiveMappingStrategy(), new FloodingMessagingStrategy());
    }

    private static Capacity createCapacity(
            String nodeName,
            double cpu,
            long memory,
            long storage,
            long bandwidth,
            int latency) {
        Map<String, Integer> latencyMap = new HashMap<>();
        ComputingAppliance node = new ComputingAppliance(
                Config.createNode(nodeName, cpu, memory, storage, 10, 20, 50, bandwidth, latency, latencyMap),
                new GeoLocation(0, 0),
                "x",
                "x",
                false);
        return new Capacity(node, cpu, memory, storage);
    }
}
