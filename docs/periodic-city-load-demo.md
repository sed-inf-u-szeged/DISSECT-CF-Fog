# Bright device markers and periodic city-load demonstration

Date: 2026-11-17. Scope: W06.1h presentation and W07a workload demonstration.
W07.1 continues to mean the future mobility-to-demand feature experiment.

## User-facing changes

All clickable observed-device dots in node coverage use actual-path yellow
`#ffd174`, a subtle halo, a 5-unit visible radius and a 14-unit transparent hit
radius in the existing SVG viewBox. Unselected devices remain visible. Nodes
and coverage stay neutral gray; keyboard activation and the connected-device
list remain available. Labels remain unchanged; dense-label collision handling
is deferred until a multi-device-per-city example is evaluated.

The new-run form adds one Device workload selector. Constant is the default,
preserving the earlier 100-byte-per-minute behavior. Periodic bursts selects
an explicit synthetic input profile. Existing saved data is never rewritten.

## Workload definition and causality

`device_workload=periodic-burst`, version 1. For zero-based device d and
measurement index k, phase=(k+3d) mod 32 and payload is
100 + 200 * max(0, 9 - abs(phase-16)) bytes. The cycle has a quiet interval,
a rise, a 1900-byte peak and a fall. It advances on actual one-minute device
measurements, including the existing start delay. This schedule is an experiment
input, not a model output and not measured city traffic.

ObservedDevice sets the actual SmartDevice fileSize before the official Sensor
measurement. Sensor creates the data one millisecond later through its existing
code. `device_demand_schedule.csv` records measurement start time and payload;
it does not claim the bytes have already uploaded at that instant. The original
upload, task assignment, execution and reactive policy produce the queue.

No forecast model, threshold, training window, target horizon, minute-grid
sampling rule or recorded prediction is changed to obtain a better score.
The schedule's future values are not passed to the predictor. Models still use
only past queue samples. Actual queue peaks can exceed one generated payload
because data can wait across measurements.

## Runs and evidence

- Short pilot: 104 movement minutes, 9 devices, load learned models disabled;
  run `mobility-multi-531038209449587413`. All nine fog nodes vary; demand total
  matches generated/uploaded/assigned bytes (567900 bytes).
- Full run: `mobility-multi-7085794150201453824`, 480 movement minutes, 9 devices, 9 fog + 1 cloud,
  Random walk 2–6 m/s within 800 m, 20 km coverage, seed 42.
- Load models: LSTM and TCN, 40 epochs, candidate 80% within tolerance; extra
  mobility comparisons disabled, normal mobility LSTM lifecycle retained.
- Demand rows: 4311; all payloads and measurement intervals verified.
- Generated = uploaded = finally assigned: 2612700 bytes.
- Model versions: 38; first qualification counts:
  {'PASSED': 2, 'REJECTED': 36}.
- Live/export chart-state comparisons: 4950; passed.
- Normal runner, sampling, baseline, learned-model/lifecycle audits and saved
  model prediction rechecks passed. Wall time including audit: 744.4 s.

Stable-window actual samples (simulation minutes 60–480):

| Node | Queued bytes range | Distinct values | Local rising peaks |
| --- | ---: | ---: | ---: |
| Budapest_cloud | 0–0 | 1 | 0 |
| Sopron_fog2 | 100–3200 | 12 | 39 |
| Szombathely_fog2 | 100–3200 | 12 | 39 |
| Gyor_fog1 | 100–1900 | 10 | 13 |
| Szeged_fog2 | 100–3200 | 12 | 39 |
| Pecs_fog2 | 100–3200 | 12 | 39 |
| Kecskemet_fog1 | 100–1900 | 10 | 13 |
| Miskolc_fog2 | 100–3200 | 12 | 39 |
| Debrecen_fog2 | 100–3200 | 12 | 39 |
| Gyongyos_fog1 | 100–1900 | 10 | 14 |

These are empirical queue measurements from this run, not prescribed chart
values. Budapest stays zero when no work is offloaded to the cloud. Each
Persistence 64-target vector is constant by definition; learned forecasts can
vary but are not guaranteed to match actual peaks. Rejected candidates keep
their saved forecasts visible at their original timestamps. Missing future
vectors remain missing rather than extrapolated by the UI.

## Verification and limits

Java compilation and 12 focused Java tests passed. Seven Python configuration
tests, 77 replay logic tests, production Angular build and real Angular template
checks passed. The latter use an in-memory renderer with a Chart.js test double,
not a Windows/Electron visual screenshot. Existing nonfatal bundle/style budget
warnings remain. Installer checks cover LF/CRLF, backups, repeat installation,
unknown edits, corrupt payloads and rollback. Portable result-reader checks are
included in the delivery's evidence folder.

Open the result at about 310 min and choose Sopron or Szeged to see actual and
issued predictions. No re-training is needed to inspect the delivered result.
For a new run select Periodic bursts; opening an old constant run does not
change its values. Windows appearance/click behavior still needs user review.

## Next mainline work

This completes one controlled varying-demand demonstration, not the entire W07
matrix or a validated controller. Next compare repeated seeds, heterogeneous
and irregular demand, multiple devices per city and supported offload cases.
Then evaluate mobility-informed features (W07.1), resource/energy actions (W08)
and predictive control (W09). The research goal remains a rule that adjusts
node load/resource decisions with measured service-quality and energy effects.
