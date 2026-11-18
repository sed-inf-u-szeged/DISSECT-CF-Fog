import {LoadForecast, LoadMethod, LoadModel, LoadNode, NodeLoadRun} from './NodeLoadRun';

export interface NodeLoadLiveRecord {
  kind:'META'|'SAMPLE'|'VECTOR'|'EVALUATION'|'SELECTION'|'EVENT';
  run_id:string; time_ms:number; sequence?:number; data:Record<string,unknown>;
}
interface NodeStatus {time_ms:number; waiting_bytes:number; running_tasks:number; completed_tasks:number;}
function check(ok:unknown, message:string):asserts ok {if(!ok) throw Error('Live node load: '+message);}
function number(row:Record<string,unknown>, key:string):number {
  const value=row[key];check(typeof value==='number' && Number.isFinite(value) && value>=0,'invalid '+key);return value;
}
function integer(row:Record<string,unknown>, key:string):number {
  const value=number(row,key);check(Number.isSafeInteger(value),'invalid integer '+key);return value;
}
function string(row:Record<string,unknown>, key:string):string {
  const value=row[key];check(typeof value==='string' && value.length>0,'invalid '+key);return value;
}
const key=(p:Pick<LoadForecast,'node_id'|'model'|'version'|'origin_index'|'target_index'>)=>
  JSON.stringify([p.node_id,p.model,p.version,p.origin_index,p.target_index]);

/** Accumulate issued records, not regenerated forecasts. UI publishes once per IPC batch. */
export class LiveNodeLoad {
  data?:NodeLoadRun;
  training?:{node_id:string;model:LoadModel;version:string;time_ms:number};
  private nodes=new Map<string,LoadNode>();
  private forecasts=new Map<string,LoadForecast>();
  private matched=new Set<string>();
  private statuses=new Map<string,NodeStatus[]>();
  private sequence=0;
  private dirty=false;

