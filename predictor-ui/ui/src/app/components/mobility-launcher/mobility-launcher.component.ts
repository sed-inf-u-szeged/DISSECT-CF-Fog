import {MobilityReadinessComponent} from '../mobility-readiness/mobility-readiness.component';
import {CommonModule} from '@angular/common';
import {Component, ElementRef, EventEmitter, OnDestroy, OnInit, Output, ViewChild} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {Subscription} from 'rxjs';
import {ElectronService} from '../../services/electron/electron.service';
import {MobilityJobState, MobilityLiveEvent, MobilityLiveBatch, MobilityRunConfig} from '../../shared/MobilityJob';
import {LiveNodeLoad, NodeLoadLiveRecord} from '../../shared/node-load-live';
import {NodeLoadRun} from '../../shared/NodeLoadRun';
import {NodeLoadViewComponent} from '../node-load-view/node-load-view.component';
import {MobilityMapComponent} from '../mobility-map/mobility-map.component';
import {MobilityLoad, MobilityNode, MobilityObservation, PositionPrediction} from '../../shared/MobilityRun';
import {MapTrack,lastKnownIndex} from '../../shared/mobility-map';

@Component({selector:'app-mobility-launcher',standalone:true,imports:[CommonModule,FormsModule,NodeLoadViewComponent,MobilityMapComponent,MobilityReadinessComponent],
  templateUrl:'./mobility-launcher.component.html',styleUrls:['./mobility-launcher.component.scss']})
