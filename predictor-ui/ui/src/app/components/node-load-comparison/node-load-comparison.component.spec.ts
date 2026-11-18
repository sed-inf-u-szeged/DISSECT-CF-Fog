import { NodeLoadComparisonComponent } from './node-load-comparison.component';
import { buildLoadComparison, loadPlotPath } from '../../shared/node-load-comparison';
import { LoadForecast, LoadModel, LoadNode, NodeLoadRun } from '../../shared/NodeLoadRun';
import { buildLoadChart, loadCanvasPoints } from '../../shared/node-load-chart';
import { buildLoadTimelineChart } from '../../shared/node-load-timeline';

const minute=60000;
export function fixture(): NodeLoadRun {
  const nodes: LoadNode[]=['Sopron_fog2','Budapest_cloud'].map((node_id,j)=> {
    const samples=Array.from({length:481},(_,i)=>({index:i,time_ms:i*minute,value:(i%3)*100+j*10}));
    const baseline:LoadForecast[]=samples.map(s=>({node_id,model:'Persistence',version:'fixed-v1',origin_index:s.index,
      origin_ms:s.time_ms,target_index:s.index+1,target_ms:s.time_ms+minute,lead_steps:1,predicted_bytes:s.value,inference_vector_ms:0}));
    const predictions:LoadForecast[]=(['LSTM','TCN'] as LoadModel[]).flatMap(model=>[286,414].flatMap((o,j)=>Array.from({length:64},(_,k)=>({
      node_id,model,version:j?'v0002':'v0001',origin_index:o,origin_ms:o*minute,target_index:o+k+1,target_ms:(o+k+1)*minute,
      lead_steps:k+1,predicted_bytes:model==='LSTM'?140:160,persistence_bytes:samples[o].value,inference_vector_ms:10}))));
    const evaluate=(p:LoadForecast)=>({...p,actual_bytes:samples[p.target_index].value,absolute_error_bytes:Math.abs(p.predicted_bytes-samples[p.target_index].value)});
    return {node_id,samples,baseline,predictions,baselineEvaluations:baseline.slice(0,-1).map(evaluate),evaluations:predictions.map(evaluate),
      selections:baseline.map(p=>({...p,selection_ms:p.origin_ms})),
      versions:(['LSTM','TCN'] as LoadModel[]).flatMap(model=>[286,414].map((o,j)=>({model,version:j?'v0002':'v0001',trained_at_ms:o*minute,training_windows:j?96:32,training_seconds:2}))),
      events:(['LSTM','TCN'] as LoadModel[]).flatMap(model=>[286,414].flatMap((o,j)=>[
        {node_id,model,version:j?'v0002':'v0001',time_ms:o*minute,event:'TRAINED'},
        {node_id,model,version:j?'v0002':'v0001',time_ms:(o+64)*minute,event:'REJECTED'}])).sort((a,b)=>a.time_ms-b.time_ms)};
  });
  return {run_id:'comparison-test',interval_ms:minute,nodes,models:['LSTM','TCN'],hasBaseline:true,
    policy:{matched_targets:64,minimum_within_tolerance_percent:80,relative_tolerance:.2,absolute_tolerance_bytes:.1}};
}

