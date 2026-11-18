'use strict';
// Verify the actual rolling-view projection against saved records, in both
// directions. No simulation, training, DOM or file mutations are involved.
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),{pathToFileURL}=require('node:url');
async function main() {
  if(!process.argv[2])throw Error('Usage: node test/check-node-load-timeline.cjs <completed-run-directory>');
  const root=path.resolve(__dirname,'..'),temp=fs.mkdtempSync(path.join(root,'.timeline-test-'));
  try {
    const run=require('../mobility-results').readMobilityRun(path.resolve(process.argv[2])),data=run.nodeLoad;
    assert.ok(data,'No load data');const end=run.frames.at(-1).time_ms;
    const out=path.join(temp,'timeline.mjs');
    require('esbuild').buildSync({stdin:{contents:"export * from './src/app/shared/node-load-timeline';",resolveDir:root},
      outfile:out,bundle:true,platform:'node',format:'esm',packages:'external'});
    const {buildLoadTimeline,buildLoadTimelineChart}=await import(pathToFileURL(out));
    const times=[...new Set([0,end,...run.frames.map(f=>f.time_ms),...data.nodes.flatMap(n=>n.events.map(e=>e.time_ms))])].sort((a,b)=>a-b);
    let snapshots=0;
    for(const node of data.nodes) {
      const original=JSON.stringify(node),actual=new Map(node.samples.map(s=>[s.time_ms,s.value]));
      const identity=p=>[p.model,p.version,p.origin_ms,p.target_ms].join('/');
      const records=new Map([...node.baseline,...node.predictions].map(p=>[identity(p),p.predicted_bytes]));
      const paired=new Map(node.predictions.map(p=>[p.origin_ms+'/'+p.target_ms,p.persistence_bytes]));
      for(const t of [...times,...times.slice().reverse()]) {
        const view=buildLoadTimeline(data,node,t,t===end);snapshots++;
        assert.equal(view.end-view.start,256*60000);assert.equal(view.start,Math.max(0,t-192*60000));
        assert.ok(view.actual.every(p=>p.time<=t && p.value===actual.get(p.time)));
        let bounds;
        for(const row of view.traces) {
          for(const [points,future] of [[row.pastPoints,false],[row.futurePoints,true]]) for(const p of points) {
            assert.equal(p.time>t,future);assert.ok(p.origin<=t && p.origin<p.time);
            assert.ok(p.time>=view.start && p.time<=view.end);
            if(p.version==='paired')assert.equal(p.value,paired.get(p.origin+'/'+p.time));
            else assert.equal(p.value,records.get([row.model,p.version,p.origin,p.time].join('/')));
          }
          const errors=row.latest.filter(p=>p.target_ms<=t && actual.has(p.target_ms)).map(p=>Math.abs(p.predicted_bytes-actual.get(p.target_ms)));
          assert.equal(row.metrics.count,errors.length);
          assert.equal(row.metrics.mae,errors.length?errors.reduce((a,b)=>a+b,0)/errors.length:null);
          const plot=buildLoadTimelineChart(view,row);
          const next=[plot.minX,plot.maxX,plot.minY,plot.maxY];
          if(bounds)assert.deepEqual(next,bounds);else bounds=next;
          for(const series of plot.series) for(const p of series.points) {
            assert.ok(Math.abs(p.x*60000-p.time)<1e-6);
            if(p.y!==null)assert.ok(p.y>=plot.minY && p.y<=plot.maxY);
          }
          if(t===end)assert.equal(row.futurePoints.length,0);
        }
      }
      assert.equal(JSON.stringify(node),original,'projection must not mutate saved records');
    }
    console.log(JSON.stringify({status:'PASS',run_id:data.run_id,nodes:data.nodes.length,models:data.models,snapshots}));
  } finally {fs.rmSync(temp,{recursive:true,force:true});}
}
main().catch(e=>{console.error(e);process.exitCode=1;});
