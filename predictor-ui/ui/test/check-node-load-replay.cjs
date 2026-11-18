'use strict';
// Validate as-of display behavior on a completed real run, without retraining.
const assert=require('node:assert/strict'),fs=require('fs'),path=require('path'),{pathToFileURL}=require('url');
async function main() {
  if(!process.argv[2])throw new Error('Usage: node test/check-node-load-replay.cjs <completed-run-directory>');
  const root=path.resolve(__dirname,'..'),temp=fs.mkdtempSync(path.join(root,'.replay-test-'));
  try {
    const data=require('../mobility-results').readMobilityRun(path.resolve(process.argv[2]));
    assert.ok(data.nodeLoad,'No uniform node-load exports in this run');
    const out=path.join(temp,'component.mjs');
    require('esbuild').buildSync({entryPoints:[path.join(root,'src/app/components/node-load-view/node-load-view.component.ts')],
      outfile:out,bundle:true,platform:'node',format:'esm',packages:'external',tsconfig:path.join(root,'tsconfig.json')});
    await import('@angular/compiler');const {NodeLoadViewComponent}=await import(pathToFileURL(out).href);
    const c=new NodeLoadViewComponent();c.data=data.nodeLoad;c.endTime=data.frames.at(-1).time_ms;c.ngOnChanges();
    const times=[...new Set([0,...data.frames.map(f=>f.time_ms),...data.nodeLoad.nodes.flatMap(n=>n.events.map(e=>e.time_ms))])].sort((a,b)=>a-b);
    let snapshots=0;const states=new Set();const started=performance.now();
    for(const n of data.nodeLoad.nodes)for(const method of ['selected','Persistence',...data.nodeLoad.models]) {
      c.selectNode(n.node_id);c.selectMethod(method);
      // Exercise backwards seeking too; no future-state cache may survive it.
      for(const t of [...times,...times.slice().reverse()]) {
        c.time=t;c.final=t===c.endTime;const v=c.view;snapshots++;
        assert.ok(v.actual.every(s=>s.time_ms<=t));assert.ok(v.versions.every(s=>s.trained_at_ms<=t));
        assert.ok(v.vector.every(p=>p.origin_ms<=t));if(v.selected)assert.ok(v.selected.selection_ms<=t);
        assert.equal(v.baselineMetrics.count,n.baselineEvaluations.filter(e=>e.target_ms<=t).length);
        for(const m of v.comparison)assert.equal(m.count,n.evaluations.filter(e=>e.model===m.model&&e.target_ms<=t).length);
        for(const s of v.versions)states.add(s.state);
        for(const key of ['actualPath','forecastPath','pairedPath'])assert.ok(!/NaN|Infinity|undefined/.test(v[key]));
        assert.equal(v.actualLast?.time,n.samples.filter(s=>s.time_ms<=t).at(-1)?.time_ms);
        if(c.final)assert.ok(!v.hasFuture);
      }
    }
    console.log(JSON.stringify({status:'PASSED',run_id:data.nodeLoad.run_id,devices:data.devices?.length??1,
      nodes:data.nodeLoad.nodes.length,snapshots,states:[...states],elapsed_ms:Math.round(performance.now()-started),
      matched:data.nodeLoad.nodes.reduce((s,n)=>s+n.evaluations.length,0)},null,2));
  } finally {fs.rmSync(temp,{recursive:true,force:true});}
}
main().catch(e=>{console.error(e);process.exitCode=1;});
