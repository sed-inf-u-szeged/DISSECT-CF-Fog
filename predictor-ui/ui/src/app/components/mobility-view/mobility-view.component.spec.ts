import { MobilityViewComponent } from './mobility-view.component';
import { MobilityRun } from '../../shared/MobilityRun';

describe('Mobility replay timing', () => {
  let c: MobilityViewComponent;
  beforeEach(() => {
    c = new MobilityViewComponent();
    c.run = {
      label: 'test', props: {}, nodes: [{name:'a',latitude:0,longitude:0,radius:1000},{name:'b',latitude:0,longitude:0.01,radius:1000}],
      observations: [{device_id:0,time_ms:100,latitude:0,longitude:0,previous_node:'NONE',current_node:'a'},
        {device_id:0,time_ms:200,latitude:0,longitude:0.01,previous_node:'a',current_node:'b'}],
      predictions: [{origin_ms:100,target_ms:200,origin_node:'a',node_latitude:0,node_longitude:0,radius_m:1000,
        hold_latitude:0,hold_longitude:0,velocity_latitude:0,velocity_longitude:0.01}],
      evaluations: [{origin_ms:100,target_ms:200,origin_node:'a',actual_node:'b',actual_latitude:0,actual_longitude:0.01,actual_exit:true,hold_exit:false,velocity_exit:true}],
      frames: [{time_ms:100,sample_kind:'OBSERVATION',nodes:[{node:'a',offload_event_sequence:0}]},
        {time_ms:200,sample_kind:'OBSERVATION',nodes:[{node:'b',offload_event_sequence:1}]},
        {time_ms:300,sample_kind:'FINAL',nodes:[{node:'b',offload_event_sequence:2}]}],
      transfers: [{transfer_id:1,event_sequence:1,time_ms:200,event:'START',source_node:'a',destination_node:'b',bytes:10},
        {transfer_id:1,event_sequence:2,time_ms:200,event:'COMPLETE',source_node:'a',destination_node:'b',bytes:10}], summary: []
    } as unknown as MobilityRun;
    c.ngOnChanges();
  });
  afterEach(() => c.ngOnDestroy());
  it('shows issued forecasts before their evaluation matures', () => {
    expect(c.forecast).toBeDefined(); expect(c.matured).toBeUndefined(); expect(c.showTruth).toBeFalse();
    expect(c.exitWarning).toBeTrue(); c.model='hold'; expect(c.exitWarning).toBeFalse();
    c.choose(1); expect(c.matured).toBeDefined();
  });
  it('uses event sequence rather than timestamp alone for transfers', () => {
    c.choose(1); expect(c.transferEvents.length).toBe(1);
    c.choose(2); expect(c.transferEvents.length).toBe(2);
  });
  it('jumps to connection changes and suppresses forecasts at final settlement', () => {
    c.nextConnection(); expect(c.index).toBe(1); expect(c.observation.current_node).toBe('b');
    c.choose(2); expect(c.final).toBeTrue(); expect(c.observation.time_ms).toBe(200); expect(c.forecast).toBeUndefined();
  });
  it('switches device trajectory while preserving simulation time and global node load', () => {
    const first = c.run;
    const second = {...first, observations: first.observations.map(o => ({...o, device_id:1, longitude:o.longitude + .01}))};
    c.run = {...first, devices:[{id:0,data:first},{id:1,data:second}]};
    c.ngOnChanges(); c.choose(1);
    const frame = c.frame, original = c.pathObservations;
    c.errorVersion='v0001'; c.showTruth=true; c.play();
    c.selectDevice(1);
    expect(c.observation.device_id).toBe(1);
    expect(c.frame).toBe(frame); expect(c.pathObservations).not.toEqual(original);
    expect(c.errorVersion).toBe(''); expect(c.showTruth).toBeTrue(); expect(c.timer).toBeUndefined();
    c.selectDevice(99); expect(c.deviceId).toBe(1);
  });
  it('handles staggered startup and keeps a forecast only until its own target time', () => {
    const first = c.run;
    const second = {...first, props:{start_latitude_deg:'0',start_longitude_deg:'0'},
      observations:first.observations.map(o=>({...o,device_id:1,time_ms:o.time_ms+50})),
      predictions:first.predictions.map(p=>({...p,origin_ms:p.origin_ms+50,target_ms:p.target_ms+50}))};
    c.run={...first,devices:[{id:0,data:first},{id:1,data:second}]};
    c.ngOnChanges(); c.selectDevice(1);
    expect(c.frame.time_ms).toBe(100); expect(c.hasObservation).toBeFalse(); expect(c.shownForecast).toBeUndefined();
    c.choose(1);
    expect(c.hasObservation).toBeTrue(); expect(c.observation.time_ms).toBe(150);
    expect(c.shownForecast?.origin_ms).toBe(150); expect(c.shownForecast?.target_ms).toBe(250);
    c.choose(2); expect(c.shownForecast).toBeUndefined();
  });
  it('keeps global node predictions across device switches and seeks the first frame after a load timestamp', () => {
    const first=c.run, nodeLoad={run_id:'shared'} as MobilityRun['nodeLoad'];
    const second={...first, observations:first.observations.map(o=>({...o,device_id:1}))};
    c.run={...first,nodeLoad,devices:[{id:0,data:first},{id:1,data:second}]};c.ngOnChanges();
    c.seekLoadTime(150);expect(c.frame.time_ms).toBe(200);expect(c.nodeLoad).toBe(nodeLoad);
    c.selectDevice(1);expect(c.nodeLoad).toBe(nodeLoad);expect(c.frame.time_ms).toBe(200);
  });
  it('limits displayed movement to known recent observations and retains them during final draining',()=>{
    const original=c.run.observations[0],frame=c.run.frames[0];
    c.run={...c.run,observations:Array.from({length:31},(_,i)=>({...original,time_ms:i*60000,longitude:i/1000})),
      frames:[{...frame,time_ms:20*60000},{...frame,time_ms:50*60000,sample_kind:'FINAL'}]};
    c.ngOnChanges();
    expect(c.pathObservations.map(o=>o.time_ms/60000)).toEqual(Array.from({length:11},(_,i)=>i+10));
    c.pathWindowMinutes=0;expect(c.pathObservations.length).toBe(21);
    c.pathWindowMinutes=10;c.choose(1);
    expect(c.pathObservations.map(o=>o.time_ms/60000)).toEqual(Array.from({length:11},(_,i)=>i+20));
    expect(c.run.observations.length).toBe(31);expect(c.shownForecast).toBeUndefined();
    c.choose(0);expect(c.pathObservations.at(-1)!.time_ms).toBe(20*60000);
  });
  it('recognizes a coincident prediction independently of map scale',()=>{
    c.model='hold';expect(c.predictionAtObservation).toBeTrue();
    c.model='velocity';expect(c.predictionAtObservation).toBeFalse();
    c.choose(2);expect(c.predictionAtObservation).toBeFalse();
  });
});

