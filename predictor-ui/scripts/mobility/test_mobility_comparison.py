"""Real model persistence and causal-boundary checks (not accuracy benchmarks)."""
from dataclasses import replace
from pathlib import Path
import tempfile
import unittest
import numpy as np
from build_mobility_windows import write_json
from mobility_comparison_models import Candidate, METHODS, imm_predict, validate_methods
from mobility_online_buffer import OnlineSampleBuffer
from mobility_online_lstm import tensorflow_runtime, training_data
from test_mobility_online_buffer import samples, feed
import test_mobility_ui
from run_mobility_ui import validate_config


class ComparisonModelTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tf = tensorflow_runtime()

    def setUp(self):
        self.folder = tempfile.TemporaryDirectory()
        self.addCleanup(self.folder.cleanup)
        self.root = Path(self.folder.name)
        buffer = OnlineSampleBuffer()
        events = feed(buffer, samples(41), final=False)
        self.request = events[-1][3]
        self.snapshot = self.root / 'request.json'
        write_json(self.snapshot, dict(self.request.metadata(), windows=[w.record() for w in self.request.windows]))

    def test_model_selection_is_optional_and_strict(self):
        cfg = test_mobility_ui.UiConfigurationTest().config()
        self.assertEqual(validate_config(cfg)['models'], [])
        self.assertEqual(validate_methods(['TCN', 'IMM/Kalman']), ['IMM/Kalman', 'TCN'])
        for invalid in ['TCN', None, ['GRU'], ['TCN', 'TCN'], [True]]:
            with self.subTest(invalid=invalid), self.assertRaises(ValueError):
                validate_config(dict(cfg, models=invalid))

    def test_imm_has_sensible_stationary_and_constant_velocity_predictions(self):
        x = np.zeros((2, 8, 6))
        x[1, :, 0] = np.arange(-7, 1) * 120
        x[1, :, 1] = np.arange(-7, 1) * -60
        predicted = imm_predict(x)
        np.testing.assert_allclose(predicted[0], [0, 0], atol=1e-8)
        np.testing.assert_allclose(predicted[1], [120, -60], atol=1)
        np.testing.assert_array_equal(x[1, -1], np.zeros(6))

    def test_real_training_reload_batch_invariance_and_artifact_integrity(self):
        x = training_data(self.request)['x']
        for method in METHODS:
            with self.subTest(method=method):
                directory = self.root / method.replace('/', '-')
                candidate = Candidate.fit(method, self.tf, self.request, self.snapshot, directory)
                replay = Candidate.load(self.tf, directory)
                predictions = candidate.predict(x)
                np.testing.assert_allclose(predictions, replay.predict(x), rtol=1e-5, atol=1e-5)
                np.testing.assert_allclose(predictions[:1], replay.predict(x[:1]), rtol=1e-5, atol=1e-5)
                self.assertEqual(candidate.metadata['fit_windows'], 0 if method == 'IMM/Kalman' else 32)
                artifact = directory / next(iter(candidate.metadata['artifacts']))
                with artifact.open('ab') as stream:
                    stream.write(b'changed')
                with self.assertRaisesRegex(ValueError, 'artifact changed'):
                    Candidate.load(self.tf, directory)

    def test_future_targets_and_changed_snapshot_are_rejected_before_fit(self):
        for method in METHODS:
            with self.assertRaisesRegex(ValueError, 'Unavailable training label'):
                Candidate.fit(method, self.tf, replace(self.request, requested_at_sim_ms=self.request.requested_at_sim_ms-1),
                              self.snapshot, self.root / 'bad')
        changed = dict(self.request.metadata(), windows=[w.record() for w in self.request.windows])
        changed['windows'][0]['y'] = [999, 999]
        write_json(self.snapshot, changed)
        with self.assertRaisesRegex(ValueError, 'snapshot differs'):
            Candidate.fit('XGBoost', self.tf, self.request, self.snapshot, self.root / 'bad')
        self.assertFalse((self.root / 'bad').exists())


if __name__ == '__main__':
    unittest.main()
