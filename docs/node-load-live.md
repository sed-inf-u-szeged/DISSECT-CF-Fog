# W06.1c: incremental node-load charts

The running Mobility UI now displays uniform node observations, issued load
forecasts and model lifecycle changes without waiting for the full run to end.
The model rows reuse the saved-replay comparison and chart builders. Sampling,
training windows, qualification thresholds and application decisions are unchanged.

## Runtime contract

- A schema-2 `live.jsonl` starts with the existing Mobility META, followed by a
  NODE_LOAD META defining run ID, node IDs, 60-second interval, models and policy.
- SAMPLE uses the uniform sampler's timestamp/index and waiting bytes. It also
  records running/completed tasks for explicitly timestamped observation cards.
  Device callbacks do not create extra node samples.
- VECTOR contains a whole issued vector: one target for rolling Persistence,
  64 for a learned model. It identifies node, family, version and origin. Learned
  records include the same-origin Persistence value and vector inference time.
  Validate all 64 values before writing any learned target row or live vector.
- EVALUATION references an already-issued target and an observed target actual.
  SELECTION identifies the recorded next-target service choice. Viewing a
  candidate never makes it the selected service model.
- EVENT carries lifecycle and version training metadata. TRAINING flushes before
  the blocking worker request; TRAINED is followed by its issued vector. Training
  and model vectors flush promptly; ordinary samples use the existing batching.
- Every envelope has the same run identity, simulation time and ordered journal
  sequence. Partial JSON lines wait for more bytes. Malformed, truncated or
  mixed-run records cannot be labelled complete.

`NodeLoadListener` is an optional Java callback; existing direct callers use a
no-op listener. `run_mobility_batch.py` includes its source in the isolated
runtime compilation, so a prior dependency JAR need not contain this new class.
Existing CSV/JSON export schemas remain unchanged. Schema-1 previews remain
readable for compatibility and do not claim live/final load validation.

## UI behavior

**Following latest** advances the shared display clock as data arrives.
**Pause view** or dragging the fixed top slider enters **Browsing history**.
It pauses display following only; simulation, training and recording continue.
**Follow latest** returns to the received simulation time. Device changes,
node-load chart updates and run completion retain the chosen historical time.

The selected node, hidden model rows, curve filters and expanded model survive
incoming batches. W06.1d3 uses replay time as its only main-view clock and
slides a fixed 192-minute history / 64-minute future viewport. No origin picker
is needed. Actuals, scores and lifecycle labels are evaluated as of replay time.
New records invalidate caches even when simulation time has not advanced.

The map starts in **Device detail**, retaining **Node coverage** as an option.
W06.1d shows the last ten minutes of observed movement by default, with longer
history options and explicit forecast issue/target times. Header section
buttons reach Mobility, load prediction and node samples without moving the
shared replay clock. Untrained load families display sample-progress cards;
issued charts share the scrolling absolute-time window. Earlier predictions
are blue and future predictions pink, divided by the shared Now line. The
primary metric card identifies the latest issued block for that model and
scores only its observed targets. Model values and training policy are unchanged. See `prediction-ui-readability.md` for saved-run checks.
Load observations include all devices and have their own node selector. The
cards below the prediction panel show the last uniform sample and its timestamp;
they do not turn the unsampled task-drain interval into zero-load samples.

Before the first load fit, one-minute Persistence is expected. The learned
configuration needs 287 samples, normally reaching its first fit at minute 286.
A first vector has no earlier learned prediction. If v1 fails and retries at minute 414, its
previous forecast block remains at targets 287–350, separate from v2's 415–478.
Successful qualification can instead issue successive vectors at their actual
origins. The view never moves an older forecast to the current clock.

At completion, Electron drains all remaining journal chunks and compares nodes,
models/policy, samples, every vector target/value/time, evaluations, selections,
events and version metadata against the validated saved output. Only then is
**Open verified replay** offered. That explicit action opens the normal saved
replay; the running page does not automatically jump out of a historical view.
Cancelled/failed runs retain partial history and diagnostics without COMPLETE.

## Automated checks

From `predictor-ui/ui`:

```powershell
node --test test/node-load-live.test.cjs test/node-load-results.test.cjs test/mobility-results.test.cjs test/mobility-multi-results.test.cjs test/mobility-ipc.test.cjs test/mobility-comparison-results.test.cjs
node test/run-replay-logic-tests.cjs
node test/check-live-render-stability.cjs
npx tsc --noEmit -p tsconfig.spec.json
npm run build
node test/check-live-node-load.cjs '<session directory>' '<completed run directory>'
```

