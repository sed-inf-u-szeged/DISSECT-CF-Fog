# Mobility and load prediction integration: progress

Updated: 2026-11-18. Step IDs below are work items, not calendar weeks.

After each completed step, update this table and its evidence, and show the user
a short progress table. Distinguish development/test completion from successful
installation and execution on the user's Windows computer.

| Step | Work | Status | Evidence / next acceptance |
| --- | --- | --- | --- |
| W01 | Review mobility/load source and node-load meanings | Complete | Waiting bytes mean received work not yet assigned to compute. |
| W02 | Define shared run, node and simulation-time identities | Complete | Common 60-second node grid; forecasts retain origin and target time. |
| W03 | Implement and export uniform node samples | Complete; user's run verified | Run `mobility-multi-5221287052711181382`: two nodes, 124 rows each, no missing/duplicate grid entries. Uploaded CSV/JSON consistency checked. |
| W04 | Add one-minute Persistence baseline and target evaluation | Complete; user's new-run exports verified | Run `mobility-multi-4237531875228351577`: 248 forecasts, 246 matched targets, two unmatched tail targets; timing identities, persistence chaining and summary metrics checked. |
| W05 | Adapt existing trained load models to the shared stream | Complete; user's new-run exports verified | Run `mobility-multi-4944157618143554283`: eight versions, 512/512 matched targets, consistent training/rejection/retry events; candidate quality not met, so both nodes retain Persistence. |
| W06 | Show node-load actuals/predictions in the shared replay UI | Complete; automated checks and user's Windows UI acceptance passed | Existing W05 run reopened; first-training and final-settlement views, west/east node selection and LSTM/TCN curves confirmed. Both nodes' final comparison/version tables match the uploaded summary. |
| W06.1a | Share the original 9-fog/1-cloud city topology with Mobility | Development checks complete; Windows short city run confirmed | User run `mobility-multi-10956657032195469642` shows nine devices, ten city nodes, COMPLETE status and Persistence load replay. Screenshot evidence only; city trained-model quality and full interactive acceptance remain open. |
| W06.1a2 | Make the city map and node charts readable | Core Windows screenshot acceptance confirmed | Existing city run reopened with separate node charts and node-row accounting. New screenshot confirms Device 1 / Szombathely and Device detail with a visible local trajectory. Exhaustive node/legacy interactive acceptance is not claimed. |
| W06.1b | Restore simultaneous model display for the selected city | Core multi-model rendering and forecast-origin visibility confirmed on Windows | At replay 414.22 min / origin 414 min, Actual stops at the last known sample, pink future vectors remain visible and v2 state is Evaluating. Earlier v1 Test remains separate. Expanded interaction and east-node prediction switching in the new layout remain unobserved. |
| W06.1b3 | Keep the shared replay timeline visible while scrolling | Core Windows screenshot acceptance confirmed | Four screenshots show Replay time retained above the map, position errors, model rows and bottom accounting at frame 409 / 411.22 min. The user reports improved usability. Keyboard, continuous dragging and narrower-window behavior were not separately demonstrated. |
| W06.1c | Update node-load charts during execution | Data-flow checks and W06.1c1 Windows saved-event/full-replay checks passed; new trained live-run acceptance pending | Windows diagnostic reaches Complete, 1,089 observations and 126.93 min with sustained UI heartbeats. Full replay now opens and rewinds to 43.22 min with Sopron selected and the shared slider visible across sections. Three earlier complete runs passed live/export equality and 4,870 chart-state comparisons. History browsing while new records arrive and a normal 480-minute trained live run remain to be checked on Windows. |
| W06.1d | Clarify mobility forecasts, load readiness and time windows | Core saved-replay UI accepted by user after W06.1d3; further presentation changes deferred | Movement, position errors, load forecasts and node observations now share a clear replay clock. Remaining baseline/gap wording is recorded as later polish, not a blocker to the mainline. Normal trained live-run acceptance remains in W06.1c. |
| W06.1d1 | Keep position-error curves thin in wide windows | Complete; user confirms the thin-line update succeeded on Windows | A scoped SVG rule fixes curve width at 1.5 CSS px with non-scaling stroke. One-source-file update for W06.1d; forecasts and model metrics are unchanged. |
| W06.1d2 | Keep node-observation curves thin | Complete; user confirms the thin-line update succeeded on Windows | Extends the 1.5 CSS px non-scaling rule to node-card paths, for both waiting bytes and running tasks. The one-file update also includes W06.1d1 and accepts W06.1d or W06.1d1. |
| W06.1d3 | Simplify node-load replay to one clock and a rolling window | Complete within core saved-replay scope; user accepts current UI logic on Windows | Screenshots at 341.22, 364.22 and 424.22 min confirm fixed 192-minute history + 64-minute future, v1 evaluation/rejection, the saved forecast gap and v2 appearance. Local checks: 70 logic cases, real-template checks, 58,656 saved snapshots, 4,870 live/export comparisons and 740 Node-canvas draws. No retraining. |
| W06.1e | Shared pan/zoom map with all devices and node-load linkage | Installed on Windows; interaction superseded at user request | User screenshots confirm the map and load panel render, but national-scale clutter and navigation were not useful. Replaced by W06.1f; earlier local tests remain recorded in its package. |
| W06.1f | Restore click-to-inspect details and neutral city colors | Complete within core saved-replay scope; user accepts the Windows layout and curves | Latest screenshots at 288.58 min show Device 2 / Gyor, compact city selection, local trajectory, linked load, thin position errors and neutral node charts. User reports trying multiple cities and replay times and accepts the presentation. Earlier 77 logic cases, real Angular templates, 16,920 saved chart states and 410 live snapshot comparisons passed. Exhaustive interactions and normal trained live operation are separate. |
| W06.1g | Clarify queue units, compact node state and fix chart-axis typography | Implemented; local build/logic/template checks passed, Windows acceptance pending | 77 replay logic cases and real Angular templates passed, including ten-city summary selection and one/all-node chart linkage. Fixed-size axes for position errors/node observations; all-node queue/running summary with selected-node detail; bytes spelled out on primary load labels. Mobility detail remains accepted. See `load-ui-review-w061g.md`. |
| W06.1h | Bright, clickable device markers | Implemented; local build/template checks passed; Windows review pending | All device dots use actual-path yellow with a subtle halo and larger hit target; neutral cities and existing click navigation preserved. |
| W07 | Validate combined prediction across scenarios and device counts | In progress: quiet city case and one explicit periodic-demand demonstration verified | New run `mobility-multi-7085794150201453824` has actual queue peaks at all nine fog nodes; demand/accounting, model audits and 4,950 live/export comparisons passed. Repeated-seed, irregular-demand and device-scaling matrix remains open. See `periodic-city-load-demo.md`. |
| W07a | Reproducible periodic city demand | Complete within one-seed demonstration scope; Windows replay review pending | 104-min pilot plus 480-min/9-device run, 40 load epochs; all nine fog nodes vary. Old constant results preserved; no predictive resource controller enabled. |
| W07b | Restore standalone load model families; early Mobility baselines and readiness | Implemented and locally verified; Windows installation/review pending | ARIMA/LR replace Persistence in new runs; GRU/PatchTST share the LSTM/TCN lifecycle. 480-min/9-device/10-node run audited with 2 load epochs, 4,950 live/export comparisons. A separate 104-min/2-device run verifies 414 early baseline predictions/errors and training events. Defaults remain 40 epochs. Yellow device labels; 86 UI logic cases, 31 reader/IPC cases, 11 Java cases and Angular production build passed. See `models-and-early-mobility-w07b.md`. |
| W07.1 | Link mobility forecasts to node demand and quantify their added value | Pending; made explicit in the roadmap review | Compare load-only and mobility-informed variants on common targets. Account for device job generation and the 60-second mobility versus 64-minute load horizons; no future actuals in decision inputs. |
| W08 | Add and validate energy, capacity states and supported actions | Pending | Verify units, idle/off power, cloud/transfer scope and transition costs; agree service-quality constraints. Define both compute and access/coverage actions and their physical limits. |
| W09 | Implement and compare capacity/offloading controllers | Pending | Fixed, reactive, load-predictive and mobility-plus-load control with equal external workloads. Start with compute control and supported offloading; add access/coverage control after its model is defined. |

