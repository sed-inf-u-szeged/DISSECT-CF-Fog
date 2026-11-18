# City load diagnosis and the next W07 task

Reviewed: 2026-11-17, after the user accepted W06.1f's core saved-replay UI.
The original diagnosis below describes the unchanged quiet run. A subsequent
user request authorized an opt-in periodic-demand experiment; it is now verified
and documented in `periodic-city-load-demo.md`. The old result, trained model
architecture and sampling contract remain unchanged.

## What the displayed lines represent

Run: `mobility-multi-1427994466115174934`, nine devices, nine fog nodes and
Budapest cloud, 480 movement minutes. The latest screenshots select Gyor at
replay 288.58 min. All three load rows contain recorded predictions, rather
than an empty dataset. Their values are almost equal to the actual 100 B stock.

`waiting_assignment_bytes` is received work that has not been assigned to
compute: `receivedData - processedData`, summed over the node's applications.
Assignment removes bytes from this stock before a task finishes. This metric
is neither total generated data, incoming bytes per minute, nor CPU utilisation.
A moving device does not automatically change its upload rate or its node.

The CSV facts below were read directly from the delivered result:

| Check | Result |
| --- | --- |
| Full minute grid | 486 samples per node, indices 0–485; 4,860 rows total |
| Each fog node, full sample value counts | 100 B: 477; 0 B: 7; 200 B: 1; 300 B: 1 |
| Each fog node, minute 60 through 480 inclusive | 421 samples, all exactly 100 B |
| Budapest cloud | All 486 samples exactly 0 B |
| Device upload configuration | 100 B per device per minute; one device starts at each fog city |
| Movement/access configuration | 800 m local random walk, 20 km fog coverage; seed 42 |
| Completed-run transfers | Zero offloads; 431,100 B generated, uploaded and finally assigned |

The constant fog-grid samples are a property of this particular configured
simulation and sampling phase. They do not mean tasks stopped: completed-task
counters continue increasing, and event-level queue snapshots change.

## Exact forecast example at the user's replay time

Origin 286 min, targets 287–350 min, Gyor v0001:

| Target minute | Actual / same-origin Persistence (B) | LSTM (B) | TCN (B) |
| --- | ---: | ---: | ---: |
| 287 | 100 | 99.9987564086914 | 100.00103759765625 |
| 288 | 100 | 99.99606323242188 | 99.99777221679688 |

Only these two targets have matured at replay 288.58. LSTM MAE is approximately
0.00259018 B and TCN MAE approximately 0.00163269 B, so two-decimal display rounds
both to zero. Their within-tolerance rates are 100% on these two targets; both
states remain Evaluating. It is not a failed qualification or a model fallback.
It is also not evidence that either learned model outperforms Persistence.

Actual samples stop at replay time. The blue line represents forecasts whose
target times have passed; the pink line represents issued forecasts with later
target times. Near 100 B these lines overlap on the same vertical scale. A plot
viewport can extend beyond the last stored target; that does not create extra
future predictions.

## Why the observation cards have dips

The forecast's actual curve comes from `node_load_samples.csv`, with one sample
at each common minute boundary after events at that boundary. Node observation
cards use the finer event snapshots in `node_load.csv`.

| Gyor snapshot time (min) | Waiting bytes | Running tasks | Completed tasks |
| --- | ---: | ---: | ---: |
| 287.20 | 100 | 0 | 278 |
| 287.583333… | 0 | 1 | 278 |
| 287.666666… | 100 | 0 | 279 |

The fixed samples at minutes 287, 288 and 289 are all 100 B. These observations
demonstrate a short dip between minute boundaries. Different cities have
different device/event phases, but this run still samples 100 B at every stable
fog boundary. The minute series is a snapshot, not an average over the preceding
minute. It must not be scored against an event-level value with another time.

The current one-minute sampler satisfies its explicit contract. Its suitability
for the eventual decision target still needs evaluation. A finer interval or
an interval-average/maximum queue would define a different prediction problem:
such a change would require new labels, documented units/horizons, retraining
and comparable baselines. It must not be made solely to produce a more varied
chart or to improve a score.

## Difference from the standalone load entry point

This comparison uses the current checked-out source, not an assumption that
every earlier standalone experiment used its defaults.

