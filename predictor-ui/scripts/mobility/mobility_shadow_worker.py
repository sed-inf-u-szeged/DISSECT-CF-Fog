"""Persistent frozen predictor. Receives only the current Java sample over stdin."""
import argparse
from collections import deque
import contextlib
import csv
import io
import json
import math
import os
from pathlib import Path
import sys
import traceback

os.environ['CUDA_VISIBLE_DEVICES']='-1'
os.environ['TF_ENABLE_ONEDNN_OPTS']='0'
os.environ['KERAS_BACKEND']='tensorflow'
os.environ.setdefault('TF_CPP_MIN_LOG_LEVEL','2')
import numpy as np
from build_mobility_windows import FEATURES,project,read_json,write_json,require,sha256
from mobility_learning import transform_x,displacement_to_gps,baseline_displacements
from evaluate_mobility_test import text_hash
from mobility_shadow_evaluation import DelayedEvaluation, MODELS, PREDICTION_FIELDS, EVALUATION_FIELDS

HEADER=['run_id','device_id','time_ms','latitude_deg','longitude_deg','delta_time_s',
        'displacement_m','observed_speed_m_s','heading_deg','motion_valid','heading_valid','current_node']


def features(history):
    """Identical input construction to the offline builder; no target argument."""
    require(len(history)==8,'Eight history points required')
    anchor=project(float(history[-1]['latitude_deg']),float(history[-1]['longitude_deg']),[47.73,18.27])
    result=[]
    for r in history:
        require(r['motion_valid']=='true','History motion missing')
        pos=project(float(r['latitude_deg']),float(r['longitude_deg']),[47.73,18.27])
        speed=float(r['observed_speed_m_s']);require(math.isfinite(speed) and speed>=0,'Invalid speed')
        require(r['heading_valid'] in ('true','false'),'Invalid heading mask')
        valid=r['heading_valid']=='true'
        if valid:
            h=float(r['heading_deg']);require(math.isfinite(h) and 0<=h<360,'Invalid heading')
            sine,cosine=math.sin(math.radians(h)),math.cos(math.radians(h))
        else:
            require(r['heading_deg']=='','Invalid masked heading');sine,cosine=0.,0.
        result.append([pos[0]-anchor[0],pos[1]-anchor[1],speed,sine,cosine,int(valid)])
    require(np.isfinite(result).all(),'Non-finite features')
    return result


class History:
    def __init__(self):
        self.rows=deque(maxlen=8);self.identity=None;self.last_time=None;self.count=0

    def append(self, raw):
        values=next(csv.reader(io.StringIO(raw)))
        require(len(values)==len(HEADER),'Sample field count mismatch')
        r=dict(zip(HEADER,values));identity=(r['run_id'],r['device_id'])
        require(identity[0] and identity[1]=='0','Expected one device')
        now=int(r['time_ms']);lat=float(r['latitude_deg']);lon=float(r['longitude_deg'])
        require(now>=0 and math.isfinite(lat) and math.isfinite(lon) and abs(lat)<=90 and abs(lon)<=180,'Invalid observation')
        require(r['current_node'] in ('NONE','mobility-west','mobility-east'),'Unexpected node')
        if self.identity is None:
            self.identity=identity
            require(r['motion_valid']=='false' and r['heading_valid']=='false'
                    and all(r[k]=='' for k in ('delta_time_s','displacement_m','observed_speed_m_s','heading_deg')),
                    'First observation must have missing motion')
        else:
            require(identity==self.identity and now-self.last_time==60000,'Mixed identity or sampling gap')
            require(r['motion_valid']=='true' and float(r['delta_time_s'])==60.,'Invalid motion interval')
            d=float(r['displacement_m']);v=float(r['observed_speed_m_s'])
            require(math.isfinite(d) and math.isfinite(v) and d>=0 and v>=0
                    and math.isclose(d/60,v,rel_tol=1e-10,abs_tol=1e-10),'Invalid motion')
        self.last_time=now;self.count+=1;self.rows.append(r)
        x=None if len(self.rows)<8 or any(q['motion_valid']!='true' for q in self.rows) else features(list(self.rows))
        return r,x