## Current checkpoint: W07b model migration and early baseline errors

The requested migration is implemented: ARIMA and LR use the original standalone
predictor implementations; GRU and PatchTST use the existing networks/training
pipeline alongside LSTM/TCN. New runs select LR before neural qualification and
on fallback (ARIMA is the available-baseline backup). Persistence remains only
for reading legacy files. No earlier run or standalone model was rewritten.
Mobility baselines now issue independently of the learned-model history window;
HoldPosition starts at observation 1 and ConstantVelocity at observation 2.
Errors wait for actual 60-second targets. The UI shows collection, real training
and evaluation progress. Device labels match the yellow observed-position dots.

Local integration and replay checks passed; installation and interactive review
on the user's Windows computer are pending. The 480-minute migration validation
uses two load epochs to check execution, not predictive quality; normal defaults
remain 40. Next: accept this update, then run matched-target W07 experiments with
ARIMA/LR and the four trained models under identical seeds/demand and report
quality/coverage/cost. W07.1 mobility-informed demand, W08 energy/action limits,
and W09 the actual node-load controller remain pending. See
`models-and-early-mobility-w07b.md` for verification and early-target semantics.

## Earlier decision: finish load presentation before extending W07 scenarios

On 2026-11-17 the user reviewed the installed W06.1e map and requested the
previous click-to-inspect approach with fewer city colors. W06.1f implements
that correction while preserving the accepted thin lines and rolling timeline.
The user then requests a focused load-UI review before changing the workload.
The research goal remains a prediction-informed resource/task-allocation
algorithm, evaluated for service quality and energy; forecasts support that
controller. Queue bytes and running-task count are observations, not a complete
resource-pressure measure. W06.1g addresses units, axis typography and the node
state overview; larger load-display decisions precede W07 expansion.

Local checks passed, and the user's subsequent review accepts the click-based
layout and curves on Windows. Screenshots at 288.58 min show the linked Gyor
device/node views and all model rows. The mainline experiment plan is unchanged.
W01-W06 are complete within the evidence above. The standard 480-minute city
run `mobility-multi-1427994466115174934` passed its audits and live/export checks
and the user has opened it on Windows. This quiet city run is the map regression,
not evidence for changing-demand prediction. Normal Windows trained live
interaction and the W07 repeated-seed/device-count matrix remain incomplete.

The user's run `mobility-multi-4944157618143554283` is engineering evidence,
not proof of useful learned-load quality: eight versions had 512/512 matched
targets and all eight were rejected under the candidate profile. Both nodes
retained Persistence. The main load chart's Persistence row is a paired
64-target reference after first training; the rolling one-minute baseline
remains distinct. The v1 gap from target 351 through 414 is not missing UI data:
v1 was rejected at 350, and v2 was trained at 414 for targets 415-478.

| Order | Mainline work | Next action and exit condition |
| --- | --- | --- |
| 1 | W06.1g load presentation — current next action | Verify the revised error/node axes, queue units, all-node state table and selected-node detail on existing city and two-node results. Keep the accepted mobility view and shared replay clock. The research objective and forecast/control distinction are recorded in `load-ui-review-w061g.md`. |
| 2 | W07 workload and sampling validation — after UI review | Retain the accepted quiet city replay as a regression. Add explicit per-device demand schedules: fixed, stepped and burst input. Use baseline-only short runs to verify arrival counters, queue response, minute samples and event timing before training. Preserve the waiting-byte target and actual observations; do not add chart noise or alter old results. See `joint-load-scenario-review.md` for exit conditions. |
| 3 | W07 combined scenario correctness | After the signal check, freeze code/configuration and inventory completed cases. Use Random walk and Nomadic waypoints at 1 and 10 devices, covering the two-node boundary case and 9-fog/1-cloud topology. Specify seeds 42, 43 and 44 explicitly; runner repeats alone reuse the same seed. Check common sampling, node/model isolation, target matching, upload/offload accounting and final drain. Use the next normal trained run for the remaining W06.1c Windows live-history acceptance. Extend to 100 devices only after smaller-run cost is acceptable. |
| 4 | W07 prediction quality and cost | Establish a matched-target baseline report before model tuning. Proposed initial repetitions: at least 3 recorded seeds per chosen condition, with separate development and held-out evaluation runs. Include quiet demand, surges, hotspot movement and offloading. Report MAE/RMSE, within-tolerance rate, qualification/selection duration, forecast coverage, train/inference time and peak memory. Do not score absent learned blocks as zero error or compare one-minute rolling baselines as though they had a 64-minute horizon. |
| 5 | W07.1 added value of mobility | Define arrival/departure and task-rate features available at decision time. Compare load-history-only and mobility-informed variants on identical targets and external workload. Resolve the 60-second mobility versus 64-minute load horizon explicitly. This fusion is not implemented yet. |
| 6 | W08 energy, capacity and feasible actions | Verify power/energy units, compute states, wake-up/drain/transfer costs and cloud scope. Define service-quality constraints with the supervisor. Model access/coverage actions separately where needed. |
| 7 | W09 controller experiments and reporting | Compare fixed capacity, reactive, load-predictive and mobility-plus-load control on the same external trace/seeds. Report total modeled energy with latency, completion, backlog and switching costs. Publish reproducible evidence after the experiments; no energy-saving claim has been established. |

The immediate next action is the focused W06.1g load-presentation review on
existing results. Once that is settled, configure and verify changing external
demand at the chosen sampling resolution. Then test coverage-boundary cases before
repeated-seed experiments. Core saved city UI review is complete; remaining
Windows live acceptance will use the next suitable trained run. The current
city minute-grid load is nearly constant; repeated local-walk seeds alone would
not establish forecast value. UI work is limited to this requested presentation pass; a large model sweep
remains deferred. Dates and elapsed effort are not
invented. W IDs here are implementation work items; the original weekly Excel
plan retains its earlier IDs and now carries the mapping in its update note.

## Accepted replay timeline: W06.1d3

The single-clock redesign is installed and accepted for core saved replay on
Windows. Main load charts use the top slider and a fixed 256-minute window;
saved origins/targets never move. The screenshots show windows 149.22-405.22,
172.22-428.22 and 232.22-488.22 min at their respective replay times. At 341.22,
v1 has 55/64 observations; at 364.22 it is Rejected with 64/64; at 424.22 v2 has
10/64 and is Evaluating. The uploaded lifecycle records confirm those times.
Additional wording for rejection/retraining and paired versus rolling baseline
can be improved later. Exhaustive controls and live execution are not claimed
from static screenshots. No training, thresholds or control rules changed.

## Earlier integration scope (before W07b)

