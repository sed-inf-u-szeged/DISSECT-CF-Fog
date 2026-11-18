# Click-to-inspect device/node UI: W06.1f

Date: 2026-11-17. W06.1e was installed on Windows: the user's screenshots
show the shared city map and rolling load panel. The user found the pan/zoom
workflow less useful than the earlier click-based interface and requested
fewer city colors. W06.1f implements that correction; local checks passed,
and Windows layout/interaction acceptance for this revision remains pending.
No prediction-model, simulation or training configuration changed.

## Confirmed city replay

The user opened portable run `mobility-multi-1427994466115174934` on Windows.
Screenshots show the nine-device city replay at 43.22 and 371.37 simulation
minutes, Sopron's three model rows, and all ten node observation/accounting rows.
This confirms core saved city rendering. It does not establish normal live
history browsing while new simulation records arrive.

The apparent missing load curves are overlapping series. For Sopron, the
forecast issued at 350 min predicts the actual target at 370 min as follows:

| Series | Waiting bytes |
| --- | ---: |
| Actual minute-grid sample | 100 |
| Same-origin Persistence | 100 |
| LSTM v0001 | 99.99080657958984 |
| TCN v0001 | 99.98159790039062 |

At replay 371.37, all 21 observed targets of the latest 64-target block equal
100 B. LSTM MAE is 0.00523994 B and TCN MAE is 0.00626083 B, each rounded to
0.01 B in the UI. The 100% within-tolerance rate is not exact equality.
Targets are 351-414 min; the viewport ending at 435.37 does not extend a saved
forecast beyond 414. Future observations remain hidden at the replay time.

`node_load_samples.csv` provides one-minute training/evaluation samples.
`node_load.csv` provides event-level node snapshots. For example, Sopron waiting
bytes change from 100 at 369.20 min to 0 at 369.2167, and back to 100 at
369.3667; the minute-grid samples at 369 and 370 are both 100. These are two
resolutions of the same metric. The current sampling misses these brief dips;
an event-level curve must not be substituted into the minute-grid model score.

## Implemented interaction

The primary interface is a compact node list, an automatically framed detail
plot and a selected-node summary. There is no manual pan/zoom control or wheel
interception. The detail plot has a bounded height rather than growing to fill
most of a large desktop window. The saved and live views use the same component.

| Interaction | Result |
| --- | --- |
| Open a result | Show the selected device's local path and issued position forecast |
| Click a city/node in the left list | Show its configured coverage, observed nearby devices and aggregate load; do not change the selected device or clock |
| Click a device marker or connected-device button | Show that device's local path and select its recorded connected node for load inspection |
| Choose a device in the top selector | Same device/connected-node behavior, without moving the replay time |
| Device path / Node coverage buttons | Switch the detail view directly, without zooming or dragging |
| View load forecasts | Scroll to the selected node's existing forecast panel |
| Change replay time | Keep the current selection and detail mode; update known observations, forecasts and load |

Device detail defaults to the last ten movement minutes. Thirty-minute and
full observed history remain available. A single forecast is one future
position, not a future trajectory. The observed path and forecast stay visibly
separate through color and marker shape; HoldPosition overlap is explained.
The node view intentionally uses coverage scale; click its device button to
inspect local movement at the appropriate scale.

Node identities use neutral gray, names and Fog/Cloud role labels rather than
one color per city. The selected list entry is gold. Node observation curves
and accounting labels use the same neutral color. Actual device paths remain
gold and issued predictions pink; meaningful model/error colors are retained.
All accepted non-scaling thin curves remain in place.

The right summary shows aggregate node workload, its actual snapshot time and
connected-device buttons. Coverage counts are in an expandable detail section.
Offloaded work is still included in node accounting; a node's workload is not
restricted to the currently selected device. A device change selects its known
connection only on user action; clock changes do not silently retarget the node.
The currently viewed connection is shown under the path and can be clicked.
Unknown/disconnected devices do not select an invented node.

The saved replay's model-lifecycle details are open by default. The linked load
panel keeps the accepted 192-minute past / 64-minute future rolling window and
saved forecast gaps. Hover precision and curve-overlap explanations are retained.