export class MobilityLauncherComponent implements OnInit, OnDestroy {
  @Output() started = new EventEmitter<void>();
  @Output() dismissed = new EventEmitter<void>();
  strategy: '' | 'random-walk' | 'nomadic' = '';
  config: MobilityRunConfig = {deviceWorkload:'constant',infrastructure:'hungary-cities',cityPlacement:'distributed',cityCoverageKm:20,strategy:'random-walk',devices:9,models:['IMM/Kalman','XGBoost','TCN'],minutes:120,drainMinutes:1440,seed:42,
    latitude:47.73,longitude:18.27,speedMin:2,speedMax:6,radius:800,speed:4.1,
    waypoints:[[0,0],[1600,0],[1600,600]],repeatRoute:false};
  readonly cities = ['Sopron_fog2','Szombathely_fog2','Gyor_fog1','Szeged_fog2','Pecs_fog2',
    'Kecskemet_fog1','Miskolc_fog2','Debrecen_fog2','Gyongyos_fog1'];
  waypointsText = '0,0\n1600,0\n1600,600';
  pythonExecutable = '';
  previewModel = 'selected';
  private mobilityStatuses:MobilityLiveEvent[]=[];
  get readiness() {
    const pending=this.mobilityStatuses.filter(e=>e.device_id===this.selectedDevice && e.time_ms!<=this.replayTime).at(-1);
    return pending && pending.time_ms!>(this.latest?.time_ms??0) ? pending.lifecycle : this.latest?.lifecycle;
  }
  get observedForDevice() {return (this.points.get(this.selectedDevice)??[]).filter(p=>p.time_ms!<=this.replayTime).length;}
  selectedNodeId='';
  mapNodes:MobilityNode[]=[];
  mapTracks:MapTrack[]=[];
  mapLoads:Pick<MobilityLoad,'node'|'waiting_assignment_bytes'|'running_tasks'|'completed_tasks'>[]=[];
  mapForecast?:PositionPrediction;
  showTruth=false;
  // Explicit retrospective overlay: use only a received observation at the
  // exact selected forecast target, never interpolation or another device.
  get targetObservation():MobilityObservation|undefined {
    const forecast=this.mapForecast;
    if(!forecast)return undefined;
    const rows=this.mapObservations.get(forecast.device_id)??[];
    const index=lastKnownIndex(rows,forecast.target_ms),truth=rows[index];
    return truth?.time_ms===forecast.target_ms?truth:undefined;
  }
  private mapObservations=new Map<number,MobilityObservation[]>();
  selectMapDevice(id:number):void {
    this.selectedDevice=id;this.refreshChart();
    if(this.latest?.connection)this.selectMapNode(this.latest.connection);
  }
  selectMapNode(name:string):void {if(this.mapNodes.some(n=>n.name===name))this.selectedNodeId=name;}
  pathWindowMinutes=10;
  previewForecast?:{model:string;origin_ms:number;target_ms:number;latitude:number;longitude:number};
  @ViewChild('liveMobility') liveMobility?:ElementRef<HTMLElement>;
  @ViewChild('liveLoad') liveLoad?:ElementRef<HTMLElement>;
  @ViewChild('liveNodes') liveNodes?:ElementRef<HTMLElement>;
  showSection(section:'mobility'|'load'|'nodes'):void {
    const target=section==='mobility'?this.liveMobility:section==='load'?this.liveLoad:this.liveNodes;
    target?.nativeElement.scrollIntoView({behavior:'smooth',block:'start'});
  }
  modelEnabled = {'IMM/Kalman':true, XGBoost:true, TCN:true};
  readonly additionalModels = ['IMM/Kalman', 'XGBoost', 'TCN'] as const;
  loadModelEnabled = {LSTM:false, TCN:false, GRU:false, PatchTST:false};
  readonly loadModels = ['LSTM', 'TCN', 'GRU', 'PatchTST'] as const;
  loadProfile: 'candidate' | 'observe' = 'candidate';
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
  observed = 0;
  elapsed = 0;
  following=true;
  replayTime=0;
  latestTime=0;
  nodeLoad?:NodeLoadRun;
  loadStream=new LiveNodeLoad();
  // Materialize only when the displayed data/time changes. Allocating row objects
  // in a template getter makes NgFor recreate every card on every Angular check.
  nodeStatuses:ReturnType<LiveNodeLoad['statusAt']>=[];
  private frameHistory=new Map<number,MobilityLiveEvent[]>();
  private loadHistory:MobilityLiveEvent[]=[];
  private lastSequence=0;
  private previewFailed=false;
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
        this.clearPreview(); if(snapshot.state.active) this.receiveEvents(snapshot.events);
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
    this.devices=[];this.observed=0;this.mapObservations.clear();this.mapNodes=[];this.mapTracks=[];this.mapLoads=[];this.mapForecast=undefined;this.selectedNodeId='';
    this.mobilityStatuses=[];
    this.frameHistory.clear();this.loadHistory=[];this.loadStream=new LiveNodeLoad();this.nodeLoad=undefined;this.nodeStatuses=[];
    this.following=true;this.replayTime=0;this.latestTime=0;this.lastSequence=0;this.previewFailed=false;
    this.previewForecast=undefined;this.pathWindowMinutes=10;this.showTruth=false;
  }
  receiveState(state: MobilityJobState): void {
    if (state.id!==this.state.id) this.clearPreview();
    this.state=state;
    if(state.error) this.error=state.error;
  }
  receiveEvents(events: MobilityLiveEvent[]): void {
    if(this.previewFailed) return;
    try {
    for(const row of events) {
      if(row.sequence!==undefined && row.sequence<=this.lastSequence) continue;
      if(row.type==='META') {
        this.meta=row;this.devices=Array.from({length:row.device_count||0},(_,i)=>i);
        this.mapNodes=(row.nodes??[]).filter(n=>n.name&&Number.isFinite(n.latitude)&&Number.isFinite(n.longitude)).map(n=>({name:n.name!,latitude:n.latitude!,longitude:n.longitude!,radius:n.radius??0,role:n.role??(n.name!.endsWith('_cloud')?'cloud':'fog'),serviceable:n.serviceable}));
        this.selectedNodeId=this.mapNodes.find(n=>n.role!=='cloud')?.name??this.mapNodes[0]?.name??'';
      }
      else if(row.type==='MOBILITY_STATUS') this.mobilityStatuses.push(row);
      else if(row.type==='NODE_LOAD') this.loadStream.accept(row as NodeLoadLiveRecord);
      else if(row.type==='LOAD') this.loadHistory.push(row);
      else if(row.device_id!==undefined) {
        if((row.type==='FRAME'||row.type==='LATEST') && (row.time_ms||0)>=(this.frames.get(row.device_id)?.time_ms||0)) {
          this.frames.set(row.device_id,row);
          const history=this.frameHistory.get(row.device_id)||[];
          if(history.at(-1)?.time_ms!==row.time_ms) history.push(row);
          this.frameHistory.set(row.device_id,history);
        }
        if(row.type==='POINT'||(row.type==='FRAME'&&row.sampled)) {
          const points=this.points.get(row.device_id)||[];
          // Snapshot and in-flight events can overlap when the renderer reconnects.
          if(!points.length || row.time_ms!>points[points.length-1].time_ms!) {points.push(row);this.observed++;
            const observations=this.mapObservations.get(row.device_id)||[];
            observations.push({device_id:row.device_id,time_ms:row.time_ms!,latitude:row.latitude!,longitude:row.longitude!,current_node:row.connection??'UNKNOWN',previous_node:observations.at(-1)?.current_node??'UNKNOWN'});
            this.mapObservations.set(row.device_id,observations);
          }
          this.points.set(row.device_id,points);
        }
      }
      this.latestTime=Math.max(this.latestTime,row.time_ms??0);
      if(row.sequence!==undefined) this.lastSequence=row.sequence;
    }
    this.nodeLoad=this.loadStream.publish();
    if(this.following) this.replayTime=this.latestTime;
    this.refreshChart();
    } catch(error) {this.previewFailed=true;this.error='Live display stopped: '+String(error)+'. Saved results will still be checked after the run.';}
  }
  seekTime(time:number):void {
    this.following=false;this.replayTime=Math.max(0,Math.min(this.latestTime,time));this.refreshChart();
  }
  seek(event:Event):void {this.seekTime(Number((event.target as HTMLInputElement).value));}
  followLatest():void {this.following=true;this.replayTime=this.latestTime;this.refreshChart();}
  configureNewRun():void {if(!this.state.active) {this.clearPreview();this.state={stage:'IDLE',active:false};this.error='';}}
  get atFinal():boolean {return this.loadHistory.at(-1)?.phase==='FINAL' && this.replayTime>=this.latestTime;}
  trackNodeStatus(_index:number,row:{node:string}):string {return row.node;}
  get trainingText():string {
    const training=this.loadStream.training;
    return training && this.state.active ? `Live training · ${training.node_id} · ${training.model} ${training.version} · at ${(training.time_ms/60000).toFixed(2)} min` : '';
  }
  private lastAt(rows:MobilityLiveEvent[]):MobilityLiveEvent|undefined {
    for(let i=rows.length-1;i>=0;i--) if((rows[i].time_ms??0)<=this.replayTime) return rows[i];
    return undefined;
  }
  refreshChart(): void {
    this.nodeStatuses=this.loadStream.statusAt(this.replayTime);
    this.latest=this.lastAt(this.frameHistory.get(this.selectedDevice)||[]);
    this.load=this.lastAt(this.loadHistory);
    if(!this.meta?.nodes?.length) return;
    const shadow=this.latest?.comparison?.forecasts.find(p=>p.model===this.previewModel);
    const early=this.latest?.baselines?.predictions.find(p=>p.model===(this.previewModel==='selected'?'HoldPosition':this.previewModel));
    const other=shadow??early;
    const alternative=other?{model:other.model,origin_ms:other.origin_ms,target_ms:other.target_ms,latitude:other.predicted_latitude_deg,longitude:other.predicted_longitude_deg}:undefined;
    const selectedForecast=this.previewModel==='selected'?(this.latest?.lifecycle?.forecast??alternative):alternative;
    const forecast=this.latest?.sampled && selectedForecast && selectedForecast.origin_ms===this.latest.time_ms
      && selectedForecast.target_ms>this.replayTime ? selectedForecast : undefined;
    this.previewForecast=forecast;
    this.mapTracks=this.devices.map(id=>({id,observations:this.mapObservations.get(id)??[]}));
    this.mapLoads=(this.load?.nodes??[]).map(n=>({node:n.node??n.name??'',waiting_assignment_bytes:n.waiting_bytes??0,running_tasks:n.running_tasks??0,completed_tasks:n.completed_tasks??0}));
    this.mapForecast=forecast ? {model:forecast.model as PositionPrediction['model'],model_version:forecast.model==='LSTM'?this.latest?.lifecycle?.candidate??'':'',device_id:this.selectedDevice,origin_ms:forecast.origin_ms,target_ms:forecast.target_ms,origin_node:this.latest?.connection??'NONE',predicted_latitude_deg:forecast.latitude,predicted_longitude_deg:forecast.longitude} : undefined;
  }

  get progress(): number {return this.meta?Math.min(100,100*this.observed/((this.meta.device_count||1)*(this.meta.expected_observations||1))):0;}
  get stageLabel(): string {
    if(this.state.stage==='RUNNING' && this.loadHistory.at(-1)?.phase==='DRAINING') return 'Finishing remaining tasks';
    return ({IDLE:'Configure a mobility run',PREPARING:'Preparing simulation',RUNNING:'Running · live observations',AUDITING:'Checking models and results',
      LOADING:'Opening verified results',COMPLETE:'Complete',FAILED:'Run failed',CANCELLING:'Stopping run',CANCELLED:'Run cancelled'} as Record<string,string>)[this.state.stage]||this.state.stage;
  }
  async run(): Promise<void> {
    if(this.submitting||this.state.active) return;
    this.error='';
    try {
      if(!this.strategy) throw Error('Select a movement strategy first.');
      const config={...this.config,strategy:this.strategy,models:this.additionalModels.filter(m=>this.modelEnabled[m]),
        loadModels:this.loadModels.filter(m=>this.loadModelEnabled[m]),loadProfile:this.loadProfile,loadEpochs:40};
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
