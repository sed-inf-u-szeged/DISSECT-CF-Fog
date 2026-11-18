// Executed only by check-live-render-stability.cjs; no browser or new simulation.
import 'zone.js/bundles/zone-node.umd.js';
import '@angular/compiler';
import assert from 'node:assert/strict';
import {createComponent,createEnvironmentInjector,createNgModule,NgModule,RendererFactory2,NgZone,ErrorHandler,enableProdMode,ɵINJECTOR_SCOPE} from '@angular/core';
import {ReplaySubject,Subject} from 'rxjs';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {MobilityViewComponent} from '../src/app/components/mobility-view/mobility-view.component';
import {MobilityMapComponent} from '../src/app/components/mobility-map/mobility-map.component';
import {NodeLoadViewComponent} from '../src/app/components/node-load-view/node-load-view.component';
import {ButtonComponent} from '../src/app/components/buttons/button/button.component';
import {CheckboxContainerComponent} from '../src/app/components/checkbox-container/checkbox-container.component';
import {createRequire} from 'node:module';
import fs from 'node:fs';
import path from 'node:path';
@NgModule({declarations:[MobilityViewComponent,ButtonComponent,CheckboxContainerComponent],imports:[CommonModule,FormsModule,MobilityMapComponent,NodeLoadViewComponent]})
class ReplayTestModule {}

import {MobilityLauncherComponent} from '../src/app/components/mobility-launcher/mobility-launcher.component';
import {NodeLoadModelChartComponent} from '../src/app/components/node-load-comparison/node-load-model-chart.component';
import {ElectronService} from '../src/app/services/electron/electron.service';
import {liveRecords} from './live-node-load-fixture.cjs';

enableProdMode();
// esbuild does not emit Angular's constructor parameter metadata.
(MobilityLauncherComponent as any).ctorParameters=()=>[{type:ElectronService}];
(NodeLoadModelChartComponent as any).ctorParameters=()=>[{type:NgZone}];
const probe={observers:new Set<()=>void>(),charts:new Set<any>(),chartCalls:[] as {action:string;inAngular:boolean}[],observerCalls:0};
(globalThis as any).__liveRenderProbe=probe;
const zoneRoot=(globalThis as any).Zone.root;
let queued=false;
function childListChanged(parent:TestNode):void {
  if(!parent.connected || !probe.observers.size || queued) return;
  queued=true;
  // Native observer delivery is outside Angular; Zone invokes each callback in
  // its registration zone. Chart.js observes document childList + subtree.
  zoneRoot.run(()=>Promise.resolve().then(()=>{
    queued=false;
    for(const callback of [...probe.observers]) callback();
  }));
}
class TestNode {
  children:TestNode[]=[];parent?:TestNode;attributes:Record<string,string>={};
  listeners:Record<string,Function>={};value='';style={};options=[];open=false;
  constructor(public name:string,public text='',public root=false) {}
  get connected():boolean {return this.root || !!this.parent?.connected;}
  scrollTo():void {}
  scrollIntoView():void {}
  close():void {this.open=false;}
  showModal():void {this.open=true;}
}
const renderer:any={
  data:{},destroy(){},createElement:(name:string)=>new TestNode(name),createText:(text:string)=>new TestNode('#text',text),
  createComment:(text:string)=>new TestNode('#comment',text),
  appendChild(parent:TestNode,child:TestNode){if(child.parent)this.removeChild(child.parent,child);parent.children.push(child);child.parent=parent;childListChanged(parent);},
  insertBefore(parent:TestNode,child:TestNode,ref:TestNode){if(child.parent)this.removeChild(child.parent,child);const index=parent.children.indexOf(ref);if(index<0)parent.children.push(child);else parent.children.splice(index,0,child);child.parent=parent;childListChanged(parent);},
  removeChild(parent:TestNode,child:TestNode){const i=parent.children.indexOf(child);if(i>=0)parent.children.splice(i,1);child.parent=undefined;childListChanged(parent);},
  parentNode:(node:TestNode)=>node.parent,nextSibling:(node:TestNode)=>node.parent?.children[node.parent.children.indexOf(node)+1],selectRootElement:(node:TestNode)=>node,
  setAttribute(node:TestNode,key:string,value:string){node.attributes[key]=value;},removeAttribute(node:TestNode,key:string){delete node.attributes[key];},
  setProperty(node:any,key:string,value:any){node[key]=value;},setValue(node:TestNode,value:string){node.text=value;},
  addClass(node:TestNode,name:string){node.attributes['class']=[...new Set([...(node.attributes['class']??'').split(' ').filter(Boolean),name])].join(' ');},
  removeClass(node:TestNode,name:string){node.attributes['class']=(node.attributes['class']??'').split(' ').filter(c=>c!==name).join(' ');},setStyle(){},removeStyle(){},
  listen(node:TestNode,name:string,fn:Function){node.listeners[name]=fn;return()=>delete node.listeners[name];}
};
function walk(node:TestNode):TestNode[] {return [node,...node.children.flatMap(walk)];}
function text(node:TestNode):string {return node.name==='#comment'?'':node.text+node.children.map(text).join('');}
const wait=()=>new Promise(resolve=>setTimeout(resolve,0));

