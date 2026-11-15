'use strict';
const {distance} = require('./mobility-learning-results');
const files = ['comparison.json', 'comparison_predictions.csv', 'comparison_evaluations.csv'];
const methods = ['IMM/Kalman', 'XGBoost', 'TCN'];
function check(ok, text) { if (!ok) throw new Error('Mobility comparison: ' + text); }
function close(a,b) { return Math.abs(a-b) <= 1e-4 + 1e-8*Math.max(Math.abs(a),Math.abs(b)); }
function buildComparison(texts, run, table) {
  if (!files.some(f => texts[f] !== undefined)) return undefined;
  check(files.every(f => typeof texts[f] === 'string') && run.learning, 'missing comparison files or learning run');
  const summary = JSON.parse(texts[files[0]]), learning = run.learning;
  check(summary.schema_version === 1 && summary.status === 'COMPLETE' && summary.role === 'SHADOW_COMPARISON'
    && summary.serving_control === false && summary.run_id === learning.run_id
    && String(summary.device_id) === String(learning.device_id) && Array.isArray(summary.methods)
    && summary.methods.length > 0 && summary.methods.every(m => methods.includes(m))
    && new Set(summary.methods).size === summary.methods.length, 'invalid comparison identity or methods');
  const numbers = ['device_id','origin_ms','target_ms','predicted_latitude_deg','predicted_longitude_deg','predict_wall_ms'];
  const strings = ['run_id','model','model_version','origin_node'];
  const predictions = table(texts[files[1]],files[1],numbers,strings);
  const evaluations = table(texts[files[2]],files[2],[...numbers,'evaluated_at_sim_ms','actual_latitude_deg','actual_longitude_deg','position_error_m'],strings);
  const samples = new Map(learning.samples.map(s => [s.time_ms,s]));
  const baseline = new Map(learning.predictions.filter(p => p.model === 'LSTM').map(p => [p.origin_ms,p]));
  check(Array.isArray(summary.versions), 'missing versions');
  const versions = new Map(), origins = new Map();
  for (const v of summary.versions) {
    const key = v.model+'|'+v.model_version, shared = learning.versions.find(s => s.model_version === v.model_version);
    check(!versions.has(key) && summary.methods.includes(v.model) && shared
      && v.status === 'COMPLETE' && v.role === 'SHADOW_COMPARISON'
      && v.run_id === learning.run_id && String(v.device_id) === String(learning.device_id)
      && v.training_cutoff_sim_ms === shared.training_cutoff_sim_ms && v.shared_training_windows === shared.training_windows
      && v.fit_windows === (v.model === 'IMM/Kalman' ? 0 : shared.training_windows)
      && Number.isFinite(v.train_wall_seconds) && v.train_wall_seconds >= 0, 'invalid training version');
    versions.set(key,v);
  }
  check(versions.size === summary.methods.length*learning.versions.length, 'incomplete model versions');
  check(predictions.length === evaluations.length && predictions.length === baseline.size*summary.methods.length
    && summary.matched_per_method === baseline.size, 'incomplete comparison targets');
  for (let i=0;i<predictions.length;i++) {
    const p=predictions[i],e=evaluations[i], shared=baseline.get(p.origin_ms), actual=samples.get(p.target_ms);
    const key=p.model+'|'+p.origin_ms;
    check(!origins.has(key) && summary.methods.includes(p.model) && versions.has(p.model+'|'+p.model_version)
      && p.run_id===learning.run_id && p.device_id===learning.device_id && shared && actual
      && p.model_version===shared.model_version && p.target_ms===shared.target_ms && p.origin_node===shared.origin_node
      && Math.abs(p.predicted_latitude_deg)<=90 && Math.abs(p.predicted_longitude_deg)<=180
      && p.predict_wall_ms>=0, 'invalid or duplicate comparison forecast');
    origins.set(key,p);
    check(Object.keys(p).every(k=>e[k]===p[k]) && e.evaluated_at_sim_ms===p.target_ms
      && close(e.actual_latitude_deg,actual.latitude_deg) && close(e.actual_longitude_deg,actual.longitude_deg)
      && e.position_error_m>=0 && close(e.position_error_m,distance([p.predicted_latitude_deg,p.predicted_longitude_deg],[actual.latitude_deg,actual.longitude_deg])),
    'evaluation differs from prediction or real target');
  }
  return {methods:summary.methods, predictions,evaluations,versions:summary.versions};
}
module.exports = {files,buildComparison};
