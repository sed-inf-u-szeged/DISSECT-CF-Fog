import { LoadEvaluation, LoadForecast, LoadNode, NodeLoadRun } from './NodeLoadRun';
import { framesAt, LoadPlotPoint, paired } from './node-load-comparison';
import { LOAD_CHART_COLORS, loadCanvasPoints } from './node-load-chart';
import { loadMetrics, loadVersionsAt } from './node-load-display';
import { loadModelProgress } from './node-load-progress';

const MINUTE=60000;

// Replay is the only viewport clock. Forecast origins only identify immutable
// records; receiving a new vector must never reset the displayed time range.
export function buildLoadTimeline(data:NodeLoadRun,node:LoadNode,time:number,final=false) {
  const start=Math.max(0,time-192*MINUTE),end=start+256*MINUTE;
  const samples=node.samples.filter(s=>s.time_ms<=time);
  const observed=new Map(samples.map(s=>[s.time_ms,s.value]));
  const frames=framesAt(node,[...(data.baselines??[]),...data.models],time,data.interval_ms);
  const versions=loadVersionsAt(node,time);
  const baseline=node.baseline.filter(p=>p.node_id===node.node_id && p.origin_ms<=time && p.target_ms>p.origin_ms);
  // A paired reference can be shared only if all vectors at this origin agree.
  // A malformed reference stays absent; it is never replaced with a later value.
  const references=frames.map(f=> {
    const vectors=[...f.vectors.values()].map(paired),first=vectors[0];
    return first?.length && vectors.every(v=>v.length===first.length && v.every((p,i)=>
      p.target_ms===first[i].target_ms && p.predicted_bytes===first[i].predicted_bytes)) ? first : [];
  }).filter(v=>v.length);
  const firstFit=frames[0]?.origin;
  const actual:LoadPlotPoint[]=samples.filter(s=>s.time_ms>=start && s.time_ms<=end)
    .map(s=>({time:s.time_ms,value:s.value,group:'actual'}));
  function score(vector:LoadForecast[]) {
    const matched:LoadEvaluation[]=vector.flatMap(p=> {
      const value=observed.get(p.target_ms);
      return value===undefined ? [] : [{...p,actual_bytes:value,absolute_error_bytes:Math.abs(p.predicted_bytes-value)}];
    });
    return loadMetrics(matched,data.policy);
  }
  function points(vector:LoadForecast[],rolling=false):LoadPlotPoint[] {
    return vector.filter(p=>p.target_ms>=start && p.target_ms<=end && (!final || p.target_ms<=time))
      .map(p=>({time:p.target_ms,value:p.predicted_bytes,origin:p.origin_ms,version:p.version,lead:p.lead_steps,
        group:rolling ? 'rolling-one-step' : `${p.model}/${p.version}/${p.origin_ms}`}));
  }
  const traces=[...(data.baselines??['Persistence']),...data.models].map(model=> {
    const legacy=model==='Persistence',isBaseline=legacy||model==='ARIMA'||model==='LR';
    const blocks=legacy ? references : frames.flatMap(f=>f.vectors.has(model)?[f.vectors.get(model)!]:[]);
    const latest=blocks.at(-1) ?? (legacy && firstFit===undefined ? baseline.slice(-1) : []);
    // Rolling one-step history is useful before any trained block exists.
    // Afterwards only paired 64-target references appear here, including gaps.
    const rolling=legacy ? baseline.filter(p=>firstFit===undefined || p.target_ms<=firstFit) : [];
    const recorded=[...points(rolling,true),...blocks.flatMap(v=>points(v))];
    // Baseline refits at neural origins can overlap one earlier block. Display
    // the most recently issued value per target, while retaining all exports.
    const all=data.baselines && isBaseline ? [...new Map(recorded.map(p=>[p.time,p])).values()] : recorded;
    const pastPoints=all.filter(p=>p.time<=time),futurePoints=all.filter(p=>p.time>time);
    const version=latest[0]?.version;
    const state=isBaseline ? 'Baseline' : versions.find(v=>v.model===model && v.version===version)?.state ?? 'Unavailable';
    const trained=versions.some(v=>v.model===model);
    const lastEvent=node.events.filter(e=>e.model===model && e.time_ms<=time).at(-1);
    const training=lastEvent?.event==='TRAINING';
    const collecting=!isBaseline && !trained && !training && samples.length<287;
    let missing='';
    if(!latest.length) {
      if(isBaseline && !legacy) missing=samples.length<192 ? `${samples.length} / 192 samples collected. Baseline fitting needs 192 past samples.` : 'No usable baseline block was saved at this time.';
      else if(legacy) missing=firstFit===undefined ? 'No saved baseline forecast yet.' : 'No consistent paired baseline was saved.';
      else if(training) missing='Training is in progress. No completed forecast is available yet.';
      else if(collecting) missing=`${samples.length} / 287 samples collected at this replay time. First load training needs 287 samples.`;
      else missing='No complete forecast has been recorded for this model at this replay time.';
    } else if(!futurePoints.length && !final) {
      missing=training ? 'Training is in progress; no new future forecast is available yet.'
        : 'No new future forecast is available. Earlier predictions remain at their original times.';
    }
    const baselineScores=(data.baselines??[]).flatMap(name=>{
      const vector=node.baseline.filter(p=>p.model===name && p.origin_ms===latest[0]?.origin_ms);
      return vector.length===64 ? [{model:name,...score(vector)}] : [];
    });
    const progress=isBaseline?undefined:loadModelProgress(data,node,model,time,final);
    return {model,version,state,latest,pastPoints,futurePoints,baselineScores,metrics:score(latest),missing,collecting,progress,
      isBaseline,readiness:training?'Training':collecting||isBaseline && !latest.length && samples.length<192?'Collecting history':'Awaiting forecast',
      paired:legacy && firstFit!==undefined,hasRecorded:!!latest.length || !!rolling.length,
      mixedBaseline:legacy && firstFit!==undefined && rolling.length>0};
  });
  return {time,start,end,interval:data.interval_ms,actual,traces,sampleCount:samples.length,lastActual:samples.at(-1),
    selection:node.selections.filter(p=>p.selection_ms<=time).at(-1),hasLearned:frames.length>0,final};
}

export type LoadTimeline=ReturnType<typeof buildLoadTimeline>;
export type LoadTimelineTrace=LoadTimeline['traces'][number];

export function buildLoadTimelineChart(view:LoadTimeline,trace:LoadTimelineTrace,commonY=true) {
  const compared=commonY ? view.traces : [trace];
  const values=[...view.actual.map(p=>p.value),...compared.flatMap(t=>[...t.pastPoints,...t.futurePoints].map(p=>p.value))];
  const maximum=Math.max(1,...values),minimum=Math.min(0,...values);
  const magnitude=10**Math.floor(Math.log10(Math.max(maximum,Math.abs(minimum))));
  return {minX:view.start/MINUTE,maxX:view.end/MINUTE,
    minY:Math.floor(minimum/magnitude)*magnitude,maxY:Math.ceil(maximum/magnitude)*magnitude,
    series:([
      {key:'actual',label:'Actual',points:view.actual},
      {key:'test',label:'Earlier prediction',points:trace.pastPoints},
      {key:'future',label:'Future prediction',points:trace.futurePoints}
    ] as const).map(s=>({...s,color:LOAD_CHART_COLORS[s.key],points:loadCanvasPoints(s.points,0,view.interval)}))};
}