async function check(models:string[],nodeCount:number):Promise<void> {
  const callsStart=probe.chartCalls.length,job=new ReplaySubject<any>(1),live=new Subject<any>();
  const service={mobilityJobUpdates$:job,mobilityLiveUpdates$:live,getMobilityJob:()=>Promise.resolve({state:{stage:'IDLE',active:false},events:[]})};
  const zone=new NgZone({enableLongStackTrace:true}),host=new TestNode('app-mobility-launcher','',true);
  const injector=createEnvironmentInjector([{provide:ɵINJECTOR_SCOPE,useValue:'root'},{provide:ElectronService,useValue:service},
    {provide:RendererFactory2,useValue:{createRenderer:()=>renderer}},{provide:NgZone,useValue:zone},{provide:ErrorHandler,useClass:ErrorHandler}]);
  let ref:any,ticks=0,phaseStart=0;
  const subscription=zone.onMicrotaskEmpty.subscribe(()=>{
    ticks++;
    if(ticks-phaseStart>100) {
      console.error('RUNAWAY_CHANGE_DETECTION',JSON.stringify({models,nodeCount,ticks}));
      process.exit(2);
    }
    zone.run(()=>ref.changeDetectorRef.detectChanges());
  });
  const settle=async()=>{await wait();await wait();assert.ok(ticks-phaseStart<25,'Angular must settle before the next batch');phaseStart=ticks;};
  let extraObserver:(()=>void)|undefined;
  try {
    zone.run(()=>{ref=createComponent(MobilityLauncherComponent,{environmentInjector:injector,hostElement:host});ref.changeDetectorRef.detectChanges();});
    await settle();
    const component=ref.instance as MobilityLauncherComponent;
    const nodes=Array.from({length:nodeCount},(_,i)=>'node-'+i);
    const rows=liveRecords({minutes:350,nodes,models});let cursor=0;
    const sendThrough=async(minute:number)=>{
      const end=rows.findIndex((r:any,index:number)=>index>=cursor && r.time_ms>minute*60000);
      const stop=end<0?rows.length:end;
      zone.run(()=>live.next({id:'test',events:[...rows.slice(cursor,stop),
        {type:'FRAME',device_id:0,time_ms:minute*60000,latitude:0,longitude:minute/1e5,sampled:true}]}));cursor=stop;
      await settle();
      assert.equal(component.error,'');
    };
    const cards=()=>walk(host).find(n=>n.attributes['class']==='node-status-grid')!.children.filter(n=>n.name==='div');
    const sameCards=(before:TestNode[])=>{const after=cards();assert.equal(after.length,before.length);after.forEach((n,i)=>assert.equal(n,before[i],'same node keeps the same card element'));};
    zone.run(()=>job.next({id:'test',stage:'RUNNING',active:true,startedAt:Date.now(),config:{models:[]}}));
    await sendThrough(1);
    const initialCards=cards();assert.equal(initialCards.length,nodeCount);
    assert.match(text(initialCards[0]),/Sample time 1\.00 min/);
    assert.equal(probe.charts.size,1,'untrained models have status cards, not empty charts');
    if(models.length) assert.match(text(host),/2 \/ 287 samples collected/);

    // Other Angular-aware observers must also be safe: stable card identities
    // stop childList feedback even independently of the chart-zone isolation.
    extraObserver=zone.run(()=>(globalThis as any).Zone.current.wrap(()=>{},'external document observer'));
    probe.observers.add(extraObserver);
    renderer.appendChild(host,new TestNode('#comment','external DOM change'));
    await settle();sameCards(initialCards);
    assert.ok(probe.observerCalls>0,'observer callbacks must actually run in this test');
    await sendThrough(2);sameCards(initialCards);
    assert.equal(component.observed,2,'device observations must keep arriving after charts appear');
    assert.match(text(cards()[0]),/Completed tasks 2/);
    zone.run(()=>component.seekTime(0));await settle();sameCards(initialCards);
    assert.match(text(cards()[0]),/Sample time 0\.00 min/);
    await sendThrough(286);sameCards(initialCards);
    assert.equal(component.replayTime,0,'new data must preserve a historical seek');
    assert.match(text(cards()[0]),/Completed tasks 0/);
    if(models.length) assert.match(text(host),/1 \/ 287 samples collected/);
    assert.equal(probe.charts.size,1,'future training must not replace historical readiness cards');
    for(const chart of probe.charts) {
      const actual=chart.data.datasets.find((s:any)=>s.label==='Actual');
      assert.ok(actual?.data.length);
      assert.ok(actual.data.every((p:any)=>p.time<=component.replayTime),'no future actuals after rewind');
    }
    zone.run(()=>component.followLatest());await settle();sameCards(initialCards);
    assert.match(text(cards()[0]),/Sample time 286\.00 min/);
    assert.equal(probe.charts.size,1+models.length);
    if(models.length) {
      assert.doesNotMatch(text(host),/samples collected at this replay time/);
      assert.match(text(host),/Waiting for actual target values · 0 \/ 64 observed/);
    }
    const map=walk(host).find(n=>n.name==='app-mobility-map')!;
    const coverage=()=>walk(map).find(n=>n.name==='polygon')!.attributes['points'];
    assert.ok(!walk(map).some(n=>n.attributes['aria-label']==='Zoom in'),'manual map controls removed');
    zone.run(()=>walk(map).find(n=>n.attributes['data-node']===nodes[1])!.listeners['click']({}));await settle();
    assert.equal(component.selectedNodeId,nodes[1]);assert.equal(component.replayTime,286*60000);assert.equal(component.selectedDevice,0);
    for(const chart of probe.charts)assert.ok(chart.data.datasets[0].data.every((p:any)=>p.y===200),'node click updates aggregate forecast actuals');
    const select=walk(host).find(n=>n.attributes['id']==='load-node')!;
    zone.run(()=>select.listeners['change']({target:{value:nodes[0]}}));await settle();
    assert.equal(component.selectedNodeId,nodes[0]);assert.equal(walk(map).find(n=>n.attributes['data-node']===nodes[0])!.attributes['aria-pressed'],'true');
    const cameraOutline=coverage();
    const chartInstances=[...probe.charts];
    for(const chart of probe.charts) {
      assert.equal(chart.options.scales.x.min,94,'viewport follows replay, not forecast origin');
      assert.equal(chart.options.scales.x.max,350);
      assert.equal(chart.options.scales.x.ticks.callback(286),'286','absolute labels');
    }
    for(const label of ['Common load forecast origin','Load chart view','Load time axis']) {
      assert.ok(!walk(host).some(n=>n.attributes['aria-label']===label),'removed primary control: '+label);
    }
    await sendThrough(320);zone.run(()=>component.followLatest());await settle();
    for(const chart of probe.charts) {
      assert.equal(chart.options.scales.x.min,128);
      assert.equal(chart.options.scales.x.max,384);
      assert.ok(chart.data.datasets[0].data.every((p:any)=>p.time<=320*60000));
    }
    assert.equal(coverage(),cameraOutline,'node coverage stays fixed while replay advances');
    assert.deepEqual([...probe.charts],chartInstances,'scrolling updates existing canvases');

    const click=(label:string)=>zone.run(()=>{
      const button=walk(host).find(n=>n.attributes['aria-label']===label);
      assert.ok(button,'button exists: '+label);button.listeners['click']({});
    });
    const normalCharts=probe.charts.size;
    click('Expand Persistence load chart');await settle();assert.equal(probe.charts.size,normalCharts+1);
    await sendThrough(350);sameCards(initialCards);assert.equal(probe.charts.size,normalCharts+1);
    click('Close expanded load chart');await settle();assert.equal(probe.charts.size,normalCharts);
    zone.run(()=>job.next({id:'test',stage:'COMPLETE',active:false}));await settle();
    assert.equal(component.state.stage,'COMPLETE');
    zone.run(()=>component.configureNewRun());await settle();
    assert.equal(component.nodeStatuses.length,0);assert.equal(probe.charts.size,0);
    const calls=probe.chartCalls.slice(callsStart);
    for(const action of ['create','update','destroy']) assert.ok(calls.some(c=>c.action===action));
    assert.ok(calls.every(c=>!c.inAngular),'chart creation, update and cleanup must stay outside Angular');
    console.log(JSON.stringify({result:'PASS',models,nodeCount,changeDetectionTicks:ticks,chartCalls:calls.length}));
  } finally {
    if(extraObserver)probe.observers.delete(extraObserver);
    subscription.unsubscribe();zone.run(()=>ref?.destroy());injector.destroy();
  }
}