describe('Three-phase aligned load comparison',()=> {
  let data:NodeLoadRun,node:LoadNode;
  beforeEach(()=>{data=fixture();node=data.nodes[0];});
  it('does not reveal future observations, training or forecasts before first fit',()=> {
    const v=buildLoadComparison(data,node,285.5*minute);
    expect(v.horizon).toBe(1);expect(v.traces[1].future.length).toBe(0);expect(v.traces[1].state).toBe('Unavailable');
    expect(v.actual.every(s=>s.time<=285*minute)).toBeTrue();expect(v.options.every(o=>o.origin<=285*minute)).toBeTrue();
    const fit=buildLoadComparison(data,node,286.22*minute);
    expect(fit.origin).toBe(286*minute);expect(fit.horizon).toBe(64);expect(fit.missingTest).toBeTrue();
    expect(fit.traces[1].state).toBe('Evaluating');expect(fit.traces[1].forecastMetrics.count).toBe(0);
    expect(fit.traces[1].future.at(-1)!.target_ms).toBe(350*minute);
  });
  it('keeps an earlier rejected version test and the unrecorded gap before a new version',()=> {
    const v=buildLoadComparison(data,node,414.22*minute), lstm=v.traces[1];
    expect(v.testStart).toBe(286*minute);expect(v.testEnd).toBe(350*minute);expect(v.gap).toBe(64*minute);
    expect(lstm.testVersion).toBe('v0001');expect(lstm.version).toBe('v0002');expect(lstm.testMetrics.count).toBe(64);
    expect(lstm.testPoints.at(-1)!.time).toBe(350*minute);expect(lstm.futurePoints[0].time).toBe(414*minute);
    expect(lstm.forecastMetrics.count).toBe(0);expect(lstm.state).toBe('Evaluating');
  });
  it('uses the most recent shared origin and never moves a missing model vector',()=> {
    node.predictions=node.predictions.filter(p=>!(p.model==='TCN' && p.origin_index===414));
    const v=buildLoadComparison(data,node,430*minute);
    expect(v.origin).toBe(286*minute);expect(v.common).toBeTrue();
    const partial=buildLoadComparison(data,node,430*minute,414*minute);
    expect(partial.origin).toBe(414*minute);expect(partial.common).toBeFalse();
    expect(partial.traces[1].future.length).toBe(64);expect(partial.traces[2].future.length).toBe(0);
    expect(partial.traces[2].missing).toContain('exact origin');
  });
  it('compares every model with the same-origin 64-target Persistence reference',()=> {
    const v=buildLoadComparison(data,node,430*minute);
    const [base,lstm,tcn]=v.traces;
    expect(base.future.length).toBe(64);
    expect(base.future.every(p=>p.origin_ms===414*minute && p.predicted_bytes===node.samples[414].value)).toBeTrue();
    expect(base.future.map(p=>p.target_ms)).toEqual(lstm.future.map(p=>p.target_ms));
    expect(lstm.future.map(p=>p.target_ms)).toEqual(tcn.future.map(p=>p.target_ms));
    expect(base.testMetrics.count).toBe(64);expect(base.testMetrics.mae).toBe(lstm.testMetrics.pairedMae);
    expect(lstm.forecastMetrics.count).toBe(16);
  });
  it('does not allow unseen actual spikes to change the chart scale or metrics',()=> {
    const before=buildLoadComparison(data,node,286.22*minute);
    node.samples.filter(s=>s.time_ms>286.22*minute).forEach(s=>s.value=1000000000);
    const after=buildLoadComparison(data,node,286.22*minute);
    expect(after.maximum).toBe(before.maximum);expect(after.actual).toEqual(before.actual);
    expect(after.traces[1].forecastMetrics).toEqual(before.traces[1].forecastMetrics);
  });
  it('rewinds evaluation counts and version states without exposing a later rejection',()=> {
    const later=buildLoadComparison(data,node,480*minute),earlier=buildLoadComparison(data,node,430*minute);
    expect(later.traces[1].state).toBe('Rejected');expect(later.traces[1].forecastMetrics.count).toBe(64);
    expect(earlier.traces[1].state).toBe('Evaluating');expect(earlier.traces[1].forecastMetrics.count).toBe(16);
  });
  it('keeps short baseline-only runs as one-step predictions rather than invented 64-step vectors',()=> {
    data.models=[];node.predictions=[];node.versions=[];node.events=[];
    const v=buildLoadComparison(data,node,110.67*minute);
    expect(v.horizon).toBe(1);expect(v.origin).toBe(110*minute);expect(v.traces.length).toBe(1);
    expect(v.traces[0].future.length).toBe(1);expect(v.traces[0].future[0].target_ms).toBe(111*minute);
    expect(v.traces[0].evaluation.length).toBe(64);expect(v.traces[0].evaluation.every(p=>p.lead_steps===1)).toBeTrue();
  });
  it('retains missing observations and unmatched final targets, without synthetic zeros',()=> {
    for(const p of node.predictions.filter(p=>p.origin_index===414)) {
      p.origin_index+=60;p.origin_ms+=60*minute;p.target_index+=60;p.target_ms+=60*minute;
    }
    node.versions.filter(v=>v.version==='v0002').forEach(v=>v.trained_at_ms+=60*minute);
    const v=buildLoadComparison(data,node,500*minute,-1,true);
    expect(v.actual.at(-1)!.time).toBe(480*minute);
    expect(v.traces[1].forecastMetrics.count).toBe(6);
    expect(v.traces[1].futurePoints.every(p=>p.time<=500*minute)).toBeTrue();
    expect(v.traces[1].future.length).toBe(64);
  });
  it('does not turn samples-only files into predictions or zero errors',()=> {
    data.models=[];data.hasBaseline=false;node.baseline=[];node.predictions=[];
    const v=buildLoadComparison(data,node,30*minute);
    expect(v.origin).toBeUndefined();expect(v.traces[0].future).toEqual([]);
    expect(v.traces[0].testMetrics.mae).toBeNull();expect(v.actual.length).toBeGreaterThan(0);
  });
  it('does not silently choose between ambiguous versions or incomplete vectors',()=> {
    const ambiguous=node.predictions.filter(p=>p.model==='LSTM' && p.origin_index===414).map(p=>({...p,version:'ambiguous'}));
    node.predictions.push(...ambiguous);
    node.predictions=node.predictions.filter(p=>!(p.model==='TCN' && p.origin_index===414 && p.lead_steps===64));
    const v=buildLoadComparison(data,node,430*minute);
    expect(v.origin).toBe(286*minute);expect(v.options.some(o=>o.origin===414*minute)).toBeFalse();
  });
  it('breaks plot paths across missing intervals and between different forecast origins',()=> {
    const v=buildLoadComparison(data,node,430*minute);
    const path=loadPlotPath(v,[{time:300*minute,value:1,group:'a'},{time:301*minute,value:2,group:'a'},
      {time:303*minute,value:3,group:'a'},{time:304*minute,value:4,group:'b'}]);
    expect(path.split('M').length-1).toBe(3);expect(path).not.toMatch(/NaN|Infinity/);
  });
  it('keeps model visibility separate from replay, metrics and node state',()=> {
    const c=new NodeLoadComparisonComponent();c.data=data;c.node=node;c.time=430*minute;c.ngOnChanges();
    const first=c.view;expect(c.view).toBe(first);expect(c.charts.length).toBe(3);
    c.toggle('TCN');expect(c.charts.length).toBe(2);
    c.setFilter('actual',false);c.setFilter('test',false);c.setFilter('commonY',false);
    expect(c.view).toBe(first);expect(c.view.traces[2].metrics.count).toBe(16);
    c.node=data.nodes[1];c.ngOnChanges();expect(c.view).not.toBe(first);expect(c.role()).toBe('Cloud');
    expect(c.view.traces[0].latest[0].node_id).toBe('Budapest_cloud');
    c.time=300*minute;c.ngOnChanges();expect(c.view.start).toBe(108*minute);expect(c.view.traces[1].latest[0].origin_ms).toBe(286*minute);
  });
  it('uses one absolute origin for every relative axis and preserves all displayed values',()=> {
    const v=buildLoadComparison(data,node,430*minute);
    for(const t of v.traces) {
      const chart=buildLoadChart(v,t);
      expect(chart.minX).toBe(-320);expect(chart.maxX).toBe(64);
      for(const [i,source] of [v.actual,t.testPoints,t.futurePoints].entries()) {
        const shown=chart.series[i].points.filter(p=>p.y!==null);
        expect(shown.map(p=>p.y)).toEqual(source.map(p=>p.value));
        expect(shown.map(p=>p.x*minute+v.anchor)).toEqual(source.map(p=>p.time));
      }
      expect(chart.series[1].points.at(-1)!.x).toBe(-64);
      expect(chart.series[2].points[0].x).toBe(0);
      expect(chart.series.map(s=>s.color)).toEqual(['#e8be6d','#7185ef','#ef5b7d']);
    }
  });
  it('shares Y bounds across models and supports independent scales without hiding negative predictions',()=> {
    node.predictions.filter(p=>p.model==='TCN' && p.origin_index===414).forEach(p=>p.predicted_bytes=1250);
    node.predictions.find(p=>p.model==='LSTM' && p.origin_index===414)!.predicted_bytes=-35;
    const v=buildLoadComparison(data,node,430*minute);
    const charts=v.traces.map(t=>buildLoadChart(v,t));
    expect(charts.every(c=>c.minY===-1000 && c.maxY===2000)).toBeTrue();
    expect(buildLoadChart(v,v.traces[1],false).maxY).toBeLessThan(charts[1].maxY);
    expect(charts[1].series[2].points.some(p=>p.y===-35)).toBeTrue();
  });
  it('breaks canvas curves at missing samples or changed vectors and marks isolated points',()=> {
    const points=loadCanvasPoints([{time:minute,value:10,group:'a'},{time:2*minute,value:20,group:'a'},
      {time:4*minute,value:40,group:'a'},{time:5*minute,value:50,group:'b'}],2*minute,minute);
    expect(points.filter(p=>p.y===null).length).toBe(2);
    expect(points.filter(p=>p.isolated).map(p=>p.y)).toEqual([40,50]);
    expect(points.filter(p=>p.y!==null).map(p=>p.x)).toEqual([-1,0,2,3]);
  });
  it('keeps unseen samples out of canvas series and scale, including expanded views',()=> {
    const v=buildLoadComparison(data,node,414.22*minute),chart=buildLoadChart(v,v.traces[1]);
    expect(chart.series[0].points.every(p=>p.time<=414*minute)).toBeTrue();
    node.samples.filter(s=>s.time_ms>414.22*minute).forEach(s=>s.value=1e9);
    const after=buildLoadComparison(data,node,414.22*minute);
    expect(buildLoadChart(after,after.traces[1])).toEqual(chart);
  });
  it('preserves replay, expanded model and curve filters when live data arrives',()=> {
    const c=new NodeLoadComparisonComponent();c.data=data;c.node=node;c.time=430*minute;c.ngOnChanges();
    c.toggle('TCN');c.setFilter('actual',false);c.openDetail('LSTM');
    const before=c.view;
    c.data={...data,nodes:[...data.nodes]};c.ngOnChanges();
    expect(c.time).toBe(430*minute);expect(c.detailModel).toBe('LSTM');
    expect(c.hidden.has('TCN')).toBeTrue();expect(c.filters.actual).toBeFalse();
    expect(c.view).not.toBe(before);expect(c.view).toEqual(before);
    c.data={...data,run_id:'another-run'};c.ngOnChanges();
    expect(c.detailModel).toBe('');expect(c.hidden.size).toBe(0);
  });
  it('keeps expanded view on the global clock and resets expansion on node change',()=> {
    const c=new NodeLoadComparisonComponent();c.data=data;c.node=node;c.time=430*minute;c.ngOnChanges();
    c.openDetail('LSTM');expect(c.detailTrace!.version).toBe('v0002');
    c.time=300*minute;c.ngOnChanges();expect(c.detailTrace!.version).toBe('v0001');
    expect(c.view.start).toBe(108*minute);expect(c.view.end).toBe(364*minute);
    c.node=data.nodes[1];c.ngOnChanges();expect(c.detailModel).toBe('');
    expect(c.view.traces[1].latest[0].node_id).toBe('Budapest_cloud');
  });
  it('shows observed load in every enabled row while collecting history, including backwards seeks',()=> {
    const c=new NodeLoadComparisonComponent();c.data=data;c.node=node;c.time=43.22*minute;c.ngOnChanges();
    expect(c.charts.map(t=>t.model)).toEqual(['Persistence','LSTM','TCN']);
    expect(c.view.sampleCount).toBe(44);expect(c.charts[1].missing).toContain('44 / 287');
    c.toggle('Persistence');c.toggle('TCN');
    for(const time of [63.45,119.04,305.15,119.04]) {
      c.time=time*minute;c.ngOnChanges();
      expect(c.charts.map(t=>t.model)).toEqual(['LSTM']);
      const plot=buildLoadTimelineChart(c.view,c.charts[0]);
      expect(plot.series[0].points.filter(p=>p.y!==null).map(p=>p.y)).toEqual(c.view.actual.map(p=>p.value));
      expect(c.view.actual.every(p=>p.time<=time*minute)).toBeTrue();
      if(time<286) {expect(c.charts[0].latest).toEqual([]);expect(c.charts[0].metrics.mae).toBeNull();}
    }
    c.final=true;expect(c.endedBeforeTraining).toBeTrue();c.final=false;
    c.time=286*minute;c.ngOnChanges();
    expect(c.charts[0].collecting).toBeFalse();expect(c.charts[0].hasRecorded).toBeTrue();
    c.time=43.22*minute;c.ngOnChanges();
    expect(c.charts[0].readiness).toBe('Collecting history');expect(c.view.sampleCount).toBe(44);
  });
  it('retains actuals and issued predictions after rejection, without filling the unrecorded gap',()=> {
    const c=new NodeLoadComparisonComponent();c.data=data;c.node=node;c.time=375*minute;c.ngOnChanges();
    c.toggle('Persistence');c.toggle('TCN');
    const row=c.charts[0],plot=buildLoadTimelineChart(c.view,row);
    expect(row.state).toBe('Rejected');expect(row.futurePoints).toEqual([]);
    expect(row.pastPoints.at(-1)!.time).toBe(350*minute);
    expect(plot.series[0].points.at(-1)!.time).toBe(375*minute);
    c.setFilter('actual',false);expect(c.charts[0]).toBe(row);expect(row.metrics.count).toBe(64);
    c.toggle('LSTM');expect(c.charts).toEqual([]);
  });
  it('shows actuals without substituting predictions while the first training is running',()=> {
    node.predictions=[];node.versions=[];node.events=[{node_id:node.node_id,model:'LSTM',version:'v1',time_ms:286*minute,event:'TRAINING'}];
    const c=new NodeLoadComparisonComponent();c.data=data;c.node=node;c.time=286*minute;c.ngOnChanges();
    expect(c.charts[1].readiness).toBe('Training');expect(c.charts[1].latest).toEqual([]);
    expect(c.view.actual.at(-1)!.time).toBe(286*minute);
  });
  it('does not report training history as still collecting once a training event is visible',()=> {
    node.events.push({node_id:node.node_id,model:'LSTM',version:'v0001',time_ms:285.9*minute,event:'TRAINING'});
    node.events.sort((a,b)=>a.time_ms-b.time_ms);
    const v=buildLoadComparison(data,node,285.95*minute);
    expect(v.traces[1].readiness).toBe('Training');expect(v.traces[1].collecting).toBeFalse();
    expect(v.traces[1].future).toEqual([]);expect(v.traces[1].forecastMetrics.mae).toBeNull();
  });
  it('focuses the viewport without changing saved curves, metric windows or causal scales',()=> {
    const v=buildLoadComparison(data,node,430*minute),trace=v.traces[1];
    const context=buildLoadChart(v,trace),focused=buildLoadChart(v,trace,true,'forecast');
    expect(focused.minX).toBe(-16);expect(focused.maxX).toBe(64);
    expect(focused.series).toEqual(context.series);expect(trace.forecastMetrics.count).toBe(16);
    node.samples.filter(s=>s.time_ms>430*minute).forEach(s=>s.value=1e9);
    const after=buildLoadComparison(data,node,430*minute);
    expect(buildLoadChart(after,after.traces[1],true,'forecast')).toEqual(focused);
    const short=buildLoadComparison(data,node,43.22*minute);
    const baseline=buildLoadChart(short,short.traces[0],true,'forecast');
    expect(baseline.minX).toBe(-3);expect(baseline.maxX).toBe(1);
    expect(short.traces[0].future.length).toBe(1);
  });
});