## Time and data rules

- Device detail fits only that device's known path and already-issued forecast.
  Node coverage uses the chosen node's static configured extent. Seeking keeps
  the current detail mode; future actuals never determine the view extent.
- Use a common geographic projection/origin for all devices. The existing
  selected-device local projection cannot be reused independently per device.
- For time T, each device marker uses its last observed position at or before
  T, with its sample time shown. Do not use the next future observation to
  interpolate an apparently known position. No sample yet means not observed.
- Prediction overlays use forecasts issued by T and show target times. Current
  mobility forecasts have a 60-second horizon. A single forecast point is not
  a recorded future travel path.
- Node chart selection, topology and accounting share the same run and T.
- Device lookup uses binary search in each time-ordered track; recent paths are
  bounded. A synthetic 100-marker calculation check is not a 100-device
  simulation or browser-performance result.
- Live node snapshots are throttled by the existing producer and can be older
  than the replay clock. The map displays their own timestamp, never relabels
  them as a newer sample, and never substitutes a future saved snapshot.

## Validation and Windows acceptance

W06.1f local checks: 77 logic cases; production build and TypeScript checks;
actual Angular template/change-detection checks for live two/ten-node fixtures
and the completed nine-device city run. The saved city and two legacy two-node
runs pass 16,920 node-chart states with finite detail geometry, stable topology,
correct node selection and no future device positions. The original city
journal's 28,271 records pass 410 device/node snapshot comparisons at their own
timestamps. Generated node-coverage and device-detail SVGs were inspected.
These checks do not substitute for a Windows/Electron layout and click review.

After installing W06.1f, reopen the existing city run at about 371 min:

1. The main plot opens on the selected device's recent local path. There is a
   compact ten-node list and no pan/zoom toolbar. Scroll over the plot to scroll
   the page normally.
2. Click Debrecen: the coverage plot and right summary change; View load
   forecasts opens Debrecen's load panel. The selected device and time stay put.
3. Click its Device 7 button: the local path opens. Choose Device 8 at the top:
   its path and its connected Gyongyos node load become selected at the same time.
4. Drag the replay clock forward and back: no future actuals appear early.
   Selecting an unobserved device shows a clear empty state. Budapest cloud
   has no device-access circle or direct device connection in this run.
5. Check that position errors and node-observation curves remain thin, city
   cards/accounting labels are neutral, and the rolling load chart is unchanged.

## Mobility-informed load is a separate mainline task

The current load worker consumes only each node's past waiting-byte series
(192 inputs -> 64 outputs). The multi-device data already support the map
interaction above, but do not yet feed predicted arrivals into load features.

For the later W07.1 work, derive expected attachment changes from predicted
positions, coverage and the actual connection rule. Combine these with device
job/upload rates, existing backlog, compute assignment and offloading state.
Coverage entry alone does not guarantee attachment, and more devices do not
necessarily increase waiting bytes if capacity can handle their work.

Keep expected incoming work rate (B per minute) separate from waiting stock (B).
Any queue-balance baseline must subtract work assigned to compute/offloaded
under a defined model; it must not count completed tasks as newly removed from
the waiting queue a second time.

Resolve the 60-second mobility versus 64-minute load horizon before fusion:
begin with an explicitly matched short horizon, or develop and validate a longer
mobility forecast. Never use future observed positions from a saved replay as
online model inputs. Compare load-only and mobility-informed variants on the
same targets and workload before predictive control/energy claims.

## Next sequence

1. Install and check the click-based detail revision using the existing city
   result. No retraining is needed. Normal Windows live-history interaction
   remains a separate acceptance item.
2. Add explicit coverage-boundary crossing and changing-demand scenarios.
   Verify they change node attachment and demand at the chosen sample resolution.
   The current 800 m local walks inside 20 km coverage are a quiet regression.
3. Continue W07 repeated-seed quality/cost evaluation, then W07.1 mobility-informed
   load features and matched-horizon comparison. Energy/control remain later.
