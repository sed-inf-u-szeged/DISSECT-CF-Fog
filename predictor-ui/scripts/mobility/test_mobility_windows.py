"""Causality and boundary regression tests; no Java, UI, NumPy or model training."""
import copy
import math
from pathlib import Path
import tempfile
import unittest

from build_mobility_windows import inside, project, unproject, windows_for_run


class MobilityWindowsTest(unittest.TestCase):
    def setUp(self):
        self.run = {'case_id': 'example', 'route_id': 'example', 'split': 'train', 'run_id': 'run-one'}
        self.reference = [47.73, 18.27]
        self.samples = []
        for i in range(121):
            self.samples.append({'run_id': 'run-one', 'device_id': '0', 'time_ms': str(193000+i*60000),
                                 'latitude_deg': str(47.73), 'longitude_deg': str(18.27+i*0.0001),
                                 'motion_valid': 'true' if i else 'false',
                                 'observed_speed_m_s': '2.0' if i else '',
                                 'heading_valid': 'true' if i else 'false',
                                 'heading_deg': '90.0' if i else '', 'current_node': 'NONE'})

    def make(self, samples=None):
        return windows_for_run(self.run, self.samples if samples is None else samples, self.reference)

    def test_window_boundaries_and_first_missing_motion(self):
        windows, skipped = self.make()
        self.assertEqual((len(windows), skipped), (112, 1))
        self.assertEqual((windows[0]['history_start_index'], windows[0]['origin_index'], windows[0]['target_index']), (1, 8, 9))
        self.assertEqual(windows[-1]['target_index'], 120)
        self.assertEqual(windows[0]['x'][-1][:2], [0.0, 0.0])

    def test_future_change_does_not_change_input(self):
        original, _ = self.make()
        changed = copy.deepcopy(self.samples)
        changed[9]['latitude_deg'] = '47.735'
        changed[9]['observed_speed_m_s'] = '999'
        changed[9]['heading_deg'] = '270'
        updated, _ = self.make(changed)
        self.assertEqual(original[0]['x'], updated[0]['x'])
        self.assertNotEqual(original[0]['y'], updated[0]['y'])
        self.assertEqual(original[0]['anchor_latitude_deg'], updated[0]['anchor_latitude_deg'])

    def test_target_recovers_actual_position(self):
        windows, _ = self.make()
        for w in windows:
            anchor = project(w['anchor_latitude_deg'], w['anchor_longitude_deg'], self.reference)
            lat, lon = unproject(anchor[0]+w['y'][0], anchor[1]+w['y'][1], self.reference)
            self.assertAlmostEqual(lat, float(self.samples[w['target_index']]['latitude_deg']), places=11)
            self.assertAlmostEqual(lon, float(self.samples[w['target_index']]['longitude_deg']), places=11)
            self.assertGreater(w['y'][0], 0)
            self.assertAlmostEqual(w['y'][1], 0)

    def test_reject_cross_run_device_and_sampling_gap(self):
        for key, value in [('run_id', 'other'), ('device_id', '1'), ('time_ms', '1')]:
            with self.subTest(key=key):
                bad = copy.deepcopy(self.samples); bad[15][key] = value
                with self.assertRaises(ValueError): self.make(bad)

    def test_heading_wrap_and_stationary_mask(self):
        samples = copy.deepcopy(self.samples)
        samples[1]['heading_deg'] = '359'; samples[2]['heading_deg'] = '1'
        samples[3].update(heading_valid='false', heading_deg='', observed_speed_m_s='0')
        windows, _ = self.make(samples)
        x = windows[0]['x']
        self.assertLess(abs(x[0][3]-x[1][3]), 0.04)
        self.assertAlmostEqual(x[0][4], x[1][4])
        self.assertEqual(x[2][2:], [0.0, 0.0, 0.0, 0])

    def test_disconnected_samples_retained_without_node_input(self):
        windows, _ = self.make()
        changed = copy.deepcopy(self.samples)
        for row in changed: row['current_node'] = 'mobility-west'
        connected, _ = self.make(changed)
        self.assertEqual(len(windows), len(connected))
        self.assertTrue(all(w['origin_node']=='NONE' for w in windows))
        self.assertEqual([w['x'] for w in windows], [w['x'] for w in connected])

    def test_non_finite_motion_is_rejected(self):
        for value in ['NaN', 'inf', '-inf']:
            bad = copy.deepcopy(self.samples); bad[1]['observed_speed_m_s'] = value
            with self.assertRaises(ValueError): self.make(bad)

    def test_path_escape_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp); (root/'ok').write_text('x')
            self.assertEqual(inside(root, 'ok'), root/'ok')
            for path in ['../ok', '/etc/passwd', 'C:\\outside', 'missing']:
                with self.assertRaises(ValueError): inside(root, path)


if __name__ == '__main__':
    unittest.main(verbosity=2)
