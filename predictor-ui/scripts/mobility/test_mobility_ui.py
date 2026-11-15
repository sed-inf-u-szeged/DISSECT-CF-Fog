"""Configuration boundary tests for the UI-to-official-strategy adapter."""
import unittest
from run_mobility_ui import validate_config, scenario_text


class UiConfigurationTest(unittest.TestCase):
    def config(self, **changes):
        return dict(dict(strategy='random-walk', devices=10, minutes=120, drainMinutes=1440,
                         seed=42, latitude=47.73, longitude=18.27, speedMin=2, speedMax=6,
                         radius=800, speed=4.1, waypoints=[[0,0],[1600,0],[1600,600]], repeatRoute=False), **changes)

    def test_strategies_generate_only_their_supported_properties(self):
        walk=scenario_text(self.config())
        self.assertIn('mobility_strategy=random_walk\n',walk)
        self.assertIn('radius_m=800.0\n',walk)
        self.assertNotIn('waypoints_m=',walk)
        nomadic=scenario_text(self.config(strategy='nomadic'))
        self.assertIn('mobility_strategy=nomadic\n',nomadic)
        self.assertIn('waypoints_m=0,0;1600,0;1600,600\n',nomadic)
        self.assertNotIn('radius_m=',nomadic)
        self.assertNotIn('speed_min_m_s=',nomadic)

    def test_rejects_invalid_or_unsafe_settings(self):
        for changes in [dict(strategy=''),dict(devices=101),dict(devices=True),dict(devices=1.5),
                        dict(minutes=103),dict(drainMinutes=0),dict(latitude=float('nan')),
                        dict(seed=-1),dict(radius=360),dict(speedMin=7),dict(speedMax='6\nseed=2'),
                        dict(output='/tmp/overwrite')]:
            with self.subTest(changes=changes), self.assertRaises(ValueError):
                validate_config(self.config(**changes))

    def test_nomadic_route_constraints_and_repeat(self):
        for points,repeat in [([[1,0],[1600,0]],False),([[0,0],[0,0]],False),
                              ([[0,0],[99,0]],False),([[0,0],[1600,0]],True),
                              ([[0,0],[float('inf'),0]],False)]:
            with self.subTest(points=points),self.assertRaises(ValueError):
                validate_config(self.config(strategy='nomadic',waypoints=points,repeatRoute=repeat))
        result=scenario_text(self.config(strategy='nomadic',waypoints=[[0,0],[1600,0],[0,0]],repeatRoute=True))
        self.assertIn('route_repeats=true',result)

    def test_drain_and_sampling_remain_independent(self):
        cfg=validate_config(self.config(minutes=120,drainMinutes=1440))
        self.assertEqual(cfg['minutes'],120)
        self.assertEqual(cfg['drainMinutes'],1440)


if __name__=='__main__': unittest.main()
