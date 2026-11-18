# Official mobility strategies — integration step 1

This step selects the existing `NomadicMobilityStrategy` or
`RandomWalkMobilityStrategy` through the same scenario entry point. Their movement
implementations are unchanged. Online training, candidate promotion and fallback
are later integration steps; this change does not implement them.

## Scene inputs

Use `--no-ui --scenario=<properties-file>` with `MobilityPredictionSimulation`.
The supplied examples are:

| File in `simulator/src/main/resources/demo/mobility` | Official class | Behaviour |
| --- | --- | --- |
| `official-nomadic.properties` | `NomadicMobilityStrategy` | Follows two destinations and remains at the final destination |
| `official-random-walk.properties` | `RandomWalkMobilityStrategy` | Samples a direction and speed within a radius, using the simulator RNG |

`mobility_strategy` accepts `nomadic` or `random_walk`. Both accept `scenario_name`,
`seed`, `start_latitude_deg` and `start_longitude_deg`. This adapter supports starts
between -85 and +85 degrees latitude and valid longitude coordinates.

Nomadic requires `waypoints_m`: east/north offsets relative to the configured
start. The first point must be `0,0`. These inputs are required by the official
waypoint strategy; they are not a separate movement algorithm. It accepts
`speed_m_s` and optional `route_repeats=false`. It does not repeat unless requested.
Explicit repetition requires a closed route and feeds repeated destinations to
the same official class.

Random walk accepts `speed_min_m_s`, `speed_max_m_s` and `radius_m`. It does not
accept waypoints, a constant `speed_m_s`, or route repetition. With the current
60-second sampler, the radius must exceed `60 * speed_max_m_s` so that an inward
step remains feasible at the boundary. This is an adapter validation rule, not a
general limit of the official simulator. Both speeds are converted from metres
per second to metres per millisecond before the official constructor is called.

Earlier configs without `mobility_strategy`, including the 36-case pilot and the
no-argument UI demo, retain the earlier closed/repeated route behaviour and their
four-field effective config format. New work should select a strategy explicitly.

## Observation contract

Both strategies use the unchanged `MobilitySampleRecorder`:

`run_id,device_id,time_ms,latitude_deg,longitude_deg,delta_time_s,displacement_m,observed_speed_m_s,heading_deg,motion_valid,heading_valid,current_node`

Sampling is after the device applies actual movement and refreshes its connection.
Each verification run exports 121 rows at 60-second intervals. The first row has
no derived motion. Stationary rows remain present with zero speed and invalid
heading. `observed_speed_m_s` is endpoint displacement divided by elapsed time;
it need not equal the configured speed when a waypoint turn or stop occurs.

Scene inputs are saved in `scenario-effective.properties`. Nomadic additionally
saves `route_waypoints.csv`. Random walk creates no waypoint file and reports its
speed range and radius instead of a fictitious fixed speed or planned trajectory.
`run.properties` identifies the actual strategy class. Future scene inputs are
not included in the observation CSV.

## Verify on Windows

From the repository root, run:

```powershell
mvn -f simulator/pom.xml "-Dtest=MobilityScenarioConfigTest,MobilityOfficialStrategyTest,MobilitySampleRecorderTest,MobilityConnectionRegressionTest" test
python predictor-ui/scripts/mobility/run_mobility_strategies.py
```

The Java suite contains 24 tests: the original 17 checks and seven official
strategy checks. The runner compiles an isolated copy of the relevant source,
uses separate JVMs and fresh output directories, and retains logs and hashes.
It clears the three shadow-worker environment variables for its simulation
children; no learned model is loaded or trained. The legacy coverage baselines
still run as part of the existing simulator entry point.

The runner executes Nomadic once and random walk twice with the same seed. It
recomputes observed speed and heading, checks random bounds and speed units,
checks terminal stationary observations, verifies forecast/actual time matching,
and checks final byte conservation. Repeated random observations must match
exactly, excluding only the generated run identifier.

Expected summary:

```text
strategies=2 runs=3 sample_rows=363 same_seed_replay=PASSED training=NOT_STARTED
MOBILITY_OFFICIAL_STRATEGIES_OK
```

Results are retained under `simulator/sim_res/mobility-strategies/<id>/`.
`strategies.json` records all outputs and whether verification completed.

## Interpretation and boundaries

- These are smoke scenarios, not a new training/test dataset or model ranking.
  The Nomadic example deliberately reaches its endpoint early: eight moving
  intervals and 112 stationary intervals verify official stop behaviour.
- The random example stays within west-node coverage to isolate movement and
  verify upload accounting. It does not test random handovers. Other scenes can
  leave coverage; the existing entry point still requires all generated data to
  finish uploading and processing. An uncovered final stop may therefore fail
  its final conservation check; it must not be reported as a successful run.
- The fixed observation duration, two nodes and one device remain the current
  integration fixture. They are not limits of the official movement strategies.
- Reproducibility requires the same simulator/RNG call order as well as a seed.
  Formal model comparisons should share the actual observation stream.
- Extending run duration, online training, TCN/GRU adaptation, quality thresholds,
  model activation and fallback remain future steps. No changes to load-model
  training or lifecycle rules are included here.