def frozen_model(training):
    t=read_json(training/'training.json')
    require(t['status']=='COMPLETE' and t['mode']=='pilot_training' and t['model']=='MobilityLSTM-v1'
            and t['read_splits']==['train','validation'] and t['test_evaluated'] is False
            and t['reload_check']=='PASSED','Expected reviewed, frozen training bundle')
    for name in ('best.keras','preprocessing.json'):
        require(sha256(training/name)==t['artifact_sha256'][name],'Frozen artifact changed: '+name)
    for name in ('mobility_learning.py','build_mobility_windows.py'):
        require(text_hash(training/'source'/name)==text_hash(Path(__file__).parent/name),'Preprocessing source changed')
    sc=read_json(training/'preprocessing.json')
    require(sc['fit_split']=='train' and sc['fit_windows']==2688 and sc['feature_names']==FEATURES
            and sc['target_names']==['delta_east_m','delta_north_m']
            and sc['x_standardized_columns']==FEATURES[:3] and sc['x_unchanged_columns']==FEATURES[3:]
            and sc['y_transform']=='divide_by_shared_train_rms_no_centering','Scaler protocol mismatch')
    p=sc['projection'];require(p['name']=='fixed_reference_equirectangular'
            and p['reference_latitude_deg']==47.73 and p['reference_longitude_deg']==18.27
            and p['earth_radius_m']==6378137.,'Projection mismatch')
    require(np.asarray(sc['x_mean']).shape==(3,) and np.isfinite(sc['x_mean']).all()
            and np.asarray(sc['x_std']).shape==(3,) and np.isfinite(sc['x_std']).all()
            and np.all(np.asarray(sc['x_std'])>0) and math.isfinite(sc['y_scale_m'])
            and sc['y_scale_m']>0,'Invalid scaler')
    with contextlib.redirect_stdout(sys.stderr):
        import tensorflow as tf
        tf.config.threading.set_inter_op_parallelism_threads(1)
        tf.config.threading.set_intra_op_parallelism_threads(2)
        model=tf.keras.models.load_model(training/'best.keras',compile=False,safe_mode=True)
    require(model.input_shape==(None,8,6) and model.output_shape==(None,2)
            and model.count_params()==5554,'Model shape mismatch')
    return model,sc


