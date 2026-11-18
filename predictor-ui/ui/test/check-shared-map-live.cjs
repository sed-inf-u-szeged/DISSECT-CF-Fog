'use strict';
// Reuses a saved live journal; never starts simulation/training.
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict'),{pathToFileURL}=require('node:url');
async function main(){
  const [journal,directory]=process.argv.slice(2);if(!journal||!directory)throw Error('Usage: node test/check-shared-map-live.cjs <live.jsonl> <completed-run>');
  const root=path.resolve(__dirname,'..'),temp=fs.mkdtempSync(path.join(root,'.map-test-'));
  try {
    const out=path.join(temp,'map.mjs');
    require('esbuild').buildSync({stdin:{resolveDir:root,contents:"export * from './src/app/components/mobility-launcher/mobility-launcher.component';export * from './src/app/components/mobility-map/mobility-map.component';"},
      outfile:out,bundle:true,platform:'node',format:'esm',packages:'external',tsconfig:path.join(root,'tsconfig.json')});
    await import('@angular/compiler');const {MobilityLauncherComponent,MobilityMapComponent}=await import(pathToFileURL(out).href);
    const data=require('../mobility-results').readMobilityRun(path.resolve(directory));
    const rows=fs.readFileSync(journal,'utf8').trim().split('\n').map(JSON.parse);
    const c=new MobilityLauncherComponent({}),map=new MobilityMapComponent();c.receiveState({id:'test',stage:'RUNNING',active:true});
    let snapshots=0;
    function compare(time){
      c.seekTime(time);assert.equal(c.error,'');map.nodes=c.mapNodes;map.tracks=c.mapTracks;map.time=c.replayTime;
      map.ngOnChanges({nodes:{},tracks:{},time:{}});
      for(const d of data.devices??[{id:0,data}]){
        const o=d.data.observations.filter(o=>o.time_ms<=time).at(-1),actual=map.devices.find(m=>m.id===d.id)?.observation;
        if(!o){assert.equal(actual,undefined);continue;}
        assert.equal(actual.time_ms,o.time_ms);assert.equal(actual.current_node,o.current_node);
        assert.ok(Math.abs(actual.latitude-o.latitude)<1e-9);assert.ok(Math.abs(actual.longitude-o.longitude)<1e-9);snapshots++;
      }
      // Live LOAD messages are wall-clock throttled. Compare at their own
      // timestamp, not with a later saved snapshot that has not been received.
      if(c.load){
        assert.ok(c.load.time_ms<=time);
        const frame=data.frames.filter(f=>f.time_ms===c.load.time_ms).at(-1);assert.ok(frame,'saved counterpart for live LOAD');
        for(const n of frame.nodes){const actual=c.mapLoads.find(m=>m.node===n.node);assert.ok(actual);
          assert.equal(actual.waiting_assignment_bytes,n.waiting_assignment_bytes);
          assert.equal(actual.running_tasks,n.running_tasks);assert.equal(actual.completed_tasks,n.completed_tasks);snapshots++;}
      }else assert.deepEqual(c.mapLoads,[]);
    }
    for(let i=0;i<rows.length;){let end=Math.min(rows.length,i+2048);while(end<rows.length && rows[end].time_ms===rows[end-1].time_ms)end++;c.receiveEvents(rows.slice(i,end));i=end;assert.equal(c.error,'');compare(c.latestTime);}
    for(const time of [43.22,284.22,286.22,341.22,371.37,424.22,3.22,0])compare(time*60000);
    compare(c.latestTime);
    // UI calculation cost only: this is not a 100-device simulation benchmark.
    map.nodes=data.nodes;map.tracks=Array.from({length:100},(_,id)=>({id,observations:(data.devices?.[id%(data.devices.length)].data??data).observations}));
    const times=[];for(let i=0;i<100;i++){map.time=(300+i)*60000;const start=performance.now();map.ngOnChanges(i?{time:{}}:{nodes:{},tracks:{},time:{}});times.push(performance.now()-start);assert.equal(map.devices.length,100);}
    times.sort((a,b)=>a-b);
    console.log(JSON.stringify({status:'PASSED',records:rows.length,matched_device_and_node_snapshots:snapshots,
      synthetic_map_only:{devices:100,updates:100,median_ms:times[50],p95_ms:times[95],scope:'map computation only; no browser drawing or training'}},null,2));
  }finally{fs.rmSync(temp,{recursive:true,force:true});}
}
main().catch(e=>{console.error(e);process.exitCode=1;});
