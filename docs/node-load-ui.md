# W06 / W06.1d3: node-load predictions during execution and replay

W06 displays W03 uniform node samples, W04 one-minute Persistence forecasts,
and W05 trained LSTM/TCN forecasts inside the existing Mobility result UI.
W06.1c also feeds incremental observations, complete issued vectors and lifecycle
events into the same model charts during a new run. The simulation still owns
training and forecast issuance; the UI does not change qualification policy,
movement, or resource/offloading decisions.

## Open an existing run

Restart the UI through `MobilityPredictorUi.java`. The launcher rebuilds newer
Angular sources. Choose **Open mobility results**, then the completed
`mobility-multi-*` directory containing the CSV/JSON exports and `devices`.
Choose the run directory, not an individual JSON or a device subdirectory.
No new 480-minute simulation or training is required for an existing W05 run.

Click **Node load** in the replay toolbar to scroll to the new panel.
The movement device selector remains at the top; the load panel has a separate
**Node** selector. Node data covers all devices connected/uploading to that
node, independent of which device trajectory is selected above.

## Persistent shared replay timeline

W06.1b3 keeps **Replay time** directly below Previous / Next / Play, outside
the scrolling content. The device filters stay below it. Scroll down to load
prediction, node observations or accounting and drag the same slider to change
the shared simulation time without scrolling back to the map. The header
reserves its own space rather than covering the charts.

The range endpoints show the run's first and last simulation times. Current
time and frame are in the playback row; dragging still selects saved replay
frames. Click the slider and use Left/Right for adjacent frames or Home/End
for the first/last frame. Manual seeking pauses playback as before.

The slider updates Mobility, node observations, accounting and every load row
at the same simulation time. W06.1d3 removes the load panel's second forecast
clock. The window is [T−192, T+64] minutes, clipped at the beginning to [0, 256]
until T reaches 192. Its width stays 256 minutes. New forecast blocks add data
without resetting the window. No relative-axis or history/focus choice is needed.

## Default comparison: rolling window

Choose a node, then use the fixed top replay slider. Model buttons show/hide
Persistence, LSTM and TCN rows. Expand enlarges one row at the same replay time.
The **Display options and reading guide** disclosure contains curve visibility
and Common Y scale. Hidden rows remain included in the common scale and scores.

| Mark | Meaning |
| --- | --- |
| Yellow actual | Uniform samples observed at or before replay time |
| Blue earlier prediction | Previously issued predictions whose target times have passed |
| Pink future prediction | Already-issued predictions whose targets are still ahead |
| Yellow Now line | Shared replay time, not a selected forecast origin |
| Empty forecast interval | No recorded vector covers those target times |

A pink point becomes blue when replay reaches its target, retaining its exact
original value, version, issue time and target time. Hover shows that identity.
Different origins are separate segments; missing intervals are never joined.
A candidate's rejection does not delete its earlier issued forecasts. No future
actuals are shown, even when loading a completed run. Node sampling and training
cadence are unchanged. The UI does not generate a new 64-step vector per minute.

Each right-side score card identifies the **latest issued forecast** for that
row: version, issue time, original target range and observed/total target count.
Scores use this block, independent of the sliding viewport or hidden curves.
A new block starts a new score; older blocks remain visible within the window.
Each trained row also exposes same-target Persistence MAE under Score details.
If model schedules differ, their issue times and target sets can differ.

Before any trained block exists, Persistence displays rolling one-step records
and labels its latest score one-step. Once trained blocks exist, its main row
shows paired references with the same origins and 64 targets. Rolling history
before the first fit is retained. Subsequent gaps stay empty; ongoing one-step
service remains separately inspectable in Diagnostics. Inconsistent saved
paired references are not replaced by an arbitrary model's reference.

