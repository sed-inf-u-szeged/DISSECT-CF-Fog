import { fixture } from '../components/node-load-comparison/node-load-comparison.component.spec';
import { LoadModel, LoadNode, NodeLoadRun } from './NodeLoadRun';
import { buildLoadTimeline, buildLoadTimelineChart } from './node-load-timeline';
import { loadModelProgress } from './node-load-progress';
const minute=60000;

describe('Single-clock rolling load timeline',()=> {
  let data:NodeLoadRun,node:LoadNode;
  beforeEach(()=>{data=fixture();node=data.nodes[0];});
  it('keeps a 256-minute viewport through training, expiry, retraining and backwards seeks',()=> {
    for(const t of [0,43.22,191.99,192,284.22,286,300,320.22,350,375,413.99,414,430,503.22,300]) {
      const v=buildLoadTimeline(data,node,t*minute);
      expect(v.end-v.start).toBe(256*minute);
      expect(v.start/minute).toBeCloseTo(Math.max(0,t-192),8);
      expect(v.actual.every(p=>p.time<=t*minute)).toBeTrue();
      expect(v.traces.every(row=>row.pastPoints.every(p=>p.time<=t*minute) && row.futurePoints.every(p=>p.time>t*minute))).toBeTrue();
      for(const row of v.traces) {
        const chart=buildLoadTimelineChart(v,row);
        expect(chart.minX).toBe(v.start/minute);expect(chart.maxX).toBe(v.end/minute);
      }
    }
  });
  it('moves identical saved target points from future to past without changing origin, version or value',()=> {
    const before=JSON.stringify(node.predictions);
    const early=buildLoadTimeline(data,node,286.22*minute).traces[1];
    const later=buildLoadTimeline(data,node,320.22*minute).traces[1];
    expect(early.futurePoints.length).toBe(64);expect(early.pastPoints.length).toBe(0);
    expect(later.pastPoints.length).toBe(34);expect(later.futurePoints.length).toBe(30);
    expect([...later.pastPoints,...later.futurePoints]).toEqual(early.futurePoints);
    expect(later.metrics.count).toBe(34);expect(later.metrics.pairedMae).toBe(buildLoadTimeline(data,node,320.22*minute).traces[0].metrics.mae);
    expect(JSON.stringify(node.predictions)).toBe(before);
  });
  it('preserves the rejection gap and old block when a new block arrives',()=> {
    const gap=buildLoadTimeline(data,node,375.22*minute);
    expect(gap.traces[1].futurePoints).toEqual([]);expect(gap.traces[1].state).toBe('Rejected');
    expect(gap.traces[1].pastPoints.at(-1)!.time).toBe(350*minute);expect(gap.traces[1].metrics.count).toBe(64);
    const next=buildLoadTimeline(data,node,414.22*minute),lstm=next.traces[1];
    expect(lstm.pastPoints.map(p=>p.time)).toEqual(gap.traces[1].pastPoints.map(p=>p.time));
    expect(lstm.futurePoints[0].time).toBe(415*minute);expect(lstm.metrics.count).toBe(0);
    expect(lstm.latest[0].version).toBe('v0002');expect(lstm.latest.length).toBe(64);
    expect(lstm.pastPoints.concat(lstm.futurePoints).some(p=>p.time>350*minute && p.time<415*minute)).toBeFalse();
  });
  it('scores the latest recorded block on exact observed targets rather than visible history',()=> {
    for(const [t,count] of [[286.22,0],[300.22,14],[320.22,34],[350,64],[413,64],[414.22,0],[430.22,16],[478,64]]) {
      const v=buildLoadTimeline(data,node,t*minute);
      expect(v.traces[1].metrics.count).toBe(count);expect(v.traces[0].metrics.count).toBe(count);
      expect(v.traces[0].latest.map(p=>p.target_ms)).toEqual(v.traces[1].latest.map(p=>p.target_ms));
      expect(v.traces[1].metrics.pairedMae).toBe(v.traces[0].metrics.mae);
    }
  });
  it('does not expose future actuals or lifecycle states through chart ranges and scores',()=> {
    const before=buildLoadTimeline(data,node,320.22*minute),plot=buildLoadTimelineChart(before,before.traces[1]);
    node.samples.filter(s=>s.time_ms>320.22*minute).forEach(s=>s.value=1e9);
    const after=buildLoadTimeline(data,node,320.22*minute);
    expect(after.traces[1].state).toBe('Evaluating');expect(after.traces[1].metrics).toEqual(before.traces[1].metrics);
    expect(buildLoadTimelineChart(after,after.traces[1])).toEqual(plot);
  });
  it('keeps family-specific origins and paired scores when model schedules differ',()=> {
    node.predictions=node.predictions.filter(p=>!(p.model==='TCN' && p.origin_index===414));
    const v=buildLoadTimeline(data,node,430.22*minute);
    expect(v.traces[1].latest[0].origin_ms).toBe(414*minute);expect(v.traces[1].metrics.count).toBe(16);
    expect(v.traces[2].latest[0].origin_ms).toBe(286*minute);expect(v.traces[2].metrics.count).toBe(64);
    expect(v.traces[2].futurePoints).toEqual([]);
    expect(v.traces[2].metrics.pairedMae).toBe(buildLoadTimeline(data,node,350*minute).traces[0].metrics.mae);
  });
  it('rejects ambiguous, incomplete or inconsistent paired blocks without rolling substitutions',()=> {
    node.predictions.push(...node.predictions.filter(p=>p.model==='LSTM' && p.origin_index===414).map(p=>({...p,version:'duplicate'})));
    node.predictions=node.predictions.filter(p=>!(p.model==='TCN' && p.origin_index===414 && p.lead_steps===64));
    expect(buildLoadTimeline(data,node,430*minute).traces[1].latest[0].origin_ms).toBe(286*minute);
    node.predictions.find(p=>p.model==='TCN' && p.origin_index===286)!.persistence_bytes=999;
    const v=buildLoadTimeline(data,node,300*minute);
    expect(v.traces[0].latest).toEqual([]);expect(v.traces[0].futurePoints).toEqual([]);
    expect(v.traces[0].missing).toContain('consistent paired');
  });
  it('keeps one-step-only and samples-only runs honest without fabricating long horizons',()=> {
    data.models=[];node.predictions=[];
    const v=buildLoadTimeline(data,node,100.22*minute);
    expect(v.traces[0].paired).toBeFalse();expect(v.traces[0].futurePoints.length).toBe(1);
    expect(v.traces[0].futurePoints[0].time).toBe(101*minute);expect(v.traces[0].latest.length).toBe(1);
    node.baseline=[];
    const empty=buildLoadTimeline(data,node,100.22*minute);
    expect(empty.traces[0].latest).toEqual([]);expect(empty.traces[0].metrics.mae).toBeNull();expect(empty.actual.length).toBe(101);
  });
  it('shares scales including hidden models and preserves negative values and original tooltip timestamps',()=> {
    node.predictions.filter(p=>p.model==='TCN').forEach(p=>p.predicted_bytes=1250);
    node.predictions.find(p=>p.model==='LSTM')!.predicted_bytes=-35;
    const v=buildLoadTimeline(data,node,300.22*minute);
    const plot=buildLoadTimelineChart(v,v.traces[1]);
    expect(v.traces.every(t=>buildLoadTimelineChart(v,t).maxY===2000)).toBeTrue();
    expect(buildLoadTimelineChart(v,v.traces[1],false).maxY).toBeLessThan(plot.maxY);
    const point=plot.series[1].points.find(p=>p.time===287*minute)!;
    expect(point.y).toBe(-35);expect(point.x).toBe(287);expect(point.origin).toBe(286*minute);expect(point.version).toBe('v0001');
  });
  it('retains final unmatched targets without drawing unobserved samples or future predictions',()=> {
    node.samples=node.samples.filter(s=>s.time_ms<=430*minute);
    const v=buildLoadTimeline(data,node,450*minute,true),lstm=v.traces[1];
    expect(v.lastActual!.time_ms).toBe(430*minute);expect(v.actual.at(-1)!.time).toBe(430*minute);
    expect(lstm.futurePoints).toEqual([]);expect(lstm.metrics.count).toBe(16);expect(lstm.latest.length).toBe(64);
    expect(v.end).toBe(514*minute);expect(lstm.pastPoints.at(-1)!.time).toBe(450*minute);
  });
});

