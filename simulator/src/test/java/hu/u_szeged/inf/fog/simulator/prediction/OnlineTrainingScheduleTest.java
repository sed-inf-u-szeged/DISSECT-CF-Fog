package hu.u_szeged.inf.fog.simulator.prediction;

import static org.junit.jupiter.api.Assertions.*;
import hu.u_szeged.inf.fog.simulator.prediction.settings.OnlineTrainingSettings;
import org.junit.jupiter.api.Test;

class OnlineTrainingScheduleTest {
    final OnlineTrainingSettings settings = OnlineTrainingSettings.getDefault();

    @Test
    void aLateJoiningFeatureMustWaitForNewDataAfterItsOwnEvaluationStart() {
        assertFalse(OnlineTrainingSchedule.needsTraining(127, 32, 64, true, true, false, false, settings));
        assertTrue(OnlineTrainingSchedule.needsTraining(128, 32, 64, true, true, false, false, settings));
        assertFalse(OnlineTrainingSchedule.needsTraining(160, 128, 64, true, true, false, false, settings));
    }

    @Test
    void pendingEvaluationOrUsableOwnerBlocksRetrainingEvenWithEnoughData() {
        assertFalse(OnlineTrainingSchedule.needsTraining(1000, 32, 32, true, true, true, false, settings));
        assertFalse(OnlineTrainingSchedule.needsTraining(1000, 32, 32, true, true, false, true, settings));
        assertFalse(OnlineTrainingSchedule.needsTraining(1000, 32, 32, true, false, false, false, settings));
        assertTrue(OnlineTrainingSchedule.needsTraining(32, -1, -1, false, false, false, false, settings));
    }

    @Test
    void sharedGrowthPreservesLoadUnitsAndCapsWithoutOverflow() {
        assertEquals(351, OnlineTrainingSchedule.afterRejection(287, settings));
        assertEquals(96, OnlineTrainingSchedule.afterRejection(32, settings));
        assertEquals(4096, OnlineTrainingSchedule.afterRejection(4080, settings));
        OnlineTrainingSettings large = new OnlineTrainingSettings(32, 64, 64, Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, OnlineTrainingSchedule.afterRejection(Integer.MAX_VALUE - 2, large));
    }
}
