package hu.u_szeged.inf.fog.simulator.prediction.settings;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SequenceTrainingRequirementsTest {

    @Test
    void currentSequenceConfigurationRequires287Points() {
        OnlineTrainingSettings settings = OnlineTrainingSettings.getDefault();

        assertEquals(287, settings.getInitialWindowPoints(192, 64));
    }

    @Test
    void shorterSequenceUsesItsOwnLengthsAndSampleCount() {
        OnlineTrainingSettings settings =
                new OnlineTrainingSettings(16, 24, 24, 1024);

        assertEquals(135, settings.getInitialWindowPoints(96, 24));
    }

    @Test
    void longerSequenceUsesItsOwnLengthsAndSampleCount() {
        OnlineTrainingSettings settings =
                new OnlineTrainingSettings(128, 96, 96, 4096);

        assertEquals(735, settings.getInitialWindowPoints(512, 96));
    }

    @Test
    void oneTrainingSampleNeedsOneCompleteInputAndTargetWindow() {
        OnlineTrainingSettings settings =
                new OnlineTrainingSettings(1, 1, 1, 256);

        assertEquals(256, settings.getInitialWindowPoints(192, 64));
    }

    @Test
    void insufficientMaximumWindowIsRejected() {
        OnlineTrainingSettings settings =
                new OnlineTrainingSettings(32, 64, 64, 286);

        assertThrows(IllegalArgumentException.class,
                () -> settings.getInitialWindowPoints(192, 64));
    }

    @Test
    void invalidSequenceLengthsAreRejected() {
        OnlineTrainingSettings settings = OnlineTrainingSettings.getDefault();

        assertThrows(IllegalArgumentException.class,
                () -> settings.getInitialWindowPoints(0, 64));
        assertThrows(IllegalArgumentException.class,
                () -> settings.getInitialWindowPoints(192, -1));
    }

    @Test
    void largeLengthsCannotOverflowIntoAnAcceptedWindow() {
        OnlineTrainingSettings settings =
                new OnlineTrainingSettings(32, 64, 64, Integer.MAX_VALUE);

        assertThrows(IllegalArgumentException.class,
                () -> settings.getInitialWindowPoints(Integer.MAX_VALUE, 64));
    }
}