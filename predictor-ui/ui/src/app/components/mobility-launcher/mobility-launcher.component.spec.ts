import {mapDevices,mapProjection} from '../../shared/mobility-map';
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
  it('holds a shared historical time while new device frames arrive, then follows latest on request',()=>{
    const frame=(time:number,device=0):MobilityLiveEvent=>({type:'FRAME',device_id:device,time_ms:time,latitude:0,longitude:time/1e7,sampled:true});
    component.receiveEvents([{type:'META',device_count:2,expected_observations:5,nodes:[{name:'west',latitude:0,longitude:0,radius:1000}]},frame(60000),frame(90000,1),frame(120000)]);
    expect(component.replayTime).toBe(120000);
    component.seekTime(60000);const path=JSON.stringify(mapDevices(component.mapTracks,component.replayTime,10,mapProjection(component.mapNodes)));
    component.receiveEvents([frame(180000),frame(180000,1)]);
    expect(component.replayTime).toBe(60000);expect(component.latestTime).toBe(180000);expect(JSON.stringify(mapDevices(component.mapTracks,component.replayTime,10,mapProjection(component.mapNodes)))).toBe(path);
    component.selectedDevice=1;component.refreshChart();expect(component.latest).toBeUndefined();
    component.receiveState({id:component.state.id,stage:'COMPLETE',active:false});expect(component.replayTime).toBe(60000);
    component.followLatest();expect(component.replayTime).toBe(180000);expect(component.latest?.time_ms).toBe(180000);
  });
  it('deduplicates a reconnect snapshot before accepting new sequenced events',()=>{
    const frame:MobilityLiveEvent={type:'FRAME',sequence:2,device_id:0,time_ms:60000,latitude:0,longitude:0,sampled:true};
    component.receiveEvents([{type:'META',sequence:1,device_count:1,nodes:[{name:'west',latitude:0,longitude:0,radius:1000}]},frame]);
    component.seekTime(0);component.receiveEvents([frame,{...frame,sequence:3,time_ms:120000}]);
    expect(component.observed).toBe(2);expect(component.latestTime).toBe(120000);expect(component.replayTime).toBe(0);
  });
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
    component.receiveEvents([row,row]);expect(component.observed).toBe(1);expect(component.mapForecast).toBeDefined();
    component.receiveEvents([{...row,time_ms:120000}]);
    expect(component.observed).toBe(2);expect(component.mapForecast).toBeUndefined();
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
    component.receiveEvents([row]); expect(component.mapForecast).toBeDefined();
    expect(component.previewForecast?.origin_ms).toBe(60000);expect(component.previewForecast?.target_ms).toBe(120000);
    expect(component.previewForecast?.model).toBe('TCN');
    component.previewModel='XGBoost'; component.refreshChart(); expect(component.mapForecast).toBeUndefined();
    expect(component.previewForecast).toBeUndefined();
    component.previewModel='TCN'; component.receiveEvents([{...row,time_ms:120000}]); expect(component.mapForecast).toBeUndefined();
  });
  it('keeps node-load choices separate from mobility comparison choices',async()=>{
    component.strategy='random-walk'; component.loadModelEnabled.LSTM=true;
    component.loadModelEnabled.TCN=true; component.loadProfile='candidate'; component.config.minutes=480;
    await component.run();
    expect(starts[0].config.loadModels).toEqual(['LSTM','TCN']);
    expect(starts[0].config.loadEpochs).toBe(40);
    expect(starts[0].config.models).toEqual(['IMM/Kalman','XGBoost','TCN']);
    expect(starts[0].config.minutes).toBe(480);
  });
  it('matches retrospective truth only to the selected device and exact forecast target already received',()=>{
    const forecast={model:'HoldPosition',origin_ms:60000,target_ms:120000,latitude:0,longitude:0};
    const row:MobilityLiveEvent={type:'FRAME',device_id:0,time_ms:60000,latitude:0,longitude:0,sampled:true,
      lifecycle:{serving:'HoldPosition',candidate:'',state:'COLLECTING',matched:0,promotions:0,fallbacks:0,error_m:null,error_target_ms:0,forecast}};
    component.receiveEvents([{type:'META',device_count:2,nodes:[{name:'west',latitude:0,longitude:0,radius:20000}]},row,{...row,device_id:1,time_ms:65000}]);
    component.seekTime(65000);component.showTruth=true;
    expect(component.targetObservation).toBeUndefined();
    component.receiveEvents([{...row,device_id:1,time_ms:120000,longitude:.5}]);
    expect(component.targetObservation).toBeUndefined();
    component.receiveEvents([{...row,time_ms:120000,longitude:.002}]);
    expect(component.replayTime).toBe(65000);expect(component.mapForecast?.target_ms).toBe(120000);
    expect(component.targetObservation?.longitude).toBe(.002);expect(component.latest?.longitude).toBe(0);
    expect(component.showTruth).toBeTrue();
    component.previewModel='TCN';component.refreshChart();expect(component.targetObservation).toBeUndefined();
    component.previewModel='selected';component.refreshChart();expect(component.targetObservation?.device_id).toBe(0);
    component.seekTime(0);expect(component.targetObservation).toBeUndefined();
    component.followLatest();expect(component.targetObservation).toBeUndefined();
    component.receiveState({id:'another',stage:'RUNNING',active:true});expect(component.showTruth).toBeFalse();
  });
  it('does not interpolate an unrecorded retrospective target from adjacent samples',()=>{
    const row:MobilityLiveEvent={type:'FRAME',device_id:0,time_ms:60000,latitude:0,longitude:0,sampled:true,
      comparison:{time_ms:60000,methods:['TCN'],matched:0,latest:[],forecasts:[{model:'TCN',model_version:'v1',device_id:0,
        origin_ms:60000,target_ms:120000,origin_node:'west',predicted_latitude_deg:0,predicted_longitude_deg:.001}]}};
    component.previewModel='TCN';component.receiveEvents([{type:'META',device_count:1,nodes:[{name:'west',latitude:0,longitude:0,radius:1000}]},row,{...row,time_ms:120001}]);
    component.seekTime(60000);component.showTruth=true;
    expect(component.previewForecast?.model).toBe('TCN');expect(component.targetObservation).toBeUndefined();
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
  it('changes only the visible movement window while retaining full history for rewind',()=>{
    component.receiveEvents([{type:'META',device_count:1,nodes:[{name:'west',latitude:0,longitude:0,radius:1000}]},
      ...Array.from({length:31},(_,i)=>({type:'FRAME',device_id:0,time_ms:i*60000,latitude:0,longitude:i/1000,sampled:true} as MobilityLiveEvent))]);
    expect(mapDevices(component.mapTracks,component.replayTime,component.pathWindowMinutes,mapProjection(component.mapNodes))[0].trail.length).toBe(11);
    component.seekTime(20*60000);expect(component.latest?.time_ms).toBe(20*60000);
    expect(mapDevices(component.mapTracks,component.replayTime,component.pathWindowMinutes,mapProjection(component.mapNodes))[0].trail.length).toBe(11);
    component.pathWindowMinutes=0;component.refreshChart();expect(mapDevices(component.mapTracks,component.replayTime,component.pathWindowMinutes,mapProjection(component.mapNodes))[0].trail.length).toBe(21);
    component.followLatest();expect(mapDevices(component.mapTracks,component.replayTime,component.pathWindowMinutes,mapProjection(component.mapNodes))[0].trail.length).toBe(31);
    expect(component.observed).toBe(31);
  });
});

