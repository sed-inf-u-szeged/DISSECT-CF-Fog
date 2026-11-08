package hu.u_szeged.inf.fog.simulator.demo.simple;

import hu.u_szeged.inf.fog.simulator.iot.mobility.GeoLocation;
import hu.u_szeged.inf.fog.simulator.iot.mobility.NomadicMobilityStrategy;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class MobilityScenarioConfigTest {
    @TempDir Path directory;

    private MobilityScenarioConfig read(String text) throws Exception {
        Path file = directory.resolve("input.properties");
        Files.writeString(file, text);
        return MobilityScenarioConfig.load(file);
    }

    @Test void defaultsPreserveRectangleAndUnitConversion() {
        MobilityScenarioConfig config = MobilityScenarioConfig.defaults();
        assertEquals(42, config.seed());
        assertEquals(4.1, config.speedMetersPerSecond());
        NomadicMobilityStrategy movement = config.movement(14_400_000);
        assertEquals(0.0041, movement.speed);
        assertEquals(128, movement.destinations.size());
        GeoLocation first = movement.destinations.peek();
        GeoLocation expected = config.start().nextLocation(1600, 90);
        assertEquals(expected.latitude, first.latitude);
        assertEquals(expected.longitude, first.longitude);
    }

    @Test void savedConfigReproducesCoordinatesAndSeed() throws Exception {
        MobilityScenarioConfig config = read("scenario_name=triangle\nseed=93\nspeed_m_s=3.2\n"
                + "waypoints_m=0,0;1600,0;800,1500;0,0\n");
        config.save(directory);
        String coordinates = Files.readString(directory.resolve("route_waypoints.csv"));
        MobilityScenarioConfig restored = MobilityScenarioConfig.load(directory.resolve("scenario-effective.properties"));
        restored.save(directory);
        assertEquals(coordinates, Files.readString(directory.resolve("route_waypoints.csv")));
        assertEquals(93, restored.seed());
        assertEquals(3.2, restored.speedMetersPerSecond());
        assertEquals(1500, restored.heightMeters());
    }

    @Test void rejectsInvalidOrMistypedSettings() {
        for (String text : new String[] {"speed_m_s=NaN", "speed_m_s=0", "speed_m_s=51",
                "seed=1.5", "speeed_m_s=3", "scenario_name=a b", "waypoints_m=0,0;1,1",
                "waypoints_m=0,0;100,100;1,0", "waypoints_m=0,0;0,0;0,0",
                "waypoints_m=0,0;NaN,100;0,0", "waypoints_m=0,0;10001,0;0,0",
                "waypoints_m=0,0;10,0;0,0", "waypoints_m=0,0;100,0;0,0;"}) {
            assertThrows(IllegalArgumentException.class, () -> read(text), text);
        }
    }

    @Test void waypointsAndRunsDoNotShareMutableLocations() {
        MobilityScenarioConfig config = MobilityScenarioConfig.defaults();
        NomadicMobilityStrategy first = config.movement(14_400_000);
        GeoLocation[] points = first.destinations.toArray(new GeoLocation[0]);
        double original = points[4].latitude;
        points[0].latitude = -80;
        assertEquals(original, points[4].latitude);
        assertEquals(original, config.movement(14_400_000).destinations.peek().latitude);
        assertNotSame(first.currentPosition, config.start());
    }

    @Test void shortFastRoutesHaveEnoughWaypointsForWholeRun() throws Exception {
        MobilityScenarioConfig config = read("speed_m_s=50\nwaypoints_m=0,0;60,0;0,0\n");
        NomadicMobilityStrategy movement = config.movement(14_400_000);
        double length = 0;
        GeoLocation previous = config.start();
        for (GeoLocation next : movement.destinations) { length += previous.calculateDistance(next); previous = next; }
        assertTrue(length > 50 * 14400);
    }
}
