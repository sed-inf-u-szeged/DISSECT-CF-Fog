import { LoadEvaluation, LoadForecast, LoadNode, NodeLoadRun } from './NodeLoadRun';
import { framesAt, paired } from './node-load-comparison';
import { loadMetrics } from './node-load-display';

/** A separate score table: every populated row uses one issue time, the same
 * 64 target timestamps and the same observed-target mask. Chart cards keep
 * scoring their own latest blocks; changing this table never moves a forecast. */
export function buildAlignedLoadScores(data:NodeLoadRun,node:LoadNode,time:number,requestedOrigin=-1) {
  const methods=[...(data.baselines??['Persistence']),...data.models];
  const observed=new Map(node.samples.filter(s=>s.time_ms<=time).map(s=>[s.time_ms,s.value]));
  const frames=framesAt(node,methods.filter(m=>m!=='Persistence'),time,data.interval_ms).map(frame=> {
    const vectors=new Map(frame.vectors);
    if(!data.baselines) {
      // Legacy paired references must agree across saved vectors. Never derive
      // a replacement baseline from future observations or shift a one-step run.
      const references=[...vectors.values()].map(paired),reference=references[0];
      if(reference?.length && references.every(v=>v.length===reference.length && v.every((p,i)=>
        p.target_ms===reference[i].target_ms && p.predicted_bytes===reference[i].predicted_bytes))) {
        vectors.set('Persistence',reference);
      }
    }
    const targets=Array.from({length:64},(_,i)=>frame.origin+(i+1)*data.interval_ms);
    const matched=targets.filter(t=>observed.has(t));
    return {...frame,vectors,targets,matched,common:methods.every(m=>vectors.has(m))};
  });
  const chosen=requestedOrigin<0
    ? frames.filter(f=>f.common && f.matched.length===64).at(-1) ?? frames.filter(f=>f.common).at(-1)
    : frames.find(f=>f.origin===requestedOrigin);
  const options=frames.map(f=>({origin:f.origin,common:f.common,available:f.vectors.size,
    observed:f.matched.length,start:f.targets[0],end:f.targets.at(-1)!}));
  const matched=new Set(chosen?.matched??[]);
  const rows=methods.map(model=> {
    const vector:LoadForecast[]=chosen?.vectors.get(model)??[];
    const evaluated:LoadEvaluation[]=vector.filter(p=>matched.has(p.target_ms)).map(p=> {
      const actual=observed.get(p.target_ms)!;
      return {...p,actual_bytes:actual,absolute_error_bytes:Math.abs(p.predicted_bytes-actual)};
    });
    return {model,version:vector[0]?.version,available:vector.length===64,metrics:loadMetrics(evaluated,data.policy)};
  });
  return {options,rows,origin:chosen?.origin,start:chosen?.targets[0],end:chosen?.targets.at(-1),
    matched:chosen?.matched.length??0,total:64,available:rows.filter(r=>r.available).length,
    common:chosen?.common??false,
    missing:chosen?'':requestedOrigin>=0?'No complete vector at this issue time has been recorded by replay time.'
      :frames.length?'No issue time is shared by all enabled methods yet. Choose an issue time to inspect available methods.'
      :'No complete 64-target forecast has been recorded yet.'};
}

export type AlignedLoadScores=ReturnType<typeof buildAlignedLoadScores>;
