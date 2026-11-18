import { LoadEvaluation, LoadForecast, LoadNode, LoadPolicy, LoadVersion } from './NodeLoadRun';

export function loadMetrics(rows: LoadEvaluation[], policy?: LoadPolicy) {
  let absolute = 0, squared = 0, within = 0, paired = 0, pairedSquared = 0;
  for (const r of rows) {
    absolute += r.absolute_error_bytes; squared += r.absolute_error_bytes ** 2;
    if (policy && r.absolute_error_bytes <= Math.max(policy.absolute_tolerance_bytes, policy.relative_tolerance * Math.abs(r.actual_bytes))) within++;
    if (r.persistence_bytes !== undefined) {
      const e = Math.abs(r.persistence_bytes-r.actual_bytes); paired += e; pairedSquared += e*e;
    }
  }
  const n=rows.length, hasPaired=n>0 && rows.every(r=>r.persistence_bytes!==undefined);
  return {count:n, mae:n ? absolute/n : null, rmse:n ? Math.sqrt(squared/n) : null,
    within:n && policy ? 100*within/n : null, pairedMae:hasPaired ? paired/n : null,
    pairedRmse:hasPaired ? Math.sqrt(pairedSquared/n) : null};
}

// Only past events determine lifecycle state; summary final_state is not an input.
export function loadVersionsAt(node: LoadNode, time: number) {
  const states=new Map<string,string>();
  for (const e of node.events) {
    if (e.time_ms>time) break;
    const k=e.model+'/'+e.version;
    if (e.event==='PROMOTED') {
      for (const [id,state] of states) if (state==='Active') states.set(id,'Ready');
    }
    const label: Record<string,string> = {TRAINING:'Training', TRAINED:'Evaluating', QUALIFIED:'Ready',
      REJECTED:'Rejected', DEGRADED:'Degraded', PROMOTED:'Active'};
    if (label[e.event]) states.set(k,label[e.event]);
  }
  return node.versions.filter(v=>v.trained_at_ms<=time).map(v=>({ ...v,
    state:states.get(v.model+'/'+v.version) ?? 'Training',
    matched:node.evaluations.filter(e=>e.model===v.model && e.version===v.version && e.target_ms<=time).length
  }));
}

export function availableVectors(node: LoadNode, time: number, method: string, version: string): LoadForecast[][] {
  const groups=new Map<string,LoadForecast[]>();
  for (const p of [...node.predictions,...node.baseline]) {
    if (p.origin_ms>time || p.model!==method || version && p.version!==version) continue;
    const k=p.version+'/'+p.origin_ms;
    if (!groups.has(k)) groups.set(k,[]);
    groups.get(k)!.push(p);
  }
  return [...groups.values()].sort((a,b)=>a[0].origin_ms-b[0].origin_ms);
}

export function meanVectorTime(predictions: LoadForecast[], version: LoadVersion, time: number): number | null {
  const vectors = new Map<number,number>();
  for (const p of predictions) if (p.origin_ms<=time && p.model===version.model && p.version===version.version) vectors.set(p.origin_ms,p.inference_vector_ms);
  return vectors.size ? [...vectors.values()].reduce((s,t)=>s+t,0)/vectors.size : null;
}
