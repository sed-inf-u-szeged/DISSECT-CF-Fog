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

    def test_device_demand_is_explicit_and_defaults_to_original_constant(self):
        self.assertEqual(validate_config(self.config())['deviceWorkload'], 'constant')
        self.assertIn('device_workload=constant\n', scenario_text(self.config()))
        self.assertIn('device_workload=periodic-burst\n', scenario_text(self.config(deviceWorkload='periodic-burst')))
        for value in ['unknown', None, True, 'constant\nseed=1']:
            with self.subTest(value=value), self.assertRaises(ValueError):
                validate_config(self.config(deviceWorkload=value))

    def test_load_models_are_opt_in_and_have_separate_quality_settings(self):
        self.assertEqual(validate_config(self.config())['loadModels'], [])
        cfg = validate_config(self.config(loadModels=['LSTM','TCN'], loadProfile='candidate', loadEpochs=40))
        self.assertEqual(cfg['loadModels'], ['LSTM','TCN'])
        self.assertNotIn('loadModels', scenario_text(cfg))
        for changes in [dict(loadModels=['XGBoost']), dict(loadModels=['LSTM','LSTM']),
                        dict(loadProfile='production'), dict(loadEpochs=True), dict(loadEpochs=0)]:
            with self.subTest(changes=changes), self.assertRaises(ValueError):
                validate_config(self.config(**changes))

    def test_city_infrastructure_keeps_roles_and_placement_explicit(self):
        self.assertEqual(validate_config(self.config())['infrastructure'], 'two-node')
        text = scenario_text(self.config(infrastructure='hungary-cities', cityPlacement='Szeged_fog2', cityCoverageKm=20))
        self.assertIn('infrastructure=hungary-cities\n', text)
        self.assertIn('city_placement=Szeged_fog2\n', text)
        self.assertIn('city_coverage_km=20\n', text)
        for fields in [dict(cityPlacement='Budapest_cloud'), dict(cityPlacement='unknown'),
                       dict(cityCoverageKm=0), dict(cityCoverageKm=2.5), dict(cityCoverageKm=True)]:
            with self.subTest(fields=fields), self.assertRaises(ValueError):
                validate_config(self.config(infrastructure='hungary-cities', **fields))


if __name__=='__main__': unittest.main()