W05 adds optional per-node LSTM/TCN load prediction, explicit qualification,
selection, monitoring and fallback. The same simulation still produces the
device mobility forecasts and W04 Persistence outputs. W06 now adds node-load
actual/predicted curves, comparisons and lifecycle replay. The user's Windows
screenshots confirm reopening an existing run, showing its first load fit,
west/east node and LSTM/TCN curve selection, and both nodes' final metrics/version
states. W06 acceptance is complete for this existing one-device/two-node run.
This is saved-result UI integration. W06.1b now adds simultaneous model charts
with semantic Actual/Test/Future colors to the shared load panel. W06.1c now
streams those records during execution and retains historical browsing while
new data arrives; the final saved exports are verified before opening replay.
W06.1a now implements the shared city topology: nine fog nodes and
Budapest_cloud, original hardware and links, explicit local movement and
synthetic access radii. New city runs export their own node manifest and
sample all ten nodes. The two-node profile and old results remain supported.
The user has installed the city update and completed a nine-device short run.
The revised Windows screenshots confirm separate node charts and the city
overview. A subsequent Device 1 / Szombathely screenshot confirms device
switching and Device detail zoom. W06.1a2's core screenshot acceptance is
complete; this is not an exhaustive check of every node or legacy interaction.
W06.1b2 uses the original workload screenshots as its display reference: compact
Chart.js model rows, thin yellow actual / blue test / pink future curves, right-side
metrics, curve filters, common Y scale and an expanded model view. All model
rows still use one actual forecast origin and target grid. Its test region uses forecasts actually issued earlier,
and may use a previous model version; it is not a retrospectively fitted
backtest. Missing predictions remain blank. Windows screenshots now confirm
the three model rows and semantic curves on the existing trained two-node run.
A screenshot at origin 414 min confirms future actuals stay hidden and v2's
historical state is Evaluating. Expanded interactions and east-node prediction
switching in the new layout remain unobserved. The original
single-vector inspector remains in a collapsed section.
W06.1b3 moves the same replay slider into the non-scrolling header, below the
playback buttons and above the device filters. Content gets the remaining
height, so charts can scroll while time controls remain available. User Windows
screenshots now confirm this fixed-header behavior across all main sections,
including the lower node/accounting view, at a rewound historical frame.
Live load updates are implemented in W06.1c. W06.1c1's Windows saved-event
diagnostic replay now completes with continuing renderer heartbeats. Subsequent
screenshots confirm opening the full saved replay, rewinding its shared clock
and selecting Sopron for load inspection. Windows acceptance of historical
browsing while new records arrive and a normal trained live run remains.
The combined pipeline does not yet feed
predicted device arrivals into load features or control energy/capacity.
W06.1d now makes that distinction visible: a device's next position and a
node's waiting-byte forecast have separate explanations, timestamps and scopes.
Untrained load families show readiness cards. W06.1d3 replaces the old main-view
origin and range controls with a fixed rolling window driven by the top slider.
Individual saved-vector inspection remains in Diagnostics. The user now accepts
the core saved-replay logic and defers further wording changes. This acceptance
does not complete the remaining live-run or model-quality experiments.
The user permits cross-node/cloud offloading and wants both
compute and access/coverage capacity considered later. Acceptable delay and
other service-quality limits remain to be discussed with the supervisor.

## Completion record

- W06.1f Windows core saved-replay acceptance and W07 data review (2026-11-17):
  the user accepts the current map/detail and curve presentation, having tried
  multiple cities and replay times. At 288.58 min, Gyor's three model rows have
  two matched targets from origin 286; displayed MAE zero is rounded for LSTM
  and TCN, not exact equality. All nine fog nodes have 100 B at every minute
  boundary from 60 through 480; Budapest has zero. Brief event-level changes
  occur between those boundaries. The current nine-device fixed-rate demo and
  the standalone entry point's phased multi-device workload are different.
  See `joint-load-scenario-review.md` for source/data evidence and the next
  bounded W07 experiment. No model or simulation code changed in this review.

- W06.1e shared map (2026-11-17): implemented one offline geographic map in
  saved replay and incremental preview, with all recorded devices, thin trails,
  pointer-anchored zoom, drag pan, explicit follow, and two-way node/load
  selection. Device and node identities remain independent. Model values,
  qualification and sampling rules are unchanged. Existing city and two-node
  results, 77 logic cases, real Angular templates and the original 28,271-record
  city journal passed local checks. Live load snapshots are checked and labelled
  at their own timestamps because the producer throttles them. A synthetic
  100-marker calculation is not a 100-device simulation/performance acceptance.
  Windows installation and pointer/layout acceptance remain pending.

- W07 Windows city saved-replay review (2026-11-17): screenshots of run
  `mobility-multi-1427994466115174934` at 43.22 / 371.37 min confirm nine devices,
  city topology, Sopron's trained model rows and all ten node observation and
  accounting rows. Current flat prediction curves reflect overlapping values
  near 100 B, checked against the saved one-minute samples and model vectors.
  Event-level observations contain brief dips between these samples. Normal
  Windows live interaction remains open. The user proposes a shared pan/zoom map,
  simultaneous devices and click-node load details; the review and causal limits
  of future arrival-to-load prediction are in `joint-map-interaction-review.md`.
  The subsequently approved map is implemented in W06.1e above; mobility/load
  feature fusion remains pending.

- W07 standard city demonstration (2026-11-17): run
  `mobility-multi-1427994466115174934`, Random walk, seed 42, nine distributed
  devices, 480 movement minutes, all ten city nodes, LSTM/TCN at 40 epochs and
  the unchanged Candidate profile. There are 486 samples per node, 20 load
  versions, 80 vectors, 3,980 matched learned targets and 1,140 unobserved tail
  targets. Load qualification/routing, past-only training, all saved vectors,
  nine device model replays, target matching, accounting and feature isolation
  passed. The normal runner delivered 338 load batches; 4,950 live/export
  chart-state comparisons, including rewinds, passed. The copied result opens
  through the existing UI reader. Simulation time is 412.40 wall seconds,
  sampled Java/worker peak 2,918.67 MiB; final settlement is 486.93335 simulation
  minutes with no queued/running work. All twenty load versions first qualified,
  but 477/486 samples per fog node equal 100 B and all cloud samples equal zero.
  This is a multi-node engineering/demo baseline, not evidence of model
  superiority or energy savings. Exact config, source snapshot, audits, live
  journal and cost report accompany the portable result. Windows graphical live
  acceptance remains open. Current strategy names are Random walk and Nomadic
  waypoints; the earlier roadmap label Given Destination was inaccurate.

- W06.1d3 Windows acceptance and mainline decision (2026-11-17): the user
  supplied screenshots at replay 341.22, 364.22 and 424.22 min, then explicitly
  accepted the overall UI logic and requested the progress plan be updated.
  The matching uploaded summary/events/audit confirm run 4944157618143554283,
  candidate threshold 80%, west v1 rejection at 350 and v2 training at 414.
  There are eight TRAINING, TRAINED and REJECTED events, no PROMOTED/FALLBACK
  events, and final Persistence selection at both nodes. Audit status is PASSED
  for target matching, past-only training, qualification/routing and saved-model
  replay. Normal Windows live interaction and W07 quality/scaling remain open.
  Later UI wording is deferred at the user's request. The current table and
  next-action sequence supersede older acceptance requests in the historical
  entries below.

- W06.1d2 node-observation line width (2026-11-17): the user requests thinner
  purple/green curves in the bottom node cards and an explanation of the load
  controls. The existing scoped 1.5 CSS px non-scaling stroke rule now also
  selects `.node-card svg path`. It applies to all nodes and both available
  observation metrics. This changes one style file; data, axes, timing and
  model selection are unchanged. Production build passes
  (`main.03fbc04ff77aa50c.js`, 734.82 kB initial total), with existing size-budget
  warnings. Compiled CSS contains the intended rule. The cumulative installer
  accepts W06.1d or W06.1d1; LF/CRLF installation, byte-exact backups,
  idempotence and preservation of unrecognized local edits pass. Native
  Windows appearance remains to be confirmed after installation.