describe('Early live Mobility readiness',()=>{
  it('shows the first issued HoldPosition and keeps training status causal when rewinding',()=>{
    const c=new MobilityLauncherComponent({} as ElectronService);
    const frame:MobilityLiveEvent={type:'FRAME',device_id:0,time_ms:60000,latitude:0,longitude:0,sampled:true,
      baselines:{predictions:[{model:'HoldPosition',model_version:'baseline-v1',device_id:0,origin_ms:60000,target_ms:120000,origin_node:'NONE',predicted_latitude_deg:0,predicted_longitude_deg:0}],evaluations:[]},
      lifecycle:{serving:'HoldPosition',candidate:'',state:'COLLECTING',matched:0,promotions:0,fallbacks:0,error_m:null,error_target_ms:0,observations:1}};
    c.receiveEvents([{type:'META',device_count:1,nodes:[{name:'west',latitude:0,longitude:0,radius:1000}]},frame]);
    expect(c.mapForecast?.model).toBe('HoldPosition');expect(c.observedForDevice).toBe(1);
    c.previewModel='ConstantVelocity';c.refreshChart();expect(c.mapForecast).toBeUndefined();
    c.receiveEvents([{type:'MOBILITY_STATUS',device_id:0,time_ms:2460000,lifecycle:{...frame.lifecycle!,state:'TRAINING',candidate:'v0001',observations:41}}]);
    expect(c.readiness?.state).toBe('TRAINING');expect(c.observedForDevice).toBe(1);
    c.seekTime(60000);expect(c.readiness?.state).toBe('COLLECTING');
    c.followLatest();c.receiveEvents([{...frame,time_ms:2460000,lifecycle:{...frame.lifecycle!,state:'EVALUATING',candidate:'v0001',observations:41}}]);
    expect(c.readiness?.state).toBe('EVALUATING');c.ngOnDestroy();
  });
});
