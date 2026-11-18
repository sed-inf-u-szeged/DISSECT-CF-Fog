'use strict';
const {test,after}=require('node:test'),assert=require('node:assert/strict');
const fs=require('node:fs'),os=require('node:os'),path=require('node:path');
const {LiveJournal,MobilityJob}=require('../mobility-job');
const {liveRecords}=require('./live-node-load-fixture.cjs');
const temp=fs.mkdtempSync(path.join(os.tmpdir(),'load-live-test-'));
const compiled=path.join(temp,'live.cjs');
require('esbuild').buildSync({stdin:{contents:"export * from './src/app/shared/node-load-live'; export * from './src/app/shared/node-load-comparison';",resolveDir:path.resolve(__dirname,'..')},outfile:compiled,bundle:true,platform:'node',format:'cjs'});
const {LiveNodeLoad,buildLoadComparison}=require(compiled);
after(()=>fs.rmSync(temp,{recursive:true,force:true}));
function replay(rows) {const store=new LiveNodeLoad();for(const r of rows) if(r.type==='NODE_LOAD')store.accept(r);store.publish();return store;}

test('live records reconstruct baseline and learned data; mature targets only; rewind is stable',()=>{
  const rows=liveRecords(),prefix=rows.filter(r=>r.type==='META'||r.time_ms<=286*60000),s=replay(prefix);
  const n=s.data.nodes[0],first=buildLoadComparison(s.data,n,286*60000);
  assert.equal(first.traces[1].future.length,64);assert.equal(first.traces[1].forecastMetrics.count,0);
  assert.equal(first.traces[1].state,'Evaluating');assert.equal(first.lastActual.time_ms,286*60000);
  assert.equal(s.training,undefined);
  for(const r of rows) if(r.type==='NODE_LOAD')s.accept(r); // snapshot overlap
  s.publish();
  assert.deepEqual(buildLoadComparison(s.data,n,286*60000),first);
  const second=buildLoadComparison(s.data,n,414*60000);
  assert.equal(second.traces[1].version,'v0002');assert.equal(second.traces[1].testVersion,'v0001');
  assert.equal(second.traces[1].testMetrics.count,64);assert.equal(second.traces[1].forecastMetrics.count,0);
  assert.equal(s.data.nodes[1].samples[300].value,200);assert.equal(n.samples[300].value,100);
  const end=buildLoadComparison(s.data,n,500*60000,-1,true);
  assert.equal(end.lastActual.time_ms,480*60000);assert.equal(n.evaluations.length,256);
  assert.equal(n.baselineEvaluations.length,480);assert.equal(n.baseline.length,481);
});

test('training is observable before a complete vector arrives and partial vectors are rejected atomically',()=>{
  const rows=liveRecords({minutes:286,nodes:['west']}),start=rows.findIndex(r=>r.kind==='EVENT' && r.data.event==='TRAINING');
  const s=replay(rows.slice(0,start+1));assert.equal(s.training.model,'LSTM');assert.equal(s.data.nodes[0].predictions.length,0);
  s.accept(rows[start+1]);const vector=rows[start+2];
  assert.throws(()=>s.accept({...vector,data:{...vector.data,values:[42]}}),/incomplete/);
  assert.equal(s.data.nodes[0].predictions.length,0);s.accept(vector);assert.equal(s.data.nodes[0].predictions.length,64);
});

test('rejects unobserved evaluations, mixed runs, missing samples and duplicate vectors',()=>{
  const rows=liveRecords({minutes:1,nodes:['west'],models:[]}),s=replay(rows.slice(0,4));
  const sample=rows.find(r=>r.kind==='SAMPLE' && r.time_ms===60000);
  const evaluation=rows.find(r=>r.kind==='EVALUATION');
  assert.throws(()=>s.accept(evaluation),/invalid.*evaluation/);
  assert.throws(()=>s.accept({...sample,run_id:'different'}),/mixed/);
  assert.throws(()=>s.accept({...sample,data:{...sample.data,index:2}}),/discontinuous/);
  assert.throws(()=>s.accept({...rows[3],sequence:100}),/duplicate/);
  s.accept(sample);s.accept(evaluation);assert.equal(s.data.nodes[0].baselineEvaluations.length,1);
});

