import { fixture } from '../components/node-load-comparison/node-load-comparison.component.spec';
import { LoadForecast, LoadModel, NodeLoadRun } from './NodeLoadRun';
import { buildLoadTimeline } from './node-load-timeline';
import { NodeLoadComparisonComponent } from '../components/node-load-comparison/node-load-comparison.component';
import { buildAlignedLoadScores } from './node-load-aligned';
const minute=60000;

function modernFixture():NodeLoadRun {
  const data=fixture(); data.baselines=['ARIMA','LR'];data.models=['LSTM','TCN','GRU','PatchTST'];
  for(const n of data.nodes) {
    for(const model of ['GRU','PatchTST'] as LoadModel[]) {
      n.predictions.push(...n.predictions.filter(p=>p.model==='LSTM').map(p=>({...p,model})));
      n.versions.push(...n.versions.filter(p=>p.model==='LSTM').map(p=>({...p,model})));
      n.events.push(...n.events.filter(p=>p.model==='LSTM').map(p=>({...p,model})));
    }
    n.predictions.forEach(p=>delete p.persistence_bytes);
    n.baseline=data.baselines.flatMap(model=>[191,255,286,350,414,478].flatMap(origin=>Array.from({length:64},(_,i):LoadForecast=>({
      node_id:n.node_id,model,version:'online-v1',origin_index:origin,origin_ms:origin*minute,target_index:origin+i+1,
      target_ms:(origin+i+1)*minute,lead_steps:i+1,predicted_bytes:origin+(model==='LR'?10:0),inference_vector_ms:1
    }))));
    n.baseline.sort((a,b)=>a.origin_ms-b.origin_ms||a.target_ms-b.target_ms);
    n.events.sort((a,b)=>a.time_ms-b.time_ms);
    n.selections=[];
  }
  return data;
}

describe('Migrated node-load methods',()=> {
  it('shows both baseline warm-up states without inventing early forecasts',()=> {
    const data=modernFixture(),n=data.nodes[0];
    const v=buildLoadTimeline(data,n,190.9*minute);
    expect(v.traces.map(t=>t.model)).toEqual(['ARIMA','LR','LSTM','TCN','GRU','PatchTST']);
    for(const t of v.traces.slice(0,2)) {expect(t.latest).toEqual([]);expect(t.missing).toContain('191 / 192');}
    const ready=buildLoadTimeline(data,n,191*minute);
    expect(ready.traces[0].futurePoints.length).toBe(64);expect(ready.traces[1].futurePoints.length).toBe(64);
    expect(ready.traces[2].collecting).toBeTrue();
  });
  it('uses the newest issued baseline for overlapping targets without shifting timestamps',()=> {
    const data=modernFixture(),n=data.nodes[0],v=buildLoadTimeline(data,n,300*minute);
    const trace=v.traces[1],all=[...trace.pastPoints,...trace.futurePoints];
    expect(new Set(all.map(p=>p.time)).size).toBe(all.length);
    expect(all.find(p=>p.time===286*minute)!.origin).toBe(255*minute);
    expect(all.find(p=>p.time===287*minute)!.origin).toBe(286*minute);
    expect(all.find(p=>p.time===287*minute)!.value).toBe(296);
  });
  it('scores GRU and PatchTST against ARIMA and LR on the exact same issue time and targets',()=> {
    const data=modernFixture(),n=data.nodes[0],v=buildLoadTimeline(data,n,300*minute);
    for(const t of v.traces.slice(2)) {
      expect(t.metrics.count).toBe(14);expect(t.metrics.pairedMae).toBeNull();
      expect(t.baselineScores.map(s=>s.model)).toEqual(['ARIMA','LR']);
      expect(t.baselineScores.every(s=>s.count===14 && Number.isFinite(s.mae))).toBeTrue();
    }
    const old=JSON.stringify(v.traces.map(t=>t.metrics));
    n.samples.filter(s=>s.time_ms>300*minute).forEach(s=>s.value=1e9);
    expect(JSON.stringify(buildLoadTimeline(data,n,300*minute).traces.map(t=>t.metrics))).toBe(old);
  });
  it('keeps six rows and handles baseline-only runs without adding Persistence',()=> {
    const data=modernFixture(),c=new NodeLoadComparisonComponent();c.data=data;c.node=data.nodes[0];c.time=300*minute;c.ngOnChanges();
    expect(c.charts.length).toBe(6);c.toggle('GRU');expect(c.charts.length).toBe(5);
    data.models=[];data.nodes[0].predictions=[];c.data={...data};c.ngOnChanges();
    expect(c.charts.map(t=>t.model)).toEqual(['ARIMA','LR']);
  });
});