await check([],2);
await check(['LSTM','TCN'],10);
assert.equal(probe.observers.size,0,'destroyed charts must not leave observers behind');

async function checkSaved(directory:string):Promise<void> {
  const require=createRequire(import.meta.url),data=require(path.join(process.cwd(),'mobility-results.js')).readMobilityRun(directory);
  const zone=new NgZone({enableLongStackTrace:true}),host=new TestNode('app-mobility-view','',true);
  const injector=createEnvironmentInjector([{provide:ɵINJECTOR_SCOPE,useValue:'root'},{provide:RendererFactory2,useValue:{createRenderer:()=>renderer}},
    {provide:NgZone,useValue:zone},{provide:ErrorHandler,useClass:ErrorHandler}]);
  const module=createNgModule(ReplayTestModule,injector);
  let ref:any;
  const refresh=async()=>{await wait();zone.run(()=>ref.changeDetectorRef.detectChanges());await wait();zone.run(()=>ref.changeDetectorRef.detectChanges());};
  try {
    zone.run(()=>{ref=createComponent(MobilityViewComponent,{environmentInjector:module.injector as any,hostElement:host});ref.setInput('run',data);ref.changeDetectorRef.detectChanges();});
    const c=ref.instance as MobilityViewComponent;
    c.seekLoadTime(371.37*60000);await refresh();
    const map=walk(host).find(n=>n.name==='app-mobility-map')!;
    const devices=()=>walk(map).filter(n=>n.attributes['data-device']!==undefined);
    assert.equal(devices().length,1,'default is one local device path');assert.equal(walk(map).filter(n=>n.attributes['data-node']).length,10);
    const coverage=()=>walk(map).find(n=>n.name==='polygon')!.attributes['points'];
    const beforeTime=c.frame.time_ms;
    const observations=()=>walk(host).find(n=>n.attributes['aria-label']==='Observed load by node')!;
    const nodeCharts=()=>walk(observations()).filter(n=>(n.attributes['class']??'').split(' ').includes('node-card'));
    const summary=()=>walk(observations()).find(n=>(n.attributes['class']??'').split(' ').includes('node-summary'))!;
    assert.equal(nodeCharts().length,1,'show selected-node detail by default');
    assert.equal(walk(summary()).filter(n=>n.name==='button').length,10,'overview retains every node');
    walk(summary()).find(n=>n.name==='button' && text(n)==='Szeged')!.listeners['click']({});await refresh();
    assert.equal(c.selectedNodeId,'Szeged_fog2');assert.equal(c.deviceId,0);assert.equal(c.frame.time_ms,beforeTime);
    assert.equal(nodeCharts().length,1);assert.match(text(nodeCharts()[0]),/Szeged/);
    c.selectObservedNode('');await refresh();assert.equal(nodeCharts().length,10,'all-node comparison remains available');
    c.selectObservedNode('Szeged_fog2');await refresh();assert.equal(nodeCharts().length,1);
    assert.equal(c.frame.time_ms,beforeTime);assert.equal(c.deviceId,0);
    walk(map).find(n=>n.attributes['data-node']==='Budapest_cloud')!.listeners['click']({});await refresh();
    assert.equal(c.selectedNodeId,'Budapest_cloud');assert.equal(devices().length,0);
    assert.ok(!walk(map).some(n=>n.name==='polygon'),'cloud has no device-access circle');
    assert.match(text(map),/No recorded device connection/);
    walk(map).find(n=>n.attributes['data-node']==='Debrecen_fog2')!.listeners['click']({});await refresh();
    assert.equal(c.selectedNodeId,'Debrecen_fog2');assert.equal(c.deviceId,0);assert.equal(c.frame.time_ms,beforeTime);
    assert.equal(walk(host).find(n=>n.attributes['id']==='load-node')!.value,'Debrecen_fog2');
    assert.ok(coverage(),'node click opens coverage');
    walk(map).find(n=>n.attributes['data-connected-device']==='7')!.listeners['click']({});await refresh();
    assert.equal(c.deviceId,7);assert.equal(c.selectedNodeId,'Debrecen_fog2');assert.equal(c.frame.time_ms,beforeTime);
    assert.equal(devices().length,1,'connected-device click shows its local path');
    c.selectDevice(8);await refresh();
    assert.equal(c.selectedNodeId,'Gyongyos_fog1','device selection links its recorded node');
    walk(map).find(n=>n.attributes['aria-label']==='Show node coverage')!.listeners['click']({});await refresh();
    if(process.argv[3]) {
      function escape(value:string){return value.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/"/g,'&quot;');}
      function serialize(node:TestNode):string {
        if(node.name==='#comment')return '';if(node.name==='#text')return escape(node.text);
        return '<'+node.name+' '+Object.entries(node.attributes).map(([k,v])=>k+'="'+escape(v)+'"').join(' ')+'>'+node.children.map(serialize).join('')+'</'+node.name+'>';
      }
      const svg=walk(map).find(n=>n.name==='svg')!;
      const css=fs.readFileSync(path.join(process.cwd(),'src/app/components/mobility-map/mobility-map-canvas.scss'),'utf8');
      fs.mkdirSync(process.argv[3],{recursive:true});
      const write=(name:string)=>fs.writeFileSync(path.join(process.argv[3],name),serialize(svg).replace('<svg ','<svg xmlns="http://www.w3.org/2000/svg" width="1400" height="756" ').replace(/(<svg[^>]*>)/,'$1<style>'+css+'</style>'));
      write('node-detail.svg');
      const detail=walk(map).find(n=>n.attributes['aria-label']==='Show device detail')!;detail.listeners['click']({});await refresh();write('device-detail.svg');
    }
    walk(map).find(n=>n.attributes['aria-label']==='Show node coverage')!.listeners['click']({});await refresh();const outline=coverage();
    c.seekLoadTime(404.22*60000);await refresh();assert.equal(coverage(),outline);
    c.choose(0);await refresh();assert.equal(coverage(),outline);
    walk(map).find(n=>n.attributes['aria-label']==='Show device detail')!.listeners['click']({});await refresh();
    assert.equal(devices().length,0,'selected device is not observed yet after rewind');
    assert.match(text(map),/has no recorded position/);
    console.log(JSON.stringify({result:'PASS',scope:'real saved city Angular template, click-to-inspect device/node selection, linked load and causal replay',nodes:10,devices:9}));
  } finally {zone.run(()=>ref?.destroy());module.destroy();injector.destroy();}
}
if(process.argv[2])await checkSaved(process.argv[2]);
assert.equal(probe.charts.size,0);assert.equal(probe.observers.size,0);
