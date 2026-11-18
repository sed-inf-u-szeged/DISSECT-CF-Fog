'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const {fixture,csv}=require('./mobility-fixture.cjs');
const {build}=require('../mobility-results');
const {distance}=require('../mobility-learning-results');
function earlyFixture(){
  const texts=fixture();const old=build(texts,'old');
  const samples=old.learning.samples,predictions=[],evaluations=[];
  samples.slice(0,-1).forEach((o,i)=>['HoldPosition',...(i?['ConstantVelocity']:[])].forEach(model=>{
    const lat=model==='HoldPosition'?o.latitude_deg:2*o.latitude_deg-samples[i-1].latitude_deg;
    const lon=model==='HoldPosition'?o.longitude_deg:2*o.longitude_deg-samples[i-1].longitude_deg;
    const identity={run_id:old.learning.run_id,device_id:0,model,model_version:'baseline-v1',origin_ms:o.time_ms,target_ms:samples[i+1].time_ms};
    predictions.push({...identity,origin_node:'mobility-west',predicted_latitude_deg:lat,predicted_longitude_deg:lon});
    const t=samples[i+1];evaluations.push({...identity,actual_latitude_deg:t.latitude_deg,actual_longitude_deg:t.longitude_deg,
      position_error_m:distance([lat,lon],[t.latitude_deg,t.longitude_deg])});
  }));
  texts['position_baseline_predictions.csv']=csv(predictions);texts['position_baseline_evaluations.csv']=csv(evaluations);
  texts['run.properties']+='\nposition_baselines=early-position-v1\n';
  return texts;
}
test('new position baselines start with one/two observations and retain real target errors',()=>{
  const r=build(earlyFixture(),'early'),b=r.baselines,n=r.learning.samples.length;
  assert.equal(b.source,'early-position');assert.equal(b.predictions.length,2*n-3);
  assert.equal(b.predictions[0].origin_ms,r.observations[0].time_ms);
  assert.equal(b.predictions.find(p=>p.model==='ConstantVelocity').origin_ms,r.observations[1].time_ms);
  assert.equal(b.evaluations[0].target_ms,r.observations[1].time_ms);
});
test('legacy results recover only existing forecasts without inventing their first observation',()=>{
  const r=build(fixture(),'legacy');assert.equal(r.baselines.source,'legacy-coverage');
  assert.equal(r.baselines.predictions.length,2*r.predictions.length);
  assert.equal(r.baselines.predictions[0].origin_ms,r.predictions[0].origin_ms);
});
test('partial files, false errors and altered first forecasts cannot pass verification',()=>{
  const t=earlyFixture();delete t['position_baseline_evaluations.csv'];assert.throws(()=>build(t,'partial'),/incomplete/);
  delete t['position_baseline_predictions.csv'];assert.throws(()=>build(t,'missing'),/missing/);
  const t2=earlyFixture();t2['position_baseline_evaluations.csv']=t2['position_baseline_evaluations.csv'].replace(/,[^,\n]+\n$/,',99999\n');
  assert.throws(()=>build(t2,'false'),/distance error/);
  const t3=earlyFixture();t3['position_baseline_predictions.csv']=t3['position_baseline_predictions.csv'].replace('HoldPosition,baseline-v1','ConstantVelocity,baseline-v1');
  assert.throws(()=>build(t3,'fake'),/forecast/);
});