- W06.1d training-boundary review (2026-11-17, screenshots 19:50 Budapest
  time): at replay 284.22 min, the Windows UI shows 285/287 samples and
  LSTM/TCN Collecting history cards; Persistence predicts target 285 min and
  its blue earlier-test series contains rolling one-step forecasts. At replay
  286.22 min, first-fit LSTM/TCN v0001 rows show forecasts for 287-350 min,
  their states are Evaluating, and recorded service is still Persistence.
  Actual values stop at 286 min. This confirms short-history readiness and
  first issued-vector visibility in this layout, but not later maturation or
  normal live execution. The user reports that the display transition is hard
  to understand. Source inspection confirms why: the Persistence row changes
  from rolling one-step history to a paired 64-step comparison, so the earlier
  rolling-test curve/scores are no longer shown in this comparison. No earlier
  learned comparison block exists at the first fit. The existing generic
  "First saved forecast" notice and disappearance of baseline scores obscure
  that change. This is an open readability issue: distinguish rolling service
  from same-origin comparison, keep issue/target identity prominent, and make
  the selected forecast's growing actual/error comparison the simple default.
  Historical-test controls can remain in details. No UI changes are claimed
  for this explanatory review.

- W06.1d1 position-error line width (2026-11-17): the user accepts the Mobility
  logic and requests thinner error curves. Their 404.22 min screenshots show
  full-width position-error SVGs; the existing 1.5 SVG-unit stroke scaled with
  the viewBox. A scoped `.position-errors svg path` rule now uses 1.5 CSS px
  and `vector-effect: non-scaling-stroke`. No chart data, template, model,
  lifecycle, map or load-chart behavior changed. Production build passes
  (`main.cf5b1954500f95d3.js`, 734.74 kB initial total) with the existing
  size-budget warnings. The compiled resource contains the rule. A one-file
  W06.1d1 installer preserves backups and passes LF/CRLF and repeat-install
  checks. Windows appearance remains to be confirmed. Source review also
  confirms independent node histories: west and east are forecast separately
  for waiting/unassigned bytes. Device uploads and incoming offloads supply
  these queues; node selection does not change the selected Mobility device.

- W06.1d Windows review (2026-11-17, screenshots 19:25-19:26 Budapest time):
  the preceding install/build output reports 22 installed files and a
  successful Angular build with size-budget warnings. Five screenshots of
  `mobility-multi-4944157618143554283` show frame 373/482, replay 375.22 min,
  Device 0 and mobility-west. The fixed timeline remains visible in every
  section. Mobility shows ten minutes of observed movement and a HoldPosition
  forecast issued at 375.22 for 376.22 min. Its overlapping observed/predicted
  markers do not contradict the 267.09 m error of an earlier forecast whose
  target is 375.22 min. Mobility candidate v0006 has 6/64 matched targets.
  The position comparison uses 332 targets per method; the 364 selected-service
  targets additionally include the earlier baseline period.
  Load comparison explicitly retains origin 286 and targets 287-350 min while
  the latest uniform sample is 375.00 min / 100 B. All 64 selected targets have
  been observed. Persistence/LSTM/TCN respectively show within-tolerance
  percentages 18.75/25/18.75 and MAEs 76.17/65.23/86.47 B. Rejected v0001
  candidates and ongoing Persistence service agree with the candidate profile;
  visible forecasts do not imply service selection. The paired 64-step
  Persistence comparison is distinct from the current rolling baseline.
  Node accounting agrees with the observed cards: west 100 B / 8 running,
  east 0 B / 1 running, and 1,850 B sent/received. Whole-run counts are final
  retrospective values, not current-frame values. Its TP/FN/FP/TN table measures
  coverage exit; zero actual exits do not establish useful exit prediction.
  This review checks screenshots and source semantics, not newly supplied raw
  exports or native interaction. Remaining Windows checks cover short-run
  readiness, selected-origin maturation, expanded/axis controls and the normal
  trained live run. Follow-up wording should bring the historical-window notice
  beside its time cards, expose the qualification requirement/rejection reason,
  and name the retrospective coverage-exit metric explicitly. No UI code,
  model parameters, saved results or delivered package changed in this review.

- W06.1d readability (2026-11-17): movement defaults to the last ten minutes
  of known observations, with 30-minute/full-history options. Hollow forecast
  markers, issue/target times and an overlap explanation distinguish a single
  next-position prediction from the observed path and position-error curves.
  Untrained load models use causal sample-progress cards; completed short runs
  explain why reopening cannot create forecasts. Load charts default to the
  selected forecast with a short lead-in and absolute simulation-minute labels.
  The old context/relative view is selectable. Scores beside the focused chart
  describe its selected targets; earlier-test metrics retain their own section.
  Fixed section navigation and explicit uniform-sample versus event-snapshot
  descriptions complete this UI-only change.
  Sixty replay/component checks and TypeScript checks pass. Real Angular
  template/observer checks cover baseline and ten-node learned streams,
  pending-to-trained transitions, history retention, range/axis changes,
  chart reuse and cleanup: 30 change-detection ticks per scenario. Existing
  saved runs pass 58,704 comparison snapshots and 357 actual Chart.js/Node
  canvas draws, including focused/context views and unchanged data arrays.
  A plotted trained-run sample was visually inspected. Production build passes
  with size-budget warnings. No simulation or training was rerun. These checks
  do not test the native Windows layout; use the saved-run acceptance in
  `prediction-ui-readability.md`, then finish W06.1c's normal 480-minute live
  acceptance before W07 experiments.
- W06.1d delivery: 22 source/test/documentation files. Installer checks pass
  for LF/CRLF, the last delivered W06.1c1 documents, exact-byte backups,
  repeat installation, unknown edits/new-file conflicts, prerequisite/payload
  failures and partial-write rollback. Final production build is
  `main.ea3e3aefcaec3149.js` (734.60 kB initial total; size-budget warnings).
  Windows installation/build and core trained saved-run appearance were
  subsequently confirmed in the review above; broader acceptance remains open.

- W06.1c1 Windows full saved-replay interaction (2026-11-17, 18:35 Budapest
  time): the user's screenshots show the same run,
  mobility-multi-5738412321639819227, in the full replay at frame 312/969,
  time 43.22 min, Device 0 and load node Sopron_fog2. The fixed timeline
  remains visible above the map, candidate comparison, load models, observed
  node charts and accounting. The displayed trajectory and node state reflect
  this historical frame. Persistence uses origin 43.00 min and a one-minute
  horizon, with 43/43 matched test targets, 90.7% within tolerance and MAE
  11.63 B. Load LSTM/TCN correctly remain Unavailable before their 287-sample
  first fit. Position candidate v0001 has no matured targets yet, while the
  separate selected-service metrics include 32 earlier baseline targets.
  The upper load plot uses uniform samples through 43.00 min; lower observed
  charts/accounting use frame snapshots through 43.22 min. Sopron's displayed
  100 B sampled value and 0 B waiting / 1 running frame state therefore need
  not agree. These are screenshot and source-semantics checks, not an audit of
  this run's raw exports. Saved-replay opening/rewinding/node selection are now
  demonstrated; live history retention during incoming data and the normal
  Windows 480-minute trained run remain pending.

- W06.1c1 Windows saved-event replay acceptance (2026-11-17, 18:24–18:25
  Budapest time): the user's screenshots show diagnostic replay of
  mobility-multi-5738412321639819227 reaching Complete, 1,089 observations
  (100%) and replay/received time 126.93 min. Heartbeats continue at one-second
  intervals, with three load-chart canvases. DevTools shows the fixed build
  main.0c251ba443a22217.js. The map, all ten node-status cards and the fixed
  timeline render while scrolling; Open verified replay is available.
  Diagnostic verifiedReplay=false checks whether app-mobility-view is mounted;
  it indicates the live page is still open, not a failed export audit. Budapest
  is idle at zero bytes, so its Persistence test is 100% / MAE 0. Samples end at
  125 min (126 points), below the 287-point first-fit requirement; configured
  load LSTM/TCN correctly remain Unavailable. This confirms recovery from the
  zero-progress renderer stall for this saved journal. Slider/node switching,
  opening the verified replay and a new normal trained live run are not shown.
  The diagnostic used saved records; it did not retrain or start a simulation.

