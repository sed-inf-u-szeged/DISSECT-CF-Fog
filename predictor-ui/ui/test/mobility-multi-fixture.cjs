'use strict';
// Synthetic data only, for validating file isolation and UI selection.
const fs = require('fs');
const path = require('path');
const {fixture, csv} = require('./mobility-fixture.cjs');
const {csv: parse} = require('../mobility-results');
function writeFixture(root, count = 10) {
  const source = fixture(), runId = 'synthetic-multi-test';
  const props = Object.fromEntries(source['run.properties'].split('\n').map(s => s.split('=')));
  Object.assign(props, {device_count: count, load_scope: 'ALL_DEVICES'});
  const properties = p => Object.entries(p).map(([k, v]) => k + '=' + v).join('\n') + '\n';
  for (const name of ['node_load.csv', 'offload_events.csv']) fs.writeFileSync(path.join(root, name), source[name]);
  fs.writeFileSync(path.join(root, 'run.properties'), properties({...props, issued: +props.issued * count, matched: +props.matched * count}));
  for (let i = 0; i < count; i++) {
    const child = path.join(root, 'devices', String(i));
    fs.mkdirSync(child, {recursive: true});
    for (const [name, text] of Object.entries(source)) {
      if (['node_load.csv', 'offload_events.csv', 'run.properties'].includes(name)) continue;
      let content;
      if (name.endsWith('.csv')) {
        const parsed = parse(text, name);
        for (const row of parsed.rows) {
          if ('device_id' in row) row.device_id = i;
          if ('run_id' in row) row.run_id = runId;
          if ('predictor_name' in row) row.predictor_name = row.predictor_name.replace(':0:position', ':' + i + ':position');
        }
        content = csv(parsed.rows, parsed.head);
      } else if (name.endsWith('.jsonl')) {
        content = text.trim().split('\n').map(line => JSON.stringify({...JSON.parse(line), device_id: String(i), run_id: runId})).join('\n') + '\n';
      } else {
        const data = JSON.parse(text);
        data.device_id = String(i); data.run_id = runId;
        content = JSON.stringify(data).replaceAll(':0:position', ':' + i + ':position');
      }
      fs.writeFileSync(path.join(child, name), content);
    }
    fs.writeFileSync(path.join(child, 'run.properties'), properties({...props, device_id: i, device_generated_bytes: 0, device_uploaded_bytes: 0}));
  }
  const manifest = {schema_version:1, status:'COMPLETE', run_id:runId, device_count:count,
    expected_observations_per_device:174, connection_control:false, issued:+props.issued * count, matched:+props.matched * count,
    generated_bytes:0, upload_completed_bytes:0,
    devices:Array.from({length:count}, (_, i) => ({device_id:String(i), path:'devices/' + i}))};
  fs.writeFileSync(path.join(root, 'multi-device.json'), JSON.stringify(manifest));
  return manifest;
}
module.exports = {writeFixture};