test('node statuses use the selected time and never add a drain sample',()=>{
  const s=replay(liveRecords({minutes:1,models:[]}));
  assert.equal(s.statusAt(0)[0].completed_tasks,0);
  assert.equal(s.statusAt(10*60000)[0].time_ms,60000);
  assert.equal(s.data.nodes[0].samples.length,2);
});

test('live journal preserves full history on reload and checks it against final exports',()=>{
  const rows=liveRecords(),journal=new LiveJournal();for(const r of rows)journal.accept(r);
  const s=replay(journal.snapshot());journal.verifyNodeLoad(s.data);
  const again=replay(journal.snapshot());assert.deepEqual(again.data,s.data);
  s.data.nodes[0].predictions[0].predicted_bytes++;
  assert.throws(()=>journal.verifyNodeLoad(s.data),/issued vectors/);
});

test('journal rejects cross-run events, sequence gaps and incomplete vectors',()=>{
  const rows=liveRecords({minutes:0}),journal=new LiveJournal();journal.accept(rows[0]);journal.accept(rows[1]);
  assert.throws(()=>journal.accept({...rows[2],run_id:'other'}),/Invalid/);
  assert.throws(()=>journal.accept({...rows[2],sequence:4}),/sequence/);
  journal.accept(rows[2]);assert.throws(()=>journal.accept({...rows[3],data:{...rows[3].data,values:[]}}),/Incomplete/);
});

test('journal buffers split records and detects truncation',async()=>{
  const file=path.join(temp,'split.jsonl'),rows=liveRecords({minutes:0,models:[]}),text=rows.map(r=>JSON.stringify(r)+'\n').join('');
  fs.writeFileSync(file,text.slice(0,50));const journal=new LiveJournal();assert.deepEqual(await journal.read(file),[]);
  fs.appendFileSync(file,text.slice(50));assert.equal((await journal.read(file)).length,rows.length);
  fs.writeFileSync(file,'');await assert.rejects(journal.read(file),/truncated/);
});

test('job drains all queued batches before validating completion and preserves terminal status',async()=>{
  const session=path.join(temp,'finish');fs.mkdirSync(session);
  const rows=liveRecords({minutes:1440}),file=path.join(session,'live.jsonl');
  fs.writeFileSync(file,rows.map(r=>JSON.stringify(r)+'\n').join(''));
  fs.writeFileSync(path.join(session,'progress.json'),JSON.stringify({stage:'RESULT_READY',output:'saved'}));
  assert.ok(fs.statSync(file).size>256*1024);
  let received=0,validated=false;
  const job=new MobilityJob({root:temp,onStatus:()=>{},onLive:b=>received+=b.events.length,onComplete:async(_,guard,verify)=>{
    assert.ok(guard());assert.equal(received,rows.length);verify(replay(rows).data);validated=true;
  }});
  job.state={id:'job',session,active:true,stage:'RUNNING'};await job.finish('job',0);
  assert.equal(job.state.stage,'COMPLETE');assert.equal(validated,true);
});

test('a truncated successful stream fails; cancellation retains partial records without claiming completion',async()=>{
  for(const cancelled of [false,true]) {
    const session=path.join(temp,'partial-'+cancelled);fs.mkdirSync(session);
    fs.writeFileSync(path.join(session,'live.jsonl'),JSON.stringify(liveRecords()[0])+'\n{"type":');
    let completed=false;const job=new MobilityJob({root:temp,onStatus:()=>{},onLive:()=>{},onComplete:async()=>{completed=true;}});
    job.state={id:'partial',session,active:true,stage:'RUNNING',output:'saved'};job.cancelRequested=cancelled;
    await job.finish('partial',cancelled?130:0);
    assert.equal(job.state.stage,cancelled?'CANCELLED':'FAILED');assert.equal(completed,false);assert.equal(job.journal.snapshot().length,1);
  }
});
