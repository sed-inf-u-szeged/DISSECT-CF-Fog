'use strict';
// Exercise the real city/legacy saved data through component projections and
// node charts. This checks replay logic, not browser layout or visual appearance.
const assert=require('node:assert/strict'),fs=require('fs'),path=require('path'),{pathToFileURL}=require('url');
async function main() {
  const dirs=process.argv.slice(2);if(!dirs.length)throw new Error('Usage: node test/check-city-layout-replay.cjs <run-directory> [...]');
  const root=path.resolve(__dirname,'..'),temp=fs.mkdtempSync(path.join(root,'.replay-test-'));
  try {
    const out=path.join(temp,'component.mjs');
    require('esbuild').buildSync({stdin:{contents:"export * from './src/app/components/mobility-view/mobility-view.component'; export * from './src/app/components/mobility-map/mobility-map.component';",resolveDir:root},
      outfile:out,bundle:true,platform:'node',format:'esm',packages:'external',tsconfig:path.join(root,'tsconfig.json')});
    await import('@angular/compiler');const {MobilityViewComponent,MobilityMapComponent}=await import(pathToFileURL(out).href);
    const results=[];
    for(const dir of dirs) {
      const data=require('../mobility-results').readMobilityRun(path.resolve(dir));
      const c=new MobilityViewComponent();c.run=data;c.ngOnChanges();
      const map=new MobilityMapComponent();map.nodes=c.mapNodes;map.tracks=c.mapTracks;map.time=c.frame.time_ms;map.selectedDevice=c.deviceId;map.selectedNode=c.selectedNodeId;map.ngOnChanges({nodes:{},tracks:{},time:{}});
      const counts=new Set();
      const colors=c.orderedNodes.map(n=>c.nodeColor(n.name));
      assert.equal(new Set(colors).size,1,'city charts use one neutral color');
      const topology=data.nodes.map(n=>[n.name,n.latitude,n.longitude,n.radius]);
      const end=data.frames.length-1,indices=[...new Set([0,c.index,Math.floor(end/3),Math.floor(end*2/3),end])];
      let snapshots=0;
      for(const device of (data.devices||[{id:c.deviceId}])) {
        c.selectDevice(device.id);
        assert.deepEqual(c.orderedNodes.map(n=>c.nodeColor(n.name)),colors);
        for(const index of [...indices,...[...indices].reverse()]) {
          c.choose(index); const now=c.frame.time_ms;
          map.time=now;map.selectedDevice=c.deviceId;map.forecast=c.shownForecast;map.ngOnChanges({time:{},selectedDevice:{},forecast:{}});
          assert.ok(map.devices.every(d=>d.observation.time_ms<=now));counts.add(map.devices.length);
          for(const d of map.devices) {
            const recorded=c.mapTracks.find(t=>t.id===d.id).observations.filter(o=>o.time_ms<=now).at(-1);
            assert.equal(d.observation,recorded,'each marker uses its own last known observation');
          }
          for(const mode of ['device','node','device-return']) {
            map.selectedNode=c.selectedNodeId;
            if(mode==='node')map.showNode();else map.showDevice();
            const camera={...map.camera};map.ngOnChanges({time:{}});assert.deepEqual(map.camera,camera,'same replay state must have the same detail framing');
            if(mode!=='node' && map.device)assert.ok(map.onScreen(map.currentPoint),'selected observation remains visible');
            for(const text of [...map.renderedDevices.map(d=>d.path),...map.renderedNodes.map(n=>n.outline)])assert.ok(!/NaN|Infinity|undefined/.test(text));
            for(const n of c.orderedNodes){c.selectNode(n.name);assert.equal(c.frame.time_ms,now);assert.equal(c.deviceId,device.id);assert.equal(c.selectedNodeId,n.name);}

            assert.ok(c.errorRows.every(r=>r.target_ms<=now));
            if(c.shownForecast)assert.ok(c.shownForecast.origin_ms<=now&&c.shownForecast.target_ms>now);
            if(c.final)assert.equal(c.shownForecast,undefined);
            for(const window of [0,30,60]) {
              c.loadWindowMinutes=window;
              for(const metric of ['waiting_assignment_bytes','running_tasks']) {
                const known=c.run.frames.slice(0,index+1).filter(f=>f.sample_kind==='OBSERVATION');
                for(const n of c.orderedNodes) {
                  const plot=c.chartPath(n.name,metric);assert.ok(!/NaN|Infinity|undefined/.test(plot));
                  const max=c.maximum(metric);assert.ok(max>0&&Number.isFinite(max));
                  const xValues=[...plot.matchAll(/[MH]([\d.e+\-]+)/g)].map(m=>Number(m[1]));
                  assert.ok(xValues.every(x=>x>=50-1e-6&&x<=420+1e-6));
                  const last=known.at(-1);
                  if(plot&&last) assert.ok(xValues.at(-1)<=c.loadX(last.time_ms)+1e-6);
                  if(last&&last.time_ms<c.loadTime(0))assert.equal(plot,'');
                  snapshots++;
                }
              }
            }
          }
        }
      }
      assert.deepEqual(data.nodes.map(n=>[n.name,n.latitude,n.longitude,n.radius]),topology);
      c.ngOnDestroy();results.push({run_id:data.nodeLoad?.run_id||data.label,devices:data.devices?.length||1,nodes:data.nodes.length,observed_device_counts:[...counts].sort((a,b)=>a-b),chart_snapshots:snapshots});
    }
    console.log(JSON.stringify({status:'PASSED',scope:'saved-result component logic; not browser visual QA',results},null,2));
  } finally { fs.rmSync(temp,{recursive:true,force:true}); }
}
main().catch(e=>{console.error(e);process.exitCode=1;});
