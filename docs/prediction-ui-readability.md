# W06.1d: reading mobility and load forecasts

This UI update uses the same simulation output and model settings. Install it
after W06.1c1, rebuild the UI, and restart `MobilityPredictorUi.java`.
No new dependencies or model training are needed to inspect saved results.

| Section | Question | Meaning |
| --- | --- | --- |
| Mobility | Where will this device be at the target time? | Yellow observed position/path, pink hollow predicted point; issue and target times are shown. |
| Position errors | How far was the earlier prediction from its observed target? | Distance in metres; lower is better, on evaluated targets only. |
| Node load | How many bytes will wait for compute at this node? | Uniform one-minute actual samples and recorded forecasts, covering all devices at the chosen node. |
| Node observations | What was recorded at this replay frame? | Event snapshots/accounting, which can be between the uniform sample times. |

The fixed timeline changes the shared replay time. Section buttons only scroll.
Mobility defaults to ten minutes of known movement; full history is selectable.
During final draining, the last movement segment remains visible. HoldPosition
can overlap the observed marker because it predicts the current position; this
does not mean that its future error is zero.

## W06.1d3: one replay clock and rolling load window

The user accepted W06.1d1/d2 thin position-error and node-observation curves on
2026-11-17. Their 1.5 CSS px non-scaling SVG rule is retained. The following
redesign replaces W06.1d's origin-anchored load view, whose viewport stayed near
286 and then jumped when a new vector appeared at 414.

- The fixed top replay slider controls all main load rows. The viewport shows
  [T−192, T+64] simulation minutes; at the beginning it is [0, 256] until T=192.
- Main Forecast issued at, Replay at origin, Chart view and Time axis controls
  are removed. Node/model selection and Expand remain. Visibility/common-scale
  choices and reading guidance are collapsed. The existing individual-vector
  inspector and first-training jump are in Diagnostics.
- Yellow means observed actual, blue means recorded past-target prediction,
  pink means already-issued future-target prediction. Passing a target changes
  its color, not its value or timestamp. The Now line divides past and future.
- New forecasts add blocks without moving the viewport anchor. Missing blocks,
  incomplete/ambiguous vectors and unsampled actual intervals remain gaps.
- Each right-side card states which latest-issued model/version/block it scores,
  with its issue time, target range and observed count. It does not score the
  arbitrary portion of history visible through the viewport.
- Paired Persistence references retain the same 64 targets as learned blocks;
  rolling one-minute baseline history is shown before the first fit and remains
  separately available in Diagnostics afterwards. These horizons are labelled.
- Expanded charts, live updates and backward replay use the same rules. No
  future actuals, later lifecycle events or unissued forecasts are exposed.
- Load Chart.js curves now use 1.5 px. Movement paths and the accepted thin
  position-error/node-observation styles are retained.

This changes presentation only: model architecture, samples, thresholds,
training, forecast cadence and resource/offloading decisions are unchanged.

## Check existing data first

1. Close the UI, install W06.1d3, rebuild, and restart MobilityPredictorUi.java.
   Use **Open mobility results** for the existing trained two-node run
   `mobility-multi-4944157618143554283`; no new training is needed.
2. Click **Node load** and choose mobility-west. Keep Diagnostics closed. Move
   only the top slider. The following boundaries should hold:

| Replay minute | Window | LSTM v1/v2 display and latest-block score |
| --- | --- | --- |
| 284.22 | 92.22–348.22 | Collecting history; no trained curve |
| 286.22 | 94.22–350.22 | First block: targets 287–350, 0/64 observed |
| 300.22 | 108.22–364.22 | 14 blue targets, 50 pink targets; nothing after 350 |
| 320.22 | 128.22–384.22 | 34 blue targets, 30 pink targets |
| 350.22 | 158.22–414.22 | v1 has 64/64 observed; future gap remains blank |
| 414.22 | 222.22–478.22 | v2 appears at 415–478, 0/64 observed; v1 stays visible |

3. Seek backwards to 300 then 284: later actuals, rejection and v2 disappear.
   The window always stays 256 minutes wide; training does not cause a page jump.
4. Switch west/east, hide/show models, Expand then Close/Escape. The time and
   saved predictions must not change. Score details show the same-target baseline.
5. Open the short city run as a compatibility check: it has too few samples for
   trained forecasts. Final settlement should explain this and leave missing data
   blank. Look at Node observations and Position errors to confirm thin strokes.

Uniform load samples and event snapshots below can differ because their times
can differ (e.g. 320.00 versus 320.22). Node selection is independent of Mobility's
device selector; waiting bytes cover the selected node's combined workload.

## Validation and remaining work

The new rolling-view checks pass locally: 70 component/logic cases, TypeScript,
production build, actual Angular-template observer/interaction tests for two-
and ten-node streams, 58,656 saved timeline snapshots in forward/backward order,
4,870 live-versus-saved chart comparisons, and 740 actual Chart.js/Node-canvas
draws. No new simulation or training was run for this update. Build size-budget
warnings remain. The template renderer and Node canvas do not test Windows
layout; installation and visual acceptance of W06.1d3 on Windows are pending.

After this UI acceptance, finish the normal Windows 480-minute live-run check
for W06.1c: browse history while records arrive, resume Follow latest, then open
verified replay. W07 then compares scenarios/seeds/device scales and model
quality/cost; W07.1 tests the added value of mobility information for node load.
Predictive capacity/offload control remains a later stage.
