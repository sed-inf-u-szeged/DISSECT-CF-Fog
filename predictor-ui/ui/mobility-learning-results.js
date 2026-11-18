'use strict';

// Display validation only. Java remains the authority for model selection.
const files = [
  'mobility_samples.csv', 'retry_predictions.csv', 'retry_evaluations.csv',
  'shared_selected_predictions.csv', 'shared_selected_evaluations.csv',
  'shared_lifecycle_events.jsonl', 'shared-lifecycle.json'
];
const markers = files.filter(name => name !== 'mobility_samples.csv');
const methods = ['HoldPosition', 'ConstantVelocity', 'LSTM'];
const step = 60000;
function requireValue(ok, message) { if (!ok) throw new Error('Mobility prediction results: ' + message); }
function same(a, b) { return Math.abs(a - b) <= 1e-4 + 1e-8 * Math.max(Math.abs(a), Math.abs(b)); }
function gps(lat, lon) { return Number.isFinite(lat) && Number.isFinite(lon) && Math.abs(lat) <= 90 && Math.abs(lon) <= 180; }
function distance(a, b) {
  const rad = Math.PI / 180, p = a[0] * rad, q = b[0] * rad;
  const h = Math.min(1, Math.max(0, Math.sin((q - p) / 2) ** 2 + Math.cos(p) * Math.cos(q) * Math.sin((b[1] - a[1]) * rad / 2) ** 2));
  return 12756274 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
}
function json(text, name) {
  try { return JSON.parse(text.replace(/^\uFEFF/, '')); }
  catch { throw new Error('Invalid JSON: ' + name); }
}
function unique(rows, key, name) {
  const result = new Map();
  for (const row of rows) {
    const id = key(row);
    requireValue(!result.has(id), 'duplicate ' + name + ': ' + id);
    result.set(id, row);
  }
  return result;
}
function metrics(rows) {
  const count = rows.length;
  return { count, mean_error_m: count ? rows.reduce((s, r) => s + r.position_error_m, 0) / count : 0,
    rmse_m: count ? Math.sqrt(rows.reduce((s, r) => s + r.position_error_m ** 2, 0) / count) : 0 };
}

