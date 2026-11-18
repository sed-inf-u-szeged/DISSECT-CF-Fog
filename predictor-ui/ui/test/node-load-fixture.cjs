'use strict';
// Fully synthetic node exports. Real-run numerical replay is tested separately.
function fixture(run='synthetic-node-load',count=351) {
  const ids=['mobility-west','mobility-east'], interval=60000, files={};
  const put=(name,value)=>files[name]=JSON.stringify(value);
  const csv=(name,head,rows)=>files[name]=[head.join(','),...rows.map(r=>head.map(k=>r[k]).join(','))].join('\n')+'\n';
  const value=(node,i)=>node===ids[0] ? (i%3)*100 : (i%2)*25;
  put('node_load_sampling.json',{schema_version:1,run_id:run,interval_ms:interval,start_ms:0,
    sample_phase:'AFTER_EVENTS_AT_TIME',node_ids:ids,rows:count*2,sample_count_per_node:count,
    last_sample_ms:(count-1)*interval,sample_until_ms:(count-1)*interval,simulation_end_ms:(count+10)*interval});
  const samples=ids.flatMap(node_id=>Array.from({length:count},(_,i)=>({run_id:run,node_id,sample_index:i,time_ms:i*interval,sample_phase:'AFTER_EVENTS_AT_TIME',waiting_assignment_bytes:value(node_id,i)})));
  csv('node_load_samples.csv',Object.keys(samples[0]),samples);
  const baseline=samples.map(r=>({run_id:run,node_id:r.node_id,task:'node_load',model:'Persistence',model_version:'fixed-v1',
    target_metric:'waiting_assignment_bytes',unit:'B',origin_sample_index:r.sample_index,last_input_time_ms:r.time_ms,
    origin_time_ms:r.time_ms,target_time_ms:r.time_ms+interval,horizon_ms:interval,
    predicted_waiting_assignment_bytes:r.waiting_assignment_bytes,inference_duration_ns:1000}));
  csv('node_load_predictions.csv',Object.keys(baseline[0]),baseline);
  const baselineE=baseline.filter(p=>p.origin_sample_index<count-1).map(p=>({...p,actual_sample_index:p.origin_sample_index+1,
    actual_time_ms:p.target_time_ms,actual_waiting_assignment_bytes:value(p.node_id,p.origin_sample_index+1),
    absolute_error_bytes:Math.abs(p.predicted_waiting_assignment_bytes-value(p.node_id,p.origin_sample_index+1))}));
  csv('node_load_evaluations.csv',Object.keys(baselineE[0]),baselineE);
  put('node_load_prediction_summary.json',{schema_version:1,run_id:run,model:'Persistence',target_metric:'waiting_assignment_bytes',
    unit:'B',horizon_ms:interval,aggregate:{generated:baseline.length,matched:baselineE.length}});
  const predictions=[], evaluations=[], versions=[], events=[];
  for (const node_id of ids) for (const model of ['LSTM','TCN']) {
    if(count<287) continue;
    const v={node_id,model,version:'v0001',trained_at_index:286,training_windows:32,training_seconds:2,
      forecasts:64,matched:Math.min(count-287,64),unmatched:Math.max(351-count,0)};
    versions.push(v);
    for(let lead_steps=1;lead_steps<=64;lead_steps++) {
      const p={run_id:run,node_id,model,version:v.version,origin_index:286,origin_ms:286*interval,
        target_index:286+lead_steps,target_ms:(286+lead_steps)*interval,lead_steps,predicted_bytes:100,
        persistence_bytes:value(node_id,286),inference_vector_ms:3}; predictions.push(p);
      if(p.target_index<count) evaluations.push({...p,actual_bytes:value(node_id,p.target_index),
        absolute_error_bytes:Math.abs(p.predicted_bytes-value(node_id,p.target_index)),persistence_error_bytes:Math.abs(p.persistence_bytes-value(node_id,p.target_index))});
    }
    for(const [event,sample_index] of [['TRAINING',286],['TRAINED',286],...(count>=351 ? [['REJECTED',350]] : [])]) {
      events.push({run_id:run,node_id,domain:'NODE_LOAD',model,version:v.version,event,sample_index,time_ms:sample_index*interval});
    }
  }
  put('node_load_model_summary.json',{schema_version:1,run_id:run,status:'COMPLETE',target:'waiting_assignment_bytes',units:'B',
    interval_ms:interval,input_points:192,output_points:64,forecast_stride:64,models:['LSTM','TCN'],profile:'candidate',
    resource_control:false,connection_control:false,offload_control:false,quality_policy:{matched_targets:64,
      minimum_within_tolerance_percent:80,relative_tolerance:.2,absolute_tolerance_bytes:.1,maximum_smape_percent:200,mae_mse_rmse_limits:'UNBOUNDED'},versions});
  csv('node_load_model_predictions.csv',['run_id','node_id','model','version','origin_index','origin_ms','target_index','target_ms','lead_steps','predicted_bytes','persistence_bytes','inference_vector_ms'],predictions);
  csv('node_load_model_evaluations.csv',['run_id','node_id','model','version','origin_index','target_index','target_ms','predicted_bytes','actual_bytes','absolute_error_bytes','persistence_error_bytes'],evaluations);
  const selections=baseline.map(p=>({run_id:run,node_id:p.node_id,model:'Persistence',version:'fixed-v1',selection_index:p.origin_sample_index,
    selection_ms:p.origin_time_ms,target_index:p.origin_sample_index+1,target_ms:p.target_time_ms,
    forecast_origin_index:p.origin_sample_index,lead_steps:1,predicted_bytes:p.predicted_waiting_assignment_bytes}));
  csv('node_load_model_selection.csv',Object.keys(selections[0]),selections);
  files['node_load_model_events.jsonl']=events.sort((a,b)=>a.time_ms-b.time_ms).map(e=>JSON.stringify(e)).join('\n')+'\n';
  return files;
}
module.exports={fixture};
