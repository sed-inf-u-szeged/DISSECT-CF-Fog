'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const {readNodeLoad}=require('../node-load-results'),{csv}=require('../mobility-results');
const {migrationFixture}=require('./node-load-migration-fixture.cjs');
const parse=f=>readNodeLoad(n=>f[n],n=>n in f,csv,'synthetic-node-load',['mobility-west','mobility-east']);
function edit(f,name,change){const {head,rows}=csv(f[name],name);change(rows);f[name]=[head.join(','),...rows.map(r=>head.map(k=>r[k]).join(','))].join('\n')+'\n';}
test('schema 2 reads real baseline identities and all four trained families',()=>{
 const d=parse(migrationFixture());assert.deepEqual(d.baselines,['ARIMA','LR']);assert.deepEqual(d.models,['LSTM','TCN','GRU','PatchTST']);
 assert.equal(d.nodes[0].predictions.length,256);assert.equal(d.nodes[0].baseline.length,512);
 assert.ok(d.nodes[0].selections.every(p=>p.model==='LR'&&p.selection_ms>=191*60000));
 assert.ok(d.nodes[0].predictions.every(p=>p.persistence_bytes===undefined));
});
test('schema 2 preserves a no-forecast warm-up and partial first evaluation',()=>{
 const early=parse(migrationFixture(191));assert.equal(early.nodes[0].selections.length,0);assert.equal(early.nodes[0].baseline.length,0);
 const d=parse(migrationFixture(300));assert.equal(d.nodes[0].evaluations.length,52);assert.equal(d.nodes[0].versions.length,4);
});
test('schema 2 rejects incomplete baseline vectors and false fit provenance',()=>{
 let f=migrationFixture();edit(f,'node_load_baseline_predictions.csv',r=>r.splice(5,1));assert.throws(()=>parse(f),/incomplete baseline block/);
 f=migrationFixture();let fits=f['node_load_baseline_fits.jsonl'].split('\n').map(JSON.parse);fits[0].raw_values[0]=999;
 f['node_load_baseline_fits.jsonl']=fits.map(JSON.stringify).join('\n');assert.throws(()=>parse(f),/invalid baseline output/);
});
test('schema 2 rejects premature neural service and mixed Persistence exports',()=>{
 let f=migrationFixture();edit(f,'node_load_model_selection.csv',r=>{const s=r.find(p=>p.selection_index==='286');s.model='GRU';s.version='v0001';});assert.throws(()=>parse(f),/eligible/);
 f=migrationFixture();Object.assign(f,Object.fromEntries(Object.entries(require('./node-load-fixture.cjs').fixture()).filter(([k])=>['node_load_predictions.csv','node_load_evaluations.csv','node_load_prediction_summary.json'].includes(k))));
 assert.throws(()=>parse(f),/mixed legacy/);
});
