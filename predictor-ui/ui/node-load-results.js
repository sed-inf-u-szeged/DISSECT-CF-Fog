'use strict';

// Shared, run-level exports: read once, never once per device. No model is run here.
const samplingFiles = ['node_load_sampling.json', 'node_load_samples.csv'];
const baselineFiles = ['node_load_prediction_summary.json', 'node_load_predictions.csv', 'node_load_evaluations.csv'];
const modelFiles = ['node_load_model_summary.json', 'node_load_model_predictions.csv',
  'node_load_model_evaluations.csv', 'node_load_model_selection.csv', 'node_load_model_events.jsonl'];
const baselineV2Files = ['node_load_baseline_summary.json', 'node_load_baseline_predictions.csv',
  'node_load_baseline_evaluations.csv', 'node_load_baseline_fits.jsonl'];
const files = [...samplingFiles, ...baselineFiles, ...baselineV2Files, ...modelFiles];
function check(ok, message) { if (!ok) throw new Error('Node-load results: ' + message); }
function close(a, b) { return Math.abs(a - b) <= 1e-6 * Math.max(1, Math.abs(a), Math.abs(b)); }
function key(p) { return JSON.stringify([p.node_id, p.model, p.version, p.origin_index, p.target_index]); }
function versionKey(v) { return JSON.stringify([v.node_id, v.model, v.version]); }

