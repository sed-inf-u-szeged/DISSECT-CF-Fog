'use strict';
const learningResults = require('./mobility-learning-results');
const comparisonResults = require('./mobility-comparison-results');
const baselineResults = require('./mobility-baseline-results');
const required=['run.properties','observations.csv','predictions.csv','evaluations.csv','node_load.csv','offload_events.csv','coverage_summary.csv'];
function fail(s){throw new Error(s)}
function csv(text,name){
 text=text.replace(/^\uFEFF/,'');let rows=[],row=[],cell='',quoted=false;
 for(let i=0;i<text.length;i++){const c=text[i];if(quoted){if(c==='"'){if(text[i+1]==='"'){cell+='"';i++;}else quoted=false;}else cell+=c;}else if(c==='"'){if(cell)fail(name+': invalid CSV quotes');quoted=true;}else if(c===','){row.push(cell);cell='';}else if(c==='\n'||c==='\r'){if(c==='\r'&&text[i+1]==='\n')i++;row.push(cell);if(row.some(v=>v!==''))rows.push(row);row=[];cell='';}else cell+=c;}
 if(quoted)fail(name+': unterminated CSV quotes');if(cell||row.length){row.push(cell);rows.push(row);}if(!rows.length)fail(name+' is empty');const head=rows.shift();if(new Set(head).size!==head.length)fail(name+': duplicate column names');return {head,rows:rows.map((r,i)=>{if(r.length!==head.length)fail(name+' row '+(i+2)+' has an incorrect column count');return Object.fromEntries(head.map((k,j)=>[k,r[j]]));})};
}
function table(text,name,nums,strings,bools=[]){const t=csv(text,name);for(const k of [...nums,...strings,...bools])if(!t.head.includes(k))fail(name+' missing column: '+k);for(const r of t.rows){for(const k of nums){if(!r[k].trim()||!Number.isFinite(Number(r[k])))fail(name+'：'+k+' must be a finite number');r[k]=Number(r[k]);}for(const k of strings){if(!r[k])fail(name+'：'+k+' must not be empty');}for(const k of bools){if(!['true','false'].includes(r[k]))fail(name+'：'+k+' must be true or false');r[k]=r[k]==='true';}}return t.rows;}
function unique(rows,key,label){const s=new Set();for(const r of rows){const k=key(r);if(s.has(k))fail(label+' contains a duplicate record: '+k);s.add(k);}}
function close(a,b){return Math.abs(a-b)<=1e-6*Math.max(1,Math.abs(a),Math.abs(b));}
function build(texts,label,{sharedFrames=false}={}){
 const props={};for(const l of texts['run.properties'].replace(/^\uFEFF/,'').split(/\r?\n/)){if(!l.trim()||/^\s*[#!]/.test(l))continue;const i=l.indexOf('=');if(i>0)props[l.slice(0,i).trim()]=l.slice(i+1).trim();}
 if(props.schema_version!=='2')fail('Mobility UI requires schema_version=2. Export with the v2 scenario.');
 const observations=table(texts['observations.csv'],'observations.csv',['device_id','time_ms','latitude','longitude'],['previous_node','current_node']);
 const predictions=table(texts['predictions.csv'],'predictions.csv',['device_id','origin_ms','target_ms','node_latitude','node_longitude','radius_m','hold_latitude','hold_longitude','velocity_latitude','velocity_longitude'],['origin_node']);
 const evaluations=table(texts['evaluations.csv'],'evaluations.csv',['device_id','origin_ms','target_ms','actual_latitude','actual_longitude','distance_to_origin_node_m'],['origin_node','actual_node'],['actual_exit','hold_exit','velocity_exit']);
 const keys=['time_ms','device_upload_completed_bytes_derived','offload_sent_bytes','offload_received_bytes','net_received_counter_bytes','assigned_to_compute_bytes','waiting_assignment_bytes','running_tasks','completed_tasks','incoming_offload_transfers','connected_devices','device_generated_bytes','device_upload_completed_bytes_total','offload_in_flight_bytes_total','offload_event_sequence'];
 const loads=table(texts['node_load.csv'],'node_load.csv',keys,['sample_kind','node']);
 const transfers=table(texts['offload_events.csv'],'offload_events.csv',['transfer_id','time_ms','bytes','event_sequence'],['event','source_node','destination_node']);
 const summary=table(texts['coverage_summary.csv'],'coverage_summary.csv',['evaluated','tp','fn','fp','tn'],['model']);
 if(observations.length<2||!predictions.length||!loads.length)fail('Insufficient data: need two observations, coverage forecasts and node snapshots.');
 const speedKeys=props.mobility_strategy==='random_walk'?['speed_min_m_s','speed_max_m_s','radius_m']:['speed_m_s'];
 for(const k of ['interval_ms',...speedKeys])if(props[k]===undefined||!Number.isFinite(Number(props[k]))||Number(props[k])<0||(k!=='speed_min_m_s'&&Number(props[k])===0))fail('Missing or invalid run parameter: '+k);
 if(props.mobility_strategy==='random_walk'&&Number(props.speed_max_m_s)<Number(props.speed_min_m_s))fail('RandomWalk speed range is reversed');
 const device=observations[0].device_id;if([...observations,...predictions,...evaluations].some(r=>r.device_id!==device))fail('This view supports one device. Multiple devices or mixed runs detected.');
 observations.sort((a,b)=>a.time_ms-b.time_ms);predictions.sort((a,b)=>a.origin_ms-b.origin_ms);evaluations.sort((a,b)=>a.target_ms-b.target_ms);transfers.sort((a,b)=>a.event_sequence-b.event_sequence);
 unique(observations,r=>r.time_ms,'Observations');unique(predictions,r=>r.origin_ms,'Predictions');unique(evaluations,r=>r.origin_ms,'Evaluations');unique(loads,r=>r.time_ms+'|'+r.sample_kind+'|'+r.node,'Node snapshots');unique(transfers,r=>r.event_sequence,'Transfer events');
 const nodes=[];
 if(texts['node_topology.json']) {
   const topology=JSON.parse(texts['node_topology.json']);
   if(topology.schema_version!==1||topology.run_id!==props.run_id||topology.infrastructure!==props.infrastructure
      ||!Array.isArray(topology.nodes)||!topology.nodes.length||!Array.isArray(topology.links))fail('Invalid node topology identity');
   for(const n of topology.nodes) {
     if(!n||typeof n.name!=='string'||!n.name||nodes.some(x=>x.name===n.name)
       ||![n.latitude,n.longitude,n.radius].every(x=>typeof x==='number'&&Number.isFinite(x))
       ||Math.abs(n.latitude)>90||Math.abs(n.longitude)>180||!['fog','cloud'].includes(n.role)
       ||n.serviceable!==(n.role==='fog')||(n.serviceable?n.radius<=0:n.radius!==0))fail('Invalid topology node geometry or role');
     nodes.push({name:n.name,latitude:n.latitude,longitude:n.longitude,radius:n.radius,role:n.role,serviceable:n.serviceable});
   }
   const names=new Set(nodes.map(n=>n.name)),links=new Set();
   for(const l of topology.links) {
     const key=l.kind+'|'+l.source+'|'+l.target;
     if(!names.has(l.source)||!names.has(l.target)||l.source===l.target||!['parent','peer'].includes(l.kind)
       ||!Number.isSafeInteger(l.latency_ms)||l.latency_ms<0||links.has(key))fail('Invalid topology link');
     links.add(key);
   }
   if(props.infrastructure==='hungary-cities'&&(nodes.length!==10||nodes.filter(n=>n.role==='cloud').length!==1
       ||!nodes.some(n=>n.name==='Budapest_cloud'&&n.role==='cloud')))fail('City topology requires nine fog nodes and Budapest cloud');
 } else if(props.infrastructure==='hungary-cities')fail('City results require node_topology.json');
 for(const p of predictions){const n=nodes.find(n=>n.name===p.origin_node);if(n){if(!close(n.latitude,p.node_latitude)||!close(n.longitude,p.node_longitude)||n.radius!==p.radius_m||n.serviceable===false)fail('Inconsistent node geometry');}else {
   if(texts['node_topology.json'])fail('Forecast node is absent from topology');
   nodes.push({name:p.origin_node,latitude:p.node_latitude,longitude:p.node_longitude,radius:p.radius_m});
 }}
 // A node may exist without ever being the origin of a coverage forecast.
 const nodeNames=[...new Set(loads.map(r=>r.node))];
 if(nodeNames.some(name=>!nodes.some(n=>n.name===name))){
   if(texts['node_topology.json'])fail('Snapshot node is absent from topology');
   for(const k of ['start_latitude_deg','start_longitude_deg','node_radius_m','node_separation_m'])if(props[k]===undefined||!Number.isFinite(Number(props[k])))fail('Missing node geometry: '+k);
   const lat=Number(props.start_latitude_deg),lon=Number(props.start_longitude_deg),radius=Number(props.node_radius_m),separation=Number(props.node_separation_m),r=Math.PI/180;
   if(Math.abs(lat)>90||Math.abs(lon)>180||radius<=0||separation<=0)fail('Invalid node geometry');
   const q=separation/6378137,a=lat*r,b=lon*r,eastLat=Math.asin(Math.sin(a)*Math.cos(q));
   const eastLon=b+Math.atan2(Math.sin(q)*Math.cos(a),Math.cos(q)-Math.sin(a)*Math.sin(eastLat));
   const geometry={'mobility-west':{name:'mobility-west',latitude:lat,longitude:lon,radius},'mobility-east':{name:'mobility-east',latitude:eastLat/r,longitude:eastLon/r,radius}};
   for(const name of nodeNames){const expected=geometry[name];if(!expected)fail('Unknown node geometry: '+name);const present=nodes.find(n=>n.name===name);if(present){if(learningResults.distance([present.latitude,present.longitude],[expected.latitude,expected.longitude])>0.001||!close(present.radius,expected.radius))fail('Node metadata differs from forecasts');}else nodes.push(expected);}
 }
 if(!nodes.length||nodes.length!==nodeNames.length||loads.some(r=>!nodes.some(n=>n.name===r.node)))fail('Incomplete node geometry');
 nodes.sort((a,b)=>a.longitude-b.longitude||a.name.localeCompare(b.name));const known=n=>n==='NONE'||nodes.some(x=>x.name===n);
 for(const o of observations){if(o.time_ms<0||Math.abs(o.latitude)>90||Math.abs(o.longitude)>180||!known(o.current_node)||!known(o.previous_node))fail('Invalid observation time, coordinates or node');}
 for(let i=1;i<observations.length;i++)if(observations[i].previous_node!==observations[i-1].current_node)fail('Discontinuous connection state');
 const obs=new Map(observations.map(o=>[o.time_ms,o])),pred=new Map(predictions.map(p=>[p.origin_ms,p]));
 for(const p of predictions){if(!obs.has(p.origin_ms)||p.target_ms<=p.origin_ms||p.radius_m<=0||obs.get(p.origin_ms).current_node!==p.origin_node)fail('Forecast origin, node or horizon is inconsistent');for(const prefix of ['node','hold','velocity'])if(Math.abs(p[prefix+'_latitude'])>90||Math.abs(p[prefix+'_longitude'])>180)fail('Invalid forecast coordinates');}
 for(const e of evaluations){const p=pred.get(e.origin_ms),o=obs.get(e.target_ms);if(!p||!o||e.target_ms!==p.target_ms||e.origin_node!==p.origin_node||e.actual_node!==o.current_node||!close(e.actual_latitude,o.latitude)||!close(e.actual_longitude,o.longitude))fail('Evaluation does not match its forecast or target observation. Check for mixed runs.');}
 const groups=new Map();for(const r of loads){if(!['OBSERVATION','FINAL'].includes(r.sample_kind))fail('Unknown snapshot kind: '+r.sample_kind);for(const k of keys)if(r[k]<0||!Number.isSafeInteger(r[k]))fail('Node counters must be non-negative integers: '+k);const k=r.time_ms+'|'+r.sample_kind;if(!groups.has(k))groups.set(k,{time_ms:r.time_ms,sample_kind:r.sample_kind,nodes:[]});groups.get(k).nodes.push(r);}
 const frames=[...groups.values()].sort((a,b)=>a.time_ms-b.time_ms||(a.sample_kind==='FINAL'?1:-1));const finals=frames.filter(f=>f.sample_kind==='FINAL');if(finals.length!==1||frames.at(-1)!==finals[0]||frames.at(-1).time_ms<observations.at(-1).time_ms)fail('A complete final snapshot must be the last record');
 for(const f of frames){if(f.nodes.length!==nodes.length)fail('Missing node snapshot');if(!sharedFrames&&f.sample_kind==='OBSERVATION'&&!obs.has(f.time_ms))fail('Node snapshot has no matching observation');const a=f.nodes[0];for(const r of f.nodes){for(const k of ['device_generated_bytes','device_upload_completed_bytes_total','offload_in_flight_bytes_total','offload_event_sequence'])if(r[k]!==a[k])fail('Inconsistent global counters at one timestamp: '+k);if(r.net_received_counter_bytes+r.offload_sent_bytes-r.offload_received_bytes!==r.device_upload_completed_bytes_derived||r.net_received_counter_bytes-r.assigned_to_compute_bytes!==r.waiting_assignment_bytes)fail('Node byte accounting failed');}}
 const observedFrames=new Set(frames.filter(f=>f.sample_kind==='OBSERVATION').map(f=>f.time_ms));
 if(observations.some(o=>!observedFrames.has(o.time_ms))||(!sharedFrames&&observedFrames.size!==observations.length))fail('Observation and snapshot counts differ');
 const active=new Map();let starts=0,sent=0;for(const e of transfers){if(!Number.isSafeInteger(e.event_sequence)||e.event_sequence<=0||!Number.isSafeInteger(e.bytes)||e.bytes<0||!['START','COMPLETE'].includes(e.event)||!nodes.some(n=>n.name===e.source_node)||!nodes.some(n=>n.name===e.destination_node))fail('Invalid transfer event');if(e.event==='START'){if(active.has(e.transfer_id))fail('Duplicate transfer ID');active.set(e.transfer_id,e);starts++;sent+=e.bytes;}else{const s=active.get(e.transfer_id);if(!s||s.done||s.bytes!==e.bytes||s.source_node!==e.source_node||s.destination_node!==e.destination_node||e.time_ms<s.time_ms)fail('Transfer start/completion mismatch');s.done=true;}}
 if([...active.values()].some(s=>!s.done))fail('Unfinished transfers. This view requires a completed run.');
 for(const f of frames){const seq=f.nodes[0].offload_event_sequence;const events=transfers.filter(e=>e.event_sequence<=seq);if(seq!==(events.at(-1)?.event_sequence??0)||events.some(e=>e.time_ms>f.time_ms))fail('Snapshot event sequence or time mismatch');for(const n of f.nodes){for(const [field,event,direction] of [['offload_sent_bytes','START','source_node'],['offload_received_bytes','COMPLETE','destination_node']])if(n[field]!==events.filter(e=>e.event===event&&e[direction]===n.node).reduce((sum,e)=>sum+e.bytes,0))fail('Transfer events do not match node counters');}}
 const end=frames.at(-1),global=end.nodes[0],total=k=>end.nodes.reduce((s,n)=>s+n[k],0);
 if(global.device_generated_bytes!==global.device_upload_completed_bytes_total||total('device_upload_completed_bytes_derived')!==global.device_upload_completed_bytes_total||total('assigned_to_compute_bytes')!==global.device_upload_completed_bytes_total||total('waiting_assignment_bytes')||total('running_tasks')||global.offload_in_flight_bytes_total||total('incoming_offload_transfers')||total('offload_sent_bytes')!==sent||total('offload_received_bytes')!==sent)fail('Final accounting is not balanced');
 for(const [k,v] of Object.entries({issued:predictions.length,matched:evaluations.length,generated_bytes:global.device_generated_bytes,upload_completed_bytes:global.device_upload_completed_bytes_total,assigned_bytes_final:total('assigned_to_compute_bytes'),offload_transfers:starts,offload_bytes:sent})){if(props[k]===undefined||Number(props[k])!==v)fail('run.properties does not match CSV: '+k);}
 for(const [model,key] of [['HoldPosition','hold_exit'],['ConstantVelocity','velocity_exit']]){const s=summary.filter(s=>s.model===model);if(s.length!==1)fail('Missing or duplicate summary baseline: '+model);const c={evaluated:evaluations.length,tp:0,fn:0,fp:0,tn:0};for(const e of evaluations)c[e.actual_exit?(e[key]?'tp':'fn'):(e[key]?'fp':'tn')]++;for(const k of Object.keys(c))if(s[0][k]!==c[k])fail('Summary does not match evaluations: '+model);}
 const result={observations,predictions,evaluations,frames,transfers,nodes,summary,props,label};
 result.learning=learningResults.buildLearning(texts,result,table);
 result.comparison=comparisonResults.buildComparison(texts,result,table);
 result.baselines=baselineResults.buildBaselines(texts,result,table);
 return result;
}

function readMobilityRun(directory) {
  const fs = require('fs');
  const path = require('path');
  const root = path.resolve(directory);
  if (fs.existsSync(path.join(root, 'multi-device.json'))) {
    return require('./mobility-multi-results').readMultiMobilityRun(root, build, required, learningResults, csv);
  }
  let bytes = 0;
  const texts = {};
  const hasLearning = learningResults.markers.some(name => fs.existsSync(path.join(root, name)));
  const hasBaselines = baselineResults.markers.some(name => fs.existsSync(path.join(root,name)));
  const hasComparison = comparisonResults.files.some(name => fs.existsSync(path.join(root,name)));
  for (const name of [...required, ...(fs.existsSync(path.join(root,'node_topology.json'))?['node_topology.json']:[]), ...(hasLearning ? learningResults.files : []), ...(hasComparison ? comparisonResults.files : []), ...(hasBaselines ? baselineResults.files : [])]) {
    const file = path.join(root, name);
    if (!fs.existsSync(file) || !fs.statSync(file).isFile()) {
      throw new Error('Mobility result file missing: ' + file);
    }
    bytes += fs.statSync(file).size;
    if (bytes > 50 * 1024 * 1024) throw new Error('Mobility result exceeds 50 MB.');
    texts[name] = fs.readFileSync(file, 'utf8');
  }
  const result = build(texts, path.basename(path.dirname(root)) + '/' + path.basename(root));
  const canonical = fs.realpathSync(root);
  result.nodeLoad = require('./node-load-results').readNodeLoad(name => {
    const file = path.join(root, name);
    if (!fs.statSync(file).isFile() || !fs.realpathSync(file).startsWith(canonical + path.sep)) throw new Error('Node-load file leaves selected run');
    bytes += fs.statSync(file).size;
    if (bytes > 50 * 1024 * 1024) throw new Error('Mobility result exceeds 50 MB.');
    return fs.readFileSync(file, 'utf8');
  }, name => fs.existsSync(path.join(root, name)), csv, result.learning?.run_id ?? result.props.run_id ?? path.basename(root), result.nodes.map(n => n.name));
  return result;
}
module.exports = { readMobilityRun, build, csv };
