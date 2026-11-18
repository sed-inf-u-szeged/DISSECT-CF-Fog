# W06.1g: load meaning, node state and fixed-size axes

Date: 2026-11-17. The user accepts the mobility detail UI and asks to settle
load presentation before expanding the workload. They additionally request
smaller, clearer axes on both position errors and per-node observations.

## Research objective and current capability

The goal is an algorithm that uses mobility and load forecasts to decide task
placement/offloading and node resource capacity, evaluated with service quality
and energy. Heterogeneous nodes need capacity-aware allocation; equal raw queue
values are not themselves the objective. Service-quality constraints and the
energy/action models remain later work.

The current UI displays forecasts and recorded predictor choices. It does not
implement predictive resource control, and the load model does not yet consume
predicted device arrivals. Do not label a predictor selection as a task-routing
or capacity decision. W07.1 adds/tests mobility information; W08/W09 validate
resource actions, energy and controllers.

## Units and state

B means byte; bytes is the plural, and one byte is eight bits. Keep a unit in
scientific displays. The primary load labels now say **Queued data (bytes)**.
These bytes have arrived but are not assigned to compute. Running tasks are
already executing and are counted in tasks; this is not a second byte series.
A zero queue can coexist with running tasks, so neither counter should be
removed from the node state overview. CPU utilisation, work rate and energy
must not be fabricated from these counters or relabelled as percentages.

## Implemented presentation

- Position-error and node-observation axes use HTML labels in CSS pixels outside
  their SVG data coordinates. Tick numbers are 12 px (11 px on small screens)
  and titles 12 px, so widening a chart does not enlarge its text. Labels have
  stronger contrast and fixed spacing; time labels have at most one decimal.
  Position-error ticks prefer readable increments, including 0/200/400/600/800
  for an 800 m scale. Curves remain 1.5 CSS px with non-scaling strokes.
- Error plots have a bounded 220–320 px height; node details use a 170 px plot
  area. The original target times, data paths, errors and metric calculations
  are retained. No smoothing, synthetic points or altered prediction data.
- Node observations begin with a compact all-node table: name, role, queued
  bytes and running tasks. A node button selects that node's load/detail while
  retaining the device and replay time. Missing recorded state remains a dash.
- The default node chart follows the selected node. The existing selector can
  still show every node's chart or one explicit node. Metric and time range
  controls remain; all visible node plots still use the common scale.
- Cumulative uploads/offloads/completed tasks remain in Detailed node accounting,
  now initially collapsed. They remain accessible for diagnosis and later
  controller evaluation without duplicating the primary state overview.
- The load forecast keeps the accepted three model rows and one rolling replay
  clock. Main queue and MAE/RMSE units are spelled out as bytes. Individual
  origins, versions and full accounting remain in their existing detail areas.

## Load-chart reading plan

The main panel should answer: which node, what queue is observed, what each
model forecast at its recorded target times, and how much has been evaluated.
Actual samples remain yellow; issued predictions whose targets have passed are
blue; issued future targets are pink. The vertical replay marker separates
known observations from future targets. The fixed 192-minute history / 64-minute
future window is retained. Overlapping actual/predicted values remain truthful;
hover provides values. No extra origin/time-axis selection is reintroduced.

Scores must show their observed-target count beside the latest-block metric.
Untrained, no saved future forecast and failed qualification are distinct
states. A flat trace is not an empty trace. Any further simplification should
preserve that distinction rather than hide missing forecasts or manufacture
future vectors. No additional forecasting models or controller widgets are part
of this presentation pass.

## Validation and user-machine acceptance

Local validation passed: 77 replay logic cases, the production Angular build,
and real Angular-template checks for two-node/ten-node live fixtures and the
saved ten-city result. The saved-template check additionally selects a node
from the new overview, switches one/all-node charts, and verifies that time
and device remain unchanged. Existing bundle/style budget warnings are nonfatal.
These checks are not a browser visual test.

The updated saved
city result should show one node chart initially; selecting All nodes restores
ten charts. Node-state buttons and selectors preserve time and selected device.
Axis text should remain approximately the same screen size at narrow and wide
window widths. Units and thin lines should be legible in both modes. This update
requires a UI rebuild, not simulation or training. Windows rendering acceptance
remains separate from local compilation/template checks.

Next: review this focused load presentation, then resume W07's demand/sampling
experiments. The quiet city result remains the regression fixture.