| Setting | Standalone `PredictionSimulationDefinition` defaults | Current city Mobility run |
| --- | --- | --- |
| Node resources | Shared city topology: nine fog nodes and one cloud | Same shared city topology |
| Predicted quantity | `receivedData - processedData` | Same waiting/unassigned-byte definition |
| Configured active-device schedule | Multiplier 30; nominal cohorts 60, 180, 90, 210, 60, then 150 additional devices from hour 6 | Nine distributed devices, one at each fog node |
| Data per device | 50 B every 60 seconds | 100 B every 60 seconds |
| Device movement | Static positions | Local random walk |
| Attachment | LoadBalancedDeviceStrategy; configured 10,000 km node range | StableConnection; 20 km fog range |
| Application task settings | Maximum 5,120 B per task, 480 instructions for a full task | Same city task settings |
| Sampling timing | Registered Feature is computed in each Application.tick before allocation | Shared minute grid, after all events at that timestamp |

Nominal standalone cohort intervals are 0–1, 1–2, 2–3, 3–4 and 4–10 hours;
the extra cohort runs during 6–10 hours. SmartDevice adds its existing random
start/stop delay of up to 179 seconds, so exact instantaneous active counts at
phase boundaries are not the nominal table counts.

Sharing node resources does not reproduce the same arrival trace, connection
pattern, queue phase or overload conditions. The current local-walk demo is a
light, fixed-rate regression with no recorded offloads. A separate earlier
west/east run also used different application settings (250 B task maximum,
2,500 instructions), so its curves are not a city-model performance baseline.

Source locations, relative to the repository root:

- `simulator/src/main/java/hu/u_szeged/inf/fog/simulator/demo/PredictionSimulationDefinition.java`
- `simulator/src/main/java/hu/u_szeged/inf/fog/simulator/demo/CityTopology.java`
- `simulator/src/main/java/hu/u_szeged/inf/fog/simulator/demo/simple/MobilityPredictionSimulation.java`
- `simulator/src/main/java/hu/u_szeged/inf/fog/simulator/application/Application.java`
- `simulator/src/main/java/hu/u_szeged/inf/fog/simulator/iot/SmartDevice.java`
- `predictor-ui/configs/mobility/w07-city-demo.json`

Result evidence: `run.properties`, `scenario-effective.properties`,
`node_load_sampling.json`, `node_load_samples.csv`, `node_load.csv`,
`node_load_model_predictions.csv` and `node_load_model_evaluations.csv`.

## Deferred next experiment: verify external demand before another model sweep

After this review the user prioritised load-UI clarity. Complete the focused
W06.1g presentation pass (`load-ui-review-w061g.md`) before the scenario work
below. Mobility detail UI is accepted; the resource-control research goal is
unchanged.

1. Preserve this completed quiet city demo and the accepted UI as regression
   references. Keep existing defaults working. Add explicit, recorded device
   demand schedules for fixed rates, stepped rates and bounded bursts; later
   include device cohorts with different active intervals. Freeze those inputs
   independently of the forecast models and controller variants.
2. Pilot short baseline-only load runs before paying for learned-model training.
   Begin with one device, then multiple devices sharing a node and ten-device
   city cases. Compare generated/uploaded interval bytes, queue stock and
   assignment/offload counters. Verify that each node's fixed-grid trace has
   the variation actually implied by the input and service schedule. Inspect
   event timing alongside it; do not invent additional samples or jitter.
3. Test a physical boundary crossing in the two-node case, with unchanged
   external demand. Verify a real connection change and resulting node workload.
   Keep this separate from national city-local movement: an 800 m walk inside
   a 20 km radius does not demonstrate travel between Hungarian cities.
4. Only after these checks, run training-length cases with the unchanged
   Candidate criteria, shared origins/targets and explicit seeds 42, 43 and 44.
   Include demand changes after model warm-up as well as before it. Record
   development/held-out separation. The next appropriate Windows trained run
   also covers the remaining live-history/Follow-latest acceptance.

Exit conditions for the immediate pilot: reproducible configuration; correct
byte conservation and nonnegative queues; observed input changes match the
configured schedule; consistent event/fixed-grid timing; explicit justification
of the chosen prediction target and cadence. The next quality report must
include Persistence on the same origins and horizons, matched errors,
within-tolerance rate, prediction coverage/gaps and computation cost. A successful
pilot need not make any learned model win.

W07.1 then tests whether causal mobility features improve those forecasts. The
current load worker receives node waiting-byte history only; predicted device
arrivals are not an input. Its 64-minute horizon and mobility's 60-second horizon
must be reconciled before fusion. Energy/capacity modelling and controller
comparisons remain W08 and W09.