- W06.1c1 renderer fix (2026-11-17): Windows diagnostics sent saved live batches
  while the UI remained at zero and failed the five-second heartbeat. The user's
  paused stack was in Angular NgFor embedded-view creation. Source review found
  a fresh-object status getter without trackBy, combined with Chart.js's
  document/subtree observer registered inside Angular's zone. Statuses now
  materialize only on display updates, cards track node IDs, and chart creation,
  updates and cleanup run outside Angular. The pre-fix real-template observer
  reproduction reached 102 checks and the runaway guard; after the fix, the
  two-node Persistence and ten-node LSTM/TCN scenarios each complete in 24 checks.
  Checks include continued device observations, stable card elements, history
  retention, no future actuals, expanded views, completion and observer cleanup.
  Existing 54 component/display tests, TypeScript checks and production build
  pass (719.40 kB; existing bundle/style budget warnings). Another 339 real
  Chart.js/Node-canvas draws on the three saved integration runs pass, including
  outside-Angular creation/update/destruction assertions. The template test
  models browser observer delivery; Node canvas does not provide browser DOM
  layout. Native Windows replay acceptance is still pending. No simulation or
  model training was rerun for this fix; W07 scale experiments remain pending.
- W06.1c1 package: seven UI/test/documentation files; installation checks pass
  for LF/CRLF, the delivered W06.1c files, exact backups, repeat installation,
  unknown edits/new-file conflicts, missing prerequisites, corrupt payloads
  and rollback after an injected partial write. Rebuild the UI and use the
  existing saved-data diagnostic before spending time on another simulation.

- W06.1c implementation and verification (2026-11-17): Java publishes uniform
  node observations, whole issued vectors, target matches, service selections
  and lifecycle/version events to the existing live journal. TRAINING flushes
  before the worker call. Electron retains chronological history, drains all
  remaining chunks at completion and compares the stream with validated saved
  exports. The UI reuses the same compact model rows during execution, with a
  fixed shared timeline, Follow latest and historical browsing. Incoming data
  retains node/origin/filter/expanded-model choices. Completion retains the
  chosen historical time and offers Open verified replay explicitly.
  The isolated Java compilation includes the new listener source.
- W06.1c focused checks: 19 Java tests, 14 Python adapter/multi-device tests,
  41 Node reader/journal tests, 54 Angular component/display logic checks and
  TypeScript checks passed. Production build passed (719.17 kB initial bundle;
  size-budget warnings, including 2.83 kB live-launcher styles). Existing sampling,
  qualification, retry, units and predictive-control boundaries are preserved.
  Tests include incomplete/invalid vectors, future actuals, reconnect overlap,
  partial/truncated journals, cancellation, final EOF, large final batches and
  preservation of historical display state.
- W06.1c real-run integration: the actual UI job launcher, Java simulation,
  TensorFlow worker, Python export audits, final reader and live-to-final checker
  were exercised together. All three new runs completed and passed record
  equality plus 4,870 chart comparisons at sample boundaries and rewound times.
  All use Random walk and seed 42; learned-load runs use 1 epoch to test data
  flow, while the normal UI retains 40 epochs. These are not accuracy benchmarks
  or W07 scaling results. The city case includes nine devices, all nine fog
  nodes and Budapest cloud; learned forecasts have both matured and unmatched
  tail targets. No GUI/browser or Windows live-interaction acceptance is claimed.

  | Run ID | Scenario / movement minutes | Samples | Learned vectors | Learned matched targets | Chart comparisons | Received load batches |
  | --- | --- | ---: | ---: | ---: | ---: | ---: |
  | mobility-multi-8227344626455854375 | 2 nodes / 1 device / 104 min / baseline | 216 | 0 | 0 | 234 | 7 |
  | mobility-multi-1488712480630071923 | 2 nodes / 1 device / 480 min / LSTM+TCN | 968 | 8 | 512 | 986 | 50 |
  | mobility-multi-18339009028549274217 | 10 city nodes / 9 devices / 350 min / LSTM+TCN | 3560 | 40 | 1380 | 3650 | 238 |

- W06.1c package: 27 source/test/documentation files. Installer checks passed
  for LF/CRLF, earlier delivered W06.1b3 documents, exact-byte backups, repeat
  installation, local edits/new-file conflicts, prerequisite and payload
  failures, and partial-write rollback. Results, models and Git state are not
  overwritten. Windows acceptance needs a new run; reopening saved results
  does not emit new live records. Follow `node-load-live.md`, then proceed to
  W07's changing-demand/seed/device-count experiments.

- W06.1b forecast-origin visibility acceptance (2026-11-17): the user shows
  run mobility-multi-4944157618143554283 at frame 412/482, replay 414.22 min
  and common origin 414 min. Yellow Actual ends at the origin's last known
  sample; pink Persistence/LSTM/TCN forecasts continue for the 64 future
  targets, with no yellow future samples shown. Blue v1 Test remains at its
  historical targets. LSTM/TCN v0002 show Evaluating, while recorded service
  is Persistence at 414 min. Test scores explicitly belong to v0001.
  Together with the earlier final-settlement screenshot, this confirms the
  core as-of display for this saved origin. No new training or raw-export
  audit is claimed. W06.1c execution-data work is the next development step.

- W06.1b3 Windows acceptance (2026-11-17): four user screenshots of
  mobility-multi-4944157618143554283 show the same top Replay time control
  while scrolling from the map through errors and learned-load rows to node
  observations/accounting. All show frame 409/482 and replay 411.22 min.
  Compared with the previous final-settlement screenshots, the load panel
  correctly chooses origin 286 min / v1 because v2's 414 min issue is still
  in the future. No earlier Test is correctly reported. The user says the
  layout is better. Core fixed-header acceptance is complete; continuous
  dragging, keyboard and narrow-window interactions were not observed.
  W06.1c remains the next development stage; this does not establish live
  execution updates or improved forecasting quality.

- W06.1b3 persistent timeline (2026-11-17): moved the existing range input out
  of `.content` into the fixed header flow. It retains the same index/seek
  binding, native keyboard controls and single shared simulation time. Start
  and end times remain visible, with current time/frame in the playback row.
  The header takes its own layout space and playback buttons can wrap on
  narrower windows. No chart overlay, duplicate clock or forecast timestamp
  changes are introduced. Angular production build and all 50 existing replay
  logic checks pass. Bundle/style budget warnings remain (initial bundle
  706.97 kB; the component styles now include the timeline row). Installer
  checks pass for LF/CRLF, exact-byte backup, repeat installation, delivered
  W06.1b2 documents, unknown local edits, missing prerequisites, corrupt
  payload and partial-write rollback. Package contains two UI files and two
  documentation files. Native Windows scrolling, slider dragging and narrower
  window layout acceptance are still pending; no browser visual test is claimed.

- W06.1b2 Windows trained-run follow-up (2026-11-17): user screenshots of
  mobility-multi-4944157618143554283 confirm three compact model rows,
  Actual/Test/Future colors and absolute simulation-time hover. Replay is
  503.22 min; the common forecast origin is 414 min. Blue Test uses v1
  targets 287–350 min, while pink v2 forecast uses 415–478 min. Right-side
  Test cards report Persistence 18.75% / 76.17 B MAE, LSTM 25% / 65.23 B,
  TCN 18.75% / 86.47 B, each 64 matched. These are earlier Test scores,
  not v2 forecast scores or evidence of qualification. Recorded service
  remains Persistence. West/east are expected for this historical run.
  This is screenshot acceptance, not a new raw-export audit; rewind,
  expanded interactions and east-node prediction switching remain pending.