function readNodeLoad(read, exists, csv, runId, nodeNames) {
  if (!files.some(exists)) return undefined; // Pre-W03 runs remain readable.
  function group(names, optional = false) {
    if (optional && !names.some(exists)) return false;
    check(names.every(exists), 'incomplete export group: ' + names.join(', '));
    return true;
  }
  function json(name) { return JSON.parse(read(name).replace(/^\uFEFF/, '')); }
  function table(name, numbers, strings = []) {
    const {head, rows} = csv(read(name), name);
    for (const col of ['run_id', 'node_id', ...numbers, ...strings]) check(head.includes(col), name + ' missing ' + col);
    for (const row of rows) {
      check(row.run_id === runId && nodeNames.includes(row.node_id), name + ' mixed run or unknown node');
      for (const col of numbers) {
        check(row[col].trim() !== '' && Number.isFinite(Number(row[col])), name + ' invalid number: ' + col);
        row[col] = Number(row[col]);
        check(row[col] >= 0, name + ' negative value: ' + col);
      }
      for (const col of strings) check(row[col] !== '', name + ' empty ' + col);
    }
    return rows;
  }
  group(samplingFiles);
  const meta = json(samplingFiles[0]);
  check(meta.schema_version === 1 && meta.run_id === runId && meta.interval_ms === 60000
    && meta.start_ms === 0 && meta.sample_phase === 'AFTER_EVENTS_AT_TIME'
    && Array.isArray(meta.node_ids) && meta.node_ids.length === nodeNames.length
    && new Set(meta.node_ids).size === nodeNames.length && meta.node_ids.every(n => nodeNames.includes(n)), 'sampling metadata differs');
  const nodes = nodeNames.map(node_id => ({node_id, samples: [], baseline: [], baselineEvaluations: [],
    predictions: [], evaluations: [], selections: [], events: [], versions: []}));
  const byNode = new Map(nodes.map(n => [n.node_id, n]));
  const samples = table(samplingFiles[1], ['sample_index', 'time_ms', 'waiting_assignment_bytes'], ['sample_phase']);
  const actuals = new Map();
  for (const r of samples) {
    check(Number.isSafeInteger(r.sample_index) && r.time_ms === r.sample_index * meta.interval_ms
      && r.sample_phase === meta.sample_phase, 'sample time or phase differs');
    const k = JSON.stringify([r.node_id, r.sample_index]);
    check(!actuals.has(k), 'duplicate node sample'); actuals.set(k, r.waiting_assignment_bytes);
    byNode.get(r.node_id).samples.push({index: r.sample_index, time_ms: r.time_ms, value: r.waiting_assignment_bytes});
  }
  for (const n of nodes) {
    n.samples.sort((a, b) => a.index - b.index);
    check(n.samples.length === meta.sample_count_per_node && n.samples.length > 0
      && n.samples.every((s, i) => s.index === i) && n.samples.at(-1).time_ms === meta.last_sample_ms, 'missing or discontinuous node samples');
  }
  check(samples.length === meta.rows && meta.last_sample_ms <= meta.sample_until_ms
    && meta.sample_until_ms <= meta.simulation_end_ms, 'sampling counts or end time differs');
  const actual = (node, index) => actuals.get(JSON.stringify([node, index]));
  const forecastMap = new Map();
  function forecast(p) {
    check(Number.isSafeInteger(p.origin_index) && Number.isSafeInteger(p.target_index)
      && p.origin_ms === p.origin_index * meta.interval_ms && p.target_ms === p.target_index * meta.interval_ms
      && p.target_index > p.origin_index && p.lead_steps === p.target_index - p.origin_index
      && actual(p.node_id, p.origin_index) !== undefined, 'forecast origin or target differs');
    check(!forecastMap.has(key(p)), 'duplicate forecast'); forecastMap.set(key(p), p); return p;
  }
  function evaluation(r, p, value, error) {
    check(p && actual(r.node_id, p.target_index) !== undefined && close(value, actual(r.node_id, p.target_index))
      && close(error, Math.abs(p.predicted_bytes - value)), 'evaluation does not match its forecast or actual sample');
    return {...p, actual_bytes: value, absolute_error_bytes: error};
  }
  let hasBaseline = false;
  if (group(baselineFiles, true)) {
    hasBaseline = true;
    const summary = json(baselineFiles[0]);
    check(summary.schema_version === 1 && summary.run_id === runId && summary.model === 'Persistence'
      && summary.target_metric === 'waiting_assignment_bytes' && summary.unit === 'B' && summary.horizon_ms === meta.interval_ms, 'baseline metadata differs');
    const nums = ['origin_sample_index', 'origin_time_ms', 'target_time_ms', 'last_input_time_ms', 'horizon_ms', 'predicted_waiting_assignment_bytes', 'inference_duration_ns'];
    function convert(r) {
      check(r.model === 'Persistence' && r.model_version === 'fixed-v1' && r.target_metric === summary.target_metric
        && r.unit === 'B' && r.task === 'node_load' && r.last_input_time_ms === r.origin_time_ms
        && r.horizon_ms === meta.interval_ms && close(r.predicted_waiting_assignment_bytes, actual(r.node_id, r.origin_sample_index)), 'baseline forecast differs');
      return {node_id:r.node_id, model:'Persistence', version:'fixed-v1', origin_index:r.origin_sample_index,
        origin_ms:r.origin_time_ms, target_index:r.target_time_ms / meta.interval_ms, target_ms:r.target_time_ms,
        lead_steps:1, predicted_bytes:r.predicted_waiting_assignment_bytes, inference_vector_ms:r.inference_duration_ns / 1e6};
    }
    for (const r of table(baselineFiles[1], nums, ['model', 'model_version', 'target_metric', 'unit', 'task'])) {
      byNode.get(r.node_id).baseline.push(forecast(convert(r)));
    }
    const evaluated = new Set();
    for (const r of table(baselineFiles[2], [...nums, 'actual_sample_index', 'actual_time_ms', 'actual_waiting_assignment_bytes', 'absolute_error_bytes'], ['model', 'model_version', 'target_metric', 'unit', 'task'])) {
      const candidate = convert(r), p = forecastMap.get(key(candidate));
      check(!evaluated.has(key(candidate)) && r.actual_time_ms === candidate.target_ms && r.actual_sample_index === candidate.target_index
        && p && close(candidate.predicted_bytes, p.predicted_bytes), 'baseline evaluation identity differs'); evaluated.add(key(candidate));
      byNode.get(r.node_id).baselineEvaluations.push(evaluation(r, p, r.actual_waiting_assignment_bytes, r.absolute_error_bytes));
    }
    check(nodes.reduce((s,n)=>s+n.baseline.length,0) === summary.aggregate.generated
      && evaluated.size === summary.aggregate.matched, 'baseline summary count differs');
  }
  let baselines;
  if (group(baselineV2Files, true)) {
    check(!hasBaseline, 'mixed legacy and new baseline exports');
    const summary=json(baselineV2Files[0]);
    check(summary.schema_version===2 && summary.run_id===runId && JSON.stringify(summary.models)===JSON.stringify(['ARIMA','LR'])
      && summary.input_points===192 && summary.output_points===64 && summary.interval_ms===60000
      && summary.default_service==='LR' && summary.backup_service==='ARIMA'
      && JSON.stringify(summary.arima_order)==='[3,0,0]', 'baseline v2 metadata differs');
    baselines=summary.models;hasBaseline=true;
    const vectors=new Map();
    for(const p of table(baselineV2Files[1], ['origin_index','origin_ms','target_index','target_ms','lead_steps','predicted_bytes','inference_vector_ms'], ['model','version'])) {
      check(baselines.includes(p.model) && p.version==='online-v1' && p.origin_index>=191 && p.lead_steps<=64,'invalid baseline identity');
      byNode.get(p.node_id).baseline.push(forecast(p));
      const k=JSON.stringify([p.node_id,p.model,p.origin_index]);if(!vectors.has(k))vectors.set(k,[]);vectors.get(k).push(p);
    }
    for(const vector of vectors.values()) check(vector.length===64 && vector.sort((a,b)=>a.lead_steps-b.lead_steps).every((p,i)=>p.lead_steps===i+1),'incomplete baseline block');
    const evaluated=new Set();
    for(const r of table(baselineV2Files[2], ['origin_index','target_index','target_ms','predicted_bytes','actual_bytes','absolute_error_bytes'], ['model','version'])) {
      const p=forecastMap.get(key(r));
      check(p && !evaluated.has(key(r)) && p.target_ms===r.target_ms && close(p.predicted_bytes,r.predicted_bytes),'baseline evaluation identity differs');
      evaluated.add(key(r));byNode.get(r.node_id).baselineEvaluations.push(evaluation(r,p,r.actual_bytes,r.absolute_error_bytes));
    }
    const attempts=new Set();let failed=0;
    for(const line of read(baselineV2Files[3]).split(/\r?\n/).filter(x=>x.trim())) {
      const f=JSON.parse(line),k=JSON.stringify([f.node_id,f.model,f.origin_index]);
      check(f.run_id===runId && byNode.has(f.node_id) && baselines.includes(f.model) && f.version==='online-v1'
        && Number.isSafeInteger(f.origin_index) && f.origin_index>=191 && actual(f.node_id,f.origin_index)!==undefined
        && f.first_input_index===f.origin_index-191 && f.last_input_index===f.origin_index && !attempts.has(k),'invalid baseline fit');
      attempts.add(k);
      check(typeof f.usable==='boolean' && f.usable===vectors.has(k),'fit/forecast mismatch');
      if(f.usable) check(f.status?.converged!==false && f.raw_values.length===64
        && f.raw_values.every((v,i)=>Number.isFinite(v) && close(Math.max(0,v),vectors.get(k)[i].predicted_bytes)),'invalid baseline output');
      else failed++;
    }
    check([...vectors.keys()].every(k=>attempts.has(k)) && failed===summary.failed_fits
      && nodes.reduce((s,n)=>s+n.baseline.length,0)===summary.generated && evaluated.size===summary.matched
      && summary.unmatched===summary.generated-summary.matched,'baseline counts differ');
  }
  let policy, models = [], profile;
  if (group(modelFiles, true)) {
    check(hasBaseline, 'model exports require baseline exports');
    const summary = json(modelFiles[0]); policy = summary.quality_policy; models = summary.models; profile = summary.profile;
    check(summary.schema_version === (baselines ? 2 : 1) && summary.run_id === runId && summary.status === 'COMPLETE'
      && summary.target === 'waiting_assignment_bytes' && summary.units === 'B' && summary.interval_ms === meta.interval_ms
      && summary.input_points === 192 && summary.output_points === 64 && summary.forecast_stride === 64
      && Array.isArray(models) && new Set(models).size === models.length && models.every(m => ['LSTM','TCN','GRU','PatchTST'].includes(m))
      && summary.resource_control === false && summary.connection_control === false && summary.offload_control === false, 'trained model metadata differs');
    check(policy && policy.matched_targets === 64 && ['candidate','observe'].includes(profile)
      && policy.minimum_within_tolerance_percent === (profile === 'candidate' ? 80 : 0)
      && policy.relative_tolerance === (profile === 'candidate' ? .2 : .1) && policy.absolute_tolerance_bytes === .1
      && policy.maximum_smape_percent === 200 && policy.mae_mse_rmse_limits === 'UNBOUNDED', 'unsupported quality policy');
    const versions = new Map();
    for (const v of summary.versions) {
      check(byNode.has(v.node_id) && models.includes(v.model) && /^v\d{4,}$/.test(v.version) && !versions.has(versionKey(v))
        && Number.isSafeInteger(v.trained_at_index) && actual(v.node_id,v.trained_at_index) !== undefined
        && Number.isSafeInteger(v.training_windows) && v.training_windows >= 32
        && Number.isFinite(v.training_seconds) && v.training_seconds >= 0, 'invalid model version');
      versions.set(versionKey(v), v);
      // Deliberately omit final state/metrics: replay derives these from past events and evaluations.
      byNode.get(v.node_id).versions.push({model:v.model, version:v.version, trained_at_ms:v.trained_at_index * meta.interval_ms,
        training_windows:v.training_windows, training_seconds:v.training_seconds});
    }
    const vectors = new Map();
    for (const r of table(modelFiles[1], ['origin_index','origin_ms','target_index','target_ms','lead_steps','predicted_bytes','inference_vector_ms',...(baselines?[]:['persistence_bytes'])], ['model','version'])) {
      const v = versions.get(versionKey(r));
      check(v && r.origin_index >= v.trained_at_index && r.lead_steps <= 64
        && (baselines || close(r.persistence_bytes,actual(r.node_id,r.origin_index))), 'trained forecast version or paired baseline differs');
      const p = forecast(r); byNode.get(r.node_id).predictions.push(p);
      const k=JSON.stringify([versionKey(p),p.origin_index]); if (!vectors.has(k)) vectors.set(k,[]); vectors.get(k).push(p);
    }
    for (const vector of vectors.values()) {
      vector.sort((a,b)=>a.lead_steps-b.lead_steps);
      check(vector.length === 64 && vector.every((p,i)=>p.lead_steps===i+1 && p.inference_vector_ms===vector[0].inference_vector_ms), 'incomplete forecast vector');
    }
    const evaluated = new Set();
    for (const r of table(modelFiles[2], ['origin_index','target_index','target_ms','predicted_bytes','actual_bytes','absolute_error_bytes',...(baselines?[]:['persistence_error_bytes'])], ['model','version'])) {
      const p=forecastMap.get(key(r));
      check(!evaluated.has(key(r)) && p && r.target_ms===p.target_ms && close(r.predicted_bytes,p.predicted_bytes)
        && (baselines || close(r.persistence_error_bytes,Math.abs(p.persistence_bytes-r.actual_bytes))), 'trained evaluation identity differs'); evaluated.add(key(r));
      byNode.get(r.node_id).evaluations.push(evaluation(r,p,r.actual_bytes,r.absolute_error_bytes));
    }
    for (const [k,v] of versions) {
      const n=byNode.get(v.node_id), pred=n.predictions.filter(p=>versionKey(p)===k), ev=n.evaluations.filter(p=>versionKey(p)===k);
      check(pred.length===v.forecasts && ev.length===v.matched && pred.length-ev.length===v.unmatched, 'version summary count differs');
    }
    const eventTypes=['TRAINING','TRAINED','QUALIFIED','REJECTED','DEGRADED','PROMOTED','FALLBACK'];
    for (const line of read(modelFiles[4]).replace(/^\uFEFF/,'').split(/\r?\n/).filter(l=>l.trim())) {
      const e=JSON.parse(line), v=versions.get(versionKey(e));
      check(e.run_id===runId && e.domain==='NODE_LOAD' && v && eventTypes.includes(e.event)
        && Number.isSafeInteger(e.sample_index) && e.time_ms===e.sample_index*meta.interval_ms
        && e.sample_index>=v.trained_at_index && actual(e.node_id,e.sample_index)!==undefined, 'invalid lifecycle event');
      byNode.get(e.node_id).events.push(e);
    }
    const selectedKeys=new Set();
    for (const r of table(modelFiles[3], ['selection_index','selection_ms','target_index','target_ms','forecast_origin_index','lead_steps','predicted_bytes'], ['model','version'])) {
      const p=forecastMap.get(key({...r,origin_index:r.forecast_origin_index})), k=JSON.stringify([r.node_id,r.selection_index]);
      check(!selectedKeys.has(k) && p && r.selection_ms===r.selection_index*meta.interval_ms && r.target_index===r.selection_index+1
        && r.target_ms===r.target_index*meta.interval_ms && p.origin_index<=r.selection_index && p.lead_steps===r.lead_steps
        && close(p.predicted_bytes,r.predicted_bytes), 'selection does not match an issued forecast'); selectedKeys.add(k);
      byNode.get(r.node_id).selections.push({...p,selection_ms:r.selection_ms});
    }
    for (const n of nodes) {
      check(n.selections.length===Math.max(0,n.samples.length-(baselines?191:0)), 'missing selection records');
      for (let i=1;i<n.events.length;i++) check(n.events[i].time_ms>=n.events[i-1].time_ms,'events out of order');
      for (const v of n.versions) {
        const es=n.events.filter(e=>e.model===v.model && e.version===v.version);
        check(es.filter(e=>e.event==='TRAINING').length===1 && es.filter(e=>e.event==='TRAINED').length===1
          && es[0]?.event==='TRAINING' && es[1]?.event==='TRAINED' && es[0].time_ms===v.trained_at_ms
          && es[1].time_ms===v.trained_at_ms, 'missing or inconsistent training events');
        for (const e of es.filter(e=>['QUALIFIED','REJECTED'].includes(e.event))) {
          const matched=n.evaluations.filter(p=>p.model===v.model && p.version===v.version && p.target_ms<=e.time_ms).sort((a,b)=>a.target_ms-b.target_ms);
          check(matched.length>=policy.matched_targets, 'qualification before 64 actual targets');
          const first=matched.slice(0,policy.matched_targets);
          const within=first.filter(p=>p.absolute_error_bytes<=Math.max(policy.absolute_tolerance_bytes,policy.relative_tolerance*Math.abs(p.actual_bytes))).length*100/first.length;
          check((e.event==='QUALIFIED')===(within>=policy.minimum_within_tolerance_percent), 'qualification disagrees with matched target quality');
        }
      }
      n.selections.sort((a,b)=>a.selection_ms-b.selection_ms);
      let eventIndex=0, owner=baselines?'BASELINE':'Persistence';
      for (const selection of n.selections) {
        while (eventIndex<n.events.length && n.events[eventIndex].time_ms<=selection.selection_ms) {
          const e=n.events[eventIndex++];
          if (e.event==='PROMOTED') {
            check(n.events.slice(0,eventIndex).some(x=>x.event==='QUALIFIED' && x.model===e.model && x.version===e.version), 'promotion without qualification');
            owner=e.model+'/'+e.version;
          }
          if (e.event==='FALLBACK') owner=baselines?'BASELINE':'Persistence';
        }
        check(owner===(baselines?.includes(selection.model) ? 'BASELINE' : selection.model==='Persistence' ? 'Persistence' : selection.model+'/'+selection.version), 'selection is not the eligible recorded model');
      }
    }
  }
  for (const n of nodes) {
    for (const field of ['baseline','predictions']) n[field].sort((a,b)=>a.origin_ms-b.origin_ms || a.target_ms-b.target_ms);
    for (const field of ['baselineEvaluations','evaluations']) n[field].sort((a,b)=>a.target_ms-b.target_ms);
    n.selections.sort((a,b)=>a.selection_ms-b.selection_ms);
    for (const [predictions,evaluations] of [[n.baseline,n.baselineEvaluations],[n.predictions,n.evaluations]]) {
      const ids=new Set(evaluations.map(key));
      check(predictions.every(p=>ids.has(key(p))===(actual(p.node_id,p.target_index)!==undefined)), 'missing or fabricated actual target evaluations');
    }
    if (hasBaseline && !baselines) check(n.baseline.length===n.samples.length && n.baseline.every((p,i)=>p.origin_index===i),'missing baseline origins');
  }
  return {run_id:runId, interval_ms:meta.interval_ms, nodes, models, policy, profile, hasBaseline, ...(baselines?{baselines}: {})};
}
module.exports = {readNodeLoad, files};
