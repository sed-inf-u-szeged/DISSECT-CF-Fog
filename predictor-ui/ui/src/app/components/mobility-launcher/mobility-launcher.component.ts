import {CommonModule} from '@angular/common';
import {Component, EventEmitter, OnDestroy, OnInit, Output} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {Subscription} from 'rxjs';
import {ElectronService} from '../../services/electron/electron.service';
import {MobilityJobState, MobilityLiveEvent, MobilityLiveBatch, MobilityRunConfig} from '../../shared/MobilityJob';

@Component({selector:'app-mobility-launcher',standalone:true,imports:[CommonModule,FormsModule],
  templateUrl:'./mobility-launcher.component.html',styleUrls:['./mobility-launcher.component.scss']})
export class MobilityLauncherComponent implements OnInit, OnDestroy {
  @Output() started = new EventEmitter<void>();
  @Output() dismissed = new EventEmitter<void>();
  strategy: '' | 'random-walk' | 'nomadic' = '';
  config: MobilityRunConfig = {strategy:'random-walk',devices:1,models:['IMM/Kalman','XGBoost','TCN'],minutes:120,drainMinutes:1440,seed:42,
    latitude:47.73,longitude:18.27,speedMin:2,speedMax:6,radius:800,speed:4.1,
    waypoints:[[0,0],[1600,0],[1600,600]],repeatRoute:false};
  waypointsText = '0,0\n1600,0\n1600,600';
  pythonExecutable = '';
  previewModel = 'selected';
  modelEnabled = {'IMM/Kalman':true, XGBoost:true, TCN:true};
  readonly additionalModels = ['IMM/Kalman', 'XGBoost', 'TCN'] as const;
  state: MobilityJobState = {stage:'IDLE',active:false};
  submitting = false;
  error = '';
  selectedDevice = 0;
  devices: number[] = [];
  meta?: MobilityLiveEvent;
  latest?: MobilityLiveEvent;
  load?: MobilityLiveEvent;
  points = new Map<number, MobilityLiveEvent[]>();
  frames = new Map<number, MobilityLiveEvent>();
  path = '';
  forecastPath = '';
  marker?: {x:number;y:number};
  target?: {x:number;y:number};
  circles: {name:string;x:number;y:number;r:number;color:string}[] = [];
  axis = {left:'',right:'',bottom:'',top:''};
  observed = 0;
  elapsed = 0;
  private subscriptions = new Subscription();
  private timer?: ReturnType<typeof setInterval>;
  private restoring = true;
  private buffered: MobilityLiveBatch[] = [];
  constructor(private electron: ElectronService) {}
  ngOnInit(): void {
    this.subscriptions.add(this.electron.mobilityJobUpdates$.subscribe(state=>this.receiveState(state)));
    this.subscriptions.add(this.electron.mobilityLiveUpdates$.subscribe(batch=>{
      if (this.restoring) {this.buffered.push(batch);return;}
      if (batch.id===this.state.id) this.receiveEvents(batch.events);
    }));
    this.electron.getMobilityJob().then(snapshot=>{
      if (!this.state.id || snapshot.state.id===this.state.id) {
        if(!this.state.id) this.receiveState(snapshot.state);
        // Reset before replaying the retained preview on a renderer reload.
        this.clearPreview(); this.receiveEvents(snapshot.events);
      }
    }).catch(error=>this.error=String(error)).finally(()=>{
      this.restoring=false;
      for(const batch of this.buffered) if(batch.id===this.state.id) this.receiveEvents(batch.events);
      this.buffered=[];
    });
    this.timer=setInterval(()=>{this.elapsed=Math.max(0,Math.round(((this.state.finishedAt||Date.now())-(this.state.startedAt||Date.now()))/1000));},1000);
  }
  ngOnDestroy(): void { this.subscriptions.unsubscribe(); if(this.timer) clearInterval(this.timer); }
  private clearPreview(): void {
    this.meta=undefined;this.latest=undefined;this.load=undefined;this.points.clear();this.frames.clear();
    this.devices=[];this.observed=0;this.path='';this.forecastPath='';this.marker=undefined;this.target=undefined;this.circles=[];
  }
  receiveState(state: MobilityJobState): void {
    if (state.id!==this.state.id) this.clearPreview();
    this.state=state;
    if(state.error) this.error=state.error;
  }
  receiveEvents(events: MobilityLiveEvent[]): void {
    for(const row of events) {
      if(row.type==='META') {this.meta=row;this.devices=Array.from({length:row.device_count||0},(_,i)=>i);}
      else if(row.type==='LOAD' && (row.time_ms||0)>=(this.load?.time_ms||0)) this.load=row;
      else if(row.device_id!==undefined) {
        if((row.type==='FRAME'||row.type==='LATEST') && (row.time_ms||0)>=(this.frames.get(row.device_id)?.time_ms||0)) this.frames.set(row.device_id,row);
        if(row.type==='POINT'||(row.type==='FRAME'&&row.sampled)) {
          const points=this.points.get(row.device_id)||[];
          // Snapshot and in-flight events can overlap when the renderer reconnects.
          if(!points.length || row.time_ms!>points[points.length-1].time_ms!) {points.push(row);this.observed++;}
          this.points.set(row.device_id,points);
        }
      }
    }
    this.refreshChart();
  }
  refreshChart(): void {
    this.latest=this.frames.get(this.selectedDevice);
    if(!this.meta?.nodes?.length) return;
    const base=this.meta.nodes[0], radius=6378137, rad=Math.PI/180;
    const project=(lat:number,lon:number)=>({x:radius*(lon-base.longitude!)*rad*Math.cos(base.latitude!*rad),y:radius*(lat-base.latitude!)*rad});
    const nodes=this.meta.nodes.map(n=>({...project(n.latitude!,n.longitude!),name:n.name!,r:n.radius!}));
    const observed=(this.points.get(this.selectedDevice)||[]).map(p=>project(p.latitude!,p.longitude!));
    const shadow=this.latest?.comparison?.forecasts.find(p=>p.model===this.previewModel);
    const selectedForecast=this.previewModel==='selected'?this.latest?.lifecycle?.forecast:shadow?{origin_ms:shadow.origin_ms,target_ms:shadow.target_ms,latitude:shadow.predicted_latitude_deg,longitude:shadow.predicted_longitude_deg}:undefined;
    const forecast=this.latest?.sampled && selectedForecast?.origin_ms===this.latest.time_ms ? selectedForecast : undefined;
    const future=forecast?project(forecast.latitude,forecast.longitude):undefined;
    const all=[...observed,...(future?[future]:[]),...nodes.flatMap(n=>[{x:n.x-n.r,y:n.y-n.r},{x:n.x+n.r,y:n.y+n.r}])];
    const minX=Math.min(...all.map(p=>p.x))-100,maxX=Math.max(...all.map(p=>p.x))+100;
    const minY=Math.min(...all.map(p=>p.y))-100,maxY=Math.max(...all.map(p=>p.y))+100;
    const scale=Math.min(860/(maxX-minX),440/(maxY-minY));
    const to=(p:{x:number;y:number})=>({x:490+(p.x-(minX+maxX)/2)*scale,y:260-(p.y-(minY+maxY)/2)*scale});
    this.circles=nodes.map((n,i)=>({...to(n),r:n.r*scale,name:n.name,color:i?'#58b3aa':'#7970ff'}));
    const xy=observed.map(to);
    this.path=xy.map((p,i)=>(i?'L':'M')+p.x.toFixed(2)+','+p.y.toFixed(2)).join(' ');
    this.marker=xy.at(-1);this.target=future?to(future):undefined;
    this.forecastPath=this.marker&&this.target?`M${this.marker.x},${this.marker.y} L${this.target.x},${this.target.y}`:'';
    this.axis={left:minX.toFixed(0),right:maxX.toFixed(0),bottom:minY.toFixed(0),top:maxY.toFixed(0)};
  }
  get progress(): number {return this.meta?Math.min(100,100*this.observed/((this.meta.device_count||1)*(this.meta.expected_observations||1))):0;}
  get stageLabel(): string {
    if(this.state.stage==='RUNNING' && this.load?.phase==='DRAINING') return 'Finishing remaining tasks';
    return ({IDLE:'Configure a mobility run',PREPARING:'Preparing simulation',RUNNING:'Running · live observations',AUDITING:'Checking models and results',
      LOADING:'Opening verified results',COMPLETE:'Complete',FAILED:'Run failed',CANCELLING:'Stopping run',CANCELLED:'Run cancelled'} as Record<string,string>)[this.state.stage]||this.state.stage;
  }
  async run(): Promise<void> {
    if(this.submitting||this.state.active) return;
    this.error='';
    try {
      if(!this.strategy) throw Error('Select a movement strategy first.');
      const config={...this.config,strategy:this.strategy,models:this.additionalModels.filter(m=>this.modelEnabled[m])};
      this.previewModel='selected';
      if(this.strategy==='nomadic') {
        config.waypoints=this.waypointsText.split(/[;\n]/).filter(s=>s.trim()).map(s=>{
          const cells=s.trim().split(',');
          if(cells.length!==2 || cells.some(s=>!s.trim()||!Number.isFinite(Number(s)))) throw Error('Use one east,north pair per line.');
          return cells.map(Number);
        });
      }
      this.submitting=true;
      const state=await this.electron.startMobilityJob({config,pythonExecutable:this.pythonExecutable.trim()});
      this.receiveState(state);if(state.active) this.started.emit();
    } catch(error) {this.error=String(error);}
    finally {this.submitting=false;}
  }
  async cancel(): Promise<void> {try {await this.electron.cancelMobilityJob();}catch(error){this.error=String(error);}}
}
