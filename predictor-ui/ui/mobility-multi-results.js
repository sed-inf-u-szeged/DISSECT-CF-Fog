'use strict';
const fs = require('fs');
const path = require('path');
function check(ok, message) { if (!ok) throw new Error('Multi-device results: ' + message); }

function readMultiMobilityRun(root, build, required, learning) {
  let bytes = 0;
  const canonical = fs.realpathSync(root);
  function read(relative) {
    const file = path.join(root, relative);
    check(fs.existsSync(file) && fs.statSync(file).isFile(), 'missing ' + relative);
    const resolved = fs.realpathSync(file);
    check(resolved.startsWith(canonical + path.sep), 'file leaves the selected run');
    bytes += fs.statSync(file).size;
    check(bytes <= 256 * 1024 * 1024, 'display bundle exceeds 256 MiB');
    return fs.readFileSync(file, 'utf8');
  }
  const manifest = JSON.parse(read('multi-device.json'));
  check(manifest.schema_version === 1 && manifest.status === 'COMPLETE'
    && typeof manifest.run_id === 'string' && manifest.run_id && manifest.connection_control === false
    && Number.isInteger(manifest.device_count) && manifest.device_count >= 1 && manifest.device_count <= 100
    && Number.isInteger(manifest.expected_observations_per_device) && manifest.expected_observations_per_device >= 105
    && Array.isArray(manifest.devices) && manifest.devices.length === manifest.device_count, 'incomplete manifest');
  const propertyRows = read('run.properties').replace(/^\uFEFF/, '').split(/\r?\n/).filter(s => s.includes('='));
  const properties = Object.fromEntries(propertyRows.map(s => [s.slice(0, s.indexOf('=')), s.slice(s.indexOf('=') + 1)]));
  check(Number(properties.device_count) === manifest.device_count && properties.load_scope === 'ALL_DEVICES', 'global device count or load scope differs');
  const shared = Object.fromEntries(['node_load.csv', 'offload_events.csv'].map(name => [name, read(name)]));
  const devices = [];
  let generated = 0, uploaded = 0, issued = 0, matched = 0;
  let first;
  for (const [i, entry] of manifest.devices.entries()) {
    const id = String(i);
    check(entry.device_id === id && entry.path === 'devices/' + id, 'duplicate, unordered or unsafe device entry');
    const texts = {...shared};
    for (const name of new Set([...required, ...learning.files])) {
      if (!(name in texts)) texts[name] = read(entry.path + '/' + name);
    }
    const comparison = require('./mobility-comparison-results');
    if (comparison.files.some(name => fs.existsSync(path.join(root,entry.path,name)))) {
      for (const name of comparison.files) texts[name] = read(entry.path + '/' + name);
    }
    const data = build(texts, path.basename(root) + ' / Device ' + id, {sharedFrames: true});
    check(data.observations.every(r => r.device_id === i) && data.learning?.device_id === i
      && data.learning.run_id === manifest.run_id && data.learning.samples.length === manifest.expected_observations_per_device,
    'device identity or sample count differs: ' + id);
    for (const key of Object.keys(properties).filter(k => !['issued', 'matched'].includes(k))) {
      check(data.props[key] === properties[key], 'shared run metadata differs: ' + key);
    }
    check(data.props.device_id === id, 'device metadata differs');
    if (!first) first = data;
    else {
      check(JSON.stringify(data.comparison?.methods) === JSON.stringify(first.comparison?.methods), 'comparison model selection differs across devices');
      // Shared load data is validated above, then retained only once in the UI payload.
      data.frames = first.frames; data.transfers = first.transfers; data.nodes = first.nodes;
    }
    generated += Number(data.props.device_generated_bytes);
    uploaded += Number(data.props.device_uploaded_bytes);
    issued += data.predictions.length; matched += data.evaluations.length;
    devices.push({id: i, data});
  }
  for (const [key, value] of Object.entries({generated_bytes:generated, upload_completed_bytes:uploaded, issued, matched})) {
    check(Number.isSafeInteger(value) && value >= 0 && value === manifest[key] && value === Number(properties[key]), 'aggregate count differs: ' + key);
  }
  const allObservationTimes = new Set(devices.flatMap(d => d.data.observations.map(o => o.time_ms)));
  check(first.frames.filter(f => f.sample_kind === 'OBSERVATION').every(f => allObservationTimes.has(f.time_ms)), 'load frame has no device observation');
  for (const frame of first.frames) {
    const counter = frame.nodes[0], total = key => frame.nodes.reduce((sum, n) => sum + n[key], 0);
    check(total('device_upload_completed_bytes_derived') === counter.device_upload_completed_bytes_total
      && total('net_received_counter_bytes') + counter.offload_in_flight_bytes_total === counter.device_upload_completed_bytes_total
      && counter.device_generated_bytes >= counter.device_upload_completed_bytes_total,
    'global byte conservation failed at ' + frame.time_ms);
  }
  return {...first, label: path.basename(root), devices, multi: manifest};
}
module.exports = {readMultiMobilityRun};
