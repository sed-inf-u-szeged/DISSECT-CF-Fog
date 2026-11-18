# Common node load samples for joint prediction

The mobility simulation now writes a second load stream for the planned load
predictor. `node_load.csv` remains the event-driven stream used by the existing
mobility UI and accounting checks. The W04 extension now feeds these samples to
a one-minute Persistence load baseline; see `node-load-prediction.md`. No energy
controller is connected.

## Contract

- `node_load_samples.csv`: one row per configured node at each common boundary
  `0, 60000, 120000, ...` simulation milliseconds. An idle node has a row too.
- `node_load_sampling.json`: schema, run and node identities, interval, boundary
  count, sampling end, last recorded boundary, and actual simulation end.
- `sample_index` is zero-based, shared across nodes, and independent of device
  count, device observations, and UI frame indices.
- `sample_phase=AFTER_EVENTS_AT_TIME`: all simulation events at the boundary
  have been processed, including events scheduled at the same timestamp. Events
  after the boundary have not been processed. The engine's internal counter may
  already be one millisecond higher after `Timed.fire()`; the CSV records the
  explicit boundary time, not that internal counter.
- Sampling covers the common grid through the latest configured device
  observation end (`max(device.stopTime + 60000)`). Device start delays can shift
  that end slightly. Task draining continues normally afterwards. There is no
  additional fixed-grid observation or synthetic zero label at final settlement.
- The recorder does not subscribe a periodic event. It advances the existing
  event queue to each boundary and reads state, preserving the order of real
  events. It cannot keep the simulation alive when work has finished.

## Values and units

| Field | Meaning |
| --- | --- |
| `waiting_assignment_bytes` | Bytes received at the node but not assigned to computation; `net_received_counter_bytes - assigned_to_compute_bytes`. |
| `running_tasks` | Tasks executing now across the node's application VMs. |
| `completed_tasks_total` | Cumulative completed task count in the current application accounting. |
| `connected_devices` | Distinct devices in the node's application connection lists. |
| `device_upload_completed_bytes_derived` | Cumulative uploads from devices, derived as net received + offload sent - offload received. |
| `offload_sent_bytes_total` | Cumulative bytes dispatched to another node, including transfers still in flight. |
| `offload_received_bytes_total` | Cumulative offloaded bytes whose transfer has completed at this node. |
| `net_received_counter_bytes` | Application received-data accounting after outgoing and completed incoming offloads. This is not a monotonic upload counter. |
| `assigned_to_compute_bytes` | Cumulative bytes assigned to computation. Assignment does not mean completion. |
| `incoming_offload_transfers` | Incoming transfers still outstanding; a count, not bytes. |

Values aggregate the node's applications. Waiting bytes are a queue stock, not
CPU utilisation, arrival rate, predicted load, or energy. Derive interval flows
from consecutive cumulative counters within the same run and node. Offloaded
data must not be counted again as newly generated work.

## Run and audit

Run a new mobility simulation through the existing UI or runner. The two new
files are at the run root beside `node_load.csv`, not inside `devices/0`.
Old saved runs can still be opened. They do not gain fixed-grid samples
retroactively.

```powershell
python predictor-ui/scripts/mobility/audit_node_load_samples.py 'PATH_TO_RUN_DIRECTORY'
```

Success prints `NODE_LOAD_SAMPLING_OK` with node and sample counts. The audit
checks node/time identity, equal intervals, missing/duplicate rows, nonnegative
stocks, monotonic cumulative counters, and byte-accounting identities. It also
reconstructs sent/received offload counters from `offload_events.csv` using only
events at or before each sample timestamp. The existing multi-device runner
automatically applies this audit when the new stream is present.

```powershell
mvn -f simulator/pom.xml -Dtest=FixedIntervalSamplerTest test
python -m unittest discover -s predictor-ui/scripts/mobility -p test_node_load_samples.py
```

The Java tests cover event ordering, same-time updates, future exclusion,
staggered device events, draining after sampling, early completion and the
simulation limit. The runtime source snapshot includes `FixedIntervalSampler`,
so the UI runner can compile it along with the mobility entry point.

## Next step

The W04 baseline emits a causal forecast for each node: at time `t`, predict
waiting bytes at `t + 60000` from the value observed at `t`, then score it when
that target sample arrives. Next, connect the existing trained load predictors
with their own history and qualification requirements. A
two-hour run does not provide the 287 raw points needed by the current
192-input / 64-output / 32-window first-fit configuration.
