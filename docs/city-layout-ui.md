# W06.1a2: city and node-load readability

Date: 2026-11-16. UI changes and automated checks complete; Windows visual acceptance pending.

## Controls

- A compact national overview keeps all ten nodes visible. Numbers map to city
  buttons; the yellow ring marks the selected device's current connection.
  Circle geometry uses actual configured radii. Marker size is for readability.
- City runs default to **Device detail**: observed history and an already-issued
  prediction determine the zoom. Unknown future positions never set the extent.
  The full access boundary may be outside this zoom.
- A city button opens **Node coverage**, centred on that node at its actual
  radius. Cloud is labelled separately and has no direct-access coverage circle.
  This changes the map, not the selected device, load-model choice or routing.
- **All nodes** remains available for a full geographic plot. Coordinates show
  metres for local movement or kilometres for large extents. All plots preserve
  equal spatial scales. The named coordinate origin is explicit.
- **Observed load by node** replaces the overlay of ten event-load lines. Each
  city has a chart and current waiting bytes/running tasks. Select the plotted
  metric, all nodes or one node, and last 30/60 minutes or the whole run.
  Nodes share time and vertical scales. Scales use only observed values in the
  selected window, including the known step at its left edge.
- A final-settlement point is separate from the last observed curve. The
  unobserved drainage interval stays blank rather than being interpolated.
- Accounting lists one node per row. Position-error legends are buttons that
  hide/show curves without changing evaluation rows, scores or serving models.
- The node-load prediction panel still displays one selected model with its
  paired baseline. Simultaneous learned-model forecasts remain W06.1b.

## Evidence and boundary

The user's screenshots show a successful nine-device/ten-node short city run
(`mobility-multi-10956657032195469642`). The load method shown is Persistence.
A 120-minute movement run cannot provide the 287 raw load samples required for
first training. In the shown position comparison, LSTM mean error 238.31 m is
slightly higher than HoldPosition 235.66 m. Integration, readable presentation
and useful model accuracy are separate acceptance questions.

The regular per-city light workload does not demonstrate congestion, intercity
handoffs or an energy-saving control rule. Forecast-to-demand fusion, energy
measurements, capacity/sleep actions and controller experiments remain planned.

## Verification

33 component/display logic tests, 32 Node tests, TypeScript checking and the
production UI build pass (size-budget warnings). Three existing city/legacy
runs pass 18,288 chart-state checks. Simulator/model code and saved outputs
are untouched; no new simulation or training is required. Browser launch is
blocked by the environment's socket restriction; Windows appearance must
still be confirmed. Installer preflight, backups and rollback are verified.

## Windows acceptance

1. Close the UI, install the guarded W06.1a2 package over W06.1a, then launch
   `MobilityPredictorUi.java` as before. Manually launched Electron requires
   `npm run build` in `predictor-ui/ui` first.
2. Open the existing completed city run. Confirm the local trajectory is
   readable and the compact national overview contains ten node choices.
3. Choose Szeged in the overview: Node coverage should show its configured
   radius. Switch back to Device detail, then select another device.
4. In Observed load by node, inspect waiting bytes and running tasks; try one
   node, all nodes, 30-minute and full-run ranges. No ten-node line overlay.
5. Seek backwards and to final settlement. Check timestamps, node values and
   the blank unsampled tail. Reopen the previous two-node result as a regression.
6. Send a map screenshot and a node-chart screenshot to complete visual acceptance.