describe('Load training progress at replay time',()=> {
  let data:NodeLoadRun,node:LoadNode;
  beforeEach(()=>{data=fixture();node=data.nodes[0];});
  const progress=(data:NodeLoadRun,node:LoadNode,time:number,model='LSTM',final=false)=>
    loadModelProgress(data,node,model,time*minute,final)!;

  it('keeps the initial sample count causal even when later versions are loaded',()=> {
    const before=progress(data,node,273.95);
    expect(before.phase).toBe('history');expect(before.value).toBe(274);expect(before.total).toBe(287);
    expect(before.version).toBeUndefined();
    node.versions.forEach(v=>v.training_windows=3000);
    expect(progress(data,node,273.95)).toEqual(before);
    node.events=[];node.versions=[];
    expect(progress(data,node,286).phase).toBe('awaiting-training');
    expect(progress(data,node,286).value).toBe(287);
  });

  it('counts new samples after rejection and resets independently for the next rejected version',()=> {
    for(const [time,count] of [[350,0],[407.02,57],[413.99,63]]) {
      const p=progress(data,node,time);
      expect(p.phase).toBe('retraining-data');expect(p.version).toBe('v0001');
      expect(p.value).toBe(count);expect(p.total).toBe(64);expect(p.eligibleAtMs).toBe(414*minute);
    }
    const trained=progress(data,node,414);
    expect(trained.phase).toBe('evaluating');expect(trained.version).toBe('v0002');expect(trained.value).toBe(0);
    expect(progress(data,node,420).value).toBe(6);
    const second=progress(data,node,478);
    expect(second.phase).toBe('retraining-data');expect(second.version).toBe('v0002');
    expect(second.value).toBe(0);expect(second.eligibleAtMs).toBe(542*minute);
    expect(progress(data,node,407.02).value).toBe(57);
  });

  it('retains old scores and curves while the next version is actually training',()=> {
    node.events=node.events.filter(e=>e.model!=='LSTM' || e.version!=='v0002');
    node.predictions=node.predictions.filter(p=>p.model!=='LSTM' || p.version!=='v0002');
    node.events.push({node_id:node.node_id,model:'LSTM',version:'v0002',time_ms:414*minute,event:'TRAINING'});
    const at=buildLoadTimeline(data,node,414*minute),row=at.traces.find(t=>t.model==='LSTM')!;
    expect(row.progress!.phase).toBe('training');expect(row.progress!.title).toBe('Retraining');
    expect(row.progress!.value).toBeNull();expect(row.progress!.version).toBe('v0002');
    expect(row.version).toBe('v0001');expect(row.state).toBe('Rejected');expect(row.metrics.count).toBe(64);
    expect(row.futurePoints).toEqual([]);expect(row.pastPoints.at(-1)!.time).toBe(350*minute);
    expect(at.actual.at(-1)!.time).toBe(414*minute);
    expect(progress(data,node,413.99).value).toBe(63);
    node.events.push({node_id:node.node_id,model:'LSTM',version:'v0002',time_ms:414*minute,event:'TRAINED'});
    expect(progress(data,node,414).phase).toBe('evaluating');
    expect(progress(data,node,414).value).toBe(0);
  });

  it('distinguishes data readiness from a recorded training start and never exceeds 64 samples',()=> {
    node.events=node.events.filter(e=>e.version==='v0001');node.versions=node.versions.filter(v=>v.version==='v0001');
    node.predictions=node.predictions.filter(p=>p.version==='v0001');
    for(const time of [414,430]) {
      const p=progress(data,node,time);
      expect(p.phase).toBe('retraining-data');expect(p.title).toBe('Ready for retraining');
      expect(p.value).toBe(64);expect(p.hint).toContain('waiting for a recorded training event');
    }
    const final=progress(data,node,487,'LSTM',true);
    expect(final.hint).toContain('no further training event was recorded');
  });

  it('advances from actual received samples rather than the wall clock or final settlement gap',()=> {
    node.samples=node.samples.filter(s=>s.time_ms<=400*minute);
    expect(progress(data,node,407.02).value).toBe(50);
    node.samples=fixture().nodes[0].samples;
    const final=progress(data,node,487.67,'LSTM',true);
    expect(final.value).toBe(2);expect(final.total).toBe(64);expect(final.title).toContain('Run ended');
    expect(final.hint).toContain('62 still needed');
    expect(final.eligibleAtMs).toBe(542*minute);
  });

  it('keeps counters isolated for each node and each migrated model family',()=> {
    for(const model of ['LSTM','TCN','GRU','PatchTST'] as LoadModel[]) {
      const run=fixture(),n=run.nodes[0];
      n.events=n.events.filter(e=>e.model==='LSTM').map(e=>({...e,model}));
      n.versions=n.versions.filter(v=>v.model==='LSTM').map(v=>({...v,model}));
      n.evaluations=n.evaluations.filter(e=>e.model==='LSTM').map(e=>({...e,model}));
      expect(progress(run,n,407.02,model).value).toBe(57);
      expect(progress(run,n,420,model).value).toBe(6);
    }
    node.events.push({node_id:'Budapest_cloud',model:'LSTM',version:'other-node',time_ms:406*minute,event:'TRAINING'});
    node.events.push({node_id:node.node_id,model:'TCN',version:'other-model',time_ms:406*minute,event:'TRAINING'});
    expect(progress(data,node,407.02).value).toBe(57);
    expect(progress(data,node,407.02,'TCN').phase).toBe('training');
  });

  it('counts only matched evaluation targets and waits for an explicit qualification decision',()=> {
    const p=progress(data,node,349.99);
    expect(p.phase).toBe('evaluating');expect(p.value).toBe(63);
    node.events=node.events.filter(e=>e.event!=='REJECTED');
    expect(progress(data,node,350).value).toBe(64);
    expect(progress(data,node,350).hint).toContain('waiting for the recorded qualification decision');
    node.evaluations=node.evaluations.filter(e=>e.model!=='LSTM' || e.target_ms!==300*minute);
    node.evaluations.push({...node.evaluations.find(e=>e.model==='LSTM')!});
    expect(progress(data,node,350).value).toBe(63);
    expect(progress(data,node,350,'LSTM',true).hint).toContain('qualification remains incomplete');
  });

  it('stops retry progress after qualification, but starts it again after degradation and fallback',()=> {
    node.events=node.events.filter(e=>e.model!=='LSTM' || e.time_ms<=286*minute);
    const add=(event:string,time:number)=>node.events.push({node_id:node.node_id,model:'LSTM',version:'v0001',time_ms:time*minute,event});
    add('QUALIFIED',350);
    expect(loadModelProgress(data,node,'LSTM',350*minute)).toBeUndefined();
    add('PROMOTED',351);
    expect(loadModelProgress(data,node,'LSTM',370*minute)).toBeUndefined();
    add('DEGRADED',380);add('FALLBACK',380);
    const p=progress(data,node,407.02);
    expect(p.phase).toBe('retraining-data');expect(p.value).toBe(27);
    expect(p.total).toBe(64);expect(p.eligibleAtMs).toBe(444*minute);
  });

  it('does not change earlier progress, charts or scores when later records change',()=> {
    const before=buildLoadTimeline(data,node,407.02*minute),row=before.traces[1];
    const plot=buildLoadTimelineChart(before,row);
    node.samples.filter(s=>s.time_ms>407.02*minute).forEach(s=>s.value=1e9);
    node.events.filter(e=>e.time_ms>407.02*minute).forEach(e=>e.event='QUALIFIED');
    node.versions.filter(v=>v.trained_at_ms>407.02*minute).forEach(v=>v.training_windows=3841);
    const after=buildLoadTimeline(data,node,407.02*minute);
    expect(after.traces[1].progress).toEqual(row.progress);expect(after.traces[1].metrics).toEqual(row.metrics);
    expect(buildLoadTimelineChart(after,after.traces[1])).toEqual(plot);
    expect(progress(data,node,273.95).value).toBe(274);
  });

  it('never shows a retraining spinner after final settlement or attaches neural progress to baselines',()=> {
    node.events=node.events.filter(e=>e.model!=='LSTM' || e.time_ms<414*minute);
    node.events.push({node_id:node.node_id,model:'LSTM',version:'v0002',time_ms:414*minute,event:'TRAINING'});
    expect(progress(data,node,487,'LSTM',true).value).toBe(0);
    expect(progress(data,node,487,'LSTM',true).title).toBe('Training incomplete');
    data.baselines=['ARIMA','LR'];
    const rows=buildLoadTimeline(data,node,407*minute).traces;
    expect(rows.filter(t=>t.isBaseline).every(t=>t.progress===undefined)).toBeTrue();
  });
});
