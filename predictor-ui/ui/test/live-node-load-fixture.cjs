'use strict';
// Deterministic transport fixture. Forecasts deliberately miss the constant actual.
function liveRecords({minutes=480,nodes=['west','east'],models=['LSTM','TCN']}={}) {
  const rows=[];let sequence=0;
  const push=(type,other)=>rows.push({type,sequence:++sequence,...other});
  const record=(kind,data)=>push('NODE_LOAD',{kind,run_id:'live-test',time_ms:data.time_ms,data});
  push('META',{schema_version:2,run_id:'live-test',device_count:1,expected_observations:minutes+1,
    nodes:nodes.map(name=>({name,latitude:0,longitude:0,radius:1000}))});
  record('META',{run_id:'live-test',time_ms:0,interval_ms:60000,node_ids:nodes,models,profile:'candidate',
    policy:{matched_targets:64,minimum_within_tolerance_percent:80,relative_tolerance:.2,absolute_tolerance_bytes:.1}});
  for(let i=0;i<=minutes;i++) for(const [j,node_id] of nodes.entries()) {
    const time_ms=i*60000,value=100+j*100;
    record('SAMPLE',{node_id,index:i,time_ms,value,running_tasks:1,completed_tasks:i});
    if(i) record('EVALUATION',{node_id,model:'Persistence',version:'fixed-v1',time_ms,origin_index:i-1,target_index:i,actual_bytes:value});
    record('VECTOR',{node_id,model:'Persistence',version:'fixed-v1',time_ms,origin_index:i,values:[value],inference_vector_ms:.001});
    for(const model of models) for(const [v,origin] of [286,414].entries()) {
      const version='v000'+(v+1),base={node_id,model,version,time_ms};
      const event=event=>record('EVENT',{...base,event,trained_at_ms:origin*60000,training_windows:32+v*64,training_seconds:event==='TRAINING'?0:.1});
      if(i===origin) {
        event('TRAINING');event('TRAINED');
        record('VECTOR',{...base,origin_index:i,values:Array(64).fill(42),persistence_bytes:value,inference_vector_ms:2});
      } else if(i>origin && i<=origin+64) {
        record('EVALUATION',{...base,origin_index:origin,target_index:i,actual_bytes:value});
        if(i===origin+64) event('REJECTED');
      }
    }
    if(models.length) record('SELECTION',{node_id,model:'Persistence',version:'fixed-v1',time_ms,origin_index:i,target_index:i+1});
  }
  return rows;
}
module.exports={liveRecords};
