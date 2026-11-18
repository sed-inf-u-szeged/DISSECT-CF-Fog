import {SimpleChange} from '@angular/core';
import {MobilityMapComponent} from './mobility-map.component';
import {mapScreen,mapDevices,mapProjection,inCoverage} from '../../shared/mobility-map';
import {boxesOverlap,layoutDeviceLabels} from '../../shared/mobility-map-labels';

describe('Click-to-inspect mobility detail',()=>{
  let c:MobilityMapComponent;
  const change=(...names:string[])=>Object.fromEntries(names.map(n=>[n,new SimpleChange(undefined,undefined,false)]));
  beforeEach(()=>{
    c=new MobilityMapComponent();
    c.nodes=[{name:'west',latitude:47,longitude:17,radius:1000},{name:'east',latitude:47,longitude:17.02,radius:1000},
      {name:'city_cloud',latitude:47.01,longitude:17.01,radius:500,role:'cloud'}];
    c.tracks=[{id:0,observations:[{device_id:0,time_ms:60000,latitude:47,longitude:17,previous_node:'NONE',current_node:'west'},
      {device_id:0,time_ms:120000,latitude:47,longitude:17.02,previous_node:'west',current_node:'east'}]},
      {id:1,observations:[{device_id:1,time_ms:90000,latitude:47,longitude:17,previous_node:'NONE',current_node:'east'}]}];
    c.time=60000;c.selectedNode='west';c.ngOnChanges(change('nodes','tracks','time'));
  });
  it('opens at the local device path with equal horizontal and vertical distance scales',()=>{
    expect(c.viewMode).toBe('device');expect(c.renderedDevices.map(d=>d.id)).toEqual([0]);
    expect(c.camera.width).toBe(1500);
    const origin=mapScreen({x:0,y:0},c.camera),x=mapScreen({x:100,y:0},c.camera),y=mapScreen({x:0,y:100},c.camera);
    expect(x.x-origin.x).toBeCloseTo(origin.y-y.y,8);
  });
  it('clicks nodes for coverage and devices for paths without changing replay time',()=>{
    const emitted:string[]=[];
    c.nodeChange.subscribe(name=>{emitted.push(name);c.selectedNode=name;c.ngOnChanges(change('selectedNode'));});
    c.activateNode('east');expect(c.viewMode).toBe('node');expect(emitted).toEqual(['east']);expect(c.time).toBe(60000);
    const camera={...c.camera};c.time=100000;c.ngOnChanges(change('time'));
    expect(c.viewMode).toBe('node');expect(c.camera).toEqual(camera);
    c.deviceChange.subscribe(id=>{c.selectedDevice=id;c.ngOnChanges(change('selectedDevice'));});
    c.activateDevice(1);expect(c.viewMode).toBe('device');expect(c.time).toBe(100000);expect(c.renderedDevices.map(d=>d.id)).toEqual([1]);
    expect(c.onScreen(c.currentPoint!)).toBeTrue();
    c.showNode();c.showDevice();expect(c.viewMode).toBe('device');
  });
  it('fits the selected issued forecast but never reveals a future forecast or observation',()=>{
    expect(c.devices.map(d=>d.id)).toEqual([0]);
    c.forecast={model:'LSTM',model_version:'v1',device_id:0,origin_node:'west',origin_ms:90000,target_ms:150000,predicted_latitude_deg:47,predicted_longitude_deg:17.2};
    c.ngOnChanges(change('forecast'));expect(c.predicted).toBeUndefined();
    c.time=100000;c.ngOnChanges(change('time'));expect(c.devices.length).toBe(2);expect(c.onScreen(c.predicted!)).toBeTrue();
    expect(c.devices[0].observation.time_ms).toBe(60000);
    c.time=60000;c.ngOnChanges(change('time'));expect(c.devices.length).toBe(1);expect(c.predicted).toBeUndefined();
    c.time=150000;c.ngOnChanges(change('time'));expect(c.predicted).toBeUndefined();
  });
  it('distinguishes coverage from attachment and excludes cloud circles',()=>{
    c.time=100000;c.ngOnChanges(change('time'));expect(c.inside.map(d=>d.id)).toEqual([0,1]);expect(c.connected.map(d=>d.id)).toEqual([0]);
    expect(inCoverage(c.devices[1].observation,c.nodes[0])).toBeTrue();
    expect(c.renderedNodes.find(n=>n.node.name==='city_cloud')!.outline).toBe('');
    c.selectedNode='city_cloud';c.showNode();expect(c.connected.length).toBe(0);expect(c.inside.length).toBe(0);
  });
  it('keeps local framing usable when the clock advances, reverses or reaches an unobserved device',()=>{
    c.time=120000;c.ngOnChanges(change('time'));expect(c.onScreen(c.currentPoint!)).toBeTrue();
    expect(c.renderedDevices[0].observation.time_ms).toBe(120000);
    c.time=60000;c.ngOnChanges(change('time'));expect(c.renderedDevices[0].observation.time_ms).toBe(60000);
    c.selectedDevice=1;c.ngOnChanges(change('selectedDevice'));expect(c.device).toBeUndefined();expect(c.renderedDevices).toEqual([]);
    c.time=100000;c.ngOnChanges(change('time'));expect(c.onScreen(c.currentPoint!)).toBeTrue();
  });
  it('does not fit retrospective future truth or include it in current node counts',()=>{
    const camera={...c.camera};
    c.forecast={model:'HoldPosition',model_version:'',device_id:0,origin_node:'west',origin_ms:60000,target_ms:120000,predicted_latitude_deg:47,predicted_longitude_deg:17};
    c.truth={...c.tracks[0].observations[1],longitude:120};c.ngOnChanges(change('truth','forecast'));
    expect(c.retrospective).toBeDefined();expect(c.camera).toEqual(camera);expect(c.connected.length).toBe(1);expect(c.truthOutside).toBeTrue();
    c.fitTruth();expect(c.truthOutside).toBeFalse();expect(c.connected.length).toBe(1);expect(c.devices[0].observation.time_ms).toBe(60000);
    c.truth={...c.truth,device_id:99};c.ngOnChanges(change('truth'));expect(c.retrospective).toBeUndefined();
  });
  it('separates labels for overlapping points without moving positions and offers an unambiguous picker',()=>{
    c.time=100000;c.ngOnChanges(change('time'));c.showNode();
    const points=JSON.stringify(c.renderedDevices.map(d=>d.point));
    expect(c.deviceLabels.map(l=>l.text)).toEqual(['D0','D1']);
    expect(boxesOverlap(c.deviceLabels[0],c.deviceLabels[1])).toBeFalse();
    const selected:number[]=[];c.deviceChange.subscribe(id=>{selected.push(id);c.selectedDevice=id;c.ngOnChanges(change('selectedDevice'));});
    c.inspectMarker(0);expect(selected).toEqual([]);expect(c.pickerDevices.map(d=>d.id)).toEqual([0,1]);
    expect(JSON.stringify(c.renderedDevices.map(d=>d.point))).toBe(points);
    c.inspectLabel(c.deviceLabels[1]);expect(selected).toEqual([1]);expect(c.viewMode).toBe('device');expect(c.pickerDevices).toEqual([]);
  });
  it('groups a hundred coincident devices and allows every device to be selected by ID',()=>{
    const o=c.tracks[0].observations[0];
    c.tracks=Array.from({length:100},(_,id)=>({id,observations:[{...o,device_id:id}]}));
    c.ngOnChanges(change('tracks'));c.showNode();
    expect(c.renderedDevices.length).toBe(100);expect(c.deviceLabels.length).toBe(1);expect(c.deviceLabels[0].text).toBe('100 devices');
    c.inspectLabel(c.deviceLabels[0]);expect(c.pickerDevices.map(d=>d.id)).toEqual(Array.from({length:100},(_,i)=>i));
    const labels=JSON.stringify(c.deviceLabels);c.tracks=[...c.tracks].reverse();c.ngOnChanges(change('tracks'));
    expect(JSON.stringify(c.deviceLabels)).toBe(labels);expect(c.pickerDevices.length).toBe(100);
  });
  it('lays out edge labels without overlapping each other or the node name',()=>{
    const points=[{id:0,point:{x:1,y:1}},{id:1,point:{x:999,y:539}},{id:2,point:{x:500,y:270}},{id:3,point:{x:502,y:270}}];
    const reserved=[{x:465,y:282,width:70,height:22}];
    const first=layoutDeviceLabels(points,reserved);
    expect(first.labels.length).toBe(4);expect(layoutDeviceLabels([...points].reverse(),reserved)).toEqual(first);
    first.labels.forEach((label,i)=>{
      expect(label.x).toBeGreaterThanOrEqual(12);expect(label.x+label.width).toBeLessThanOrEqual(988);
      expect(label.y).toBeGreaterThanOrEqual(40);expect(label.y+label.height).toBeLessThanOrEqual(480);
      expect(boxesOverlap(label,reserved[0])).toBeFalse();
      expect(first.labels.slice(i+1).every(other=>!boxesOverlap(label,other))).toBeTrue();
    });
  });
  it('suppresses the unrelated selected-device forecast while inspecting a node',()=>{
    c.forecast={model:'HoldPosition',model_version:'',device_id:0,origin_node:'west',origin_ms:60000,target_ms:120000,predicted_latitude_deg:47,predicted_longitude_deg:17};
    c.ngOnChanges(change('forecast'));expect(c.predicted).toBeDefined();
    c.showNode();expect(c.predicted).toBeUndefined();
    c.showDevice();expect(c.predicted).toBeDefined();
  });
  it('retains recent known trails through final draining without interpolating missing minutes',()=>{
    const o=c.tracks[0].observations[0],tracks=[{id:0,observations:[{...o,time_ms:0},{...o,time_ms:60000},{...o,time_ms:1200000}]}];
    const project=mapProjection(c.nodes);
    expect(mapDevices(tracks,1190000,10,project)[0].observation.time_ms).toBe(60000);
    const final=mapDevices(tracks,5000000,10,project)[0];expect(final.trail.length).toBe(1);expect(final.observation.time_ms).toBe(1200000);
  });
});