describe('Same-window load score table',()=> {
  it('compares the same completed block when latest model cards have different issue times',()=> {
    const data=modernFixture(),n=data.nodes[0],before=JSON.stringify(data);
    const timeline=buildLoadTimeline(data,n,403.2*minute),scores=buildAlignedLoadScores(data,n,403.2*minute);
    expect(timeline.traces[0].latest[0].origin_ms).toBe(350*minute);
    expect(timeline.traces[2].latest[0].origin_ms).toBe(286*minute);
    expect(scores.origin).toBe(286*minute);expect(scores.start).toBe(287*minute);expect(scores.end).toBe(350*minute);
    expect(scores.common).toBeTrue();expect(scores.rows.every(r=>r.metrics.count===64)).toBeTrue();
    expect(scores.rows[0].metrics.mae).toBeCloseTo(n.samples.filter(s=>s.index>=287&&s.index<=350).reduce((sum,s)=>sum+Math.abs(286-s.value),0)/64,10);
    expect(JSON.stringify(data)).toBe(before);
    expect(buildAlignedLoadScores(data,n,420*minute).origin).toBe(286*minute);
    expect(buildAlignedLoadScores(data,n,478*minute).origin).toBe(414*minute);
  });
  it('shows unavailable methods at an explicitly chosen origin instead of substituting old forecasts',()=> {
    const data=modernFixture(),n=data.nodes[0],scores=buildAlignedLoadScores(data,n,403.2*minute,350*minute);
    expect(scores.origin).toBe(350*minute);expect(scores.common).toBeFalse();expect(scores.available).toBe(2);
    expect(scores.rows.slice(0,2).every(r=>r.available&&r.metrics.count===53)).toBeTrue();
    expect(scores.rows.slice(2).every(r=>!r.available&&r.metrics.count===0&&r.metrics.mae===null&&r.metrics.within===null)).toBeTrue();
    const unseen=buildAlignedLoadScores(data,n,403.2*minute,414*minute);
    expect(unseen.origin).toBeUndefined();expect(unseen.options.every(o=>o.origin<=403.2*minute)).toBeTrue();
  });
  it('scores partial shared blocks only against actual observations available at replay time',()=> {
    const data=modernFixture(),n=data.nodes[0];
    const issued=buildAlignedLoadScores(data,n,286*minute);
    expect(issued.origin).toBe(286*minute);expect(issued.rows.every(r=>r.metrics.count===0&&r.metrics.mae===null)).toBeTrue();
    const before=buildAlignedLoadScores(data,n,300*minute);
    expect(before.rows.every(r=>r.metrics.count===14)).toBeTrue();
    n.samples.filter(s=>s.time_ms>300*minute).forEach(s=>s.value=1e9);
    n.predictions.filter(p=>p.origin_ms>300*minute).forEach(p=>p.predicted_bytes=1e9);
    expect(buildAlignedLoadScores(data,n,300*minute)).toEqual(before);
    const earlier=buildAlignedLoadScores(data,n,285*minute);
    expect(earlier.origin).toBeUndefined();expect(earlier.missing).toContain('shared');
    expect(earlier.rows.every(r=>r.metrics.mae===null)).toBeTrue();
  });
  it('uses one identical observed-target mask, including missing samples, for all methods',()=> {
    const data=modernFixture(),n=data.nodes[0];
    n.samples=n.samples.filter(s=>![290,330].includes(s.index));
    const scores=buildAlignedLoadScores(data,n,403.2*minute);
    expect(scores.origin).toBe(286*minute);expect(scores.matched).toBe(62);
    expect(scores.rows.every(r=>r.metrics.count===62)).toBeTrue();
    const actual=n.samples.filter(s=>s.index>=287&&s.index<=350);
    expect(scores.rows[2].metrics.mae).toBeCloseTo(actual.reduce((sum,s)=>sum+Math.abs(140-s.value),0)/62,10);
  });
  it('excludes incomplete or ambiguous vectors while retaining a prior unambiguous shared block',()=> {
    const data=modernFixture(),n=data.nodes[0];
    n.predictions=n.predictions.filter(p=>!(p.model==='TCN'&&p.origin_index===414&&p.lead_steps===64));
    n.predictions.push(...n.predictions.filter(p=>p.model==='GRU'&&p.origin_index===414).map(p=>({...p,version:'duplicate'})));
    expect(buildAlignedLoadScores(data,n,480*minute).origin).toBe(286*minute);
    const partial=buildAlignedLoadScores(data,n,480*minute,414*minute);
    expect(partial.available).toBe(4);
    expect(partial.rows.filter(r=>!r.available).map(r=>r.model)).toEqual(['TCN','GRU']);
  });
  it('supports baseline-only runs and preserves legacy paired references without inventing vectors',()=> {
    const data=modernFixture();data.models=[];const n=data.nodes[0];
    const base=buildAlignedLoadScores(data,n,300*minute);
    expect(base.rows.map(r=>r.model)).toEqual(['ARIMA','LR']);expect(base.origin).toBe(191*minute);
    const legacy=fixture(),ln=legacy.nodes[0],shared=buildAlignedLoadScores(legacy,ln,403*minute);
    expect(shared.common).toBeTrue();expect(shared.rows[0].model).toBe('Persistence');
    expect(shared.rows[0].metrics.mae).toBe(shared.rows[1].metrics.pairedMae);
    ln.predictions.find(p=>p.model==='TCN'&&p.origin_index===286)!.persistence_bytes=999;
    const inconsistent=buildAlignedLoadScores(legacy,ln,403*minute,286*minute);
    expect(inconsistent.rows[0].available).toBeFalse();expect(inconsistent.common).toBeFalse();
    expect(buildAlignedLoadScores(legacy,ln,100*minute).origin).toBeUndefined();
  });
  it('keeps selection and scores stable through visibility and live updates, then resets an unseen origin on rewind',()=> {
    const data=modernFixture(),c=new NodeLoadComparisonComponent();c.data=data;c.node=data.nodes[0];c.time=403.2*minute;c.ngOnChanges();
    c.comparisonOrigin=350*minute;const before=c.alignedScores;
    c.toggle('ARIMA');c.toggle('LSTM');c.setFilter('actual',false);
    expect(c.alignedScores).toBe(before);
    c.data={...data};c.ngOnChanges();expect(c.comparisonOrigin).toBe(350*minute);expect(c.alignedScores).toEqual(before);
    c.time=300*minute;c.ngOnChanges();expect(c.comparisonOrigin).toBe(-1);expect(c.alignedScores.matched).toBe(14);
    c.comparisonOrigin=286*minute;c.node=data.nodes[1];c.ngOnChanges();
    expect(c.comparisonOrigin).toBe(-1);expect(c.hidden.size).toBe(0);
    expect(c.alignedScores.rows[2].metrics.mae).not.toBe(before.rows[2].metrics.mae);
  });
});
