package hu.u_szeged.inf.fog.simulator.prediction.settings;

import hu.u_szeged.inf.fog.simulator.prediction.lifecycle.LifecyclePolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LifecycleSettingsTest {

    @Test
    void shouldConvertSettingsToLifecyclePolicy() {
        LifecycleSettings settings = new LifecycleSettings(
                true,
                25,
                1.5,
                2.5,
                3.5,
                4.5,
                95.0,
                0.1
        );

        LifecyclePolicy policy = settings.toPolicy();

        assertTrue(settings.isEnabled());
        assertEquals(25, policy.getMinimumMatchedSamples());
        assertEquals(1.5, policy.getMaximumMae());
        assertEquals(2.5, policy.getMaximumMse());
        assertEquals(3.5, policy.getMaximumRmse());
        assertEquals(4.5, policy.getMaximumSmape());
        assertEquals(95.0, policy.getMinimumAccuracy());
        assertEquals(0.1, policy.getAccuracyTolerance());
    }
}