- W06.1b2 Windows follow-up (2026-11-17, 13:12 local): screenshots from
  mobility-multi-10956657032195469642 confirm the revised shared-load panel
  renders on Windows. Selected node Sopron_fog2, origin 119 min, replay
  126.93 min, Persistence only, horizon 1 min. The visible Test card reports
  64/64 matched and MAE 0. Actual is flat around 100 B over that test interval.
  Source inspection confirms Chart.js draws the first Actual dataset on top
  of the prediction datasets; coincident lines can therefore be concealed.
  Pink spans only origin 119 to target 120 (relative 0 to +1), not a new
  64-minute forecast at final settlement. Next inspect individual curves
  with the display toggles and reopen the saved 480-minute trained run.
  No raw files from this user run were newly audited. Curve distinguishability,
  expanded interactions and multi-model Windows acceptance remain open.

- W06.1b2 reference review and revision (2026-11-17): four user screenshots
  confirm the original standalone workload UI still opens and establish the
  preferred design. The shared view adopts compact per-model rows, semantic
  colors, thin unsmoothed lines, right-side within-tolerance/MAE cards and
  collapsed details. Expand opens a large chart with model switching and
  bounded saved-origin navigation, using the existing Chart.js dependency.
  The relative axis is in minutes from a single common absolute origin;
  tooltips show absolute simulation time. Processed input is absent from
  shared exports and is not reconstructed. The adapter still covers
  Persistence/LSTM/TCN, not all standalone model families. Separate rows
  replace the earlier overlay option. No prediction or training code changed.
- W06.1b2 verification: 50 display tests, 32 reader/IPC tests and TypeScript
  checks passed. Production build passed with existing size-budget warnings.
  The three real saved runs listed below again passed 28,496 snapshots; the
  checker now also verifies canvas values, common axis bounds and reversible
  relative-to-absolute time mapping. Negative predictions remain visible;
  missing samples/vectors break lines; unseen actuals cannot change scales.
  Expanded-view state/navigation is checked without a browser. Windows
  appearance and native dialog interaction remain pending because Chromium
  cannot create sockets here. User screenshots are the original UI reference,
  not visual acceptance of this revision.
- W06.1b2 installer: all 12 payload files preflight before writing. Isolated
  tests passed for LF/CRLF and installed W06.1b document variants, exact-byte
  backups, repeat installation, unknown edits/new-file conflicts, missing
  prerequisites, corrupt payload and partial-write rollback. Saved runs and
  the original standalone workload chart are not modified.
- W06.1b Windows design feedback (2026-11-17): screenshots from short city run
  `mobility-multi-10956657032195469642` confirm the new History/Test/Forecast
  Persistence card, city observations and accounting render at final settlement.
  This is baseline-only output, not a learned multi-model acceptance run. The
  user requests reopening the original standalone workload UI because the
  presentation still differs substantially. Source verification confirms the
  original PredictionChart, actual/processed/backtest/future filters, common Y
  scale, View simulation and read-only SQLite result entry remain present.
  Next compare the original on-screen behavior before redesigning; do not
  declare W06.1b accepted or advance to live updates on these screenshots alone.
- W06.1b implementation (2026-11-17): one city's Persistence, LSTM and TCN
  share a saved forecast origin, target grid, time range and Y scale. Separate
  model cards are the default; overlay and model visibility controls are
  available. The test block is the latest fully matured earlier vector at a
  common origin where possible. Version/origin labels distinguish it from the
  shown forecast. Rejected-model gaps are not bridged, unavailable models stay
  empty, and short baseline-only runs keep their one-minute horizon.
  Actual values and lifecycle states respect replay time in both directions.
  Test metrics and partially matured forecast metrics remain separate from
  cumulative metrics and serving eligibility. Original node sampling, training,
  qualification, forecast issuance and resource decisions are unchanged.
- W06.1b verification: 45 Angular component/display logic tests and 32 Node
  reader/IPC tests passed, along with TypeScript checks and the production
  build (size-budget warnings). Three existing runs passed 28,496 comparison
  snapshots, including 48 explicit origin selections: nine-device city
  baseline (`6876324114484664219`), ten-node city learned load
  (`5130445083437718753`) and legacy two-node learned load
  (`13251787079068413921`), all prefixed `mobility-multi-`. Checks independently
  recompute target metrics and verify record identity, common origins/targets,
  past-only actuals, missing tails, finite chart paths and forward/backward
  seeking. Chromium cannot launch because socket creation is denied, so this
  does not claim browser visual QA or Windows installation acceptance.
- W06.1b Windows acceptance: first reopen the existing trained W05 run
  `mobility-multi-4944157618143554283`. At origin 414 min, expect the earlier
  v1 test targets 287–350 min and the v2 forecast targets 415–478 min, with
  the actual-only gap retained. At origin 286 min there is no earlier learned
  test vector. The user's current 120-minute city run has Persistence only;
  adding a display cannot create absent trained forecasts. Accept its honest
  short-run state separately, then use a long city run for trained-city checks.
- W06.1b installer verification: 12 payload files; isolated tests passed for
  LF/CRLF, the installed W06.1a2 document variants, exact original-byte backups,
  idempotence, unknown edits/new-file conflicts, missing prerequisites,
  corrupt payload preflight and rollback after a partial write failure.
  Installation updates the repository sources without committing, pushing or
  rewriting saved results. Windows installation acceptance remains pending.
- W06.1a2 device-detail confirmation (2026-11-17): the user's next screenshot
  shows Device 1, Szombathely_fog2, Device detail, and a clearly resolved local
  trajectory at 110.67 minutes. LSTM candidate v0002 is displayed while the
  serving model remains HoldPosition; viewing a candidate does not promote it.
- W06.1b design review (2026-11-17): source comparison confirms the original
  workload chart separates actual history, backtest predictions and future
  predictions. Its relative axis aligns each model's first future target at
  zero; that alone does not prove equal absolute simulation times. The shared
  UI currently shows one method's saved vector or rolling Persistence on an
  absolute node grid. The next design must show three distinct time regions,
  multiple models, common origins/targets, honest missing-data states and
  separately measured serving eligibility. No prediction/runtime code is
  changed by this review; the implementation remains pending.
- W06.1a2 Windows follow-up (2026-11-17): the same user run is reopened at
  110.67 minutes. Screenshots confirm the revised overview, individual city
  load charts, time filtering and node-row accounting. Device 0 is selected;
  Node coverage shows Sopron's 20 km radius. Clicking a city changes the
  coverage viewport, not the selected device. The default RandomWalk radius
  is 800 m; the user's raw settings/trajectory files have not been re-audited.
  Device detail and another device's path still require Windows confirmation.
- W06.1a2 follow-up data check (2026-11-17): independently rechecked existing
  local nine-city run `mobility-multi-6876324114484664219`, not the user's raw
  run. All nine devices have 121 distinct observed positions. All 1,260
  Persistence forecasts use their origin sample and target the next minute;
  all 1,250 matched evaluations use the actual at that target. Sopron through
  110.67 minutes has 110 matches, MAE 4.55 B and RMSE 25.23 B, matching the
  screenshot values. Uniform samples at 109/110/111 minutes are 100 B, while
  observation snapshots at 109.2167/110.2167 minutes include 0 B waiting and
  one running task. This explains the flat sampled forecast panel versus brief
  dips in observed-node charts. It does not establish trained-model quality,
  general accuracy under variable demand, or energy-control effectiveness.