Example from the existing two-node run: first fit at 286 min predicts 287–350.
At replay 300.22, the window is 108.22–364.22, 14 targets are blue/observed and
50 targets remain pink. No forecast is extended beyond 350. At replay
320.22, 34 targets have matured and 30 remain pink. At 350.22, the whole block
is historical. If rejection delays the next issue until 414, targets 351–414
stay empty. At 414.22, v2 targets 415–478 appear at their true times while v1's
history remains visible; the viewport continues to 222.22–478.22.

Short runs show training-history cards until a completed forecast is recorded.
Reopening a 120-minute run cannot supply the 287 samples needed for first fit.
Final settlement keeps a fixed-width window but hides beyond-end forecasts;
unsampled drain time remains blank and missing actual targets are unscored.

## Recorded service and individual-vector inspector

Expand **Diagnostics · saved forecasts, service and cumulative metrics** to
use the original W06 controls and cumulative lifecycle tables below.

| Control / mark | Meaning |
| --- | --- |
| Node | The node whose waiting bytes are sampled and predicted |
| System selected | Recorded W05 next-target choices; trained values retain their original forecast lead |
| Persistence · 1 min | W04 rolling one-minute baseline, issued at each grid sample |
| LSTM / TCN · 64 min | One saved 64-value vector from that node and model family |
| Version | Filters learned model vectors and the trained-model comparison table |
| Forecast origin | Latest vector issued by this replay time, or a chosen earlier origin |
| Jump to first load training | Seeks the first Mobility frame at or after this node's first load fit |
| Yellow line | Actual uniform samples available by the replay time |
| Pink dashed line | Shown predictions, including future targets if their forecast has already been issued |
| Green dashed line | Persistence using the learned vector's same origin and target times |
| Vertical dashed line | Current shared simulation/replay time |

The target is **waiting_assignment_bytes**, not CPU utilization or running task
count: bytes received but not yet assigned to compute. Units are bytes (B).
The older observed waiting-data/running-tasks charts remain below this panel.

The shared sample grid is every 60 simulation seconds starting at zero. A
Mobility frame can fall between grid samples (for example 286.22 minutes).
At that frame, the panel can show the sample/fit at minute 286 and the forecast
for minutes 287–350, but no actual value from minute 287 or later.

Rejected versions remain inspectable. A version labelled Rejected is not
eligible to supply the selected load prediction; the whole simulation may
still have completed normally. Selecting LSTM or TCN for viewing does not
promote that model or change the recorded selection.

## Evaluation and lifecycle

- Only target actuals at or before the replay time contribute to metrics.
- MAE, RMSE and within-tolerance are recomputed for the chosen version filter.
- Each learned row has its own paired Persistence comparison with identical
  origin/target times. Different model rows may have different target sets.
- W04's rolling one-minute baseline is reported separately, since its forecast
  horizon differs from the 1–64-minute leads of a trained vector.
- System-selected metrics preserve the original forecast lead; being selected
  for the next target does not turn an old prediction into a fresh one.
- Version training time, matched counts and state only appear after their
  actual simulated training/event times. Rewinding removes later information.
- Lifecycle state comes from events, never from the whole-run final state.
  A promoted model becomes Active; another active model becomes Ready. A
  degraded model is labelled Degraded and the selection can fall back.
- Inference duration is per 64-value model call, counted once per vector.
  It is not divided by 64 or compared as if it were one-point latency.
- No future actual overlay is enabled in this panel. Missing final targets
  remain unscored; task-drain zeros are not substituted for missing samples.

The candidate/observe thresholds are inherited unchanged from W05 and remain
uncalibrated experimental settings. The current result's low accuracy is not
fixed by this visualization update.

## Compatibility and validation

No new dependencies are required beyond the existing Angular UI installation.
The Electron result worker loads the node files once at run level and shares
the simulation clock across device switches. Node chart snapshots are cached
until their time, node, method, version, origin or run changes.

Pre-W03 runs open with an explanatory empty panel. W03 samples-only, W04
baseline-only, models-disabled and short/untrained W05 runs also remain
readable. Partial groups and mixed run/node identities fail explicitly.
The reader checks grid continuity, forecast/target identity, paired baseline,
matched values/errors, complete vectors, count consistency, qualification
timing/quality and recorded selected-model eligibility. It does not replace
the backend's saved-model and training-snapshot audit.

