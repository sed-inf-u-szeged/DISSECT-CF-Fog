"""City and cloud training must keep one node's identity, without changing legacy selection."""
import sys
import unittest
from pathlib import Path

import pandas as pd

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from predictor_models.lstm_optimized_training import select_target_frames


class CityTrainingTargetTest(unittest.TestCase):
    def setUp(self):
        self.fog = 'Szeged_fog2::notYetProcessedData'
        self.cloud = 'Budapest_cloud::notYetProcessedData'
        self.frames = [pd.DataFrame({self.fog: [100, 200], self.cloud: [0, 25], 'time': [0, 1]})]

    def test_legacy_training_still_excludes_cloud(self):
        frames, names = select_target_frames(self.frames)
        self.assertEqual(names, [self.fog])
        self.assertEqual(frames[0].iloc[:, 0].tolist(), [100, 200])

    def test_named_cloud_does_not_mix_in_fog_values(self):
        frames, names = select_target_frames(self.frames, self.cloud)
        self.assertEqual(names, [self.cloud])
        self.assertEqual(frames[0].iloc[:, 0].tolist(), [0, 25])

    def test_missing_node_cannot_silently_train_on_another_node(self):
        with self.assertRaisesRegex(ValueError, 'unidentified target'):
            select_target_frames(self.frames, 'Other_fog1::notYetProcessedData')


if __name__ == '__main__':
    unittest.main()
