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
    const frame = c.frame, original = c.trajectory;
    c.errorVersion='v0001'; c.showTruth=true; c.play();
    c.selectDevice(1);
    expect(c.observation.device_id).toBe(1);
    expect(c.frame).toBe(frame); expect(c.trajectory).not.toBe(original);
    expect(c.errorVersion).toBe(''); expect(c.showTruth).toBeFalse(); expect(c.timer).toBeUndefined();
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
    expect(c.chartPath('a','waiting_assignment_bytes')).toBe('M60,130');
    c.choose(1);
    expect(c.chartPath('a','waiting_assignment_bytes')).toBe('M60,130H220V30');
    c.choose(2);
    expect(c.chartPath('a','waiting_assignment_bytes')).toBe('M60,130H220V30');
    expect(c.chartX(c.frame.time_ms)).toBe(860);
    expect(c.chartY(0,'waiting_assignment_bytes')).toBe(180);
    c.choose(0);
    expect(c.chartPath('a','waiting_assignment_bytes')).toBe('M60,130');
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
});
