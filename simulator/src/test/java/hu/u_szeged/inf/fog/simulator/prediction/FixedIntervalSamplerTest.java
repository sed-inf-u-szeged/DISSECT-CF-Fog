package hu.u_szeged.inf.fog.simulator.prediction;

import static org.junit.jupiter.api.Assertions.*;
import hu.mta.sztaki.lpds.cloud.simulator.DeferredEvent;
import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FixedIntervalSamplerTest {
    @BeforeEach void reset() { Timed.resetTimed(); }
    @AfterEach void cleanup() { Timed.resetTimed(); }

    private void at(long time, Runnable action) {
        new DeferredEvent(time) {
            @Override protected void eventAction() { action.run(); }
        };
    }

    @Test void observesAllSameTimeEventsButNeverFutureEvents() throws Exception {
        List<Long> times = new ArrayList<>();
        List<Integer> values = new ArrayList<>();
        int[] value = {0};
        at(60, () -> { value[0]++; at(0, () -> value[0]++); });
        at(60, () -> value[0]++);
        at(61, () -> value[0] += 100);
        at(120, () -> value[0]++);
        long count = FixedIntervalSampler.simulate(60, 120, 200, (index, time) -> {
            assertEquals(times.size(), index);
            times.add(time);
            values.add(value[0]);
        });
        assertEquals(List.of(0L, 60L, 120L), times);
        assertEquals(List.of(0, 3, 104), values);
        assertEquals(3, count);
        assertEquals(-1, Timed.getNextFire());
    }

    @Test void samplesBothNodesOnceDespiteStaggeredDeviceEvents() throws Exception {
        long[] loads = {0, 0};
        for (int device = 0; device < 10; device++) {
            int node = device % 2;
            at(3 + device * 5, () -> loads[node] += 100);
        }
        at(125, () -> loads[0] = 0);
        List<String> rows = new ArrayList<>();
        FixedIntervalSampler.simulate(60, 120, 200, (index, time) -> {
            for (int node = 0; node < 2; node++) {
                rows.add(index + ":" + time + ":" + node + ":" + loads[node]);
            }
        });
        assertEquals(List.of("0:0:0:0", "0:0:1:0", "1:60:0:500", "1:60:1:500",
                "2:120:0:500", "2:120:1:500"), rows);
        assertEquals(0, loads[0]); // Normal draining continues after sampling ends.
        assertEquals(126, Timed.getFireCount());
    }

    @Test void doesNotManufactureSamplesAfterEarlyCompletion() throws Exception {
        at(61, () -> { });
        List<Long> times = new ArrayList<>();
        assertEquals(2, FixedIntervalSampler.simulate(60, 600, 1200,
                (index, time) -> times.add(time)));
        assertEquals(List.of(0L, 60L), times);
        assertEquals(62, Timed.getFireCount());
        assertEquals(-1, Timed.getNextFire());
    }

    @Test void cannotKeepAnEmptySimulationAlive() throws Exception {
        assertEquals(0, FixedIntervalSampler.simulate(60, 120, 240,
                (index, time) -> fail("Empty simulation must not produce a tail")));
        assertEquals(0, Timed.getFireCount());
    }

    private List<String> trace(boolean sampled) throws Exception {
        Timed.resetTimed();
        Random random = new Random(42);
        List<String> trace = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            int id = i;
            at(10 + (i % 5) * 60, () -> trace.add(id + ":" + Timed.getFireCount() + ":" + random.nextLong()));
        }
        if (sampled) {
            FixedIntervalSampler.simulate(60, 240, 500, (index, time) -> { });
        } else {
            Timed.simulateUntil(500);
        }
        trace.add("end:" + Timed.getFireCount() + ":" + Timed.getNextFire());
        return trace;
    }

    @Test void preservesOriginalEventOrderingAndRandomDraws() throws Exception {
        assertEquals(trace(false), trace(true));
    }

    @Test void leavesUnfinishedWorkVisibleAtTheSimulationLimit() throws Exception {
        at(1000, () -> fail("Future event must not be processed"));
        assertEquals(3, FixedIntervalSampler.simulate(60, 120, 240, (index, time) -> { }));
        assertEquals(1000, Timed.getNextFire());
        assertEquals(240, Timed.getFireCount());
    }

    @Test void rejectsInvalidIntervalsAndReusingAnAdvancedClock() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> FixedIntervalSampler.simulate(0, 120, 240, (index, time) -> { }));
        assertThrows(IllegalArgumentException.class,
                () -> FixedIntervalSampler.simulate(60, 300, 240, (index, time) -> { }));
        Timed.jumpTime(1);
        assertThrows(IllegalArgumentException.class,
                () -> FixedIntervalSampler.simulate(60, 120, 240, (index, time) -> { }));
    }
}
