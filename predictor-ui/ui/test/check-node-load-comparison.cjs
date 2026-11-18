'use strict';
// Audit the comparison against saved records; no new simulation or fitting.
const assert=require('node:assert/strict'),fs=require('fs'),path=require('path'),{pathToFileURL}=require('url');
async function main() {
  if(!process.argv[2])throw new Error('Usage: node test/check-node-load-comparison.cjs <completed-run-directory>');
  const root=path.resolve(__dirname,'..'),temp=fs.mkdtempSync(path.join(root,'.comparison-test-'));
  try {
    const run=require('../mobility-results').readMobilityRun(path.resolve(process.argv[2]));
    const data=run.nodeLoad;assert.ok(data,'No node-load exports');
    const out=path.join(temp,'comparison.mjs');
    require('esbuild').buildSync({stdin:{contents:"export * from './src/app/shared/node-load-comparison'; export * from './src/app/shared/node-load-chart';",resolveDir:root},
      outfile:out,bundle:true,platform:'node',format:'esm',packages:'external',tsconfig:path.join(root,'tsconfig.json')});
    const {buildLoadComparison,loadPlotPath,buildLoadChart}=await import(pathToFileURL(out).href);
    const end=run.frames.at(-1).time_ms;
    const times=[...new Set([0,end,...run.frames.map(f=>f.time_ms),...data.nodes.flatMap(n=>n.events.map(e=>e.time_ms))])].sort((a,b)=>a-b);
    let snapshots=0,explicitOrigins=0;const states=new Set(),started=performance.now();
    function check(n,t,origin=-1) {
      const v=buildLoadComparison(data,n,t,origin,t===end);snapshots++;
      const actual=new Map(n.samples.filter(s=>s.time_ms<=t).map(s=>[s.time_ms,s.value]));
      assert.ok(v.actual.every(p=>p.time<=t && p.value===actual.get(p.time)));
      assert.ok(v.origin===undefined || v.origin<=t);
      if(v.selection)assert.ok(v.selection.selection_ms<=t);
      let sharedBounds;
      for(const s of v.traces) {
        states.add(s.state);
        assert.ok(s.future.every(p=>p.origin_ms===v.origin && p.origin_ms<=t));
        if(s.future.length)assert.deepEqual(s.future.map(p=>p.target_ms),Array.from({length:v.horizon},(_,i)=>v.anchor+(i+1)*data.interval_ms));
        assert.ok(s.evaluation.every(p=>p.target_ms<=v.anchor && p.origin_ms<p.target_ms));
        for(const p of [...s.future,...s.evaluation]) {
          if(s.model==='Persistence' && v.hasLearned)assert.equal(p.predicted_bytes,actual.get(p.origin_ms));
          else assert.ok((s.model==='Persistence' ? n.baseline : n.predictions).some(r=>r.model===p.model && r.version===p.version && r.origin_ms===p.origin_ms && r.target_ms===p.target_ms && r.predicted_bytes===p.predicted_bytes));
        }
        for(const [rows,metrics,cutoff] of [[s.future,s.forecastMetrics,t],[s.evaluation,s.testMetrics,v.anchor]]) {
          const errors=rows.filter(p=>p.target_ms<=cutoff && actual.has(p.target_ms)).map(p=>Math.abs(p.predicted_bytes-actual.get(p.target_ms)));
          assert.equal(metrics.count,errors.length);
          if(errors.length) {
            assert.ok(Math.abs(metrics.mae-errors.reduce((a,b)=>a+b,0)/errors.length)<1e-8);
            assert.ok(Math.abs(metrics.rmse-Math.sqrt(errors.reduce((a,b)=>a+b*b,0)/errors.length))<1e-8);
          } else {assert.equal(metrics.mae,null);assert.equal(metrics.rmse,null);}
        }
        for(const p of [...s.testPoints,...s.futurePoints])assert.ok(p.time>=v.start && p.time<=v.end && p.value<=v.maximum);
        if(t===end)assert.ok(s.futurePoints.every(p=>p.time<=t));
        for(const ps of [s.testPoints,s.futurePoints,v.actual])assert.ok(!/NaN|Infinity|undefined/.test(loadPlotPath(v,ps)));
        const chart=buildLoadChart(v,s);
        const bounds=[chart.minX,chart.maxX,chart.minY,chart.maxY];
        if(sharedBounds)assert.deepEqual(bounds,sharedBounds);else sharedBounds=bounds;
        assert.deepEqual(chart.series.map(series=>series.color),['#e8be6d','#7185ef','#ef5b7d']);
        for(const [i,source] of [v.actual,s.testPoints,s.futurePoints].entries()) {
          const shown=chart.series[i].points.filter(p=>p.y!==null);
          assert.equal(shown.length,source.length);
          source.forEach((p,j)=> {
            assert.equal(shown[j].y,p.value);
            assert.equal(shown[j].time,p.time);
            assert.ok(Math.abs(shown[j].x*60000+v.anchor-p.time)<1e-6);
            assert.ok(p.value>=chart.minY && p.value<=chart.maxY);
          });
        }
      }
      if(v.hasLearned && v.common)assert.ok(v.traces.every(s=>s.future.length===64));
      if(v.gap)assert.ok(v.traces.every(s=>s.testPoints.every(p=>p.time<=v.testEnd) && s.futurePoints.every(p=>p.time>=v.anchor)));
    }
    for(const n of data.nodes) {
      for(const t of [...times,...times.slice().reverse()])check(n,t);
      for(const o of [...new Set(n.predictions.map(p=>p.origin_ms))]) {check(n,o,o);check(n,end,o);explicitOrigins+=2;}
    }
    console.log(JSON.stringify({status:'PASSED',run_id:data.run_id,nodes:data.nodes.length,models:data.models,
      snapshots,explicitOrigins,states:[...states],elapsed_ms:Math.round(performance.now()-started)},null,2));
  } finally {fs.rmSync(temp,{recursive:true,force:true});}
}
main().catch(e=>{console.error(e);process.exitCode=1;});
