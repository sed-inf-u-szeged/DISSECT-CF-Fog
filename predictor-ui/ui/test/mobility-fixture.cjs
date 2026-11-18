'use strict';
// Synthetic UI fixture, not a simulator run or evidence of model performance.
const { distance, metrics } = require('../mobility-learning-results');
function csv(rows, columns) {
  const fields = columns || Object.keys(rows[0]);
  const cell = v => /[",\r\n]/.test(String(v ?? '')) ? '"' + String(v ?? '').replaceAll('"', '""') + '"' : String(v ?? '');
  return fields.join(',') + '\n' + rows.map(r => fields.map(k => cell(r[k])).join(',')).join('\n') + '\n';
}
function fixture({ random = true, learning = true } = {}) {
  const n = 174, start = 193000, time = i => start + i * 60000;
  const id = 'synthetic-ui-fixture', feature = 'mobility:synthetic:0:position', texts = {};
  const observations = Array.from({length:n}, (_, i) => ({device_id:0,time_ms:time(i),latitude:47.73,
    longitude:18.27 + ((i * 7) % 300) * .00001,previous_node:i ? 'mobility-west' : 'NONE',current_node:'mobility-west'}));
  const coverage = observations.slice(1,-1).map(o => ({device_id:0,origin_ms:o.time_ms,target_ms:o.time_ms+60000,
    origin_node:'mobility-west',node_latitude:47.73,node_longitude:18.27,radius_m:1000,
    hold_latitude:o.latitude,hold_longitude:o.longitude,velocity_latitude:o.latitude,velocity_longitude:o.longitude+.00007}));
  const coverageEvaluations = coverage.map(p => {
    const o=observations[(p.target_ms-start)/60000];
    return {device_id:0,origin_ms:p.origin_ms,target_ms:p.target_ms,origin_node:p.origin_node,actual_node:o.current_node,
      actual_latitude:o.latitude,actual_longitude:o.longitude,distance_to_origin_node_m:distance([o.latitude,o.longitude],[47.73,18.27]),
      actual_exit:false,hold_exit:false,velocity_exit:false};
  });
  const keys=['device_upload_completed_bytes_derived','offload_sent_bytes','offload_received_bytes','net_received_counter_bytes',
    'assigned_to_compute_bytes','waiting_assignment_bytes','running_tasks','completed_tasks','incoming_offload_transfers',
    'connected_devices','device_generated_bytes','device_upload_completed_bytes_total','offload_in_flight_bytes_total','offload_event_sequence'];
  const frames=[...observations.map(o=>({time_ms:o.time_ms,sample_kind:'OBSERVATION'})),{time_ms:time(n-1)+1,sample_kind:'FINAL'}];
  const loads=frames.flatMap(f=>['mobility-west','mobility-east'].map(node=>({...f,node,...Object.fromEntries(keys.map(k=>[k,0]))})));
  const props={schema_version:2,scenario_name:'Synthetic UI test fixture',seed:42,interval_ms:60000,
    mobility_strategy:random?'random_walk':'nomadic',start_latitude_deg:47.73,start_longitude_deg:18.27,
    node_radius_m:1000,node_separation_m:1600,issued:coverage.length,matched:coverage.length,
    generated_bytes:0,upload_completed_bytes:0,assigned_bytes_final:0,offload_transfers:0,offload_bytes:0};
  Object.assign(props,random?{speed_min_m_s:2,speed_max_m_s:6,radius_m:800}:{speed_m_s:4.1,route_repeats:false});
  texts['run.properties']=Object.entries(props).map(([k,v])=>k+'='+v).join('\n');
  texts['observations.csv']=csv(observations);texts['predictions.csv']=csv(coverage);texts['evaluations.csv']=csv(coverageEvaluations);
  texts['node_load.csv']=csv(loads);texts['offload_events.csv']=csv([],['transfer_id','time_ms','bytes','event_sequence','event','source_node','destination_node']);
  texts['coverage_summary.csv']=csv(['HoldPosition','ConstantVelocity'].map(model=>({model,evaluated:coverage.length,tp:0,fn:0,fp:0,tn:coverage.length})));
  if(!learning)return texts;
  const samples=observations.map(o=>({run_id:id,device_id:0,time_ms:o.time_ms,latitude_deg:o.latitude,longitude_deg:o.longitude,current_node:o.current_node}));
  const predictions=[];
  for(let i=40;i<n-1;i++)for(const model of ['HoldPosition','ConstantVelocity','LSTM']){
    const o=observations[i],target=observations[i+1],previous=observations[i-1];
    const lon=model==='HoldPosition'?o.longitude:model==='ConstantVelocity'?2*o.longitude-previous.longitude:
      target.longitude+(i===109?.2:i>=110?.001:.0000001);
    predictions.push({model_version:i<110?'v0001':'v0002',model,device_id:0,origin_ms:time(i),target_ms:time(i+1),
      origin_node:o.current_node,predicted_latitude_deg:o.latitude,predicted_longitude_deg:lon});
  }
  const evaluate=p=>{
    const o=observations[(p.target_ms-start)/60000];
    return {...p,run_id:id,evaluated_at_sim_ms:p.target_ms,actual_latitude_deg:o.latitude,actual_longitude_deg:o.longitude,
      position_error_m:distance([p.predicted_latitude_deg,p.predicted_longitude_deg],[o.latitude,o.longitude])};
  };
  const evaluations=predictions.map(evaluate),selected=[];
  for(let i=8;i<n-1;i++){
    const active=i>=104&&i<110,o=observations[i];
    const p=active?predictions.find(p=>p.origin_ms===time(i)&&p.model==='LSTM'):
      {model:'HoldPosition',model_version:'',origin_ms:time(i),target_ms:time(i+1),predicted_latitude_deg:o.latitude,predicted_longitude_deg:o.longitude};
    selected.push({origin_ms:p.origin_ms,target_ms:p.target_ms,model:p.model,model_version:p.model_version,
      predictor_name:feature+(active?':LSTM:v0001':':HoldPosition'),predicted_latitude_deg:p.predicted_latitude_deg,predicted_longitude_deg:p.predicted_longitude_deg});
  }
  const selectedEvaluations=selected.map(p=>{
    const e=evaluate(p);return {origin_ms:e.origin_ms,target_ms:e.target_ms,model:e.model,model_version:e.model_version,
      actual_latitude_deg:e.actual_latitude_deg,actual_longitude_deg:e.actual_longitude_deg,position_error_m:e.position_error_m};
  });
  const quality={evaluated_origins:64,reference_baseline:'HoldPosition',metrics:Object.fromEntries(['HoldPosition','ConstantVelocity','LSTM'].map(model=>{
    const m=metrics(evaluations.filter(e=>e.model_version==='v0001'&&e.model===model).slice(0,64));
    return [model,{mean_error_m:m.mean_error_m,rmse_m:m.rmse_m}];
  }))};
  const versions=[{model_version:'v0001',predictor_name:feature+':LSTM:v0001',training_windows:32,training_cutoff_sim_ms:time(40),
    matched_origins:70,selected_origins:6,quality_decision:'PASS',decided_at_sim_ms:time(104),state:'DEGRADED',quality,training_reason:'INITIAL'},
    {model_version:'v0002',predictor_name:feature+':LSTM:v0002',training_windows:32,training_cutoff_sim_ms:time(110),matched_origins:63,
      selected_origins:0,quality_decision:'WAITING_FOR_EVALUATION',decided_at_sim_ms:-1,state:'EVALUATING',quality:null,training_reason:'DEGRADATION'}];
  const events=[];
  function event(i,event,extra={}){events.push({sequence:events.length+1,sim_time_ms:time(i),run_id:id,device_id:'0',event,...extra});}
  for(let i=0;i<n;i++){
    event(i,'OBSERVATION');
    if(i===104){event(i,'QUALITY_DECISION',{model_version:'v0001',decision:'PASS'});event(i,'PROMOTED',{model_version:'v0001'});}
    if(i>104&&i<=110)event(i,'MONITORED',{model_version:'v0001',state:i===110?'DEGRADED':'ACTIVE'});
    if(i===110)event(i,'FALLBACK',{model_version:'v0001'});
    if(i===40||i===110){const model_version=i===40?'v0001':'v0002';event(i,'TRAINING_STARTED',{model_version,training_windows:32});event(i,'TRAINING_COMPLETE',{model_version});}
    const p=selected.find(p=>p.origin_ms===time(i));if(p)event(i,'SELECTED',{model:p.model,model_version:p.model_version});
  }
  event(n-1,'COMPLETE');
  const summary={schema_version:1,status:'COMPLETE',run_id:id,device_id:'0',feature_name:feature,
    controller_integration:'SHARED_COORDINATOR_AND_MANAGER',connection_control:false,observations:n,expected_observations:n,
    pending_forecasts:0,selected_origins:selected.length,selected_matched:selected.length,lstm_selected_origins:6,
    promotions:1,fallbacks:1,active_lstm:false,degradation_retrainings:1,versions};
  texts['mobility_samples.csv']=csv(samples);texts['retry_predictions.csv']=csv(predictions);texts['retry_evaluations.csv']=csv(evaluations);
  texts['shared_selected_predictions.csv']=csv(selected);texts['shared_selected_evaluations.csv']=csv(selectedEvaluations);
  texts['shared_lifecycle_events.jsonl']=events.map(e=>JSON.stringify(e)).join('\n')+'\n';texts['shared-lifecycle.json']=JSON.stringify(summary);
  return texts;
}
module.exports={fixture,csv};
