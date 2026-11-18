"""Contract failures that would corrupt a future node-load predictor's inputs."""
import csv
import json
from pathlib import Path
import tempfile
import unittest

from audit_node_load_samples import audit, HEADER, PHASE


class NodeLoadSamplesTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        self.meta = dict(schema_version=1, sample_phase=PHASE, run_id="run-1", node_ids=["west", "east"],
                         sample_count_per_node=2, interval_ms=60000, start_ms=0, last_sample_ms=60000,
                         sample_until_ms=60000, simulation_end_ms=120001, rows=4)
        self.rows = []
        for index in range(2):
            for node in self.meta["node_ids"]:
                row = dict.fromkeys(HEADER[5:], 0)
                row.update(run_id="run-1", node_id=node, sample_index=index, time_ms=index * 60000, sample_phase=PHASE)
                if index == 1:
                    row.update(waiting_assignment_bytes=90 if node == "west" else 10,
                               net_received_counter_bytes=90 if node == "west" else 10,
                               device_upload_completed_bytes_derived=100 if node == "west" else 0,
                               offload_sent_bytes_total=10 if node == "west" else 0,
                               offload_received_bytes_total=10 if node == "east" else 0)
                self.rows.append(row)
        (self.root / "offload_events.csv").write_text(
            "transfer_id,time_ms,event,source_node,destination_node,bytes,event_sequence\n"
            "1,60000,START,west,east,10,1\n1,60000,COMPLETE,west,east,10,2\n", encoding="utf-8")

    def save(self):
        (self.root / "node_load_sampling.json").write_text(json.dumps(self.meta), encoding="utf-8")
        with (self.root / "node_load_samples.csv").open("w", encoding="utf-8", newline="") as stream:
            writer = csv.DictWriter(stream, fieldnames=HEADER)
            writer.writeheader()
            writer.writerows(self.rows)

    def test_accepts_boundary_transfers_and_includes_idle_nodes(self):
        self.save()
        self.assertEqual(audit(self.root)["rows"], 4)

    def test_rejects_duplicate_nodes_or_device_triggered_times(self):
        for key, value in (("node_id", "west"), ("time_ms", 61000), ("run_id", "other")):
            with self.subTest(key=key):
                previous = self.rows[3][key]
                self.rows[3][key] = value
                self.save()
                with self.assertRaises(ValueError):
                    audit(self.root)
                self.rows[3][key] = previous

    def test_rejects_future_transfer_counters(self):
        self.rows[1].update(waiting_assignment_bytes=10, net_received_counter_bytes=10,
                            offload_received_bytes_total=10)
        self.save()
        with self.assertRaisesRegex(ValueError, "phase mismatch"):
            audit(self.root)

    def test_rejects_assigned_work_counted_as_waiting(self):
        self.rows[2]["assigned_to_compute_bytes"] = 50
        self.save()
        with self.assertRaisesRegex(ValueError, "Waiting bytes"):
            audit(self.root)


if __name__ == "__main__":
    unittest.main()