- W06.1a Windows evidence (2026-11-16): user screenshots of
  `mobility-multi-10956657032195469642` show COMPLETE, 969 frames, nine devices,
  ten city-labelled nodes and final time 126.93 minutes. Device 0 connects to
  Sopron; the selected city's Persistence and observed load are displayed.
  All nine fog nodes show 11,900 B uploaded in the accounting screenshot.
  This is screenshot evidence, not an independent audit of that run's raw files.
  It does not verify trained city-load models, intercity movement or energy control.
- W06.1a2 readability: national overview and local device detail are separate.
  City buttons focus a node's true coverage radius; the default local zoom is
  based only on observed positions and issued predictions. Cloud has no access
  circle. Node identity/colors remain stable across device/frame ordering.
  Event loads use one node per chart, shared scales, and 30/60-minute or full-run
  ranges. Waiting bytes and running tasks are selectable; current values remain
  visible. Accounting uses node rows. Position-error model curves can be hidden
  without changing evaluation metrics or serving ownership. Existing model
  prediction, qualification, simulation and resource controls are unchanged.
- W06.1a2 verification: 33 Angular component/display logic tests, 32 Node tests,
  TypeScript checks and the production UI build passed (size-budget warnings).
  Three existing runs cover nine-device cities, ten-node learned load, and the
  legacy two-node scenario. A total of 18,288 chart states passed forward/backward
  replay, finite projection, time-range, node identity and final-gap checks.
  Chromium cannot launch in this environment (socket permission denied), so
  these checks do not claim browser visual or Windows installation acceptance.
  Reopen the user's completed run for that final visual check; no new training.
- W06.1a2 installer checks: all payloads are preflighted before writing; CRLF/LF,
  exact backups, idempotence, unknown local edit protection, missing prerequisites,
  corrupt payload rejection and injected write-failure rollback passed in isolated
  repositories. No Git commit/push or existing result rewrite is performed.

- W06.1a (2026-11-16): extracted shared CityTopology and retained the original
  workload hardware, VM settings, link order/latencies and static scenario.
  New city Mobility uses explicit starting cities, local official movement,
  synthetic fog coverage and existing reactive Pliant offloading. The saved
  topology manifest keeps all ten nodes, including an unvisited cloud. Device
  history validation now uses this run's access-node list. Exact-node training
  supports cloud without changing the standalone trainer's fog-only default.
- W06.1a automated checks: 41 distinct Java tests, 64 Python tests, 32 Node
  tests and 29 Angular component/display logic tests passed, along with the
  production UI build (existing size-budget warnings). The Java topology test
  runs isolated 20 km and 10,000 km scenarios, verifies original hardware and
  roles, injects a backlog, confirms cloud offloading and checks byte drainage.
- W06.1a complete city runs: RandomWalk with nine devices/120 minutes
  (`mobility-multi-6876324114484664219`), and Nomadic with two devices/120
  minutes and IMM/Kalman, XGBoost and TCN shadow comparisons
  (`mobility-multi-9126157911771237482`). Each has ten sampled nodes and 126
  samples per node. Causal feature reconstruction, device isolation, saved
  model replay, accounting and UI parsing passed. The Nomadic run also
  verifies resource/application metadata in the node manifest.
- W06.1a load path: one device at Szeged/350 minutes
  (`mobility-multi-5130445083437718753`), LSTM/TCN at all ten nodes, one epoch
  for integration testing. Twenty versions and forty saved vectors passed
  training-data, target-time, qualification/routing and saved-model audits.
  There are 1,340 matched and 1,220 explicitly unmatched future targets.
  Constant/light load explains qualification here; this is not an accuracy
  benchmark. Normal UI training defaults and quality thresholds are unchanged.
  Forward/backward UI checks covered 28,400 display states across all ten
  nodes, with no future observations/versions/evaluations shown early.
- W06.1a legacy regression: a new two-node Nomadic/one-device/120-minute run
  (`mobility-multi-12332373867604150520`) passed the full pipeline and audits.
  Old result fixtures without node_topology.json remain readable.
- W06.1a remaining acceptance (updated after screenshots): installation and a
  completed short Windows city run are confirmed. Check the revised map/device
  zoom and all ten load-node choices, then reopen the previous two-node result.
  Full Windows interactive acceptance, a city 100-device benchmark, model-quality
  improvement and energy saving are not yet established. Simultaneous model curves and live node-load charts remain
  W06.1b/c. W07 onward remains as planned.
- W06.1a installer: all 30 payload files are checked before writing. Isolated
  tests passed for CRLF/LF, original-byte backups, repeat installation, known
  document variants, preservation of unknown local edits, missing prerequisites,
  corrupt payloads and rollback after an injected write failure. These checks
  do not substitute for the pending Windows UI acceptance.

- W03: the user's installed sampler produced valid CSV and JSON files.
- W04: 12 relevant Java tests and 13 Python tests passed. Four complete runs
  passed existing LSTM lifecycle, byte-accounting, node sampling and baseline
  audits. Before/after checks found unchanged movement samples, observed node
  load and offload events in all four cases (run identifiers excluded).
- W04: replaying the user's 248 sample rows through the same baseline class
  produced 248 forecasts, 246 matched targets and two unmatched tail targets.
  An independent calculation confirmed west MAE/RMSE 78.66/100.38 B and east
  9.55/15.45 B. This is a saved-data replay, not a new Windows simulation.
- W04 user verification: the user supplied `node_load_predictions.csv`,
  `node_load_evaluations.csv` and `node_load_prediction_summary.json` from new
  run `mobility-multi-4237531875228351577`. All three-file checks passed:
  per-node continuous origin indices; 60-second horizons; exact forecast and
  evaluation identities; actual-time matching; the next forecast persisting
  the preceding evaluation's actual; signed/absolute errors; per-node and
  aggregate MAE/RMSE/bias/timing; and two correctly unmatched final forecasts.
  Each node has 124 forecasts and 123 evaluations. West MAE/RMSE is
  78.66/100.38 B; east is 9.55/15.45 B.
- Verification scope: this user's current-run check covers the three forecast
  exports. Raw node samples and offload events for that run were not supplied.
  The previously uploaded W03 samples have a different run ID and were not
  joined to the new results. This is not an independent raw-event audit.
- W05: 17 Java tests, 19 Python tests, 10 Node tests and six Angular component
  logic tests passed. The production UI build passed with size-budget warnings.
  Chromium could not start in this container; the six DOM-free component tests
  were run with Jasmine/Node and native Angular modules. No browser visual QA
  or Windows UI launch is claimed.
- W05 RandomWalk candidate run, one device, 480 movement minutes:
  `mobility-multi-13251787079068413921`. Each node had 484 grid samples.
  Eight fitted versions produced 512 matched targets. All eight versions were
  rejected under the experimental 80% criterion; final selection stayed
  Persistence. All saved forecast vectors reproduced from the `.keras` files.
- W05 Nomadic observe run, one device, 360 movement minutes:
  `mobility-multi-8790565862090077708`. Four trained versions qualified under
  the permissive 0% workflow profile, with 308 matches and 204 pending tails.
  Eligibility, switching and saved-model replay passed. This is not evidence
  of useful predictive accuracy.
- W05 control run: the same RandomWalk scenario with trained load models off
  had identical node samples, event-driven load, offload events, device
  observations and selected mobility evaluations (run IDs excluded).
- W05 ten-device 120-minute regression:
  `mobility-multi-7895558935451081771`. All ten mobility lifecycles passed their
  existing audits. There were 126 samples per node and zero trained load
  versions, correctly retaining Persistence. This checks short-run handling,
  not trained-load accuracy or scaling at ten devices.
- W05 independent audit also checks training snapshots, normalization inputs,
  future target matching, first qualification, retry window growth, routing,
  final states and unmatched tails. Adversarial tests reject future labels,
  early selection and changed training snapshots. Synthetic lifecycle tests
  cover degradation/fallback separately from the real experimental runs.
