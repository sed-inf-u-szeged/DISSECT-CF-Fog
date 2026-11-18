import {LoadNode, NodeLoadRun} from './NodeLoadRun';

export interface LoadModelProgress {
  phase:'history'|'awaiting-training'|'retraining-data'|'training'|'evaluating';
  title:string;
  detail:string;
  hint:string;
  version?:string;
  value:number|null; // null means real training, whose epoch progress is not exported.
  total:number;
  eligibleAtMs?:number;
}

// NodeLoadLearning uses OnlineTrainingSettings.getDefault(). These are raw
// observed load points, not elapsed wall time or training epochs. Its wire
// format currently fixes 192 inputs, 64 outputs and 32 initial training windows.
const INITIAL_POINTS=192+64+32-1, NEW_POINTS=64, GROWTH=64, MAX_POINTS=4096;
const minutes=(time:number)=>(time/60000).toFixed(2);
const bounded=(value:number,total:number)=>Math.max(0,Math.min(total,value));

/** Lifecycle progress is independent of the latest scored forecast. A rejected
 * v1 block may remain in the chart while v2 trains. Only received records at or
 * before the selected replay time can advance any counter or change its stage. */
export function loadModelProgress(data:NodeLoadRun,node:LoadNode,model:string,time:number,final=false):LoadModelProgress|undefined {
  const samples=node.samples.filter(s=>s.time_ms<=time);
  const events=node.events.filter(e=>e.node_id===node.node_id && e.model===model && e.time_ms<=time)
    .sort((a,b)=>a.time_ms-b.time_ms);
  const states=events.filter(e=>['TRAINING','TRAINED','QUALIFIED','REJECTED','DEGRADED','PROMOTED'].includes(e.event));
  const latest=states.at(-1);
  if(!latest) {
    if(node.versions.some(v=>v.model===model && v.trained_at_ms<=time)) return undefined;
    const count=bounded(samples.length,INITIAL_POINTS),ready=count===INITIAL_POINTS;
    return {phase:ready?'awaiting-training':'history',title:ready?'Awaiting training':'Collecting history',
      detail:`${count} / ${INITIAL_POINTS} observed samples. First load training needs ${INITIAL_POINTS} samples.`,
      hint:final?'Run ended; no first training result was recorded.':ready?'History is ready; waiting for a recorded training event.':'',
      value:count,total:INITIAL_POINTS};
  }
  const version=node.versions.find(v=>v.model===model && v.version===latest.version && v.trained_at_ms<=time);
  if(latest.event==='TRAINING') {
    const retry=states.some(e=>e.version!==latest.version);
    return {phase:'training',title:final?'Training incomplete':retry?'Retraining':'Training',version:latest.version,
      detail:`${latest.version} ${retry?'retraining':'training'} started at ${minutes(latest.time_ms)} min.`,
      hint:final?'Run ended before a completed training record.':'Training percentage is not reported. Existing observations and issued forecasts remain visible.',
      value:final?0:null,total:1};
  }
  if(latest.event==='REJECTED' || latest.event==='DEGRADED') {
    // Same anchor as needsTraining(max(lastAttempt, lastEvaluation)). Only
    // samples arriving after that event count toward another fitting attempt.
    const anchor=Math.max(latest.time_ms,version?.trained_at_ms??latest.time_ms);
    const previous=samples.filter(s=>s.time_ms<=anchor).length;
    const rawWindow=version ? version.training_windows+192+64-1 : INITIAL_POINTS;
    const nextWindow=latest.event==='REJECTED'?Math.min(MAX_POINTS,rawWindow+GROWTH):rawWindow;
    const total=Math.max(NEW_POINTS,nextWindow-previous);
    const count=bounded(samples.length-previous,total),remaining=total-count;
    const eligibleAtMs=anchor+total*data.interval_ms;
    return {phase:'retraining-data',title:final?'Run ended · retraining pending':remaining?'Collecting retraining data':'Ready for retraining',
      version:latest.version,value:count,total,eligibleAtMs,
      detail:`${latest.version} ${latest.event==='REJECTED'?'rejected':'degraded'} at ${minutes(latest.time_ms)} min. ${count} / ${total} new samples collected.`,
      hint:final?(remaining?`No more load samples in this run; ${remaining} still needed before another training attempt.`
        :'Required data was collected, but no further training event was recorded before the run ended.')
        :remaining?`Eligible at ${minutes(eligibleAtMs)} simulation min if sampling continues; ${remaining} samples remaining.`
        :'Required data is available; waiting for a recorded training event.'};
  }
  if(latest.event==='TRAINED') {
    const total=data.policy?.matched_targets??64;
    const targets=new Set(node.evaluations.filter(e=>e.node_id===node.node_id && e.model===model && e.version===latest.version
      && e.origin_ms<=time && e.target_ms<=time).map(e=>e.target_ms));
    const count=bounded(targets.size,total);
    return {phase:'evaluating',title:'Evaluating',version:latest.version,value:count,total,
      detail:`${count} / ${total} real targets evaluated for ${latest.version}.`,
      hint:final && count<total?'Run ended before enough real targets arrived; qualification remains incomplete.'
        :count<total?'Candidate predictions are visible while qualification is pending.'
        :'All required targets are evaluated; waiting for the recorded qualification decision.'};
  }
  // Qualified/active models need no retraining countdown. A later degradation
  // event starts a fresh countdown, including when FALLBACK follows at the same time.
  return undefined;
}
