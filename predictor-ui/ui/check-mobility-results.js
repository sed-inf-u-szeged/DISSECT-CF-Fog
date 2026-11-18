'use strict';
const { readMobilityRun } = require('./mobility-results');
try {
  if (process.argv.length !== 3) throw new Error('Usage: node check-mobility-results.js <completed-run-directory>');
  const run = readMobilityRun(process.argv[2]);
  console.log('Run: ' + run.label);
  console.log('Strategy: ' + (run.props.mobility_strategy || 'legacy'));
  if (run.devices) {
    console.log('Devices: ' + run.devices.length + '; node load scope: all devices');
    for (const {id, data} of run.devices) console.log('Device ' + id + ': observations=' + data.learning.samples.length
      + ', selected matched=' + data.learning.selectedEvaluations.length + ', versions=' + data.learning.versions.length);
  }
  console.log('Observed positions: ' + run.observations.length);
  if (run.learning) {
    const data = run.learning;
    console.log('Device: ' + data.device_id + '; candidate comparisons: ' + data.evaluations.length / 3);
    console.log('Selected evaluations: ' + data.selectedEvaluations.length);
    for (const v of data.versions) console.log(v.model_version + ': training=' + v.training_windows
      + ', matched=' + v.matched_origins + ', qualification=' + v.quality_decision + ', final=' + v.state + ', selected=' + v.selected_origins);
  } else console.log('Legacy baseline results; no shared lifecycle bundle is present.');
  console.log('MOBILITY_UI_DATA_OK');
} catch (error) {
  console.error('MOBILITY_UI_DATA_FAILED: ' + error.message);
  process.exitCode = 1;
}
