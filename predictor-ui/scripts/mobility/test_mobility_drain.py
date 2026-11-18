"""Checks experiment-limit overrides without extending the sampling window."""
import contextlib
import io
import unittest

from run_mobility_multi import main, scenario_with_limits


class MobilityDrainTest(unittest.TestCase):
    def test_case_copy_replaces_limits_and_preserves_route(self):
        original = ("# route chosen by user\nmobility_strategy=nomadic\n"
                    "waypoints_m=0,0;1600,0;1600,600\nroute_repeats=false\n"
                    "duration_minutes = 360\ndrain_minutes: 60\ndrain_minutes=120\n")
        result = scenario_with_limits(original, 120, 1440)
        self.assertEqual(result.count("duration_minutes"), 1)
        self.assertEqual(result.count("drain_minutes"), 1)
        self.assertTrue(result.endswith("duration_minutes=120\ndrain_minutes=1440\n"))
        self.assertIn("waypoints_m=0,0;1600,0;1600,600\nroute_repeats=false\n", result)
        self.assertIn("# route chosen by user\n", result)
        self.assertIn("duration_minutes = 360", original)

    def test_invalid_drain_fails_before_creating_batch(self):
        for value in ("0", "-1", "1441"):
            with self.subTest(value=value), contextlib.redirect_stderr(io.StringIO()) as error:
                self.assertEqual(main(["--devices", "100", "--drain-minutes", value]), 1)
                self.assertIn("Use 1-1440 drain minutes", error.getvalue())


if __name__ == "__main__":
    unittest.main(verbosity=2)
