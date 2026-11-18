'use strict';
const test=require('node:test'), assert=require('node:assert/strict');
const fs=require('fs'),path=require('path'),os=require('os');
const {readNodeLoad}=require('../node-load-results'),{csv,readMobilityRun}=require('../mobility-results');
const {fixture}=require('./node-load-fixture.cjs');
const parse=(files,run='synthetic-node-load')=>readNodeLoad(n=>files[n],n=>n in files,csv,run,['mobility-west','mobility-east']);
function editRows(files,name,fn){const {head,rows}=csv(files[name],name);fn(rows);files[name]=[head.join(','),...rows.map(r=>head.map(k=>r[k]).join(','))].join('\n')+'\n';}
test('loads shared samples, both model families and exact 64-point vectors',()=>{
 const data=parse(fixture());assert.equal(data.nodes.length,2);assert.equal(data.nodes[0].predictions.length,128);
 assert.equal(data.nodes[0].evaluations.length,128);assert.equal(data.nodes[1].baselineEvaluations.length,350);
 assert.equal(data.nodes[0].versions[0].training_windows,32);assert.equal(data.nodes[0].versions[0].final_state,undefined);
});
test('old exports, sampling-only exports and short untrained runs remain supported',()=>{
 assert.equal(parse({}),undefined);let f=fixture();for(const k of Object.keys(f))if(!['node_load_samples.csv','node_load_sampling.json'].includes(k))delete f[k];
 assert.equal(parse(f).hasBaseline,false);assert.deepEqual(parse(f).models,[]);
 const short=parse(fixture('synthetic-node-load',10));assert.equal(short.nodes[0].versions.length,0);assert.equal(short.nodes[0].selections.length,10);
});
test('partial groups, mixed run IDs and unknown nodes are rejected',()=>{
 let f=fixture();delete f['node_load_model_selection.csv'];assert.throws(()=>parse(f),/incomplete/);
 f=fixture();editRows(f,'node_load_samples.csv',r=>r[0].run_id='other');assert.throws(()=>parse(f),/mixed run/);
 f=fixture();editRows(f,'node_load_samples.csv',r=>r[0].node_id='other');assert.throws(()=>parse(f),/unknown node/);
});
test('duplicate or missing grid points and nonfinite values are rejected',()=>{
 let f=fixture();editRows(f,'node_load_samples.csv',r=>r.push(r[0]));assert.throws(()=>parse(f),/duplicate/);
 f=fixture();editRows(f,'node_load_samples.csv',r=>r.splice(4,1));assert.throws(()=>parse(f),/missing|discontinuous/);
 f=fixture();editRows(f,'node_load_model_predictions.csv',r=>r[0].predicted_bytes='NaN');assert.throws(()=>parse(f),/invalid number/);
});
test('forecast time, baseline pairing and actual target errors are validated',()=>{
 let f=fixture();editRows(f,'node_load_model_predictions.csv',r=>r[0].origin_ms='0');assert.throws(()=>parse(f),/origin/);
 f=fixture();editRows(f,'node_load_model_predictions.csv',r=>r[0].persistence_bytes='999');assert.throws(()=>parse(f),/paired baseline/);
 f=fixture();editRows(f,'node_load_model_evaluations.csv',r=>r[0].actual_bytes='999');assert.throws(()=>parse(f),/evaluation/);
 f=fixture();editRows(f,'node_load_model_evaluations.csv',r=>r[0].absolute_error_bytes='999');assert.throws(()=>parse(f),/evaluation/);
});
test('pending targets remain pending, without fabricated zero actuals',()=>{
 const data=parse(fixture('synthetic-node-load',300));assert.equal(data.nodes[0].predictions.length,128);assert.equal(data.nodes[0].evaluations.length,26);
 const f=fixture('synthetic-node-load',300);editRows(f,'node_load_model_evaluations.csv',r=>{const p={...r[0],target_index:'350',target_ms:String(350*60000),actual_bytes:'0'};r.push(p);});
 assert.throws(()=>parse(f),/evaluation/);
});
test('unqualified selections and premature qualification events are rejected',()=>{
 let f=fixture();editRows(f,'node_load_model_selection.csv',r=>{let s=r.find(x=>x.selection_index==='286');s.model='LSTM';s.version='v0001';s.predicted_bytes='100';});assert.throws(()=>parse(f),/eligible/);
 f=fixture();f['node_load_model_events.jsonl']=f['node_load_model_events.jsonl'].split('\n').filter(Boolean).map(l=>{const e=JSON.parse(l);if(e.event==='REJECTED'){e.sample_index=287;e.time_ms=287*60000;}return e;}).sort((a,b)=>a.time_ms-b.time_ms).map(JSON.stringify).join('\n');assert.throws(()=>parse(f),/64 actual targets/);
});
test('multi-device reader retains node data once and rejects symlink escapes',t=>{
 const root=fs.mkdtempSync(path.join(os.tmpdir(),'node-load-reader-'));t.after(()=>fs.rmSync(root,{recursive:true,force:true}));
 const manifest=require('./mobility-multi-fixture.cjs').writeFixture(root,2);
 const f=fixture(manifest.run_id,10);for(const [name,text] of Object.entries(f))fs.writeFileSync(path.join(root,name),text);
 const data=readMobilityRun(root);assert.equal(data.nodeLoad.nodes.length,2);assert.ok(data.devices.every(d=>!d.data.nodeLoad));
 const outside=path.join(root,'../outside-'+path.basename(root)+'.json');fs.writeFileSync(outside,f['node_load_sampling.json']);t.after(()=>fs.rmSync(outside,{force:true}));
 fs.unlinkSync(path.join(root,'node_load_sampling.json'));
 try{fs.symlinkSync(outside,path.join(root,'node_load_sampling.json'));}catch(error){if(process.platform==='win32'&&error.code==='EPERM'){t.diagnostic('Symlink escape case requires Windows Developer Mode; other checks ran.');return;}throw error;}
 assert.throws(()=>readMobilityRun(root),/leaves/);
});