function buildLearning(texts, run, table) {
  if (!markers.some(name => texts[name] !== undefined)) return undefined;
  for (const name of files) requireValue(typeof texts[name] === 'string', 'missing ' + name);
  const summary = json(texts['shared-lifecycle.json'], 'shared-lifecycle.json');
  requireValue(summary && summary.schema_version === 1 && summary.status === 'COMPLETE'
    && summary.controller_integration === 'SHARED_COORDINATOR_AND_MANAGER' && summary.connection_control === false,
  'expected a completed shared lifecycle run');
  const device = Number(summary.device_id), identity = summary.run_id;
  requireValue(typeof identity === 'string' && identity.length > 0 && Number.isSafeInteger(device)
    && device >= 0 && String(device) === String(summary.device_id), 'invalid run or device identity');
  requireValue(run.observations.every(row => row.device_id === device), 'device differs from trajectory');

  const samples = table(texts[files[0]], files[0], ['device_id', 'time_ms', 'latitude_deg', 'longitude_deg'], ['run_id', 'current_node']);
  const actual = unique(samples, r => r.time_ms, 'sample time');
  const observed = new Map(run.observations.map(r => [r.time_ms, r]));
  samples.sort((a, b) => a.time_ms - b.time_ms);
  samples.forEach((r, i) => {
    const o = observed.get(r.time_ms);
    requireValue(r.run_id === identity && r.device_id === device && Number.isSafeInteger(r.time_ms) && r.time_ms >= 0
      && gps(r.latitude_deg, r.longitude_deg) && (!i || r.time_ms - samples[i - 1].time_ms === step)
      && o && distance([r.latitude_deg, r.longitude_deg], [o.latitude, o.longitude]) <= 1e-4
      && r.current_node === o.current_node, 'sample identity, time or trajectory differs');
  });
  requireValue(samples.length === summary.observations && samples.length === summary.expected_observations
    && samples.length >= 105 && summary.pending_forecasts === 0, 'incomplete sample stream');

  const pointNumbers = ['origin_ms', 'target_ms', 'predicted_latitude_deg', 'predicted_longitude_deg'];
  const predictions = table(texts[files[1]], files[1], ['device_id', ...pointNumbers], ['model_version', 'model', 'origin_node']);
  const evaluations = table(texts[files[2]], files[2], ['device_id', ...pointNumbers, 'evaluated_at_sim_ms',
    'actual_latitude_deg', 'actual_longitude_deg', 'position_error_m'], ['run_id', 'model_version', 'model', 'origin_node']);
  const selectedPredictions = table(texts[files[3]], files[3], pointNumbers, ['model', 'predictor_name']);
  const selectedEvaluations = table(texts[files[4]], files[4], ['origin_ms', 'target_ms',
    'actual_latitude_deg', 'actual_longitude_deg', 'position_error_m'], ['model']);
  for (const rows of [selectedPredictions, selectedEvaluations]) {
    for (const row of rows) requireValue(typeof row.model_version === 'string', 'missing selected model version column');
  }
  requireValue(Array.isArray(summary.versions) && summary.versions.length > 0, 'missing model versions');
  const versions = unique(summary.versions, r => r.model_version, 'model version');
  for (const v of versions.values()) {
    requireValue(typeof v.model_version === 'string' && /^v\d{4,}$/.test(v.model_version)
      && Number.isSafeInteger(v.training_cutoff_sim_ms) && actual.has(v.training_cutoff_sim_ms)
      && Number.isSafeInteger(v.training_windows) && v.training_windows >= 32
      && v.training_windows <= samples.findIndex(s => s.time_ms === v.training_cutoff_sim_ms) - 8
      && typeof v.predictor_name === 'string' && typeof v.state === 'string', 'invalid version metadata');
  }
  const key = r => r.model_version + '|' + r.model + '|' + r.origin_ms;
  const forecastIndex = unique(predictions, key, 'candidate forecast');
  const evaluationIndex = unique(evaluations, key, 'candidate evaluation');
  const groups = new Map();
  for (const p of predictions) {
    const origin = actual.get(p.origin_ms), target = actual.get(p.target_ms), v = versions.get(p.model_version);
    requireValue(p.device_id === device && methods.includes(p.model) && origin && target && v
      && p.target_ms === p.origin_ms + step && p.origin_ms >= v.training_cutoff_sim_ms
      && p.origin_node === origin.current_node && gps(p.predicted_latitude_deg, p.predicted_longitude_deg),
    'invalid candidate identity, coordinates or target');
    if (!groups.has(p.origin_ms)) groups.set(p.origin_ms, []);
    groups.get(p.origin_ms).push(p);
  }
  for (const rows of groups.values()) requireValue(rows.length === 3 && new Set(rows.map(r => r.model)).size === 3
    && new Set(rows.map(r => r.model_version)).size === 1, 'methods do not share one version and target');
  requireValue(groups.size === samples.length - 41 && forecastIndex.size === evaluationIndex.size,
    'incomplete candidate prediction/evaluation stream');
  const checkError = (e, p) => {
    const a = actual.get(e.target_ms);
    requireValue(p && a && e.target_ms === p.target_ms && e.model === p.model && e.model_version === p.model_version
      && gps(e.actual_latitude_deg, e.actual_longitude_deg)
      && distance([e.actual_latitude_deg, e.actual_longitude_deg], [a.latitude_deg, a.longitude_deg]) <= 1e-4
      && e.position_error_m >= 0 && same(e.position_error_m,
        distance([p.predicted_latitude_deg, p.predicted_longitude_deg], [a.latitude_deg, a.longitude_deg])),
    'evaluation does not match its forecast and actual target');
  };
  for (const e of evaluations) {
    const p = forecastIndex.get(key(e));
    requireValue(e.run_id === identity && e.device_id === device && e.evaluated_at_sim_ms === e.target_ms
      && p && e.origin_node === p.origin_node
      && distance([e.predicted_latitude_deg, e.predicted_longitude_deg], [p.predicted_latitude_deg, p.predicted_longitude_deg]) <= 1e-4,
    'candidate evaluation identity or issue time differs');
    checkError(e, p);
  }
  predictions.sort((a, b) => a.origin_ms - b.origin_ms || methods.indexOf(a.model) - methods.indexOf(b.model));
  evaluations.sort((a, b) => a.target_ms - b.target_ms || methods.indexOf(a.model) - methods.indexOf(b.model));
  const selectedIndex = unique(selectedPredictions, r => r.origin_ms, 'selected forecast');
  unique(selectedEvaluations, r => r.origin_ms, 'selected evaluation');
  for (const p of selectedPredictions) {
    const o = actual.get(p.origin_ms);
    requireValue(o && actual.has(p.target_ms) && p.target_ms === p.origin_ms + step
      && ['HoldPosition', 'LSTM'].includes(p.model) && gps(p.predicted_latitude_deg, p.predicted_longitude_deg),
    'invalid selected prediction');
    p.device_id = device;
    p.origin_node = o.current_node;
    if (p.model === 'LSTM') {
      const c = forecastIndex.get(key(p));
      requireValue(c && p.predictor_name === versions.get(p.model_version).predictor_name
        && distance([p.predicted_latitude_deg, p.predicted_longitude_deg], [c.predicted_latitude_deg, c.predicted_longitude_deg]) <= 1e-4,
      'selected LSTM differs from the issued candidate');
    } else requireValue(p.model_version === '' && p.predictor_name === summary.feature_name + ':HoldPosition'
      && distance([p.predicted_latitude_deg, p.predicted_longitude_deg], [o.latitude_deg, o.longitude_deg]) <= 1e-4,
    'selected baseline is not HoldPosition');
  }
  for (const e of selectedEvaluations) checkError(e, selectedIndex.get(e.origin_ms));
  selectedPredictions.sort((a, b) => a.origin_ms - b.origin_ms);
  selectedEvaluations.sort((a, b) => a.target_ms - b.target_ms);
  requireValue(selectedPredictions.length === samples.length - 9 && selectedEvaluations.length === selectedPredictions.length
    && summary.selected_origins === selectedPredictions.length && summary.selected_matched === selectedEvaluations.length
    && summary.lstm_selected_origins === selectedPredictions.filter(p => p.model === 'LSTM').length,
  'selected prediction counts differ');
  selectedPredictions.forEach((p, i) => requireValue(p.origin_ms === samples[i + 8].time_ms, 'selected origin gap'));

  const events = texts[files[5]].replace(/^\uFEFF/, '').split(/\r?\n/).filter(line => line.trim()).map((line, i) => json(line, files[5] + ':' + (i + 1)));
  requireValue(events.length > 0 && events.at(-1).event === 'COMPLETE', 'missing lifecycle completion');
  let previousTime = -1, owner = 'HoldPosition', ownerVersion = '';
  const decisionEvents = new Map(), selectionEvents = new Map(), trainingStarts = new Map(), states = new Map();
  for (let i = 0; i < events.length; i++) {
    const e = events[i];
    requireValue(e.sequence === i + 1 && e.run_id === identity && String(e.device_id) === String(device)
      && Number.isSafeInteger(e.sim_time_ms) && actual.has(e.sim_time_ms) && e.sim_time_ms >= previousTime,
    'lifecycle event identity, sequence or time differs');
    previousTime = e.sim_time_ms;
    if (e.event === 'TRAINING_STARTED') {
      const v = versions.get(e.model_version);
      requireValue(v && !trainingStarts.has(e.model_version) && v.training_cutoff_sim_ms === e.sim_time_ms
        && v.training_windows === e.training_windows, 'training event differs from version');
      trainingStarts.set(e.model_version, e);
      states.set(e.model_version, 'TRAINING');
    }
    if (e.event === 'TRAINING_COMPLETE') {
      requireValue(states.get(e.model_version) === 'TRAINING', 'training completion without a matching start');
      states.set(e.model_version, 'EVALUATING');
    }
    if (e.event === 'QUALITY_DECISION') {
      requireValue(versions.has(e.model_version) && !decisionEvents.has(e.model_version), 'duplicate or unknown qualification');
      decisionEvents.set(e.model_version, e);
      states.set(e.model_version, e.decision === 'PASS' ? 'READY' : 'FAILED');
    }
    if (e.event === 'PROMOTED') {
      const decision = decisionEvents.get(e.model_version);
      requireValue(decision && decision.decision === 'PASS' && decision.sim_time_ms <= e.sim_time_ms, 'promotion before qualification');
      owner = 'LSTM'; ownerVersion = e.model_version;
      states.set(e.model_version, 'ACTIVE');
    }
    if (e.event === 'MONITORED') {
      requireValue(owner === 'LSTM' && ownerVersion === e.model_version && ['ACTIVE', 'DEGRADED'].includes(e.state), 'monitoring state differs');
      states.set(e.model_version, e.state);
    }
    if (e.event === 'FALLBACK') {
      requireValue(owner === 'LSTM' && ownerVersion === e.model_version && states.get(e.model_version) === 'DEGRADED', 'fallback without degradation');
      owner = 'HoldPosition'; ownerVersion = '';
    }
    if (e.event === 'SELECTED') {
      const p = selectedIndex.get(e.sim_time_ms);
      requireValue(p && !selectionEvents.has(e.sim_time_ms) && p.model === e.model && p.model_version === e.model_version
        && p.model === owner && p.model_version === ownerVersion, 'selected owner differs from lifecycle events');
      selectionEvents.set(e.sim_time_ms, e);
    }
  }
  requireValue(selectionEvents.size === selectedPredictions.length && trainingStarts.size === versions.size
    && summary.promotions === events.filter(e => e.event === 'PROMOTED').length
    && summary.fallbacks === events.filter(e => e.event === 'FALLBACK').length
    && summary.active_lstm === (owner === 'LSTM'), 'lifecycle summary differs from event history');
  for (const v of versions.values()) {
    requireValue(v.state === states.get(v.model_version), 'final version state differs from event history');
    const rows = evaluations.filter(r => r.model_version === v.model_version);
    const count = rows.length / 3, decision = decisionEvents.get(v.model_version);
    requireValue(count === v.matched_origins && v.selected_origins === selectedPredictions.filter(p => p.model_version === v.model_version).length,
      'version counts differ');
    if (count < 64) requireValue(!decision && v.quality_decision === 'WAITING_FOR_EVALUATION', 'incomplete version has a qualification decision');
    else {
      requireValue(decision && ['PASS', 'REJECT'].includes(v.quality_decision) && decision.decision === v.quality_decision
        && v.decided_at_sim_ms === v.training_cutoff_sim_ms + 64 * step && decision.sim_time_ms === v.decided_at_sim_ms,
      'first qualification time or decision differs');
      for (const model of methods) {
        const m = metrics(rows.filter(r => r.model === model).slice(0, 64));
        requireValue(v.quality?.metrics?.[model] && same(m.mean_error_m, v.quality.metrics[model].mean_error_m)
          && same(m.rmse_m, v.quality.metrics[model].rmse_m), 'frozen qualification metrics differ');
      }
    }
  }
  return { run_id: identity, device_id: device, samples, predictions, evaluations,
    selectedPredictions, selectedEvaluations, events, versions: summary.versions, summary };
}

module.exports = { files, markers, buildLearning, distance, metrics };