describe('Mobility replay chart caching', () => {
  function makeRun(): MobilityRun {
    const load = (node: string, waiting: number, tasks: number) => ({node,
      waiting_assignment_bytes: waiting, running_tasks: tasks, offload_event_sequence: 0,
      completed_tasks: 0, device_upload_completed_bytes_derived: 0,
      offload_sent_bytes: 0, offload_received_bytes: 0, device_generated_bytes: 0,
      device_upload_completed_bytes_total: 0});
    return {
      label:'cache-test', props:{},
      nodes:[{name:'a',latitude:0,longitude:0,radius:1000},{name:'b',latitude:0,longitude:.01,radius:1000}],
      observations:[{device_id:0,time_ms:100,latitude:0,longitude:0,previous_node:'NONE',current_node:'a'},
        {device_id:0,time_ms:200,latitude:.001,longitude:.002,previous_node:'a',current_node:'a'}],
      predictions:[], evaluations:[], transfers:[], summary:[],
      frames:[{time_ms:100,sample_kind:'OBSERVATION',nodes:[load('a',100,1),load('b',0,0)]},
        {time_ms:200,sample_kind:'OBSERVATION',nodes:[load('a',300,3),load('b',200,2)]},
        {time_ms:600,sample_kind:'FINAL',nodes:[load('a',0,0),load('b',0,0)]}],
      learning:{run_id:'test',device_id:0,samples:[],predictions:[],selectedPredictions:[],selectedEvaluations:[],events:[],versions:[],
        summary:{promotions:0,fallbacks:0,active_lstm:false,selected_matched:0,lstm_selected_origins:0},
        evaluations:[{model:'LSTM',model_version:'v0001',origin_ms:0,target_ms:100,actual_latitude_deg:0,actual_longitude_deg:0,position_error_m:10},
          {model:'LSTM',model_version:'v0002',origin_ms:100,target_ms:200,actual_latitude_deg:0,actual_longitude_deg:0,position_error_m:20}]}
    };
  }
  let c: MobilityViewComponent;
  beforeEach(() => { c = new MobilityViewComponent(); c.run = makeRun(); c.ngOnChanges(); });
  afterEach(() => c.ngOnDestroy());

  it('preserves exact step geometry, future-free paths and the final settlement gap when seeking backwards', () => {
    expect(c.maximum('waiting_assignment_bytes')).toBe(300);
    expect(c.chartPath('a','waiting_assignment_bytes')).toBe('M50,103.33333333333334');
    c.choose(1);
    expect(c.chartPath('a','waiting_assignment_bytes')).toBe('M50,103.33333333333334H124V30');
    c.choose(2);
    expect(c.chartPath('a','waiting_assignment_bytes')).toBe('M50,103.33333333333334H124V30');
    expect(c.chartX(c.frame.time_ms)).toBe(860);
    expect(c.chartY(0,'waiting_assignment_bytes')).toBe(140);
    c.choose(0);
    expect(c.chartPath('a','waiting_assignment_bytes')).toBe('M50,103.33333333333334');
  });

  it('invalidates errors for time, version and device while sharing global load paths', () => {
    const first = c.run, second = makeRun();
    second.learning!.evaluations[0].position_error_m = 99;
    second.frames = first.frames;
    second.observations = second.observations.map(o => ({...o,device_id:1}));
    c.run = {...first,devices:[{id:0,data:first},{id:1,data:second}]}; c.ngOnChanges();
    expect(c.errorMaximum).toBe(10);
    expect(c.comparisonMetrics[2].count).toBe(1);
    const loadPath = c.chartPath('a','running_tasks'), oldError = c.errorPath('LSTM');
    c.selectDevice(1);
    expect(c.errorMaximum).toBe(100); expect(c.errorPath('LSTM')).not.toBe(oldError);
    expect(c.chartPath('a','running_tasks')).toBe(loadPath);
    c.selectDevice(0); c.choose(1);
    expect(c.errorPath('LSTM').split('M').length - 1).toBe(2); // No line across versions.
    expect(c.comparisonMetrics[2].mean).toBe(15);
    c.errorVersion='v0001';
    expect(c.errorRows.length).toBe(1); expect(c.errorMaximum).toBe(10);
    c.errorVersion='v0002'; c.choose(0);
    expect(c.errorRows.length).toBe(0); expect(c.errorPath('LSTM')).toBe('');
    expect(c.comparisonMetrics[2].mean).toBeNull();
  });

  it('replaces scales and paths when another run has the same frame count and times', () => {
    const oldPath = c.chartPath('a','waiting_assignment_bytes');
    const another = makeRun(); another.frames[1].nodes[0].waiting_assignment_bytes=900;
    another.learning!.evaluations[0].position_error_m=500;
    c.run = another; c.ngOnChanges();
    expect(c.maximum('waiting_assignment_bytes')).toBe(300); // Future peaks do not affect past scales.
    expect(c.chartPath('a','waiting_assignment_bytes')).toBe(oldPath);
    c.choose(1);
    expect(c.maximum('waiting_assignment_bytes')).toBe(900);
    expect(c.chartPath('a','waiting_assignment_bytes')).not.toBe(oldPath);
    expect(c.errorMaximum).toBe(500);
  });

  it('switches shadow forecasts and matures comparison errors without changing serving ownership', () => {
    const run = makeRun();
    run.comparison = {methods:['TCN','XGBoost'],versions:[],predictions:[
      {model:'TCN',model_version:'v0001',device_id:0,origin_ms:100,target_ms:200,origin_node:'a',predicted_latitude_deg:.001,predicted_longitude_deg:.002},
      {model:'XGBoost',model_version:'v0001',device_id:0,origin_ms:100,target_ms:200,origin_node:'a',predicted_latitude_deg:.003,predicted_longitude_deg:.004}],
      evaluations:[{model:'TCN',model_version:'v0001',origin_ms:100,target_ms:200,actual_latitude_deg:.001,actual_longitude_deg:.002,position_error_m:0,predict_wall_ms:2}]};
    c.run = run; c.ngOnChanges(); c.model='TCN';
    expect(c.methods.length).toBe(5); expect(c.shownForecast?.predicted_latitude_deg).toBe(.001);
    expect(c.comparisonMetrics.find(m=>m.model==='TCN')?.count).toBe(0);
    expect(c.latestPositionEvaluation).toBeUndefined();
    c.model='XGBoost'; expect(c.shownForecast?.predicted_latitude_deg).toBe(.003);
    c.choose(1); c.model='TCN';
    expect(c.comparisonMetrics.find(m=>m.model==='TCN')?.count).toBe(1);
    expect(c.comparisonTime('TCN')).toBe(2); expect(c.latestPositionEvaluation?.position_error_m).toBe(0);
    expect(c.comparisonTime('HoldPosition')).toBeNull();
    c.errorVersion='v0002'; expect(c.comparisonMetrics.find(m=>m.model==='TCN')?.count).toBe(0);
    c.choose(2); expect(c.shownForecast).toBeUndefined();
    expect(run.learning!.summary.promotions).toBe(0); expect(run.learning!.summary.active_lstm).toBeFalse();
  });

  it('keeps load-counter reads linear and does not rescan on an unrelated device switch', () => {
    const run=makeRun(); let reads=0;
    run.frames=Array.from({length:128},(_,i)=>({time_ms:100+i,sample_kind:'OBSERVATION',
      nodes:run.frames[0].nodes.map(n=>({...n,get waiting_assignment_bytes(){ reads++; return 100+i; }}))}));
    const second={...run,observations:run.observations.map(o=>({...o,device_id:1}))};
    c.run={...run,devices:[{id:0,data:run},{id:1,data:second}]}; c.ngOnChanges(); c.choose(127);
    for(const node of c.run.nodes) c.chartPath(node.name,'waiting_assignment_bytes');
    expect(reads).toBeLessThanOrEqual(4*run.frames.length);
    const afterRender=reads;
    c.selectDevice(1);
    for(const node of c.run.nodes) c.chartPath(node.name,'waiting_assignment_bytes');
    expect(reads).toBe(afterRender);
  });
  it('links map node selection to aggregate load without moving time or changing devices',()=>{
    const first=makeRun(),second={...first,observations:first.observations.map(o=>({...o,device_id:1}))};
    c.run={...first,devices:[{id:0,data:first},{id:1,data:second}]};c.ngOnChanges();c.choose(1);
    c.selectNode('b');expect(c.selectedNodeId).toBe('b');expect(c.loadNodeFilter).toBe('b');
    expect(c.frame.time_ms).toBe(200);expect(c.deviceId).toBe(0);
    c.selectDevice(1);expect(c.selectedNodeId).toBe('a');expect(c.frame.time_ms).toBe(200);
    c.model='lstm';c.selectDevice(1);expect(c.model).toBe('lstm');
    expect(c.mapTracks.length).toBe(2);expect(c.mapNodes).toBe(first.nodes);
    c.selectNode('unknown');expect(c.selectedNodeId).toBe('a');
    c.selectNode('b');c.choose(0);expect(c.selectedNodeId).toBe('b');
  });

  it('retains node identity/colors when a device or frame lists nodes in another order', () => {
    const first=makeRun(), second={...first,nodes:[...first.nodes].reverse()};
    c.run={...first,devices:[{id:0,data:first},{id:1,data:second}]};c.ngOnChanges();
    const colors=c.orderedNodes.map(n=>c.nodeColor(n.name));
    c.selectDevice(1);expect(c.orderedNodes.map(n=>c.nodeColor(n.name))).toEqual(colors);
    c.loadNodeFilter='b';expect(c.visibleLoadNodes.map(n=>n.name)).toEqual(['b']);
    expect(c.currentLoad('b')!.waiting_assignment_bytes).toBe(0);
  });

  it('clips steps at the selected time window and leaves an unobserved drain window empty', () => {
    const run=makeRun();
    run.frames=Array.from({length:6},(_,i)=>({time_ms:i*60000,sample_kind:'OBSERVATION' as const,
      nodes:run.frames[0].nodes.map(n=>({...n,waiting_assignment_bytes:i*100}))}));
    run.frames.push({time_ms:20*60000,sample_kind:'FINAL',nodes:run.frames[0].nodes});
    c.run=run;c.ngOnChanges();c.choose(4);c.loadWindowMinutes=2;
    expect(c.loadTime(0)).toBe(120000);expect(c.loadTime(3)).toBe(240000);
    expect(c.maximum('waiting_assignment_bytes')).toBe(600);
    expect(c.chartPath('a','waiting_assignment_bytes').startsWith('M50,')).toBeTrue();
    expect((c.chartPath('a','waiting_assignment_bytes').match(/H/g)||[]).length).toBe(3);
    c.choose(6);expect(c.chartPath('a','waiting_assignment_bytes')).toBe('');
    c.loadWindowMinutes=0;expect(c.chartPath('a','waiting_assignment_bytes')).not.toBe('');
    expect(c.chartPath('a','waiting_assignment_bytes')).not.toContain('H420');
    c.choose(0);expect(c.maximum('waiting_assignment_bytes')).toBe(300);
  });

  it('hides an error curve without changing evaluation counts or serving predictions', () => {
    const run=makeRun();run.learning!.evaluations.push({...run.learning!.evaluations[0],model:'ConstantVelocity',position_error_m:1000});
    c.run=run;c.ngOnChanges();const metrics=JSON.stringify(c.comparisonMetrics), serving=c.shownForecast;
    expect(c.errorMaximum).toBe(1000);c.toggleErrorMethod('ConstantVelocity');
    expect(c.errorMaximum).toBe(10);expect(c.visibleMethods).not.toContain('ConstantVelocity');
    expect(JSON.stringify(c.comparisonMetrics)).toBe(metrics);expect(c.shownForecast).toBe(serving);
    c.toggleErrorMethod('ConstantVelocity');expect(c.errorMaximum).toBe(1000);
  });

  it('limits recent errors and their scale while leaving whole-history comparisons unchanged',()=> {
    const run=makeRun(),minute=60000,template=run.learning!.evaluations[0];
    run.frames=[0,120,180].map(t=>({...run.frames[0],time_ms:t*minute}));
    run.learning!.evaluations=[10,70,100,120,150].flatMap(t=>[
      {...template,model:'LSTM' as const,origin_ms:(t-1)*minute,target_ms:t*minute,position_error_m:t===10?5000:100},
      {...template,model:'HoldPosition' as const,origin_ms:(t-1)*minute,target_ms:t*minute,position_error_m:200}]);
    run.learning!.selectedEvaluations=[...run.learning!.evaluations];
    c.run=run;c.ngOnChanges();c.choose(1);
    const metrics=JSON.stringify(c.comparisonMetrics),selected=JSON.stringify(c.selectedMetrics);
    expect(c.errorRows.length).toBe(8);expect(c.errorMaximum).toBe(5000);
    c.errorWindowMinutes=60;
    expect(c.errorRows.length).toBe(6);expect(c.errorMaximum).toBe(200);
    expect(c.errorTime(0)).toBe(60*minute);expect(c.errorTime(3)).toBe(120*minute);
    expect(c.errorX(120*minute)).toBe(860);expect(c.errorEndpoints.every(p=>p.x===100)).toBeTrue();
    c.errorWindowMinutes=30;expect(c.errorRows.length).toBe(4);
    expect(c.errorTime(0)).toBe(90*minute);expect(c.errorRows.every(e=>e.target_ms<=120*minute)).toBeTrue();
    c.toggleErrorMethod('HoldPosition');expect(c.errorMaximum).toBe(100);
    expect(JSON.stringify(c.comparisonMetrics)).toBe(metrics);expect(JSON.stringify(c.selectedMetrics)).toBe(selected);
    c.errorWindowMinutes=0;expect(c.errorRows.length).toBe(8);expect(c.errorMaximum).toBe(5000);
    expect(run.learning!.evaluations.length).toBe(10);
  });

  it('leaves an empty recent window blank and restores version-filtered history when rewound',()=> {
    const run=makeRun(),minute=60000,template=run.learning!.evaluations[0];
    run.frames=[0,60,200].map(t=>({...run.frames[0],time_ms:t*minute}));
    run.learning!.evaluations=[
      {...template,model_version:'v0001',origin_ms:59*minute,target_ms:60*minute},
      {...template,model_version:'v0002',origin_ms:99*minute,target_ms:100*minute}];
    c.run=run;c.ngOnChanges();c.choose(2);c.errorWindowMinutes=60;c.errorVersion='v0001';
    expect(c.hasErrorHistory).toBeTrue();expect(c.errorRows).toEqual([]);expect(c.errorEndpoints).toEqual([]);
    expect(c.errorPath('LSTM')).toBe('');expect(c.comparisonMetrics[2].count).toBe(1);
    c.choose(1);expect(c.errorTime(0)).toBe(0);expect(c.errorRows.length).toBe(1);
    c.choose(0);expect(c.errorRows).toEqual([]);expect(c.hasErrorHistory).toBeFalse();
    expect(c.errorTime(3)-c.errorTime(0)).toBeGreaterThan(0);
    expect(c.comparisonMetrics[2].count).toBe(0);
  });

});

