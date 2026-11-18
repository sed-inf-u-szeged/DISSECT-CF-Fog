import {CommonModule} from '@angular/common';
import {ChangeDetectionStrategy, Component, EventEmitter, Input, OnChanges, Output, SimpleChanges} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {DeviceMapLabel, layoutDeviceLabels} from '../../shared/mobility-map-labels';
import {MobilityLoad, MobilityNode, MobilityObservation, PositionPrediction} from '../../shared/MobilityRun';
import {coveragePoints,fitMap,hasCoverage,inCoverage,MAP_HEIGHT,MAP_WIDTH,MapCamera,MapDevice,mapDevices,MapPoint,mapProjection,mapScreen,MapTrack,orderedMapNodes,NODE_COLOR} from '../../shared/mobility-map';

@Component({selector:'app-mobility-map',standalone:true,imports:[CommonModule,FormsModule],
  templateUrl:'./mobility-map.component.html',styleUrls:['./mobility-map.component.scss','./mobility-map-canvas.scss','./mobility-map-picker.scss'],changeDetection:ChangeDetectionStrategy.OnPush})
export class MobilityMapComponent implements OnChanges {
  @Input() nodes:MobilityNode[]=[];
  @Input() tracks:MapTrack[]=[];
  @Input() time=0;
  @Input() selectedDevice=0;
  @Input() selectedNode='';
  @Input() loads:Pick<MobilityLoad,'node'|'waiting_assignment_bytes'|'running_tasks'|'completed_tasks'>[]=[];
  @Input() loadTime?:number;
  @Input() forecast?:PositionPrediction;
  @Input() truth?:MobilityObservation;
  @Input() truthRequested=false;
  @Input() showPaths=true;
  @Input() showForecast=true;
  @Input() pathMinutes=10;
  @Output() deviceChange=new EventEmitter<number>();
  @Output() nodeChange=new EventEmitter<string>();
  @Output() viewLoad=new EventEmitter<void>();
  @Output() pathMinutesChange=new EventEmitter<number>();
  viewMode:'device'|'node'='device';
  camera:MapCamera={x:0,y:0,width:2000};
  devices:MapDevice[]=[];
  orderedNodes:MobilityNode[]=[];
  renderedNodes:{node:MobilityNode;point:MapPoint;outline:string}[]=[];
  renderedDevices:{id:number;point:MapPoint;path:string;observation:MobilityObservation}[]=[];
  predicted?:MapPoint;
  retrospective?:MapPoint;
  deviceLabels:DeviceMapLabel[]=[];
  nearbyGroups:number[][]=[];
  pickerIds:number[]=[];
  private includeTruthInView=false;
  private project=mapProjection([]);
  private geometry:{node:MobilityNode;point:MapPoint;ring:MapPoint[]}[]=[];
  readonly hasCoverage=hasCoverage;
  readonly nodeColor=NODE_COLOR;

  ngOnChanges(changes:SimpleChanges):void {
    if(changes['time']||changes['selectedDevice']||changes['forecast']||changes['truth']) this.includeTruthInView=false;
    if(changes['nodes']) {
      this.project=mapProjection(this.nodes);this.orderedNodes=orderedMapNodes(this.nodes);
      this.geometry=this.orderedNodes.map(node=>({node,point:this.project(node.latitude,node.longitude),ring:coveragePoints(node,this.project)}));
      this.viewMode='device';
    }
    if(changes['nodes']||changes['tracks']||changes['time']||changes['pathMinutes']) {
      this.devices=mapDevices(this.tracks,this.time,this.pathMinutes,this.project);
    }
    // A deliberate device choice takes priority when its connected node changes too.
    if(changes['selectedDevice']) this.viewMode='device';
    this.updateView();
  }
  get device(){return this.devices.find(d=>d.id===this.selectedDevice);}
  get node(){return this.nodes.find(n=>n.name===this.selectedNode);}
  get load(){return this.loads.find(n=>n.node===this.selectedNode);}
  get connected(){return this.devices.filter(d=>d.observation.current_node===this.selectedNode);}
  get inside(){const n=this.node;return n?this.devices.filter(d=>inCoverage(d.observation,n)):[];}
  get visibleDevices(){return this.renderedDevices.filter(d=>this.onScreen(d.point)).length;}
  get pickerDevices(){return this.renderedDevices.filter(d=>this.pickerIds.includes(d.id)).sort((a,b)=>a.id-b.id);}
  get currentPoint(){return this.device?mapScreen(this.device.point,this.camera):undefined;}
  get visibleForecast(){return this.forecast && this.forecast.device_id===this.selectedDevice && this.forecast.origin_ms<=this.time && this.forecast.target_ms>this.time ? this.forecast:undefined;}
  get coincident(){const p=this.visibleForecast,o=this.device?.observation;return !!p&&!!o&&Math.abs(p.predicted_latitude_deg-o.latitude)<1e-9&&Math.abs(p.predicted_longitude_deg-o.longitude)<1e-9;}
  get truthOutside(){return !!this.retrospective&&!this.onScreen(this.retrospective);}
  get matchingTruth(){const p=this.visibleForecast,t=this.truth;return p&&t&&t.device_id===this.selectedDevice&&t.time_ms===p.target_ms&&Number.isFinite(t.latitude)&&Number.isFinite(t.longitude)?t:undefined;}
  get scaleBar(){
    const raw=this.camera.width*.14,unit=10**Math.floor(Math.log10(raw)),n=raw/unit;
    const metres=(n>=5?5:n>=2?2:1)*unit;
    return {pixels:metres/this.camera.width*MAP_WIDTH,label:metres>=1000?(metres/1000)+' km':metres+' m'};
  }
  minutes(t:number){return (t/60000).toFixed(2);}
  city(name:string){return name.replace(/_(fog[12]|cloud)$/,'');}
  role(n:MobilityNode){return n.role==='cloud'||n.name.endsWith('_cloud')?'Cloud':'Fog';}
  knownNode(name:string){return this.nodes.some(n=>n.name===name);}
  connectionName(name:string){return name==='NONE'?'Disconnected':name==='UNKNOWN'?'Not recorded':this.city(name);}
  connectionCount(name:string){return this.devices.filter(d=>d.observation.current_node===name).length;}
  trackDevice(_index:number,d:{id:number}){return d.id;}
  trackNode(_index:number,n:{node:MobilityNode}){return n.node.name;}
  onScreen(p:MapPoint){return p.x>=0&&p.x<=MAP_WIDTH&&p.y>=0&&p.y<=MAP_HEIGHT;}