From `predictor-ui/ui`:

```powershell
node --test test/mobility-results.test.cjs test/mobility-multi-results.test.cjs test/mobility-ipc.test.cjs test/mobility-comparison-results.test.cjs test/node-load-results.test.cjs
node test/run-replay-logic-tests.cjs
npm run build
node test/check-node-load-replay.cjs '<completed mobility-multi run directory>'
node test/check-node-load-timeline.cjs '<completed mobility-multi run directory>'
```

The replay checker uses the real Angular component logic without a browser.
It checks both forward/backward time traversal on existing run files without
retraining. A successful production build additionally checks the templates.
These checks do not claim browser visual QA or a successful Windows launch.

## W06.1d3 Windows acceptance

Use `prediction-ui-readability.md`. First reopen saved short/long runs without
retraining, then finish the normal live acceptance in `node-load-live.md`.

## Individual inspector checks

1. Open the user's completed W05 run `mobility-multi-4944157618143554283`.
2. Click **Node load**, open **Diagnostics**, then **Jump to first load training**. Around minute 286, switch
   Load prediction to LSTM. See a 64-point forecast and zero matured targets.
3. Seek after minute 350. Each v1 has 64 matched targets and Rejected state.
4. Seek after minute 414. Select v0002; training windows are 96. It is still
   Evaluating until target minute 478 matures.
5. At final settlement each node/family has 128 matched targets across v1/v2;
   both nodes' recorded selected model remains Persistence. The model version
   table has four rows per node (two families, two versions).
6. Switch west/east and then the top device selector. The node selection and
   shared replay time persist through a device switch. Existing trajectory,
   observed load and accounting sections remain usable.

W06.1c now implements live updates; Windows interactive acceptance remains.
Next is W07 repeated scenario/seed/device-count experiments. Predictions remain
separate from future energy/capacity/offloading control work.


## W06.1c: a new run while it is executing

After starting a new run, the top controls offer **Following latest** and
**Pause view**. The same slider remains visible while scrolling to the load
panel. Dragging it enters **Browsing history**; new observations keep arriving
but do not move the selected replay time. **Follow latest** resumes tracking.
Changing device does not reset the shared clock or the selected load node.

The load panel uses the same rolling Actual/Earlier prediction/Future prediction rows as saved replay.
Actuals come from the uniform 60-second node sampler. Complete issued vectors
appear when available, and target errors/lifecycle states mature with actuals.
A training banner names the node, model, version and simulation time. Training
may pause simulation advancement; the last issued curves stay visible.
A refresh does not refit a model or move a saved vector to a new origin.

The first learned vector needs 287 samples and is normally issued at minute
286. No earlier learned prediction exists then. Targets gradually change from pink
to blue as replay advances, preserving their version, target times and gaps.
Before the first fit, the baseline-only view is expected. Use 480 movement
minutes with node-load LSTM and TCN enabled to exercise both initial training
and a possible rejection/retraining cycle. Qualification still depends on
actual quality, so later version times are not guaranteed in every scenario.

Completion drains the journal, checks the saved exports against live records,
and then exposes **Open verified replay**. It keeps the running page at the
chosen historical time until that button is clicked. The verified replay is
loaded normally with its saved-run timeline. **Configure another run** returns
to the form. Cancelled/failed runs retain received history and diagnostics but
are not labelled complete. Old saved runs remain readable; reopening one does
not create a live stream or retrain missing models.

The cards below the prediction panel are labelled **Last sampled load at replay
time**. Their sample timestamps remain visible. They are 60-second snapshots,
not a reconstructed drain sequence or predictions. Unsampled drain time is
not filled with zeros. The completed replay retains the detailed event-snapshot
charts and accounting for final inspection.

Protocol, reproducible checks and acceptance: see `node-load-live.md`.
