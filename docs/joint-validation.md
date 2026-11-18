# Joint prediction validation: first city demonstration

Updated: 2026-11-17. This starts W07; it is not a completed benchmark matrix.

## W06.1f click-to-inspect regression on existing results

After Windows review, the user requested that W06.1e's pan/zoom UI be replaced
with click-based node/device details and neutral city colors. This revision
reuses the city run below and two existing two-node results. No simulation or
training was repeated, and no model/scoring rules changed.

77 logic checks, TypeScript/production compilation and actual Angular template
integration checks passed. The templates exercise city clicks, linked load
selection, connected-device clicks, top device switching, cloud/no-observation
states and time reversal. The city and legacy replay checks cover 16,920
node-chart states. Device detail uses known observations and issued forecasts;
retrospective future truth cannot influence its automatically fitted extent.

The 28,271-record city journal passes 410 device/node snapshot comparisons.
Live LOAD messages are wall-clock throttled: compare them with saved rows at
their own timestamps, not later saved snapshots that were never streamed.
Node-load samples/vectors still use the existing NODE_LOAD stream and minute grid.

Generated node-coverage and local-device SVGs were inspected. The actual revised
Windows/Electron layout/click workflow still needs review; normal Windows live
history interaction also remains open. A separate synthetic 100-device detail
calculation checks finite geometry/update cost only, not simulation, training
or browser rendering performance. See `joint-map-interaction-review.md`.

## Scope and fixed configuration

Use the same audited runner as the normal UI. The reproducible configuration is
`predictor-ui/configs/mobility/w07-city-demo.json`. No UI or qualification rules
change for this demonstration.

| Setting | Value |
| --- | --- |
| Infrastructure | Hungary cities: nine fog nodes and Budapest cloud |
| Devices | 9, one starting at each fog city |
| Movement | Random walk; seed 42; speed 2–6 m/s; local radius 800 m |
| Fog access radius | 20 km, the existing experimental default |
| Movement duration | 480 simulation minutes |
| Drain allowance | Up to 1,440 further simulation minutes; no additional movement samples |
| Position forecasts | Existing HoldPosition, ConstantVelocity and per-device LSTM |
| Additional position comparisons | Off for this acceptance run |
| Node-load models | Persistence plus LSTM and TCN, independently for each node |
| Load training | 40 epochs, unchanged Candidate profile |
| Qualification | 64 matched targets, 80% within the configured tolerance; no permissive profile |

Ten nodes do not require ten devices. Nine distributed devices give each fog
city a local source; the cloud receives offloaded work and has no direct device
attachment. The demo has ten node time series in one run, not ten unrelated
simulations. These are simulated city-labelled resources, not real city traffic.

The default movement is local to each city and stays well inside its access
radius. This checks multiple nodes and model isolation; it does not demonstrate
intercity travel or meaningful connection handovers. Separate boundary-crossing
and demand-shift cases are needed for later mobility-informed experiments.

## Inventory before this run

| Existing evidence | What it establishes | What it does not establish |
| --- | --- | --- |
| User's two-node 480-minute run, 4944157618143554283 | Training, target matching, rejection/retry and saved UI | Ten-node trained city results |
| User's nine-device short city run | City topology and node/device selection | Load model training; duration is too short |
| Local city run 18339009028549274217, nine devices / 350 minutes / one load epoch | Incremental records and live/export equality at all ten nodes | Standard-epoch prediction quality or Windows live interaction |

The new demonstration uses the normal 40-epoch setting, retains failed
qualification honestly, and saves its configuration and audits. Quiet or zero
cloud load must remain visible as measured; do not add artificial data to make
all charts vary. Passing a constant-zero series is not evidence of useful
forecasting on a changing load.

## Acceptance and demonstration order

1. Confirm all ten nodes and the nine device starting cities in the saved
   manifest. Compare samples on the shared one-minute grid, including idle nodes.
2. Check the first trained load vectors (normally issued at minute 286 for
   targets 287–350) and subsequent lifecycle events. Retries depend on the saved
   decisions; no particular promotion outcome is required for pipeline acceptance.
3. Verify real-time samples, vectors, evaluations, selections and lifecycle
   events equal the completed exports. Compare historical chart states after
   later data arrive; future observations must not leak into an earlier frame.
4. Check upload/offload accounting and final workload drain with the existing
   audits. Record wall time and sampled process-tree memory separately from
   simulation minutes.