def serve(training,output):
    state={'schema_version':1,'status':'STARTING','training':False,'scaler_fitting':False,
           'prediction_mode':'synchronous_shadow','evaluation':'TARGET_TIME_MATCH',
           'connection_control':False,'history_points':8,'horizon_ms':60000,
           'model_sha256':sha256(training/'best.keras'),'scaler_sha256':sha256(training/'preprocessing.json'),
           'training_manifest_sha256':sha256(training/'training.json')}
    require(not (output/'shadow-worker.json').exists(),'Refusing existing worker result')
    write_json(output/'shadow-worker.json',state)
    try:
        model,sc=frozen_model(training);history=History();issued=0;scoring=DelayedEvaluation()
        with (output/'shadow_received_samples.csv').open('x',encoding='utf-8',newline='') as rawout, \
             (output/'shadow_inputs.jsonl').open('x',encoding='utf-8') as inputs, \
             (output/'shadow_predictions.csv').open('x',encoding='utf-8',newline='') as predout, \
             (output/'shadow_evaluations.csv').open('x',encoding='utf-8',newline='') as evalout, \
             (output/'shadow_events.jsonl').open('x',encoding='utf-8') as events:
            rawwriter=csv.DictWriter(rawout,fieldnames=HEADER);rawwriter.writeheader()
            writer=csv.DictWriter(predout,fieldnames=PREDICTION_FIELDS);writer.writeheader()
            evalwriter=csv.DictWriter(evalout,fieldnames=EVALUATION_FIELDS);evalwriter.writeheader()
            sequence=0
            def event(kind,now,**details):
                nonlocal sequence
                sequence+=1
                events.write(json.dumps(dict(sequence=sequence,event=kind,sim_time_ms=now,**details),
                                        allow_nan=False)+'\n');events.flush()
            print('READY',flush=True)
            ended=False
            for line in sys.stdin:
                line=line.rstrip('\r\n')
                if line=='END':ended=True;break
                parts=line.split('\t',2)
                require(len(parts)==3 and parts[0]=='OBS' and parts[1] in ('true','false'),'Invalid request')
                r,x=history.append(parts[2]);rawwriter.writerow(r);rawout.flush();now=int(r['time_ms'])
                event('OBSERVATION',now,run_id=r['run_id'],device_id=r['device_id'])
                matured=scoring.observe(r)
                if matured:
                    evalwriter.writerows(matured);evalout.flush()
                    event('EVALUATION',now,origin_ms=matured[0]['origin_ms'],target_ms=now,models=list(MODELS))
                if x is None or parts[1]=='false':print('WAIT\t'+str(now),flush=True);continue
                z=np.asarray([x],dtype=np.float64)
                with contextlib.redirect_stdout(sys.stderr):
                    delta=np.asarray(model(transform_x(z,sc),training=False))*sc['y_scale_m']
                require(delta.shape==(1,2) and np.isfinite(delta).all(),'Invalid prediction')
                anchor=np.array([[float(r['latitude_deg']),float(r['longitude_deg'])]])
                values=dict(baseline_displacements(z),LSTM=delta);lstm=None;predictions=[]
                for name,value in values.items():
                    gps=displacement_to_gps(value,anchor,[47.73,18.27])[0]
                    predictions.append(dict(model=name,device_id=r['device_id'],origin_ms=now,target_ms=now+60000,
                        origin_node=r['current_node'],predicted_latitude_deg=gps[0],predicted_longitude_deg=gps[1]))
                    if name=='LSTM':lstm=gps
                scoring.issue(predictions);writer.writerows(predictions)
                record={'run_id':r['run_id'],'device_id':r['device_id'],'origin_ms':now,'target_ms':now+60000,
                        'history_time_ms':[int(q['time_ms']) for q in history.rows],'x':x}
                inputs.write(json.dumps(record,allow_nan=False)+'\n');inputs.flush();predout.flush();issued+=1
                event('PREDICTION',now,origin_ms=now,target_ms=now+60000,models=list(MODELS))
                print('PRED\t%d\t%d\t%.17g\t%.17g'%(now,now+60000,*lstm),flush=True)
            require(ended,'Java stream closed without END')
        require(history.count==121 and issued==112,'Incomplete observation/prediction stream')
        metrics=scoring.finish()
        write_json(output/'shadow_metrics.json',dict(schema_version=1,status='COMPLETE',
            role='development_integration_check',evaluation='TARGET_TIME_MATCH',
            error_definition='great_circle_position_error_m',earth_radius_m=6378137.,
            horizon_ms=60000,prediction_origins=issued,evaluation_rows=3*issued,pending_forecasts=0,
            metrics=metrics))
        require(sha256(training/'best.keras')==state['model_sha256']
                and sha256(training/'preprocessing.json')==state['scaler_sha256'],'Frozen bundle changed')
        state.update(status='COMPLETE',observations=history.count,prediction_origins=issued,
                     evaluation_rows=3*issued,pending_forecasts=0)
        write_json(output/'shadow-worker.json',state)
        print('DONE\t%d\t%d'%(history.count,issued),flush=True)
    except BaseException as error:
        state.update(status='FAILED',error=str(error));write_json(output/'shadow-worker.json',state);raise


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--training',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True);args=parser.parse_args()
    try:serve(args.training.resolve(),args.output.resolve())
    except Exception:traceback.print_exc(file=sys.stderr);return 1
    return 0


if __name__=='__main__':sys.exit(main())
