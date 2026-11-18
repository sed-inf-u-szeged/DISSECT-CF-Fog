import { LoadEvaluation, LoadForecast, LoadNode, NodeLoadRun } from './NodeLoadRun';
import { loadMetrics, loadVersionsAt } from './node-load-display';

export interface LoadPlotPoint { time: number; value: number; group: string; origin?: number; version?: string; lead?: number; }
interface ForecastFrame { origin: number; vectors: Map<string, LoadForecast[]>; common: boolean; }
const COLORS: Record<string, string> = {Persistence:'#61b8ac', LSTM:'#ff7395', TCN:'#8498ff'};

// Saved vectors are immutable. Never move a vector to a newer forecast origin.
export function framesAt(node: LoadNode, models: string[], time: number, interval: number): ForecastFrame[] {
  const grouped = new Map<string, LoadForecast[]>();
  for (const p of [...node.predictions,...node.baseline]) {
    if (p.origin_ms > time || !models.includes(p.model)) continue;
    const key = `${p.model}/${p.version}/${p.origin_ms}`;
    if (!grouped.has(key)) grouped.set(key, []);
    grouped.get(key)!.push(p);
  }
  const frames = new Map<number, Map<string, LoadForecast[][]>>();
  for (const group of grouped.values()) {
    const v = [...group].sort((a,b)=>a.target_ms-b.target_ms), first = v[0];
    if (v.length !== 64 || !v.every((p,i)=>p.node_id===node.node_id && p.origin_ms===first.origin_ms
      && p.target_ms===first.origin_ms+(i+1)*interval && p.lead_steps===i+1)) continue;
    if (!frames.has(first.origin_ms)) frames.set(first.origin_ms,new Map());
    const map=frames.get(first.origin_ms)!;
    if (!map.has(first.model)) map.set(first.model,[]);
    map.get(first.model)!.push(v);
  }
  return [...frames].map(([origin,groups])=> {
    // Two versions at one origin are ambiguous; do not silently pick either.
    const vectors=new Map([...groups].filter(([,v])=>v.length===1).map(([m,v])=>[m,v[0]]));
    return {origin,vectors,common:models.length>0 && models.every(m=>vectors.has(m))};
  }).filter(f=>f.vectors.size>0).sort((a,b)=>a.origin-b.origin);
}

export function paired(vector: LoadForecast[]): LoadForecast[] {
  if (!vector.length || vector.some(p=>p.persistence_bytes===undefined)) return [];
  return vector.map(p=>({...p,model:'Persistence',version:'paired',predicted_bytes:p.persistence_bytes!}));
}

function preferCommon(frames: ForecastFrame[]): ForecastFrame | undefined {
  return frames.filter(f=>f.common).at(-1) ?? frames.at(-1);
}

