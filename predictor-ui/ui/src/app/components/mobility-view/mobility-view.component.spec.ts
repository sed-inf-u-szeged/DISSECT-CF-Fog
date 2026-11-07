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
});
