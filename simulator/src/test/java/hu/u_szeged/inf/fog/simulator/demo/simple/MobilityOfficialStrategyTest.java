package hu.u_szeged.inf.fog.simulator.demo.simple;

import static org.junit.jupiter.api.Assertions.*;

import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;
import hu.u_szeged.inf.fog.simulator.iot.Device;
import hu.u_szeged.inf.fog.simulator.iot.mobility.GeoLocation;
import hu.u_szeged.inf.fog.simulator.iot.mobility.MobilitySampleRecorder;
import hu.u_szeged.inf.fog.simulator.iot.mobility.MobilityStrategy;
import hu.u_szeged.inf.fog.simulator.iot.mobility.NomadicMobilityStrategy;
import hu.u_szeged.inf.fog.simulator.iot.mobility.RandomWalkMobilityStrategy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests official movement semantics and the shared observation contract. */
class MobilityOfficialStrategyTest {
    @TempDir Path directory;
    private static final long LIMIT = 14_400_000;
    private static final String NOMADIC = "mobility_strategy=nomadic\nwaypoints_m=0,0;1600,0;1600,600\n";
    private static final String RANDOM = "mobility_strategy=random_walk\nspeed_min_m_s=2\nspeed_max_m_s=6\nradius_m=800\n";

    private MobilityScenarioConfig config(String text) throws Exception {
        Path path = directory.resolve("input.properties");
        Files.writeString(path, text);
        return MobilityScenarioConfig.load(path);
    }

    private Device clock() {
        Device device = new Device() {
            @Override public void tick(long fires) { }
        };
        device.freq = 60000;
        return device;
    }

    @Test void nomadicUsesOfficialClassAndStopsAtLastDestination() throws Exception {
        MobilityScenarioConfig config = config(NOMADIC);
        MobilityStrategy strategy = config.movement(LIMIT);
        assertEquals(NomadicMobilityStrategy.class, strategy.getClass());
        assertFalse(config.repeats());
        NomadicMobilityStrategy movement = (NomadicMobilityStrategy) strategy;
        assertEquals(2, movement.destinations.size());
        GeoLocation last = movement.destinations.toArray(new GeoLocation[0])[1];
        GeoLocation expected = new GeoLocation(last.latitude, last.longitude);
        Device device = clock();
        GeoLocation first = movement.move(device);
        assertEquals(246, config.start().calculateDistance(first), 0.01);
        for (int i = 0; i < 20; i++) { movement.move(device); }
        assertTrue(movement.destinations.isEmpty());
        assertEquals(0, expected.calculateDistance(movement.currentPosition), 1e-6);
        for (int i = 0; i < 10; i++) {
            assertEquals(0, expected.calculateDistance(movement.move(device)), 1e-6);
        }
    }

    @Test void randomWalkMatchesDirectOfficialCallsWithSameSeedAndUnits() throws Exception {
        MobilityScenarioConfig config = config(RANDOM);
        MobilityStrategy actual = config.movement(LIMIT);
        assertEquals(RandomWalkMobilityStrategy.class, actual.getClass());
        MobilityStrategy direct = new RandomWalkMobilityStrategy(config.start(), 0.002, 0.006, 800);
        Device device = clock();
        double[][] positions = new double[300][2];
        SeedSyncer.centralRnd.setSeed(config.seed());
        GeoLocation previous = config.start();
        for (int i = 0; i < positions.length; i++) {
            GeoLocation point = actual.move(device);
            double step = previous.calculateDistance(point);
            assertTrue(step >= 120 - 1e-5 && step <= 360 + 1e-5);
            assertTrue(config.start().calculateDistance(point) <= 800 + 1e-5);
            positions[i] = new double[] {point.latitude, point.longitude};
            previous = new GeoLocation(point.latitude, point.longitude);
        }
        SeedSyncer.centralRnd.setSeed(config.seed());
        for (double[] expected : positions) {
            GeoLocation point = direct.move(device);
            assertEquals(expected[0], point.latitude);
            assertEquals(expected[1], point.longitude);
        }
        SeedSyncer.centralRnd.setSeed(config.seed() + 1);
        GeoLocation other = config.movement(LIMIT).move(device);
        assertFalse(positions[0][0] == other.latitude && positions[0][1] == other.longitude);
    }

