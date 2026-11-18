'use strict';
const {test}=require('node:test'), assert=require('node:assert/strict');
const {fixture,csv}=require('./mobility-fixture.cjs');
const {build}=require('../mobility-results');
const methods=['IMM/Kalman','XGBoost','TCN'];
function extraFixture(){
  const texts=fixture(), learning=build(texts,'fixture').learning;
  const predictions=[],evaluations=[],versions=[];
  for(const method of methods){
    for(const p of learning.predictions.filter(p=>p.model==='LSTM')){
      const issued={...p,run_id:learning.run_id,model:method,predict_wall_ms:1};predictions.push(issued);
      const e=learning.evaluations.find(e=>e.model==='LSTM'&&e.origin_ms===p.origin_ms);
      evaluations.push({...e,...issued,evaluated_at_sim_ms:p.target_ms});
    }
    for(const v of learning.versions)versions.push({model:method,model_version:v.model_version,run_id:learning.run_id,
      device_id:learning.device_id,status:'COMPLETE',role:'SHADOW_COMPARISON',training_cutoff_sim_ms:v.training_cutoff_sim_ms,
      shared_training_windows:v.training_windows,fit_windows:method==='IMM/Kalman'?0:v.training_windows,train_wall_seconds:1});
  }
  const summary={schema_version:1,status:'COMPLETE',role:'SHADOW_COMPARISON',serving_control:false,run_id:learning.run_id,
    device_id:learning.device_id,methods,matched_per_method:predictions.length/3,versions};
  const render=()=>({...texts,'comparison.json':JSON.stringify(summary),'comparison_predictions.csv':csv(predictions),'comparison_evaluations.csv':csv(evaluations)});
  return {texts,summary,predictions,evaluations,render};
}
test('old runs remain readable without comparison files',()=>assert.equal(build(fixture(),'old').comparison,undefined));
test('three real-format candidates use identical LSTM targets and preserve serving summary',()=>{
  const f=extraFixture(),run=build(f.render(),'comparison');
  assert.deepEqual(run.comparison.methods,methods);
  assert.equal(run.comparison.predictions.length,run.learning.predictions.length);
  assert.deepEqual(run.learning.summary,build(f.texts,'old').learning.summary);
});
test('partial, duplicate, wrong-device and fabricated evaluation data are rejected',()=>{
  const edits=[f=>f.predictions.pop(),f=>f.predictions[1]={...f.predictions[0]},f=>f.predictions[0].device_id=99,
    f=>f.evaluations[0].position_error_m+=10,f=>f.evaluations[0].actual_latitude_deg+=.01,
    f=>f.predictions[0].model_version='v9999',f=>f.summary.versions[0].training_cutoff_sim_ms+=60000,
    f=>f.summary.serving_control=true];
  for(const edit of edits){const f=extraFixture();edit(f);assert.throws(()=>build(f.render(),'bad'),/Mobility comparison/);}
  const f=extraFixture().render();delete f['comparison_evaluations.csv'];assert.throws(()=>build(f,'partial'),/missing comparison/);
});
