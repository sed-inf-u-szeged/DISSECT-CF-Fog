import {ReplaySubject, Subject} from 'rxjs';
import {MobilityLauncherComponent} from './mobility-launcher.component';
import {ElectronService} from '../../services/electron/electron.service';
import {MobilityJobState, MobilityLiveBatch, MobilityLiveEvent} from '../../shared/MobilityJob';

describe('Mobility launcher',()=>{
  let component: MobilityLauncherComponent;
  let starts: any[];
  beforeEach(()=>{
    starts=[];
    component=new MobilityLauncherComponent({startMobilityJob:async(request:any)=>{
      starts.push(request);return {id:'new',stage:'PREPARING',active:true};
    }} as ElectronService);
  });
  afterEach(()=>component.ngOnDestroy());
  it('requires a strategy and passes explicit nomadic waypoints',async()=>{
    await component.run();expect(starts.length).toBe(0);
    component.strategy='nomadic';component.waypointsText='0,0\n1600,0\n0,0';component.config.repeatRoute=true;
    await component.run();expect(starts[0].config.strategy).toBe('nomadic');
    expect(starts[0].config.waypoints).toEqual([[0,0],[1600,0],[0,0]]);
    expect(starts[0].config.repeatRoute).toBeTrue();
  });
  it('ignores overlapping samples and never draws a stale forecast',()=>{
    component.receiveEvents([{type:'META',device_count:1,expected_observations:2,nodes:[{name:'west',latitude:0,longitude:0,radius:1000}]}]);
    const row:MobilityLiveEvent={type:'FRAME',device_id:0,time_ms:60000,latitude:0,longitude:0,sampled:true,
      lifecycle:{serving:'HoldPosition',candidate:'',state:'COLLECTING',matched:0,promotions:0,fallbacks:0,error_m:null,error_target_ms:0,
        forecast:{model:'HoldPosition',origin_ms:60000,target_ms:120000,latitude:0,longitude:0}}};
    component.receiveEvents([row,row]);expect(component.observed).toBe(1);expect(component.target).toBeDefined();
    component.receiveEvents([{...row,time_ms:120000}]);
    expect(component.observed).toBe(2);expect(component.target).toBeUndefined();
  });
  it('sends only enabled comparison models while allowing the legacy run',async()=>{
    component.strategy='random-walk'; component.modelEnabled['XGBoost']=false;
    await component.run(); expect(starts[0].config.models).toEqual(['IMM/Kalman','TCN']);
    component.state={stage:'COMPLETE',active:false};
    component.modelEnabled['IMM/Kalman']=false; component.modelEnabled['TCN']=false;
    await component.run(); expect(starts[1].config.models).toEqual([]);
  });
  it('displays the selected shadow forecast only at its causal origin',()=>{
    component.receiveEvents([{type:'META',device_count:1,expected_observations:2,nodes:[{name:'west',latitude:0,longitude:0,radius:1000}]}]);
    component.previewModel='TCN';
    const row:MobilityLiveEvent={type:'FRAME',device_id:0,time_ms:60000,latitude:0,longitude:0,sampled:true,
      comparison:{time_ms:60000,methods:['TCN'],matched:0,latest:[],forecasts:[{model:'TCN',model_version:'v0001',device_id:0,
        origin_ms:60000,target_ms:120000,origin_node:'west',predicted_latitude_deg:0,predicted_longitude_deg:.001}]}};
    component.receiveEvents([row]); expect(component.target).toBeDefined();
    component.previewModel='XGBoost'; component.refreshChart(); expect(component.target).toBeUndefined();
    component.previewModel='TCN'; component.receiveEvents([{...row,time_ms:120000}]); expect(component.target).toBeUndefined();
  });
  it('keeps observations arriving while a reconnect snapshot is pending',async()=>{
    const states=new ReplaySubject<MobilityJobState>(1),live=new Subject<MobilityLiveBatch>();let resolve:any;
    const snapshot=new Promise<any>(r=>resolve=r);
    component=new MobilityLauncherComponent({mobilityJobUpdates$:states.asObservable(),mobilityLiveUpdates$:live.asObservable(),getMobilityJob:()=>snapshot} as ElectronService);
    states.next({id:'same',stage:'RUNNING',active:true});component.ngOnInit();
    live.next({id:'same',events:[{type:'FRAME',device_id:0,time_ms:2,latitude:1,longitude:1,sampled:true}]});
    resolve({state:{id:'same',stage:'PREPARING',active:true},events:[{type:'POINT',device_id:0,time_ms:1,latitude:0,longitude:0}]});
    await new Promise(resolve=>setTimeout(resolve,0));
    expect(component.observed).toBe(2);expect(component.state.stage).toBe('RUNNING');expect(component.latest?.time_ms).toBe(2);
  });
});