describe('Early Mobility baseline errors',()=>{
  function fixture():MobilityRun {
    const sample=(i:number)=>({device_id:0,time_ms:i*60000,latitude:0,longitude:i/1000,previous_node:'NONE',current_node:'NONE'});
    const p=(model:'HoldPosition'|'ConstantVelocity',i:number)=>({model,model_version:'baseline-v1',device_id:0,origin_ms:i*60000,target_ms:(i+1)*60000,origin_node:'NONE',predicted_latitude_deg:0,predicted_longitude_deg:(i+(model==='ConstantVelocity'?1:0))/1000});
    const predictions=[p('HoldPosition',0),p('HoldPosition',1),p('ConstantVelocity',1),p('HoldPosition',2),p('ConstantVelocity',2)];
    return {label:'early',props:{},observations:[0,1,2,3].map(sample),predictions:[],evaluations:[],transfers:[],summary:[],nodes:[],
      frames:[0,1,2,3].map(i=>({time_ms:i*60000,sample_kind:'OBSERVATION',nodes:[]})),
      baselines:{source:'early-position',predictions,evaluations:predictions.map(p=>({model:p.model,model_version:p.model_version,origin_ms:p.origin_ms,target_ms:p.target_ms,actual_latitude_deg:0,actual_longitude_deg:0,position_error_m:p.model==='HoldPosition'?100:0}))},
      learning:{run_id:'early',device_id:0,samples:[0,1,2,3].map(i=>({time_ms:i*60000,latitude_deg:0,longitude_deg:i/1000})),predictions:[],selectedPredictions:[],selectedEvaluations:[],events:[],versions:[],evaluations:[],summary:{promotions:0,fallbacks:0,active_lstm:false,selected_matched:0,lstm_selected_origins:0}}};
  }
  it('starts at the first observation and waits for the real 60-second target',()=>{
    const c=new MobilityViewComponent();c.run=fixture();c.ngOnChanges();
    expect(c.frame.time_ms).toBe(0);expect(c.shownForecast?.model).toBe('HoldPosition');
    expect(c.errorRows.length).toBe(0);expect(c.latestPositionEvaluation).toBeUndefined();
    c.model='velocity';expect(c.shownForecast).toBeUndefined();
    c.choose(1);expect(c.shownForecast?.model).toBe('ConstantVelocity');
    expect(c.errorRows.map(r=>r.model)).toEqual(['HoldPosition']);
    expect(c.errorEndpoints.length).toBe(1);expect(c.errorEndpoints[0].title).toContain('100.00 m');
    c.choose(2);expect(c.latestPositionEvaluation?.position_error_m).toBe(0);
    expect(c.errorRows.length).toBe(3);c.choose(0);expect(c.errorRows.length).toBe(0);
  });
  it('keeps early curves out of the candidate same-target metric table',()=>{
    const c=new MobilityViewComponent();const run=fixture();
    const base=run.baselines!.evaluations.filter(e=>e.target_ms===180000).map(e=>({...e,model_version:'v0001'}));
    run.learning!.evaluations=[...base,{...base[0],model:'LSTM',position_error_m:20}];
    c.run=run;c.ngOnChanges();c.choose(2);
    expect(c.comparisonMetrics.every(m=>m.count===0)).toBeTrue();expect(c.errorRows.length).toBe(3);
    c.choose(3);expect(c.comparisonMetrics.map(m=>m.count)).toEqual([1,1,1]);expect(c.errorRows.length).toBe(6);
    c.errorVersion='v0001';expect(c.errorRows.length).toBe(3);expect(c.errorRows.every(e=>e.target_ms===180000)).toBeTrue();
  });
});
