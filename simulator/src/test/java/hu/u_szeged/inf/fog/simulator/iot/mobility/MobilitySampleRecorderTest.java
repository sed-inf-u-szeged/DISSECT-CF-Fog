package hu.u_szeged.inf.fog.simulator.iot.mobility;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MobilitySampleRecorderTest {
    @TempDir Path temp;

    @Test void firstSampleHasNoFabricatedSpeedOrHeading() throws Exception {
        Path file = temp.resolve("first.csv");
        try (MobilitySampleRecorder recorder = new MobilitySampleRecorder(file, "run-1")) {
            recorder.record("0", 193000, 47.73, 18.27, "NONE");
            assertEquals(1, recorder.getSampleCount());
        }
        String[] row = rows(file).get(0).split(",", -1);
        assertEquals(12, row.length);
        for (int col = 5; col <= 8; col++) { assertEquals("", row[col]); }
        assertEquals("false", row[9]);
        assertEquals("false", row[10]);
        assertEquals("\"NONE\"", row[11]);
    }

    @Test void computesCardinalAndWraparoundBearingsInDegrees() throws Exception {
        GeoLocation origin = new GeoLocation(47.73, 18.27);
        for (double bearing : new double[] {0, 90, 180, 270, 359}) {
            Path file = temp.resolve("bearing-" + bearing + ".csv");
            GeoLocation target = origin.nextLocation(246, bearing);
            try (MobilitySampleRecorder recorder = new MobilitySampleRecorder(file, "run")) {
                recorder.record("0", 0, origin.latitude, origin.longitude, "west");
                recorder.record("0", 60000, target.latitude, target.longitude, "NONE");
            }
            String[] row = rows(file).get(1).split(",", -1);
            assertEquals(60, Double.parseDouble(row[5]), 1e-10);
            assertEquals(246, Double.parseDouble(row[6]), 1e-6);
            assertEquals(4.1, Double.parseDouble(row[7]), 1e-8);
            double angularError = Math.abs((Double.parseDouble(row[8]) - bearing + 540) % 360 - 180);
            assertTrue(angularError < 1e-6);
            assertEquals("true", row[9]);
            assertEquals("true", row[10]);
        }
    }

    @Test void stationarySamplesHaveZeroSpeedAndUndefinedHeading() throws Exception {
        Path file = temp.resolve("stationary.csv");
        try (MobilitySampleRecorder recorder = new MobilitySampleRecorder(file, "run")) {
            recorder.record("0", 100, 47.73, 18.27, "west");
            recorder.record("0", 60100, 47.73, 18.27, "west");
        }
        String[] row = rows(file).get(1).split(",", -1);
        assertEquals(0, Double.parseDouble(row[7]), 0);
        assertEquals("", row[8]);
        assertEquals("true", row[9]);
        assertEquals("false", row[10]);
    }

    @Test void usesActualElapsedTimeAndKeepsDeviceHistoriesSeparate() throws Exception {
        Path file = temp.resolve("devices.csv");
        GeoLocation start = new GeoLocation(0, 0);
        GeoLocation target = start.nextLocation(300, 90);
        try (MobilitySampleRecorder recorder = new MobilitySampleRecorder(file, "run")) {
            recorder.record("a", 1000, 0, 0, "NONE");
            recorder.record("b", 500, 40, 20, "east");
            recorder.record("a", 31000, target.latitude, target.longitude, "west");
            recorder.record("b", 60500, 40, 20, "NONE");
        }
        String[] a = rows(file).get(2).split(",", -1);
        String[] b = rows(file).get(3).split(",", -1);
        assertEquals(30, Double.parseDouble(a[5]), 0);
        assertEquals(10, Double.parseDouble(a[7]), 1e-8);
        assertEquals(0, Double.parseDouble(b[7]), 0);
    }

    @Test void rejectsInvalidObservationsWithoutAdvancingHistory() throws Exception {
        Path file = temp.resolve("invalid.csv");
        try (MobilitySampleRecorder recorder = new MobilitySampleRecorder(file, "run")) {
            recorder.record("0", 1000, 0, 0, "NONE");
            assertThrows(IllegalArgumentException.class, () -> recorder.record("0", 1000, 0, 1, "NONE"));
            assertThrows(IllegalArgumentException.class, () -> recorder.record("0", 999, 0, 1, "NONE"));
            assertThrows(IllegalArgumentException.class, () -> recorder.record("0", -1, 0, 0, "NONE"));
            assertThrows(IllegalArgumentException.class, () -> recorder.record("0", 2000, Double.NaN, 0, "NONE"));
            assertThrows(IllegalArgumentException.class, () -> recorder.record("0", 2000, 91, 0, "NONE"));
            assertThrows(IllegalArgumentException.class, () -> recorder.record("0", 2000, 0, 181, "NONE"));
            assertThrows(IllegalArgumentException.class, () -> recorder.record("", 2000, 0, 0, "NONE"));
            recorder.record("0", 2000, 0, 0, "NONE");
            assertEquals(2, recorder.getSampleCount());
        }
        assertEquals(2, rows(file).size());
        assertEquals("1.0", rows(file).get(1).split(",", -1)[5]);
    }

    @Test void takesShortestDistanceAcrossDateLine() throws Exception {
        Path file = temp.resolve("dateline.csv");
        try (MobilitySampleRecorder recorder = new MobilitySampleRecorder(file, "run")) {
            recorder.record("0", 0, 0, 179.999, "NONE");
            recorder.record("0", 60000, 0, -179.999, "NONE");
        }
        String[] row = rows(file).get(1).split(",", -1);
        assertEquals(222.6389816, Double.parseDouble(row[6]), 1e-5);
        assertEquals(90, Double.parseDouble(row[8]), 1e-6);
    }

    @Test void antipodalAndPolarOriginsHaveNoUniqueHeading() throws Exception {
        Path file = temp.resolve("singular.csv");
        try (MobilitySampleRecorder recorder = new MobilitySampleRecorder(file, "run")) {
            recorder.record("a", 0, 0, 0, "NONE");
            recorder.record("a", 60000, 0, 180, "NONE");
            recorder.record("b", 0, 90, 0, "NONE");
            recorder.record("b", 60000, 89, 0, "NONE");
        }
        for (int index : new int[] {1, 3}) {
            String[] row = rows(file).get(index).split(",", -1);
            assertTrue(Double.isFinite(Double.parseDouble(row[7])));
            assertEquals("", row[8]);
            assertEquals("false", row[10]);
        }
    }

    @Test void escapesTextAndRefusesToOverwriteExistingData() throws Exception {
        Path file = temp.resolve("escaping.csv");
        try (MobilitySampleRecorder recorder = new MobilitySampleRecorder(file, "run,\"1\"")) {
            recorder.record("device,1", 0, 0, 0, "node,\"west\"");
        }
        assertTrue(rows(file).get(0).startsWith("\"run,\"\"1\"\"\",\"device,1\","));
        assertTrue(rows(file).get(0).endsWith("\"node,\"\"west\"\"\""));
        assertThrows(FileAlreadyExistsException.class, () -> new MobilitySampleRecorder(file, "run"));
    }

    private static List<String> rows(Path file) throws Exception {
        List<String> lines = Files.readAllLines(file);
        return lines.subList(1, lines.size());
    }
}
