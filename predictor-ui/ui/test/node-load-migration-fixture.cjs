'use strict';
const {fixture}=require('./node-load-fixture.cjs');
const {csv}=require('../mobility-results');
function migrationFixture(count=351) {
  const f=fixture('synthetic-node-load',count),run='synthetic-node-load',ids=['mobility-west','mobility-east'];
  const value=(node,i)=>node===ids[0]?(i%3)*100:(i%2)*25;
  const write=(name,head,rows)=>f[name]=[head.join(','),...rows.map(r=>head.map(k=>r[k]).join(','))].join('\n')+'\n';
  for(const name of ['node_load_predictions.csv','node_load_evaluations.csv','node_load_prediction_summary.json']) delete f[name];
  for(const name of ['node_load_model_predictions.csv','node_load_model_evaluations.csv']) {
    const {head,rows}=csv(f[name],name);
    const extra=['GRU','PatchTST'].flatMap(model=>rows.filter(r=>r.model==='LSTM').map(r=>({...r,model})));
    write(name,head.filter(k=>!k.startsWith('persistence_')),rows.concat(extra));
  }
  const summary=JSON.parse(f['node_load_model_summary.json']);summary.schema_version=2;summary.baselines=['ARIMA','LR'];summary.baseline_service='LR';
  summary.models=['LSTM','TCN','GRU','PatchTST'];
  summary.versions.push(...['GRU','PatchTST'].flatMap(model=>summary.versions.filter(v=>v.model==='LSTM').map(v=>({...v,model}))));
  f['node_load_model_summary.json']=JSON.stringify(summary);
  const events=f['node_load_model_events.jsonl'].split('\n').filter(Boolean).map(JSON.parse);
  events.push(...['GRU','PatchTST'].flatMap(model=>events.filter(e=>e.model==='LSTM').map(e=>({...e,model}))));
  f['node_load_model_events.jsonl']=events.sort((a,b)=>a.time_ms-b.time_ms).map(JSON.stringify).join('\n');
  const predictions=[],evaluations=[],fits=[],selections=[];
  const origins=[191,255,...(count>=287?[286,350]:[])].filter(o=>o<count);
  for(const node_id of ids) for(const model of ['ARIMA','LR']) for(const origin_index of origins) {
    fits.push({run_id:run,node_id,model,version:'online-v1',origin_index,first_input_index:origin_index-191,last_input_index:origin_index,
      usable:true,status:{status:'synthetic-fixture'},raw_values:Array(64).fill(model==='LR'?100:120),clipped_count:0});
    for(let lead_steps=1;lead_steps<=64;lead_steps++) {
      const p={run_id:run,node_id,model,version:'online-v1',origin_index,origin_ms:origin_index*60000,target_index:origin_index+lead_steps,
        target_ms:(origin_index+lead_steps)*60000,lead_steps,predicted_bytes:model==='LR'?100:120,inference_vector_ms:1};predictions.push(p);
      if(p.target_index<count)evaluations.push({...p,actual_bytes:value(node_id,p.target_index),absolute_error_bytes:Math.abs(p.predicted_bytes-value(node_id,p.target_index))});
    }
  }
  for(const node_id of ids)for(let i=191;i<count;i++) {
    const p=predictions.filter(p=>p.node_id===node_id&&p.model==='LR'&&p.target_index===i+1&&p.origin_index<=i).at(-1);
    selections.push({...p,selection_index:i,selection_ms:i*60000,forecast_origin_index:p.origin_index});
  }
  write('node_load_baseline_predictions.csv',['run_id','node_id','model','version','origin_index','origin_ms','target_index','target_ms','lead_steps','predicted_bytes','inference_vector_ms'],predictions);
  write('node_load_baseline_evaluations.csv',['run_id','node_id','model','version','origin_index','target_index','target_ms','predicted_bytes','actual_bytes','absolute_error_bytes'],evaluations);
  write('node_load_model_selection.csv',['run_id','node_id','selection_index','selection_ms','target_index','target_ms','model','version','forecast_origin_index','lead_steps','predicted_bytes'],selections);
  f['node_load_baseline_fits.jsonl']=fits.map(JSON.stringify).join('\n');
  f['node_load_baseline_summary.json']=JSON.stringify({schema_version:2,run_id:run,models:['ARIMA','LR'],default_service:'LR',backup_service:'ARIMA',
    input_points:192,output_points:64,interval_ms:60000,arima_order:[3,0,0],first_origin_index:191,refit_stride:64,refit_at_neural_origins:true,
    nonnegative_output:'CLIP_AT_ZERO',nonconverged_arima:'SKIP_BLOCK',generated:predictions.length,matched:evaluations.length,unmatched:predictions.length-evaluations.length,failed_fits:0});
  return f;
}
module.exports={migrationFixture};