  accept(event:NodeLoadLiveRecord):void {
    // Reconnection snapshot and buffered live batches can overlap.
    if(event.sequence!==undefined && event.sequence<=this.sequence) return;
    const row=event.data,time=event.time_ms;
    check(Number.isSafeInteger(time) && time>=0 && row['time_ms']===time,'invalid timestamp');
    if(event.kind==='META') {
      check(!this.data && row['run_id']===event.run_id && time===0 && row['interval_ms']===60000,'invalid metadata');
      const ids=row['node_ids'],models=row['models'],profile=row['profile'];
      check(Array.isArray(ids) && ids.length>0 && ids.length<=10 && new Set(ids).size===ids.length
        && ids.every(id=>typeof id==='string' && /^[A-Za-z0-9_-]{1,100}$/.test(id)),'invalid nodes');
      check(Array.isArray(models) && new Set(models).size===models.length && models.every(m=>['LSTM','TCN','GRU','PatchTST'].includes(m)),'invalid models');
      check(profile==='candidate'||profile==='observe','invalid profile');
      const baselines=row['baselines'];
      check(baselines===undefined || JSON.stringify(baselines)==='["ARIMA","LR"]','invalid baselines');
      const policy=row['policy'] as Record<string,unknown>;
      check(policy && policy['matched_targets']===64 && policy['absolute_tolerance_bytes']===.1
        && policy['relative_tolerance']===(profile==='candidate'?.2:.1)
        && policy['minimum_within_tolerance_percent']===(profile==='candidate'?80:0),'invalid policy');
      for(const id of ids) this.nodes.set(id,{node_id:id,samples:[],baseline:[],baselineEvaluations:[],
        predictions:[],evaluations:[],selections:[],events:[],versions:[]});
      this.data={run_id:event.run_id,interval_ms:60000,nodes:[...this.nodes.values()],models:models as LoadModel[],hasBaseline:true,
        ...(baselines?{baselines:baselines as ('ARIMA'|'LR')[]}:{}),
        ...((models.length||baselines)?{profile,policy:{matched_targets:64,absolute_tolerance_bytes:.1,
          relative_tolerance:profile==='candidate'?.2:.1,minimum_within_tolerance_percent:profile==='candidate'?80:0}}:{})};
    } else {
      check(this.data && this.data.run_id===event.run_id,'mixed or missing run');
      const id=string(row,'node_id'),node=this.nodes.get(id),interval=this.data.interval_ms;
      check(node,'unknown node');
      if(event.kind==='SAMPLE') {
        const index=integer(row,'index'),value=number(row,'value');
        check(index===node.samples.length && time===index*interval,'discontinuous sample grid');
        const status={time_ms:time,waiting_bytes:value,running_tasks:integer(row,'running_tasks'),completed_tasks:integer(row,'completed_tasks')};
        node.samples.push({index,time_ms:time,value});
        if(!this.statuses.has(id)) this.statuses.set(id,[]);
        this.statuses.get(id)!.push(status);
      } else {
        const model=string(row,'model') as LoadMethod,version=string(row,'version');
        check(model==='Persistence' && !this.data.baselines || this.data.baselines?.includes(model as 'ARIMA'|'LR') || this.data.models.includes(model as LoadModel),'unconfigured model');
        const online=model==='ARIMA'||model==='LR';
        check(online?version==='online-v1':model==='Persistence'?version==='fixed-v1':/^v\d{4,}$/.test(version),'invalid version');
        if(event.kind==='EVENT') {
          check(!online && model!=='Persistence' && node.samples.some(s=>s.time_ms===time),'lifecycle before observation');
          const name=string(row,'event');
          check(['TRAINING','TRAINED','QUALIFIED','REJECTED','DEGRADED','PROMOTED','FALLBACK'].includes(name),'unknown lifecycle event');
          check(!node.events.length || node.events.at(-1)!.time_ms<=time,'lifecycle moved backwards');
          const trained=integer(row,'trained_at_ms'),windows=integer(row,'training_windows'),seconds=number(row,'training_seconds');
          check(trained<=time && windows>=32,'invalid training record');
          const v=node.versions.find(v=>v.model===model && v.version===version);
          if(name==='TRAINING') {
            check(!v && trained===time,'duplicate training');
            node.versions.push({model:model as LoadModel,version,trained_at_ms:trained,training_windows:windows,training_seconds:seconds});
            this.training={node_id:id,model:model as LoadModel,version,time_ms:time};
          } else {
            check(v,'event without version');
            if(name==='TRAINED') {
              v.training_seconds=seconds;
              if(this.training?.node_id===id && this.training.model===model && this.training.version===version) this.training=undefined;
            }
            if(name==='QUALIFIED'||name==='REJECTED') check(node.evaluations.filter(e=>e.model===model && e.version===version).length>=64,'qualification before 64 actuals');
          }
          node.events.push({node_id:id,model:model as LoadModel,version,time_ms:time,event:name});
        } else {
          const origin=integer(row,'origin_index'),sample=node.samples[origin];
          check(sample && sample.time_ms<=time,'forecast without observed origin');
          if(event.kind==='VECTOR') {
            const values=row['values'],ms=number(row,'inference_vector_ms');
            const baseline=model==='Persistence',paired=baseline||this.data.baselines?undefined:number(row,'persistence_bytes');
            check(time===sample.time_ms && Array.isArray(values) && values.length===(baseline?1:64)
              && values.every(v=>typeof v==='number' && Number.isFinite(v) && v>=0),'incomplete or invalid vector');
            check(this.data.baselines || (baseline?values[0]===sample.value:paired===sample.value),'baseline differs from origin');
            check(online || baseline || node.events.some(e=>e.model===model && e.version===version && e.event==='TRAINED' && e.time_ms<=time),'vector before training');
            const vector:LoadForecast[]=values.map((value,i)=>({node_id:id,model,version,origin_index:origin,origin_ms:time,
              target_index:origin+i+1,target_ms:time+(i+1)*interval,lead_steps:i+1,predicted_bytes:value,
              inference_vector_ms:ms,...(baseline?{}:{persistence_bytes:paired})}));
            check(vector.every(p=>!this.forecasts.has(key(p))),'duplicate vector');
            for(const p of vector) this.forecasts.set(key(p),p);
            (baseline||online?node.baseline:node.predictions).push(...vector);
          } else {
            const target=integer(row,'target_index'),k=key({node_id:id,model,version,origin_index:origin,target_index:target}),p=this.forecasts.get(k);
            check(p,'record without issued forecast');
            if(event.kind==='EVALUATION') {
              const value=number(row,'actual_bytes');
              check(p.target_ms===time && node.samples[target]?.value===value && !this.matched.has(k),'invalid or duplicate evaluation');
              this.matched.add(k);
              (model==='Persistence'||online?node.baselineEvaluations:node.evaluations).push({...p,actual_bytes:value,absolute_error_bytes:Math.abs(p.predicted_bytes-value)});
            } else {
              check(event.kind==='SELECTION' && target*interval===time+interval && node.samples[time/interval]
                && (!node.selections.length || node.selections.at(-1)!.selection_ms<time),'invalid selection');
              node.selections.push({...p,selection_ms:time});
            }
          }
        }
      }
    }
    if(event.sequence!==undefined) this.sequence=event.sequence;
    this.dirty=true;
  }

  publish():NodeLoadRun|undefined {
    if(this.data && this.dirty) this.data={...this.data,nodes:[...this.nodes.values()]};
    this.dirty=false;return this.data;
  }
  statusAt(time:number):({node:string}&NodeStatus)[] {
    return [...this.statuses].flatMap(([node,rows])=>{
      for(let i=rows.length-1;i>=0;i--) if(rows[i].time_ms<=time) return [{node,...rows[i]}];
      return [];
    });
  }
}
