"""Synthetic protocol tests; model doubles are not TensorFlow performance results."""
import csv
import io
import json
from pathlib import Path
import tempfile
import unittest

from audit_mobility_closed_loop import audit_closed_worker
from build_mobility_windows import read_json, write_json
from check_mobility_run import motion
from mobility_multi_worker import serve_multi
from mobility_online_lstm import training_data
from test_mobility_closed_loop import controlled_stream
from test_mobility_online_buffer import samples
from test_mobility_online_retry import RetryModelDouble


class MultiDeviceTest(unittest.TestCase):
    def setUp(self):
        folder = tempfile.TemporaryDirectory()
        self.addCleanup(folder.cleanup)
        self.root = Path(folder.name)
        self.output = self.root / "output"
        self.output.mkdir()
        self.policy = self.root / "policy.json"
        write_json(self.policy, {})
        self.requests = []
        self.data = []

    def input(self, count=10, special=False, staggered=False):
        streams = []
        for device in range(count):
            data = samples(121, stationary=True)
            for i, row in enumerate(data):
                row.update(run_id="multi-test", device_id=str(device), longitude_deg=str(18.27 + device * .0001))
                if staggered:
                    row["time_ms"] = str(int(row["time_ms"]) + device * 39000)
                if special and device == 0:
                    row["longitude_deg"] = str(18.27 + .000001 * i * i)
                    if i:
                        distance, heading = motion(data[i - 1], row)
                        row.update(displacement_m=str(distance), observed_speed_m_s=str(distance / 60),
                                   heading_deg=str(heading), heading_valid="true")
            schedule = {40: (32, "INITIAL")}
            if not special or device != 0:
                schedule[104] = (96, "REJECTION")
            streams.append(controlled_stream(data, schedule, good=special and device == 0).splitlines())
            self.data.append(data)
        transactions = []
        for i in range(121):
            for device in range(count):
                transactions.append("DEVICE\t%d\n%s\n%s\n" % (device, *streams[device][i * 2:i * 2 + 2]))
        return sorted(transactions, key=lambda item: json.loads(item.splitlines()[2].split("\t", 1)[1])["sim_time_ms"])

    def trainer(self, request, snapshot, directory):
        training_data(request)
        self.requests.append(request)
        return RetryModelDouble(request, snapshot, accelerate=request.windows[0].window.device_id == "0")

    def run_input(self, transactions, count=10, end=True):
        reply = io.StringIO()
        result = serve_multi(self.policy, self.output, "multi-test", count, 121, self.trainer,
                             io.StringIO("".join(transactions) + ("END\n" if end else "")), reply)
        return result, reply.getvalue()

    def test_ten_devices_keep_independent_training_and_qualification(self):
        result, reply = self.run_input(self.input(special=True))
        self.assertEqual(result["status"], "COMPLETE")
        self.assertEqual(len(self.requests), 19)
        self.assertTrue(reply.endswith("DONE\t10\t1210\t1120\n"))
        for i in range(10):
            child = self.output / "devices" / str(i)
            audit = audit_closed_worker(child, self.data[i])
            self.assertEqual(audit["versions"][0]["quality_decision"], "PASS" if i == 0 else "REJECT")
            self.assertEqual(audit["trained_versions"], 1 if i == 0 else 2)
            self.assertEqual(result["devices"][i]["predict"]["count"], 80)
            self.assertEqual(read_json(child / "multi-worker-device.json")["device_id"], str(i))
            for path in child.glob("scheduled-request-*.json"):
                self.assertEqual({w["device_id"] for w in read_json(path)["windows"]}, {str(i)})
        for line in reply.splitlines()[1:-1]:
            prefix, device, payload = line.split("\t", 2)
            self.assertEqual(prefix, "DEVICE")
            if payload.startswith("LIFECYCLE\t"):
                self.assertEqual(json.loads(payload.split("\t", 1)[1])["device_id"], device)

    def test_city_nodes_come_from_run_topology_and_cloud_is_not_device_access(self):
        write_json(self.output / "node_topology.json", dict(schema_version=1, run_id="multi-test", nodes=[
            dict(name="Szeged_fog2", role="fog", serviceable=True),
            dict(name="Budapest_cloud", role="cloud", serviceable=False)]))
        transactions = [s.replace('mobility-west', 'Szeged_fog2').replace('mobility-east', 'Szeged_fog2')
                        for s in self.input(1)]
        result, _ = self.run_input(transactions, 1)
        self.assertEqual(result['status'], 'COMPLETE')
        from mobility_shadow_worker import History
        history = History(['Szeged_fog2'])
        from test_mobility_shadow import raw, sample
        row = sample(0); row['current_node'] = 'Budapest_cloud'
        with self.assertRaisesRegex(ValueError, 'Unexpected node'):
            history.append(raw(row))

    def test_misrouted_observation_is_rejected_before_training(self):
        transactions = self.input(2)
        transactions[0] = transactions[0].replace("multi-test,0,", "multi-test,1,", 1)
        with self.assertRaisesRegex(ValueError, "routed to wrong device"):
            self.run_input(transactions, 2)
        self.assertEqual(self.requests, [])
        self.assertEqual(read_json(self.output / "multi-worker.json")["status"], "FAILED")

    def test_staggered_device_start_times_keep_independent_target_matching(self):
        result, reply = self.run_input(self.input(3, staggered=True), 3)
        self.assertEqual(result["status"], "COMPLETE")
        for i in range(3):
            audit = audit_closed_worker(self.output / "devices" / str(i), self.data[i])
            self.assertEqual(audit["versions"][0]["training_cutoff_sim_ms"], 2593000 + 39000 * i)
            self.assertEqual(audit["versions"][0]["matched_origins"], 64)
        self.assertTrue(reply.endswith("DONE\t3\t363\t336\n"))

    def test_misrouted_control_is_rejected(self):
        transactions = self.input(2)
        transactions[0] = transactions[0].replace('"device_id": "0"', '"device_id": "1"')
        with self.assertRaisesRegex(ValueError, "Control routed"):
            self.run_input(transactions, 2)

    def test_missing_device_observation_round_is_rejected(self):
        transactions = self.input(2)
        del transactions[3]
        with self.assertRaisesRegex(ValueError, "sampling gap"):
            self.run_input(transactions, 2)

    def test_duplicate_device_round_is_rejected(self):
        transactions = self.input(2)
        transactions.insert(1, transactions[0])
        with self.assertRaisesRegex(ValueError, "sampling gap"):
            self.run_input(transactions, 2)

    def test_missing_end_never_reports_complete(self):
        with self.assertRaisesRegex(ValueError, "without complete END"):
            self.run_input(self.input(1), 1, False)
        self.assertEqual(read_json(self.output / "multi-worker.json")["status"], "FAILED")


if __name__ == "__main__":
    unittest.main(verbosity=2)