- W05 user verification (2026-11-16): the user supplied
  `node_load_model_audit.json`, `node_load_model_summary.json` and
  `node_load_model_events.jsonl` for run
  `mobility-multi-4944157618143554283`. All 56 cross-file consistency checks
  passed. The audit reports PASSED for past-only training, target matching,
  qualification/routing and saved-model replay (eight vectors). Summary and
  events agree on two nodes, LSTM and TCN, two versions per node/family,
  512 forecasts, 512 matches and zero unmatched targets. The audit also
  reports 968 selection rows.
- W05 user lifecycle evidence: each v0001 used 287 raw points / 32 training
  windows at sample index 286 and was rejected after 64 targets at index 350.
  Each v0002 used 351 raw points / 96 windows at index 414, after 64 new
  observations following rejection, and was rejected at index 478. All eight
  versions failed the experimental candidate requirement of at least 80%
  within max(0.1 B, 20% of actual magnitude). West LSTM v1/v2 achieved
  25%/18.75%; west TCN 18.75%/20.3125%; east LSTM 4.6875%/0%; east TCN
  10.9375%/3.125%. Final selection remains Persistence for both nodes, with
  no promotion or fallback event. FAILED denotes a rejected model version;
  the run status is COMPLETE. Resource, connection and offload control are off.
- W05 user verification scope: the three uploaded exports were checked
  independently against one another and the documented lifecycle. Per-target
  CSVs, raw training snapshots and saved model files from this run were not
  supplied, so their numerical audits and model replay are reported from the
  user's audit file, not independently rerun here. W05 integration verification
  is complete; useful learned-model accuracy has not been demonstrated.
- W06: the shared run reader loads node samples and forecasts once, separately
  from device-level Mobility forecasts. Node/model/version/origin controls and
  the toolbar's Node load jump show actuals, saved forecast vectors, paired
  Persistence, matured metrics and lifecycle state at the shared replay time.
  Legacy/no-export, samples-only, baseline-only and short/untrained runs remain
  readable. Incomplete/mixed exports and invalid selections fail explicitly.
- W06: 31 Node reader/IPC/regression tests and 28 Angular component/display
  logic tests passed. TypeScript spec checks and the production UI build passed;
  the build has the existing size-budget warnings. No model or simulator logic
  was changed, and no new simulation/training was required for this step.
- W06: forward/backward component replay checks passed on four saved W05 test
  runs: candidate (`13251787079068413921`), observe (`8790565862090077708`),
  load models off (`17304737189916810831`) and ten-device short/untrained
  (`7895558935451081771`), all prefixed `mobility-multi-`. In total 35,000
  display snapshots checked known-only actuals/versions, matured metric counts,
  forecast origin/lead handling, node isolation and final unsampled gaps.
  Candidate/observe retained 512/308 learned-model matches respectively.
  These are saved-result display checks, not new training or scale experiments.
- W06 installer: verified CRLF/LF compatibility, exact backup contents,
  idempotent reinstallation, both known W05 progress-document variants,
  unknown local-edit and missing-prerequisite protection, payload integrity,
  and rollback after an injected write failure. These are isolated installer
  tests; they do not themselves establish a successful Windows installation.
- W06 visual/installation scope: Chromium cannot start because the execution
  environment denies its socket creation. Component logic and template build
  checks therefore do not claim browser visual QA. Subsequent user screenshots
  provide the limited Windows display evidence described below.
- W06 Windows partial acceptance (2026-11-16): screenshots
  `image(20261116-202517).png` and `image(20261116-202652).png` confirm that
  run `mobility-multi-4944157618143554283` opens with the new Node load button.
  At replay time 286.22 min, the west node shows its 286.00-min actual of
  200 B, LSTM v0001's 64-minute vector, the same-origin Persistence line at
  200 B, Evaluating state and the recorded Persistence choice for minute 287.
  Future actuals are hidden in the displayed plot. The first-training screenshot
  does not include the comparison rows; final metrics are verified below.
- W06 Windows west final-settlement check (2026-11-16): screenshot
  `image(20261116-203010).png` shows 482/482 frames at 503.22 min. Reaggregation
  of the user's uploaded W05 summary matches all displayed learned-model row
  values: LSTM 128 matches, MAE/RMSE 63.62/84.87 B, within tolerance 21.88%;
  TCN 128 matches, MAE/RMSE 78.23/94.15 B, within tolerance 19.53%. Both rows'
  paired Persistence MAE/RMSE are 71.09/93.59 B. All four displayed versions
  have 64 matches and Rejected state; v1/v2 use 32/96 training windows. The
  last recorded choice is Persistence. The displayed latest sample is 125 B
  at minute 483; the plot leaves the gap to final settlement blank, as intended.
  This is a screenshot-to-summary check, not an independent raw-target audit.
- W06 Windows east/TCN acceptance (2026-11-16): screenshot
  `image(20261116-203310).png` shows mobility-east with TCN selected at the same
  503.22-min final settlement. The latest actual is 25 B at minute 483; TCN
  v0002's vector was issued at minute 414, is displayed as Rejected, and the
  last recorded choice remains Persistence. Reaggregation of the uploaded
  summary matches all learned-model row values: LSTM 128 matches, MAE/RMSE
  10.85/14.09 B, within tolerance 2.34%; TCN 128 matches, MAE/RMSE 12.33/13.89 B,
  within tolerance 7.03%. Both paired baselines show MAE/RMSE 10.16/15.93 B.
  The four per-node version rows each have 64 matches and Rejected state,
  with v1/v2 training windows of 32/96. The displayed node, actual scale and
  model vector changed consistently. This completes W06's requested Windows
  UI acceptance for the saved W05 run; it does not establish forecast quality
  or trained-load scalability at 10/100 devices.
- W06.1 scope clarification (2026-11-16): the user notes that the original
  standalone workload UI displays several model charts together and changes
  during a running simulation. Source review confirms that ScenarioComponent
  renders all prediction records at the selected prediction number and receives
  dataUpdates; PredictionChart renders actual/backtest/future series with an
  optional common scale. NodeLoadView currently renders only one selected
  model's latest/chosen vector, with actuals and paired Persistence. Its model
  metrics are simultaneous, but its model forecast curves are not. The full
  Mobility result is loaded after run completion and validation; job progress
  events do not constitute live node-load chart updates. Forecast issuance
  cadence is also distinct from UI refresh cadence and must remain explicit.
  Existing W05 files support a multi-model saved-result view without retraining;
  live chart updates require a separate backend-to-UI incremental data path.
- City/roadmap review (2026-11-16): PredictionSimulationDefinition contains
  Budapest_cloud; level-1 fog nodes Gyor, Kecskemet and Gyongyos; and level-2 fog
  nodes Sopron, Szombathely, Szeged, Pecs, Miskolc and Debrecen. Parent and peer
  links are explicit. These are simulated city-labelled resources. The original
  workload uses static devices and range=10000 km; LoadBalancedDeviceStrategy
  selects the least-loaded eligible application. City-mobility work therefore
  needs explicit movement/access configuration, not just label replacement.
  Both old and new workload targets are receivedData minus processedData,
  meaning work not yet assigned to compute. The current Mobility runtime and
  result geometry still construct west/east explicitly. Source verification
  and roadmap alignment are complete; no city integration or controller test
  is claimed. The integration-plan status was also brought up to date from W04.
- Next (updated 2026-11-17): close the remaining W06.1c normal Windows trained
  live-run acceptance, then W07 scenario/quality/scale checks,
  W07.1 mobility-to-demand fusion, W08 energy/actions and W09 controller
  comparisons. The research goal remains lower total modeled energy with
  service requirements respected; prediction and display are supporting work.
