package hu.u_szeged.inf.fog.simulator.prediction.mobility;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MobilityPositionBaselinesTest {
    @TempDir Path directory;
    @Test void startsWithoutTrainingAndWaitsForRealTargetsEvenWhenDisconnected() throws Exception {
        try (MobilityPositionBaselines b = new MobilityPositionBaselines(directory, "test", "2")) {
            b.observe(1000, 0, 0, "NONE", true);
            assertEquals(1, b.preview().getJSONArray("predictions").length());
            assertEquals(0, b.preview().getJSONArray("evaluations").length());
            assertEquals("HoldPosition", b.preview().getJSONArray("predictions").getJSONObject(0).getString("model"));
            b.observe(61000, 0, .001, "city", true);
            assertEquals(2, b.preview().getJSONArray("predictions").length());
            JSONObject first = b.preview().getJSONArray("evaluations").getJSONObject(0);
            assertEquals(111.31949, first.getDouble("position_error_m"), 1e-4);
            JSONObject cv = b.preview().getJSONArray("predictions").getJSONObject(1);
            assertEquals(.002, cv.getDouble("predicted_longitude_deg"), 1e-10);
            assertEquals(121000, cv.getLong("target_ms"));
            b.observe(121000, 0, .002, "city", false);
            assertEquals(0, b.preview().getJSONArray("predictions").length());
            assertEquals(0, b.preview().getJSONArray("evaluations").getJSONObject(1).getDouble("position_error_m"), 1e-10);
        }
        assertEquals(4, Files.readAllLines(directory.resolve("position_baseline_predictions.csv")).size());
        assertEquals(4, Files.readAllLines(directory.resolve("position_baseline_evaluations.csv")).size());
    }
    @Test void refusesToMatchAnUnobservedTargetAcrossAGap() throws Exception {
        try (MobilityPositionBaselines b = new MobilityPositionBaselines(directory, "test", "0")) {
            b.observe(1000, 0, 0, "NONE", true);
            assertThrows(IOException.class, () -> b.observe(121000, 0, .002, "NONE", true));
        }
    }
}
