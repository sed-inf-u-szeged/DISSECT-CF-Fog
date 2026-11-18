'use strict';
const {distance} = require('./mobility-learning-results');
const markers = ['position_baseline_predictions.csv', 'position_baseline_evaluations.csv'];
const files = [...markers, 'mobility_samples.csv'];
const methods = ['HoldPosition', 'ConstantVelocity'];
function check(ok, message) { if (!ok) throw Error('Position baselines: ' + message); }
const point = p => [p.predicted_latitude_deg, p.predicted_longitude_deg];

function buildBaselines(texts, run, table) {
  if (!markers.some(name => texts[name] !== undefined)) {
    check(!run.props.position_baselines, 'declared early baseline files are missing');
    // Legacy files contain issued coverage baselines. Recover errors only where a
    // real recorded forecast and its target observation exist; never backfill a forecast.
    const predictions = run.predictions.flatMap(p => methods.map((model, i) => ({model, model_version:'baseline-legacy',
      device_id:run.observations[0].device_id, origin_ms:p.origin_ms,target_ms:p.target_ms,origin_node:p.origin_node,
      predicted_latitude_deg:i ? p.velocity_latitude:p.hold_latitude,
      predicted_longitude_deg:i ? p.velocity_longitude:p.hold_longitude})));
    const actual = new Map(run.evaluations.map(e => [e.origin_ms,e]));
    const evaluations = predictions.filter(p=>actual.has(p.origin_ms)).map(p=>{
      const e=actual.get(p.origin_ms);
      return {model:p.model,model_version:p.model_version,origin_ms:p.origin_ms,target_ms:p.target_ms,
        actual_latitude_deg:e.actual_latitude,actual_longitude_deg:e.actual_longitude,
        position_error_m:distance(point(p),[e.actual_latitude,e.actual_longitude])};
    });
    return {source:'legacy-coverage',predictions,evaluations};
  }
  check(files.every(name=>typeof texts[name]==='string'), 'incomplete result files');
  const samples=table(texts[files[2]], files[2], ['device_id','time_ms','latitude_deg','longitude_deg'], ['run_id','current_node']);
  const predictions=table(texts[files[0]],files[0],['device_id','origin_ms','target_ms','predicted_latitude_deg','predicted_longitude_deg'],['run_id','model','model_version','origin_node']);
  const evaluations=table(texts[files[1]],files[1],['device_id','origin_ms','target_ms','actual_latitude_deg','actual_longitude_deg','position_error_m'],['run_id','model','model_version']);
  const device=run.observations[0].device_id, identity=run.props.run_id ?? run.learning?.run_id ?? samples[0]?.run_id;
  check(samples.length>=2 && [...samples,...predictions,...evaluations].every(r=>r.run_id===identity && r.device_id===device), 'mixed run/device');
  const observed=new Map(run.observations.map(o=>[o.time_ms,o]));
  samples.forEach((s,i)=>{
    const o=observed.get(s.time_ms);
    check(o && distance([s.latitude_deg,s.longitude_deg],[o.latitude,o.longitude])<1e-4
      && s.current_node===o.current_node && (!i || s.time_ms-samples[i-1].time_ms===60000), 'sample time or position differs');
  });
  const expected=new Map();
  samples.slice(0,-1).forEach((s,i)=>methods.slice(0,i ? 2:1).forEach(model=>{
    const xy=model==='HoldPosition' ? [s.latitude_deg,s.longitude_deg]
      : [2*s.latitude_deg-samples[i-1].latitude_deg,2*s.longitude_deg-samples[i-1].longitude_deg];
    expected.set(model+'|'+s.time_ms,{s,target:samples[i+1],xy});
  }));
  check(predictions.length===expected.size && evaluations.length===expected.size, 'missing or extra early forecasts/targets');
  const keys=new Set(), evaluated=new Set();
  for(const p of predictions) {
    const key=p.model+'|'+p.origin_ms, x=expected.get(key);
    check(x && !keys.has(key) && p.model_version==='baseline-v1' && p.target_ms===x.target.time_ms
      && p.origin_node===x.s.current_node && point(p).every(Number.isFinite)
      && distance(point(p),x.xy)<1e-4, 'invalid/non-causal forecast');
    keys.add(key);
  }
  for(const e of evaluations) {
    const key=e.model+'|'+e.origin_ms,x=expected.get(key);
    check(x && !evaluated.has(key) && e.model_version==='baseline-v1' && e.target_ms===x.target.time_ms
      && distance([e.actual_latitude_deg,e.actual_longitude_deg],[x.target.latitude_deg,x.target.longitude_deg])<1e-4
      && e.position_error_m>=0 && Math.abs(e.position_error_m-distance(x.xy,[x.target.latitude_deg,x.target.longitude_deg]))<1e-4,
    'target or distance error differs');
    evaluated.add(key);
  }
  predictions.sort((a,b)=>a.origin_ms-b.origin_ms); evaluations.sort((a,b)=>a.target_ms-b.target_ms);
  return {source:'early-position',predictions,evaluations};
}
module.exports={markers,files,buildBaselines};