  private updateView():void {
    const node=this.geometry.find(n=>n.node.name===this.selectedNode);
    if(this.viewMode==='device'&&this.device) {
      const p=this.showForecast?this.visibleForecast:undefined;
      this.camera=fitMap([this.device.point,...(this.showPaths?this.device.trail:[]),
        ...(p?[this.project(p.predicted_latitude_deg,p.predicted_longitude_deg)]:[]),
        ...(this.includeTruthInView&&this.matchingTruth?[this.project(this.matchingTruth.latitude,this.matchingTruth.longitude)]:[])],1500);
    } else if(node) {
      this.camera=fitMap([node.point,...node.ring],3000);
    } else this.camera=fitMap(this.geometry.flatMap(n=>[n.point,...n.ring]));
    const point=(p:MapPoint)=>mapScreen(p,this.camera);
    const path=(points:MapPoint[])=>points.map(p=>{const v=point(p);return `${v.x.toFixed(2)},${v.y.toFixed(2)}`;}).join(' ');
    this.renderedNodes=this.geometry.map(n=>({node:n.node,point:point(n.point),outline:path(n.ring)}));
    const shown=this.viewMode==='device'?this.devices.filter(d=>d.id===this.selectedDevice):this.devices.filter(d=>this.onScreen(point(d.point)));
    this.renderedDevices=shown.map(d=>({id:d.id,point:point(d.point),path:path(d.trail),observation:d.observation}));
    const reserved=this.renderedNodes.filter(n=>this.onScreen(n.point)).flatMap(n=>[
      {x:n.point.x-9,y:n.point.y-9,width:18,height:18},
      {x:n.point.x-(this.city(n.node.name).length*8+16)/2,y:n.point.y+12,width:this.city(n.node.name).length*8+16,height:22}
    ]);
    const labels=this.viewMode==='node'?layoutDeviceLabels(this.renderedDevices,reserved):{labels:[],groups:[]};
    this.deviceLabels=labels.labels;this.nearbyGroups=labels.groups;
    this.pickerIds=this.viewMode==='node'?this.pickerIds.filter(id=>shown.some(d=>d.id===id)):[];
    const p=this.viewMode==='device'?this.visibleForecast:undefined;
    this.predicted=p?point(this.project(p.predicted_latitude_deg,p.predicted_longitude_deg)):undefined;
    // Only explicit "Fit target" changes framing; truth never changes counts.
    const truth=this.matchingTruth;
    this.retrospective=p&&truth?point(this.project(truth.latitude,truth.longitude)):undefined;
  }
  fitTruth():void {this.includeTruthInView=true;this.updateView();}
  inspectMarker(id:number):void {
    const group=this.nearbyGroups.find(g=>g.includes(id))??[id];
    if(this.viewMode==='node'&&group.length>1)this.pickerIds=[...group];
    else this.activateDevice(id);
  }
  inspectLabel(label:DeviceMapLabel):void {
    if(label.ids.length===1)this.activateDevice(label.ids[0]);
    else this.pickerIds=[...label.ids];
  }
  chooseVisibleDevice():void {this.pickerIds=this.renderedDevices.map(d=>d.id);}
  showDevice():void {this.activateDevice(this.selectedDevice);}
  showNode():void {this.viewMode='node';this.updateView();}
  activateDevice(id:number):void {
    this.viewMode='device';this.deviceChange.emit(id);this.updateView();
  }
  activateNode(name:string):void {
    this.pickerIds=[];this.viewMode='node';this.nodeChange.emit(name);this.updateView();
  }
}