    @Test void effectiveConfigsRoundTripWithoutInventingRandomWaypoints() throws Exception {
        for (String source : new String[] {NOMADIC, RANDOM}) {
            MobilityScenarioConfig original = config(source);
            Path output = directory.resolve(original.strategy());
            Files.createDirectory(output);
            original.save(output);
            String before = Files.readString(output.resolve("scenario-effective.properties"));
            MobilityScenarioConfig restored = MobilityScenarioConfig.load(output.resolve("scenario-effective.properties"));
            restored.save(output);
            assertEquals(before, Files.readString(output.resolve("scenario-effective.properties")));
            assertEquals(original.metadata(), restored.metadata());
            assertEquals(!original.isRandomWalk(), Files.exists(output.resolve("route_waypoints.csv")));
            if (original.isRandomWalk()) {
                assertFalse(before.contains("waypoints_m="));
                assertFalse(original.metadata().contains("\nspeed_m_s="));
                assertFalse(original.metadata().contains("route_file="));
            }
        }
    }

    @Test void rejectsUnknownMixedOrInfeasibleConfigurations() {
        for (String text : new String[] {
                "mobility_strategy=unknown", "mobility_strategy=nomadic",
                NOMADIC + "route_repeats=maybe", NOMADIC + "route_repeats=true",
                NOMADIC + "speed_min_m_s=2", RANDOM + "speed_m_s=4",
                RANDOM + "waypoints_m=0,0;100,0", RANDOM + "route_repeats=false",
                "mobility_strategy=random_walk\nspeed_min_m_s=7\nspeed_max_m_s=6",
                "mobility_strategy=random_walk\nspeed_max_m_s=NaN",
                "mobility_strategy=random_walk\nradius_m=360",
                "mobility_strategy=random_walk\nradius_m=0",
                RANDOM + "start_latitude_deg=91", RANDOM + "start_longitude_deg=NaN"}) {
            assertThrows(IllegalArgumentException.class, () -> config(text), text);
        }
    }

    @Test void explicitClosedRouteDoesNotRepeatUnlessRequested() throws Exception {
        String source = "mobility_strategy=nomadic\nwaypoints_m=0,0;600,0;0,0\n";
        NomadicMobilityStrategy once = (NomadicMobilityStrategy) config(source).movement(LIMIT);
        NomadicMobilityStrategy repeated = (NomadicMobilityStrategy) config(source + "route_repeats=true").movement(LIMIT);
        assertEquals(2, once.destinations.size());
        assertTrue(repeated.destinations.size() > 2);
    }

    @Test void configuredStartIsRespectedAndMovementStateIsNotShared() throws Exception {
        for (String source : new String[] {NOMADIC, RANDOM}) {
            MobilityScenarioConfig config = config(source + "start_latitude_deg=46.25\nstart_longitude_deg=20.15\n");
            MobilityStrategy first = config.movement(LIMIT);
            MobilityStrategy second = config.movement(LIMIT);
            assertEquals(46.25, first.startPosition.latitude);
            assertEquals(20.15, first.startPosition.longitude);
            first.currentPosition.latitude = 0;
            assertEquals(46.25, second.currentPosition.latitude);
            assertEquals(46.25, config.start().latitude);
        }
    }

    @Test void bothStrategiesUseIdenticalSampleSchemaAndRetainStationaryRows() throws Exception {
        String header = null;
        for (String source : new String[] {NOMADIC, RANDOM}) {
            MobilityScenarioConfig config = config(source);
            SeedSyncer.centralRnd.setSeed(config.seed());
            MobilityStrategy movement = config.movement(LIMIT);
            Device device = clock();
            Path file = directory.resolve(config.strategy() + ".csv");
            try (MobilitySampleRecorder recorder = new MobilitySampleRecorder(file, "run")) {
                for (int i = 0; i < 121; i++) {
                    GeoLocation point = movement.move(device);
                    recorder.record("0", i * 60000L, point.latitude, point.longitude, "NONE");
                }
            }
            List<String> rows = Files.readAllLines(file);
            assertEquals(122, rows.size());
            if (header == null) { header = rows.get(0); } else { assertEquals(header, rows.get(0)); }
            assertEquals(12, rows.get(0).split(",", -1).length);
            String[] initial = rows.get(1).split(",", -1);
            assertEquals("false", initial[9]);
            for (int i = 5; i < 9; i++) { assertEquals("", initial[i]); }
            String[] last = rows.get(121).split(",", -1);
            assertEquals("true", last[9]);
            assertEquals("\"NONE\"", last[11]);
            if (!config.isRandomWalk()) {
                assertEquals(0, Double.parseDouble(last[7]));
                assertEquals("", last[8]);
                assertEquals("false", last[10]);
            }
        }
    }
}
