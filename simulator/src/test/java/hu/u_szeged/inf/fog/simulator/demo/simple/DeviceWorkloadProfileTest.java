package hu.u_szeged.inf.fog.simulator.demo.simple;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DeviceWorkloadProfileTest {
    @TempDir Path directory;

    @Test void omittedProfilePreservesConstantInput() {
        DeviceWorkloadProfile profile = MobilityScenarioConfig.defaults().deviceWorkload();
        assertEquals(DeviceWorkloadProfile.CONSTANT, profile);
        for (int device = 0; device < 100; device++) {
            for (int minute = 0; minute < 1440; minute++) { assertEquals(100, profile.bytesAt(device, minute)); }
        }
    }

    @Test void burstHasQuietPeriodRisePeakFallAndStablePeriod() {
        DeviceWorkloadProfile p = DeviceWorkloadProfile.PERIODIC_BURST;
        assertEquals(100, p.bytesAt(0, 0));
        assertEquals(100, p.bytesAt(0, 7));
        assertEquals(300, p.bytesAt(0, 8));
        assertEquals(1900, p.bytesAt(0, 16));
        assertEquals(300, p.bytesAt(0, 24));
        assertEquals(100, p.bytesAt(0, 25));
        long total = 0;
        for (int minute = 0; minute < 32; minute++) {
            total += p.bytesAt(0, minute);
            for (int device = 0; device < 100; device++) {
                long bytes = p.bytesAt(device, minute);
                assertTrue(bytes >= 100 && bytes <= 1900);
                assertEquals(bytes, p.bytesAt(device, minute + 32));
                assertEquals(p.bytesAt(0, minute + device * 3L), bytes);
            }
        }
        assertEquals(19400, total);
    }

    @Test void invalidProfilesAndIndicesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> DeviceWorkloadProfile.parse("unknown"));
        assertThrows(IllegalArgumentException.class, () -> DeviceWorkloadProfile.CONSTANT.bytesAt(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> DeviceWorkloadProfile.PERIODIC_BURST.bytesAt(0, -1));
    }

    @Test void savedProfileReproducesInputAndDoesNotClaimConstantRate() throws Exception {
        Path input = directory.resolve("input.properties");
        Files.writeString(input, "mobility_strategy=random_walk\ndevice_workload=periodic-burst\n");
        MobilityScenarioConfig config = MobilityScenarioConfig.load(input);
        config.save(directory);
        MobilityScenarioConfig loaded = MobilityScenarioConfig.load(directory.resolve("scenario-effective.properties"));
        assertEquals(DeviceWorkloadProfile.PERIODIC_BURST, loaded.deviceWorkload());
        assertEquals(config.metadata(), loaded.metadata());
        assertFalse(config.metadata().contains("device_bytes_per_minute=100"));
        assertTrue(config.metadata().contains("device_bytes_max=1900"));
    }
}