export function buildLoadComparison(data: NodeLoadRun, node: LoadNode, time: number, requestedOrigin=-1, final=false) {
  const interval=data.interval_ms, models=[...data.models];
  const samples=node.samples.filter(s=>s.time_ms<=time), actualByTime=new Map(samples.map(s=>[s.time_ms,s.value]));
  const frames=framesAt(node,models,time,interval);
  const baseline=node.baseline.filter(p=>p.origin_ms<=time);
  const options=frames.length ? frames.map(f=>({origin:f.origin,common:f.common,models:[...f.vectors.keys()]}))
    : baseline.map(p=>({origin:p.origin_ms,common:true,models:['Persistence']}));
  const chosen= requestedOrigin<0 ? preferCommon(frames) : frames.find(f=>f.origin===requestedOrigin);
  const base= requestedOrigin<0 ? baseline.at(-1) : baseline.find(p=>p.origin_ms===requestedOrigin);
  const validRequest=requestedOrigin<0 || options.some(o=>o.origin===requestedOrigin);
  const origin=validRequest ? chosen?.origin ?? (!frames.length ? base?.origin_ms : undefined) : undefined;
  const anchor=origin ?? samples.at(-1)?.time_ms ?? 0;
  const horizon=chosen ? 64 : 1;
  const reference=chosen ? [...chosen.vectors.values()][0] : [];
  // A test block must have fully matured by this forecast origin, not merely by
  // the later replay time. Rejected-model gaps are kept as gaps in the plot.
  const testFrame=chosen ? preferCommon(frames.filter(f=>f.origin+64*interval<=anchor)) : undefined;
  const testReference=testFrame ? [...testFrame.vectors.values()][0] : [];
  const testStart=testFrame?.origin ?? Math.max(0,anchor-64*interval);
  const testEnd=testFrame ? testFrame.origin+64*interval : anchor;
  const start=Math.max(0,testStart-192*interval), end=Math.max(anchor+horizon*interval,start+interval);
  const versions=loadVersionsAt(node,time);

  function evaluate(vector: LoadForecast[], cutoff: number): LoadEvaluation[] {
    return vector.flatMap(p=> {
      const actual=actualByTime.get(p.target_ms);
      return actual!==undefined && p.target_ms<=cutoff
        ? [{...p,actual_bytes:actual,absolute_error_bytes:Math.abs(p.predicted_bytes-actual)}] : [];
    });
  }
  function points(vector: LoadForecast[], rolling=false): LoadPlotPoint[] {
    const kept=vector.filter(p=>!final || p.target_ms<=time);
    const result=kept.map(p=>({time:p.target_ms,value:p.predicted_bytes,
      group:rolling ? 'rolling-one-step' : `${p.model}/${p.version}/${p.origin_ms}`}));
    if (!rolling && result.length) {
      const first=kept[0], value=actualByTime.get(first.origin_ms);
      if (value!==undefined) result.unshift({time:first.origin_ms,value,group:result[0].group});
    }
    return result;
  }
  const traces=['Persistence',...models].map(model=> {
    const future=model==='Persistence' ? (chosen ? paired(reference) : origin!==undefined && base ? [base] : [])
      : chosen?.vectors.get(model) ?? [];
    const evaluation=model==='Persistence' ? (chosen ? paired(testReference)
      : baseline.filter(p=>p.target_ms>testStart && p.target_ms<=anchor)) : testFrame?.vectors.get(model) ?? [];
    const version=future[0]?.version, testVersion=evaluation[0]?.version;
    const state=model==='Persistence' ? 'Baseline' : versions.find(v=>v.model===model && v.version===version)?.state;
    const trained=versions.some(v=>v.model===model);
    const lastEvent=node.events.filter(e=>e.model===model && e.time_ms<=time).at(-1);
    const training=!future.length && lastEvent?.event==='TRAINING';
    const collecting=model!=='Persistence' && !trained && !training && samples.length<287;
    const forecastMetrics=loadMetrics(evaluate(future,time),data.policy);
    let missing='';
    if (!future.length) {
      if (model==='Persistence') missing='No saved baseline forecast at this origin.';
      else if (training) missing='Training is in progress. No completed forecast is available yet.';
      else if (collecting) missing=`${samples.length} / 287 samples collected at this replay time. First load training needs 287 samples.`;
      else if (!trained) missing='History is ready; no completed training or forecast has been recorded at this replay time.';
      else missing='No saved vector at this exact origin. An older vector is not shifted here.';
    }
    return {model,color:COLORS[model] ?? '#c7a2e8',version,testVersion,state:state ?? 'Unavailable',
      collecting,readiness:training ? 'Training' : collecting ? 'Collecting history' : trained ? 'No forecast at this origin' : 'Awaiting forecast',
      future,evaluation,futurePoints:points(future),testPoints:points(evaluation,!chosen),
      testMetrics:loadMetrics(evaluate(evaluation,anchor),data.policy),forecastMetrics,missing};
  });
  const actual=samples.filter(s=>s.time_ms>=start && s.time_ms<=end)
    .map(s=>({time:s.time_ms,value:s.value,group:'actual'}));
  // Scales use only records that are visible at replay time, not future actuals.
  const values=[...actual.map(p=>p.value),...traces.flatMap(t=>[...t.testPoints,...t.futurePoints].map(p=>p.value))];
  const rawMax=Math.max(1,...values), magnitude=10**Math.floor(Math.log10(rawMax));
  const maximum=Math.ceil(rawMax/magnitude)*magnitude;
  const selection=node.selections.filter(p=>p.selection_ms<=time).at(-1);
  return {origin,anchor,horizon,start,end,testStart,testEnd,testOrigin:testFrame?.origin,
    options,traces,actual,maximum,interval,selection,
    common:chosen?.common ?? !frames.length,hasLearned:!!chosen,validRequest,
    sampleCount:samples.length,lastActual:samples.at(-1),historical:time>=end,
    replayInside:time>=start && time<=end,
    missingTest:chosen && !testFrame,
    gap:Math.max(0,anchor-testEnd)};
}

export type LoadComparison = ReturnType<typeof buildLoadComparison>;

export function loadPlotX(v: LoadComparison, time: number): number { return 64+1104*(time-v.start)/(v.end-v.start); }
export function loadPlotY(v: LoadComparison, value: number): number { return 194-145*value/v.maximum; }
export function loadPlotPath(v: LoadComparison, points: LoadPlotPoint[], step=false): string {
  return points.map((p,i)=> {
    const previous=points[i-1], connected=previous && previous.group===p.group && p.time-previous.time===v.interval;
    const x=loadPlotX(v,p.time), y=loadPlotY(v,p.value);
    return connected ? step ? `H${x}V${y}` : `L${x},${y}` : `M${x},${y}`;
  }).join(' ');
}
