const test=require('node:test'),assert=require('node:assert/strict');
const fs=require('node:fs'),os=require('node:os'),path=require('node:path');
const {build,readMobilityRun,csv}=require('../mobility-results');
const {fixture,csv:writeCsv}=require('./mobility-fixture.cjs');
function changeRows(texts,name,update){const data=csv(texts[name],name);update(data.rows);texts[name]=writeCsv(data.rows,data.head);}
test('RandomWalk with only west-node origins loads both node geometries and all model records',()=>{
  const r=build(fixture(),'Synthetic fixture');
  assert.equal(r.nodes.length,2);assert.equal(r.learning.predictions.length,399);assert.equal(r.learning.selectedPredictions.length,165);
  assert.equal(r.learning.versions[0].quality_decision,'PASS');assert.equal(r.learning.versions[0].state,'DEGRADED');
  assert.equal(r.learning.versions[1].quality_decision,'WAITING_FOR_EVALUATION');assert.equal(r.learning.summary.lstm_selected_origins,6);
});
test('Nomadic with fixed speed and legacy baseline-only runs remain supported',()=>{
  assert.equal(build(fixture({random:false}),'Nomadic').props.speed_m_s,'4.1');
  assert.equal(build(fixture({random:false,learning:false}),'Legacy').learning,undefined);
});
test('a partial model result fails rather than silently showing the legacy baselines',()=>{
  const t=fixture();delete t['retry_predictions.csv'];assert.throws(()=>build(t,'broken'),/missing retry_predictions/);
});
test('mixed run identity and incorrect actual targets are rejected',()=>{
  const t=fixture();changeRows(t,'retry_evaluations.csv',rows=>rows[0].run_id='other');assert.throws(()=>build(t,'mixed'),/identity/);
  const u=fixture();changeRows(u,'retry_evaluations.csv',rows=>rows[0].actual_latitude_deg='48');assert.throws(()=>build(u,'wrong'),/actual target/);
});
test('duplicate candidate rows and different comparison versions are rejected',()=>{
  const t=fixture();changeRows(t,'retry_predictions.csv',rows=>rows.push(rows[0]));assert.throws(()=>build(t,'duplicate'),/duplicate/);
  const u=fixture();changeRows(u,'retry_predictions.csv',rows=>rows[0].model_version='v9999');assert.throws(()=>build(u,'version'),/candidate/);
});
test('error values and selected forecast ownership must match the saved predictions',()=>{
  const t=fixture();changeRows(t,'retry_evaluations.csv',rows=>rows[0].position_error_m='999');assert.throws(()=>build(t,'error'),/actual target/);
  const u=fixture();changeRows(u,'shared_selected_predictions.csv',rows=>rows.find(r=>r.model==='LSTM').predicted_longitude_deg='19');assert.throws(()=>build(u,'selection'),/selected LSTM differs/);
});
test('an incomplete version cannot be presented as passed',()=>{
  const t=fixture(),s=JSON.parse(t['shared-lifecycle.json']);s.versions[1].quality_decision='PASS';t['shared-lifecycle.json']=JSON.stringify(s);
  assert.throws(()=>build(t,'incomplete'),/incomplete version/);
});
test('a changed qualification score or event identity is rejected',()=>{
  const t=fixture(),s=JSON.parse(t['shared-lifecycle.json']);s.versions[0].quality.metrics.LSTM.rmse_m=500;t['shared-lifecycle.json']=JSON.stringify(s);
  assert.throws(()=>build(t,'score'),/qualification metrics/);
  const u=fixture();u['shared_lifecycle_events.jsonl']=u['shared_lifecycle_events.jsonl'].replace('"device_id":"0"','"device_id":"1"');
  assert.throws(()=>build(u,'identity'),/event identity/);
});
test('explicit folder reader loads completed results and identifies missing files',()=>{
  const dir=fs.mkdtempSync(path.join(os.tmpdir(),'mobility-ui-'));
  try{for(const [name,contents]of Object.entries(fixture()))fs.writeFileSync(path.join(dir,name),contents);
    assert.equal(readMobilityRun(dir).learning.run_id,'synthetic-ui-fixture');
    fs.unlinkSync(path.join(dir,'shared_selected_evaluations.csv'));assert.throws(()=>readMobilityRun(dir),/file missing/);
  }finally{fs.rmSync(dir,{recursive:true,force:true});}
});

test('explicit topology retains an unvisited node and rejects mixed identities or geometry',()=>{
  const t=fixture(), old=build(t,'old');
  t['run.properties']+='\nrun_id=synthetic-ui-fixture\ninfrastructure=two-node\n';
  const topology={schema_version:1,run_id:'synthetic-ui-fixture',infrastructure:'two-node',
    nodes:old.nodes.map(n=>({...n,role:'fog',serviceable:true})),
    links:[{source:'mobility-west',target:'mobility-east',kind:'peer',latency_ms:10}]};
  t['node_topology.json']=JSON.stringify(topology);
  assert.equal(build(t,'explicit').nodes.length,2);
  topology.run_id='other';t['node_topology.json']=JSON.stringify(topology);
  assert.throws(()=>build(t,'mixed'),/identity/);
  topology.run_id='synthetic-ui-fixture';topology.nodes.find(n=>n.name==='mobility-west').latitude+=.1;
  t['node_topology.json']=JSON.stringify(topology);assert.throws(()=>build(t,'geometry'),/geometry/);
  delete t['node_topology.json'];t['run.properties']=t['run.properties'].replace('infrastructure=two-node','infrastructure=hungary-cities');
  assert.throws(()=>build(t,'missing city geometry'),/node_topology/);
});
