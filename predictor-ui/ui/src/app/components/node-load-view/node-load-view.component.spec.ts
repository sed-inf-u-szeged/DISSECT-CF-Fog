import { NodeLoadViewComponent } from './node-load-view.component';
import { LoadForecast, LoadNode, NodeLoadRun } from '../../shared/NodeLoadRun';

function run(): NodeLoadRun {
  const nodes:LoadNode[]=['west','east'].map((node_id,j)=> {
    const samples=Array.from({length:481},(_,i)=>({index:i,time_ms:i*60000,value:(i%3)*100+j*10}));
    const baseline:LoadForecast[]=samples.map(s=>({node_id,model:'Persistence',version:'fixed-v1',origin_index:s.index,
      origin_ms:s.time_ms,target_index:s.index+1,target_ms:s.time_ms+60000,lead_steps:1,predicted_bytes:s.value,inference_vector_ms:.01}));
    const predictions:LoadForecast[]=[286,414].flatMap((origin,i)=>Array.from({length:64},(_,k)=>({node_id,model:'LSTM',version:i?'v0002':'v0001',
      origin_index:origin,origin_ms:origin*60000,target_index:origin+k+1,target_ms:(origin+k+1)*60000,
      lead_steps:k+1,predicted_bytes:150+j*100,persistence_bytes:samples[origin].value,inference_vector_ms:i?8:4})));
    const evaluate=(p:LoadForecast)=>({...p,actual_bytes:samples[p.target_index].value,absolute_error_bytes:Math.abs(p.predicted_bytes-samples[p.target_index].value)});
    return {node_id,samples,baseline,baselineEvaluations:baseline.slice(0,-1).map(evaluate),predictions,evaluations:predictions.map(evaluate),
      selections:baseline.map(p=>({...p,selection_ms:p.origin_ms})),
      versions:[286,414].map((i,j)=>({model:'LSTM',version:j?'v0002':'v0001',trained_at_ms:i*60000,training_windows:j?96:32,training_seconds:j?20:10})),
      events:[286,414].flatMap((i,j)=>[{node_id,model:'LSTM' as const,version:j?'v0002':'v0001',time_ms:i*60000,event:'TRAINING'},
        {node_id,model:'LSTM' as const,version:j?'v0002':'v0001',time_ms:i*60000,event:'TRAINED'},
        {node_id,model:'LSTM' as const,version:j?'v0002':'v0001',time_ms:(i+64)*60000,event:'REJECTED'}])};
  });
  return {run_id:'test',interval_ms:60000,nodes,models:['LSTM','TCN'],hasBaseline:true,profile:'candidate',
    policy:{matched_targets:64,minimum_within_tolerance_percent:80,relative_tolerance:.2,absolute_tolerance_bytes:.1}};
}
describe('Node-load synchronized replay',()=>{
  let c:NodeLoadViewComponent;
  beforeEach(()=>{c=new NodeLoadViewComponent();c.data=run();c.time=285.5*60000;c.endTime=500*60000;c.ngOnChanges();});
  it('uses the shared clock without revealing future actuals or versions',()=>{
    c.selectMethod('LSTM');expect(c.view.vector.length).toBe(0);expect(c.view.versions.length).toBe(0);
    expect(c.view.lastSample!.time_ms).toBe(285*60000);expect(c.view.comparison[0].count).toBe(0);
    c.time=286.22*60000;expect(c.view.vector.length).toBe(64);expect(c.view.versions.length).toBe(1);
    expect(c.view.versions[0].state).toBe('Evaluating');expect(c.view.forecastLast!.time).toBe(350*60000);
    expect(c.view.lastSample!.time_ms).toBe(286*60000);expect(c.view.comparison[0].count).toBe(0);
  });
  it('matures exact targets, then restores earlier metrics and states when rewinding',()=>{
    c.time=350.22*60000;expect(c.view.comparison[0].count).toBe(64);expect(c.view.versions[0].state).toBe('Rejected');
    c.time=300.22*60000;expect(c.view.comparison[0].count).toBe(14);expect(c.view.versions[0].state).toBe('Evaluating');
    c.time=290.22*60000;expect(c.view.comparison[0].count).toBe(4);expect(c.view.versions[0].matched).toBe(4);
  });
  it('selects one issued vector and computes paired baseline metrics on identical targets',()=>{
    c.selectMethod('LSTM');c.time=430*60000;expect(c.view.vector[0].version).toBe('v0002');
    expect(c.view.comparison[0].count).toBe(80);expect(c.view.baselineMetrics.count).toBe(430);
    c.origin=286*60000;expect(c.view.vector[0].version).toBe('v0001');expect(c.view.latestState).toBe('Rejected');
    c.selectVersion('v0002');expect(c.view.comparison[0].count).toBe(16);expect(c.view.vector[0].version).toBe('v0002');
    const rows=c.node!.evaluations.filter(e=>e.version==='v0002' && e.target_ms<=c.time);
    const expected=rows.reduce((s,r)=>s+Math.abs(r.persistence_bytes!-r.actual_bytes),0)/rows.length;
    expect(c.view.comparison[0].pairedMae).toBeCloseTo(expected,8);
  });
  it('switches nodes and runs without sharing per-node state or stale cached paths',()=>{
    c.selectMethod('LSTM');c.time=300*60000;const first=c.view,clock=c.time;
    c.selectNode('east');expect(c.time).toBe(clock);expect(c.view.forecastPath).not.toBe(first.forecastPath);
    expect(c.view.vector[0].node_id).toBe('east');expect(c.view).toBe(c.view);
    const replacement=run();replacement.run_id='different-run';replacement.nodes[0].samples[0].value=999;c.data=replacement;c.ngOnChanges();
    expect(c.nodeId).toBe('west');expect(c.method).toBe('selected');expect(c.view.maximum).toBe(1000);
  });
  it('preserves node and forecast selection across batches from the same live run',()=>{
    c.time=430*60000;c.selectNode('east');c.selectMethod('LSTM');c.origin=286*60000;
    const before=c.view;c.data={...c.data!};c.ngOnChanges();
    expect(c.nodeId).toBe('east');expect(c.method).toBe('LSTM');expect(c.origin).toBe(286*60000);
    expect(c.view).not.toBe(before);expect(c.view.vector[0].version).toBe('v0001');
  });
  it('retains the unsampled drain gap and does not score unmatched tails as zero',()=>{
    c.time=500*60000;c.final=true;c.selectMethod('Persistence');
    expect(c.view.lastSample!.time_ms).toBe(480*60000);expect(c.view.baselineMetrics.count).toBe(480);
    expect(c.view.selectedMetrics.count).toBe(480);expect(c.view.actualLast!.time).toBe(480*60000);
    expect(c.view.hasFuture).toBeFalse();expect(c.view.forecastOrigin!.origin_ms).toBe(480*60000);
  });
  it('preserves original lead times for selected predictions and promotion/fallback states',()=>{
    const n=c.node!,p=n.predictions.find(p=>p.target_index===351);expect(p).toBeUndefined();
    n.events=n.events.filter(e=>e.event!=='REJECTED');
    n.events.push({node_id:n.node_id,model:'LSTM',version:'v0001',time_ms:350*60000,event:'QUALIFIED'},
      {node_id:n.node_id,model:'LSTM',version:'v0001',time_ms:350*60000,event:'PROMOTED'},
      {node_id:n.node_id,model:'LSTM',version:'v0001',time_ms:370*60000,event:'DEGRADED'},
      {node_id:n.node_id,model:'LSTM',version:'v0001',time_ms:370*60000,event:'FALLBACK'});
    n.events.sort((a,b)=>a.time_ms-b.time_ms);
    const selected={...n.predictions[3],origin_index:350,origin_ms:350*60000,target_index:354,target_ms:354*60000,selection_ms:353*60000};
    n.selections=n.selections.filter(p=>p.selection_ms<selected.selection_ms);n.selections.push(selected);
    c.time=353.22*60000;expect(c.view.selected!.lead_steps).toBe(4);expect(c.view.selected!.origin_ms).toBe(350*60000);
    c.time=360*60000;expect(c.view.versions[0].state).toBe('Active');
    c.time=371*60000;expect(c.view.versions[0].state).toBe('Degraded');
  });
  it('handles missing, samples-only and untrained model data without inventing forecasts',()=>{
    c.data=undefined;c.ngOnChanges();expect(c.view.actual.length).toBe(0);
    c.data=run();c.data.models=[];c.data.hasBaseline=false;
    for(const n of c.data.nodes){n.baseline=[];n.baselineEvaluations=[];n.versions=[];n.predictions=[];n.evaluations=[];n.events=[];n.selections=[];}
    c.ngOnChanges();expect(c.view.lastSample).toBeDefined();expect(c.view.shown.length).toBe(0);expect(c.hasTraining).toBeFalse();
  });
  it('computes inference time once per vector and reuses a snapshot until its inputs change',()=>{
    c.time=430*60000;expect(c.view.versions[0].inference).toBe(4);expect(c.view.versions[1].inference).toBe(8);
    const first=c.view;expect(c.view).toBe(first);c.time+=60000;expect(c.view).not.toBe(first);
  });
});
