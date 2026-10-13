package hu.u_szeged.inf.fog.simulator.prediction.lifecycle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShadowPredictionTest {

    @Test
    void shouldStartAsUnresolved() {
        ShadowPrediction prediction = new ShadowPrediction(
                "linear-regression",
                "cpu",
                1,
                1000,
                75.5
        );

        assertEquals("linear-regression", prediction.getPredictorName());
        assertEquals("cpu", prediction.getFeatureName());
        assertEquals(1, prediction.getPredictionNumber());
        assertEquals(1000, prediction.getTargetTimestamp());
        assertEquals(75.5, prediction.getPredictedValue());
        assertFalse(prediction.isResolved());
    }

    @Test
    void shouldBecomeResolvedWhenActualValueArrives() {
        ShadowPrediction prediction = new ShadowPrediction(
                "linear-regression",
                "cpu",
                1,
                1000,
                75.5
        );

        prediction.resolve(72.0);

        assertTrue(prediction.isResolved());
        assertEquals(72.0, prediction.getActualValue());
    }
}