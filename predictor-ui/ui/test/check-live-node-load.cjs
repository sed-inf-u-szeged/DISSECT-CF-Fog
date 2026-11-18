'use strict';
// Replay a real incremental journal and compare visible chart state with verified exports.
const fs=require('node:fs'),path=require('node:path'),os=require('node:os'),assert=require('node:assert/strict');
const {LiveJournal}=require('../mobility-job');
const {readMobilityRun}=require('../mobility-results');

async function checkLiveNodeLoad(session,directory) {
  const journal=new LiveJournal();let offset;
  do {offset=journal.offset;await journal.read(path.join(session,'live.jsonl'));} while(journal.offset!==offset);
  assert.equal(journal.pending,'');
  const saved=readMobilityRun(directory),data=saved.nodeLoad;journal.verifyNodeLoad(data);
  const temp=fs.mkdtempSync(path.join(os.tmpdir(),'check-live-load-'));
  try {
    const bundle=path.join(temp,'helpers.cjs');
    require('esbuild').buildSync({stdin:{contents:"export * from './src/app/shared/node-load-live'; export * from './src/app/shared/node-load-timeline';",resolveDir:path.resolve(__dirname,'..')},
      outfile:bundle,bundle:true,platform:'node',format:'cjs'});
    const {LiveNodeLoad,buildLoadTimeline}=require(bundle),store=new LiveNodeLoad();
    const project=v=>({time:v.time,start:v.start,end:v.end,actual:v.actual,lastActual:v.lastActual,
      traces:v.traces.map(t=>({model:t.model,version:t.version,state:t.state,
        future:t.futurePoints,past:t.pastPoints,metrics:t.metrics,latest:t.latest.map(p=>({node:p.node_id,model:p.model,version:p.version,origin:p.origin_ms,target:p.target_ms,lead:p.lead_steps,value:p.predicted_bytes,persistence:p.persistence_bytes}))}))});
    const records=journal.history.filter(r=>r.type==='NODE_LOAD');let comparisons=0;
    // Time groups preserve all records at a boundary, including model training at that time.
    for(let i=0;i<records.length;i++) {
      const record=records[i];store.accept(record);
      if(i+1<records.length && records[i+1].time_ms===record.time_ms) continue;
      const live=store.publish();
      for(const n of live.nodes) {
        const expected=data.nodes.find(s=>s.node_id===n.node_id);
        assert.deepEqual(project(buildLoadTimeline(live,n,record.time_ms)),project(buildLoadTimeline(data,expected,record.time_ms)));
        comparisons++;
      }
    }
    // Rewind after all later vectors/actuals have arrived; future actuals stay hidden.
    for(const time of [0,60,285,286,300,350,414,430,478].map(m=>m*60000)) {
      for(const n of store.data.nodes) {
        const expected=data.nodes.find(s=>s.node_id===n.node_id);
        assert.deepEqual(project(buildLoadTimeline(store.data,n,time)),project(buildLoadTimeline(data,expected,time)));comparisons++;
      }
    }
    return {run_id:data.run_id,devices:saved.devices?.length??1,nodes:data.nodes.length,models:data.models,
      records:journal.history.length,chart_comparisons:comparisons,
      samples:data.nodes.reduce((s,n)=>s+n.samples.length,0),vectors:records.filter(r=>r.kind==='VECTOR'&&r.data.model!=='Persistence').length,
      evaluated:data.nodes.reduce((s,n)=>s+n.evaluations.length,0),status:'PASSED'};
  } finally {fs.rmSync(temp,{recursive:true,force:true});}
}
module.exports={checkLiveNodeLoad};
if(require.main===module) checkLiveNodeLoad(process.argv[2],process.argv[3]).then(r=>console.log(JSON.stringify(r,null,2))).catch(e=>{console.error(e);process.exitCode=1;});
