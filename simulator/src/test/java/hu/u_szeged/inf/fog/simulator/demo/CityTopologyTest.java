package hu.u_szeged.inf.fog.simulator.demo;

import static org.junit.jupiter.api.Assertions.*;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;
import hu.u_szeged.inf.fog.simulator.application.Application;
import hu.u_szeged.inf.fog.simulator.application.strategy.PliantApplicationStrategy;
import hu.u_szeged.inf.fog.simulator.node.ComputingAppliance;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Isolated JVMs verify actual hardware, roles and existing reactive offloading. */
class CityTopologyTest {
    @TempDir Path directory;

    @Test void legacyAndCityCoverageUseTheOriginalHardwareAndHierarchy() throws Exception {
        for (String range : new String[] {"10000", "20"}) {
            Path log = directory.resolve("topology-" + range + ".log");
            String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
            String cp = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
            Process process = new ProcessBuilder(java, "-cp", cp, Probe.class.getName(), range)
                    .redirectErrorStream(true).redirectOutput(log.toFile()).start();
            try {
                assertTrue(process.waitFor(45, TimeUnit.SECONDS), "City probe timed out");
                assertEquals(0, process.exitValue(), Files.readString(log));
                assertTrue(Files.readString(log).contains("CITY_TOPOLOGY_PROBE_OK"));
            } finally {
                if (process.isAlive()) { process.destroyForcibly().waitFor(); }
            }
        }
    }

    public static class Probe {
        public static void main(String[] args) throws Exception {
            Path resources = Path.of("src/main/resources/demo");
            if (!Files.isDirectory(resources)) { resources = Path.of("simulator/src/main/resources/demo"); }
            SeedSyncer.centralRnd.setSeed(42);
            long range = Long.parseLong(args[0]);
            List<ComputingAppliance> nodes = CityTopology.create(resources.resolve("LPDS_original.xml"),
                    resources, range, 5120, 480, () -> new PliantApplicationStrategy(.9, 2));
            assertEquals(10, nodes.size());
            assertEquals(9, nodes.stream().filter(n -> n.applications.get(0).serviceable).count());
            ComputingAppliance cloud = nodes.get(0), szeged = nodes.get(4), kecskemet = nodes.get(6);
            assertEquals("Budapest_cloud", cloud.name);
            assertFalse(cloud.applications.get(0).serviceable);
            assertNull(cloud.parent);
            assertEquals(kecskemet, szeged.parent);
            assertEquals(cloud, kecskemet.parent);
            assertEquals("Pecs_fog2", szeged.neighbors.get(0).name);
            assertEquals(12, cloud.iaas.machines.size());
            assertEquals(16, szeged.iaas.getCapacities().getRequiredCPUs());
            assertEquals(32, kecskemet.iaas.getCapacities().getRequiredCPUs());
            assertEquals(2, szeged.applications.get(0).instance.arc.getRequiredCPUs());
            assertEquals(4, kecskemet.applications.get(0).instance.arc.getRequiredCPUs());
            assertEquals(8, cloud.applications.get(0).instance.arc.getRequiredCPUs());
            assertTrue(nodes.stream().allMatch(n -> n.range == range));
            // An isolated backlog exercises parent/peer transfers without changing device generation.
            long bytes = 2000000;
            szeged.applications.get(0).receivedData = bytes;
            szeged.applications.get(0).subscribeApplication();
            Timed.simulateUntil(120000000);
            assertEquals(-1, Timed.getNextFire());
            assertTrue(Application.totalBytesOnNetwork > 0);
            assertTrue(cloud.applications.get(0).receivedData > 0, "Cloud must receive offloaded work");
            assertEquals(bytes, Application.allApplications.stream().mapToLong(a -> a.processedData).sum());
            assertTrue(Application.allApplications.stream().allMatch(a -> a.receivedData == a.processedData && a.incomingData == 0));
            System.out.println("CITY_TOPOLOGY_PROBE_OK range=" + range + " cloud_bytes=" + cloud.applications.get(0).receivedData);
        }
    }
}