5. Reopen the saved result through **Open mobility results**. Demonstrate the
   city overview, two devices in different cities, an active fog node's load
   curves, Budapest cloud, and final settlement.
6. Windows live acceptance remains a user-machine check: while a normal run is
   active, browse older time, verify received time advances, return to **Follow
   latest**, then open the verified completed replay. A saved demo cannot prove
   this interaction. Existing evidence should be reused where available.

## Next controlled experiments

Current executable strategy names are **Random walk** (`random-walk`) and
**Nomadic · waypoints** (`nomadic`). Earlier roadmap wording “Given Destination”
must not be treated as a third selectable UI strategy.

After this demonstration and live acceptance, freeze configuration and collect
1- and 10-device cases for both supported strategies. Use explicit seeds 42, 43,
and 44 initially. The runner's `--repeats` alone repeats the same scenario seed;
it must not be reported as three different random seeds. Separate development
and held-out runs before tuning criteria or models. Extend to 100 devices only
after reviewing smaller-run cost.

Report matched-target errors, within-tolerance rate, prediction coverage,
qualification and service durations, inference/training cost and peak memory.
Keep rolling one-minute Persistence separate from same-origin 64-target
Persistence comparisons. Missing learned forecast blocks are unscored, not
zero-error predictions. This stage does not implement mobility-to-load feature
fusion, energy measurement or predictive resource control.

## Latest run

Run `mobility-multi-1427994466115174934` completed locally on 2026-11-17 through
the normal MobilityJob runner with the configuration above. The delivered
portable replay is `DISSECT_City_Demo_480min_20261117.zip`.

| Evidence | Result |
| --- | --- |
| Topology and devices | All ten nodes; nine distributed devices |
| Uniform node samples | 486 per node; 4,860 total, minute 0 through 485 |
| Load versions | 20, one LSTM and one TCN per node; 40 epochs |
| Learned vectors / target predictions | 80 / 5,120 |
| Evaluated targets | 3,980; remaining 1,140 tail targets unobserved and unscored |
| Load audit | Past-only training, target matching, qualification/routing and all 80 saved-vector replays PASSED |
| Mobility and accounting audit | All nine devices PASSED, 472 selected targets per device; accounting and feature isolation PASSED |
| Incremental journal | 28,271 records, 338 batches containing load events |
| Live versus saved chart states | 4,950 comparisons PASSED, including historical rewinds |
| Portable copy | Existing UI result reader returns `MOBILITY_UI_DATA_OK` from a separate copied directory |
| Final settlement | 486.93335 simulation minutes; zero waiting bytes and running tasks |
| Simulation wall time | 412.40 seconds |
| Runner through audit/loading | 511.71 seconds |
| Sampled Java/worker process-tree peak | 2,918.67 MiB; excludes a complete graphical Electron session |

All twenty load versions passed first qualification. This is **engineering
acceptance**, not a claim of learned prediction superiority: each fog node has
477 of its 486 minute-grid samples at 100 B, with only startup/end variation;
all Budapest cloud samples are zero. Event-level node observations can vary
between these minute-grid samples. The current movement stays within its
starting city's coverage and does not establish a mobility-to-demand benefit.
Final selection is LSTM at Budapest, Szombathely, Gyor, Debrecen and Gyongyos;
TCN at Sopron, Szeged, Pecs, Kecskemet and Miskolc. Qualification does not grant
these forecasts control of connections, capacity or offloading.

The package contains the complete saved output and models, exact configuration,
source snapshot/hashes, logs, cost table, live journal, consistency report and
updated progress documents. It opens with the existing UI; no patch is required.
The user's subsequent Windows screenshots confirm core saved city rendering at
43.22 and 371.37 min: nine devices, Sopron's Persistence/LSTM/TCN rows, ten node
observation charts and accounting. The normal live-history/Follow-latest
interaction remains a user-machine check. See `joint-map-interaction-review.md`
for the curve-overlap diagnosis and the current click-to-inspect design.

Before starting the repeated-seed matrix, make the changed-demand cases
observable at the prediction sampling resolution. Include a controlled burst
or rate change and a coverage-boundary crossing with a verified connection
change. Merely changing seeds while nine isolated local walks yield nearly
constant node samples would add cost without establishing predictive value.
Keep this quiet city run as the topology/regression baseline.
