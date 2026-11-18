import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, OnChanges, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { LoadEvaluation, LoadForecast, NodeLoadRun } from '../../shared/NodeLoadRun';
import { availableVectors, loadMetrics, loadVersionsAt, meanVectorTime } from '../../shared/node-load-display';
import { NodeLoadComparisonComponent } from '../node-load-comparison/node-load-comparison.component';

interface Point { time: number; value: number; group?: string; }
@Component({
  selector:'app-node-load-view', standalone:true, imports:[CommonModule,FormsModule,NodeLoadComparisonComponent],
  templateUrl:'./node-load-view.component.html', styleUrls:['./node-load-view.component.scss']
})
export class NodeLoadViewComponent implements OnChanges {
  @Input() data?: NodeLoadRun;
  @Input() time = 0;
  @Input() endTime = 1;
  @Input() final = false;
  @Input() selectedNodeId = '';
  @Output() seekTime = new EventEmitter<number>();
  @Output() nodeChange = new EventEmitter<string>();
  nodeId = '';
  method = 'selected';
  version = '';
  origin = -1;
  readonly ticks = [0,1,2,3];
  private previousData?: NodeLoadRun;
  private cache?: {key:string; value:ReturnType<NodeLoadViewComponent['buildView']>};

  ngOnChanges(): void {
    if (this.previousData!==this.data) {
      if(this.previousData?.run_id!==this.data?.run_id || !this.data?.nodes.some(n=>n.node_id===this.nodeId)) {
        this.nodeId=this.data?.nodes[0]?.node_id ?? '';
        this.method=this.data?.models.length || this.data?.baselines ? 'selected' : 'Persistence';
        this.version=''; this.origin=-1;
      }
      this.previousData=this.data;this.cache=undefined;
    }
    if(this.selectedNodeId && this.selectedNodeId!==this.nodeId) {
      this.nodeId=this.selectedNodeId;this.version='';this.origin=-1;
    }
  }
  selectNode(id: string): void { this.nodeId=id; this.version=''; this.origin=-1; this.nodeChange.emit(id); }
  selectMethod(method: string): void { this.method=method; this.version=''; this.origin=-1; }
  selectVersion(version: string): void { this.version=version; this.origin=-1; }
  get node() { return this.data?.nodes.find(n=>n.node_id===this.nodeId); }
  get firstTraining() { return this.node?.versions.reduce((t,v)=>Math.min(t,v.trained_at_ms),Infinity) ?? Infinity; }
  get hasTraining() { return Number.isFinite(this.firstTraining); }
  get hasSelections() { return !!this.node?.selections.length; }
  get learned() { return ['LSTM','TCN','GRU','PatchTST'].includes(this.method); }
  get blockMethod() { return this.learned || this.method==='ARIMA' || this.method==='LR'; }
  get view() {
    const key=JSON.stringify([this.nodeId,this.time,this.endTime,this.final,this.method,this.version,this.origin]);
    if (this.cache?.key===key) return this.cache.value;
    const value=this.buildView(); this.cache={key,value}; return value;
  }
  private buildView() {
    const node=this.node, time=this.time, interval=this.data?.interval_ms ?? 60000;
    const actual=(node?.samples ?? []).filter(s=>s.time_ms<=time);
    const versions=node ? loadVersionsAt(node,time) : [];
    const vectors=node ? availableVectors(node,time,this.method,this.version) : [];
    const vector=vectors.find(v=>v[0].origin_ms===this.origin) ?? vectors.at(-1) ?? [];
    const selections=(node?.selections ?? []).filter(s=>s.selection_ms<=time);
    const selected=selections.at(-1);
    const baseline=(node?.baseline ?? []).filter(p=>p.origin_ms<=time);
    let shown:LoadForecast[]=this.blockMethod ? vector : this.method==='selected' ? selections : baseline;
    // At settlement show historical predictions, never introduce a new forecast.
    if (this.final) shown=shown.filter(p=>p.target_ms<=time);
    const points:Point[]=shown.map(p=>({time:p.target_ms,value:p.predicted_bytes,group:p.model+'/'+p.version}));
    if (this.blockMethod && vector.length) {
      const originSample=node!.samples.find(s=>s.time_ms===vector[0].origin_ms)!;
      points.unshift({time:originSample.time_ms,value:originSample.value,group:points[0]?.group});
    }
    const paired:Point[]=this.learned && vector.length && vector.every(p=>p.persistence_bytes!==undefined) ? [
      {time:vector[0].origin_ms,value:vector[0].persistence_bytes!},
      ...shown.map(p=>({time:p.target_ms,value:p.persistence_bytes!}))] : [];
    const actualPoints=actual.map(s=>({time:s.time_ms,value:s.value}));
    let maximum=1;
    for (const p of [...actualPoints,...points,...paired]) maximum=Math.max(maximum,p.value);
    const unit=10**Math.floor(Math.log10(maximum)); maximum=Math.ceil(maximum/unit)*unit;
    const end=Math.max(this.endTime,time,shown.at(-1)?.target_ms ?? 0,interval);
    const x=(t:number)=>60+800*t/end, y=(v:number)=>180-150*v/maximum;
    function path(series:Point[],step=false) {
      return series.map((p,i)=> {
        const prior=series[i-1], connected=prior && p.time-prior.time===interval && p.group===prior.group;
        return !connected ? `M${x(p.time)},${y(p.value)}` : step ? `H${x(p.time)}V${y(p.value)}` : `L${x(p.time)},${y(p.value)}`;
      }).join(' ');
    }
    const matured=[...(node?.evaluations ?? []),...(node?.baselineEvaluations??[])].filter(e=>e.target_ms<=time && (!this.version || e.version===this.version));
    const comparison=[...(this.data?.baselines??[]),...(this.data?.models ?? [])].map(model=>({model,...loadMetrics(matured.filter(e=>e.model===model),this.data?.policy)}));
    const baselineMetrics=loadMetrics((node?.baselineEvaluations ?? []).filter(e=>e.target_ms<=time));
    const selectionEvaluations:LoadEvaluation[]=selections.flatMap(p=>{
      const s=node!.samples[p.target_index];
      return s && s.time_ms<=time ? [{...p,actual_bytes:s.value,absolute_error_bytes:Math.abs(p.predicted_bytes-s.value)}] : [];
    });
    return {actual,versions:versions.map(v=>({...v,inference:meanVectorTime(node!.predictions,v,time)})),
      versionOptions:[...new Set(versions.map(v=>v.version))],vectors,vector,selected,shown,
      comparison,baselineMetrics,selectedMetrics:loadMetrics(selectionEvaluations),
      maximum,end,actualPath:path(actualPoints,true),forecastPath:path(points),pairedPath:path(paired),
      actualLast:actualPoints.at(-1),forecastLast:points.at(-1),
      forecastOrigin:this.blockMethod ? vector[0] : shown.at(-1),
      latestState:versions.filter(v=>v.model===this.method && (!vector[0] || v.version===vector[0].version)).at(-1)?.state,
      hasFuture:shown.some(p=>p.target_ms>time),
      lastSample:actual.at(-1)};
  }
  x(t:number) { return 60+800*t/this.view.end; }
  y(v:number) { return 180-150*v/this.view.maximum; }
  minutes(t:number) { return (t/60000).toFixed(2); }
  metric(v:number|null|undefined) { return v==null ? '—' : v.toLocaleString('en-US',{minimumFractionDigits:2,maximumFractionDigits:2}); }
}
