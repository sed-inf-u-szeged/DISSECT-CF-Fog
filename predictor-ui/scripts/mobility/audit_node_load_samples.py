"""Audit the common node time grid and independently replay its offload counters."""
import argparse
import csv
import json
from pathlib import Path


HEADER = ["run_id", "node_id", "sample_index", "time_ms", "sample_phase",
          "waiting_assignment_bytes", "running_tasks", "completed_tasks_total", "connected_devices",
          "device_upload_completed_bytes_derived", "offload_sent_bytes_total", "offload_received_bytes_total",
          "net_received_counter_bytes", "assigned_to_compute_bytes", "incoming_offload_transfers"]
PHASE = "AFTER_EVENTS_AT_TIME"
CUMULATIVE = ("completed_tasks_total", "device_upload_completed_bytes_derived",
              "offload_sent_bytes_total", "offload_received_bytes_total", "assigned_to_compute_bytes")


def require(condition, message):
    if not condition:
        raise ValueError(message)


def audit(output):
    output = Path(output)
    meta = json.loads((output / "node_load_sampling.json").read_text(encoding="utf-8"))
    require(meta["schema_version"] == 1 and meta["sample_phase"] == PHASE, "Unknown node sampling contract")
    nodes = meta["node_ids"]
    require(nodes and len(nodes) == len(set(nodes)), "Missing or duplicate nodes")
    count, interval = meta["sample_count_per_node"], meta["interval_ms"]
    require(count > 0 and interval > 0 and meta["start_ms"] == 0, "Invalid sample grid")
    require(meta["last_sample_ms"] == (count - 1) * interval <= meta["sample_until_ms"], "Invalid sampling end")
    require(meta["last_sample_ms"] <= meta["simulation_end_ms"]
            and count * interval > min(meta["sample_until_ms"], meta["simulation_end_ms"]), "Truncated or invented sample tail")
    require(meta["rows"] == count * len(nodes), "Wrong declared row count")
    with (output / "node_load_samples.csv").open(encoding="utf-8", newline="") as stream:
        reader = csv.DictReader(stream)
        require(reader.fieldnames == HEADER, "Wrong node sample columns")
        samples = list(reader)
    require(len(samples) == meta["rows"], "Missing or extra sample rows")
    with (output / "offload_events.csv").open(encoding="utf-8", newline="") as stream:
        events = list(csv.DictReader(stream))
    require(all(int(a["time_ms"]) <= int(b["time_ms"]) for a, b in zip(events, events[1:])),
            "Offload events out of order")
    sent, received = dict.fromkeys(nodes, 0), dict.fromkeys(nodes, 0)
    previous = {node: dict.fromkeys(CUMULATIVE, 0) for node in nodes}
    event_index = 0
    for index in range(count):
        time = index * interval
        while event_index < len(events) and int(events[event_index]["time_ms"]) <= time:
            event = events[event_index]
            size = int(event["bytes"])
            require(size >= 0, "Negative transfer")
            if event["event"] == "START":
                sent[event["source_node"]] += size
            elif event["event"] == "COMPLETE":
                received[event["destination_node"]] += size
            else:
                raise ValueError("Unsuccessful offload event")
            event_index += 1
        for offset, node in enumerate(nodes):
            row = samples[index * len(nodes) + offset]
            require((row["run_id"], row["node_id"], int(row["sample_index"]), int(row["time_ms"]), row["sample_phase"])
                    == (meta["run_id"], node, index, time, PHASE), "Duplicate, gapped or mixed node/time identity")
            values = {key: int(row[key]) for key in HEADER[5:]}
            require(all(v >= 0 for v in values.values()), "Negative load counter")
            require(values["waiting_assignment_bytes"] == values["net_received_counter_bytes"]
                    - values["assigned_to_compute_bytes"], "Waiting bytes differ from unassigned work")
            require(values["offload_sent_bytes_total"] == sent[node]
                    and values["offload_received_bytes_total"] == received[node], "Offload boundary/phase mismatch")
            require(values["device_upload_completed_bytes_derived"]
                    == values["net_received_counter_bytes"] + sent[node] - received[node], "Upload accounting mismatch")
            for key in CUMULATIVE:
                require(values[key] >= previous[node][key], "Cumulative counter decreased: " + key)
                previous[node][key] = values[key]
    return dict(nodes=len(nodes), samples_per_node=count, rows=len(samples), interval_ms=interval,
                last_sample_ms=meta["last_sample_ms"])


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("run", type=Path, help="The complete mobility run directory (not a device subdirectory)")
    args = parser.parse_args()
    result = audit(args.run)
    print("NODE_LOAD_SAMPLING_OK " + json.dumps(result, sort_keys=True))