`session directory` is shown in the running UI's Run files and diagnostics,
under `simulator/sim_res/mobility-ui/<id>`. Use its Results path for the second
argument. The checker does not retrain or edit files. It checks live/final
record equality and compares the actual model-chart projection at every
sample boundary, plus historical rewinds after all later data has arrived.

The Node tests cover incomplete vectors, unseen actuals, missing sample grid
entries, mixed runs, overlapping reconnect snapshots, partial JSON lines,
truncated journals, cancellation, final EOF and draining more than 256 KiB.
Component tests cover fixed viewport bounds, preserved gaps, target maturation,
paired-baseline scores and time/node/filter preservation. Java tests cover
TRAINING before the worker call and atomic vector validation in addition to
existing sampling, qualification, retry and Persistence behavior.

Integration results are recorded in `joint-prediction-progress.md`. The real
simulation checks use loadEpochs=1 to exercise the data flow efficiently, not to
establish model quality. The normal UI still requests 40 epochs. They do not
replace W07's repeated seeds, changing demand, 10/100-device scaling or latency
measurements. No full browser/native Windows visual check is claimed here.

## W06.1c1: live renderer feedback-loop fix

Windows diagnostics showed the producer finishing successfully and saved live
records being sent, while the renderer stayed at zero and did not answer the
diagnostic heartbeat. Pausing JavaScript showed Angular NgFor creating embedded
views. The status getter allocated new row objects on every Angular check, and
the status list had no node identity tracking. Chart.js registers a document-wide
subtree MutationObserver for canvas attachment/detachment; when registered in
Angular's zone, even unrelated card changes caused another Angular check.
That combination could recreate the cards indefinitely and starve timers/IPC.

The fix materializes statuses when the displayed time/data changes, tracks cards
by node ID, and creates/updates/destroys model charts outside Angular's zone.
Input bindings, replay controls and Expand still run normally through Angular.
The existing chart data and causal time rules are unchanged.

`check-live-render-stability.cjs` compiles the real Angular templates with an
in-memory renderer and a chart double that models the Zone-bound subtree
observer. It exercises two-node Persistence and ten-node LSTM/TCN streams,
card identity, continued observations, history retention, future-actual hiding,
expanded charts, completion and cleanup. An additional observer registered in
Angular checks list stability independently of chart-zone isolation. It runs
in a timeout-bounded child process. The pre-fix sources hit the runaway-check
guard; the fixed sources settle. This is not a browser layout/performance test.

Rebuild the UI after installing W06.1c1. The previously supplied
`diagnose_mobility_ui.py` can then replay an existing completed schema-2 session
to verify that received time/observations advance and heartbeats return; this
does not train or simulate again. Exit a paused DevTools session first. The
user's Windows saved-event replay and subsequent full saved replay now pass;
normal trained live-run and incoming-data history-browsing acceptance remain.

## Windows acceptance after installing W06.1c

1. Restart through `MobilityPredictorUi.java`. Open an old run once to confirm
   compatibility, then choose **New mobility run** for live acceptance.
2. For a small trained check, choose Two-node mobility demo, Random walk,
   one device, 480 movement minutes, node-load LSTM and TCN, Candidate profile.
   Additional mobility shadow models may be unchecked for this wiring check.
   Retain the current speed/radius and drain settings.
3. Scroll to the load model rows while RUNNING. Check the fixed live slider,
   advancing sample time and Persistence observations. Around minute 286,
   training messages and then the first learned vectors should become available.
4. Choose another node and drag the slider backwards. It should show Browsing
   history and stay at that time while the received time advances. No later
   actuals or version states should appear. Click Follow latest to resume.
5. Hide a model or expand a chart. Incoming data should retain those choices.
6. At completion, the page should stay at the chosen display time. Click
   Open verified replay to inspect the complete saved run; it is not a new fit.
7. Repeat with Hungary cities, distributed starting cities, nine devices,
   20 km coverage and 480 movement minutes. Check all nine fog nodes and
   Budapest cloud. An idle cloud may legitimately remain near zero.

Live results are produced only by a new run with this updated producer. Old
120-minute results cannot gain trained vectors by reopening them. Prediction
accuracy and workload realism are separate W07 experiments. Predictive capacity,
energy and offload control remain W08–W09 after W07.1's mobility-to-demand study.
