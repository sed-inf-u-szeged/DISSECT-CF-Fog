"""First offline Mobility LSTM: train/validation only; test remains unread."""
import argparse
import csv
from datetime import datetime, timezone
import importlib.metadata
import os
from pathlib import Path
import shutil
import sys
import time
import traceback
import uuid

# Keep this pilot small and portable; these settings affect only this process.
os.environ['CUDA_VISIBLE_DEVICES'] = '-1'
os.environ['TF_ENABLE_ONEDNN_OPTS'] = '0'
os.environ['KERAS_BACKEND'] = 'tensorflow'
os.environ.setdefault('TF_CPP_MIN_LOG_LEVEL','2')
import numpy as np
from build_mobility_windows import read_json, write_json, sha256, require
from mobility_learning import (load_windows, fit_scaler, transform_x, displacement_to_gps,
                               position_errors, error_metrics, baseline_displacements)


def infer(model, x, batch_size=128):
    return np.concatenate([np.asarray(model(x[i:i+batch_size],training=False))
                           for i in range(0,len(x),batch_size)],axis=0)


def make_model(tf):
    model = tf.keras.Sequential([
        tf.keras.layers.Input(shape=(8,6)),
        tf.keras.layers.LSTM(32),
        tf.keras.layers.Dense(16,activation='relu'),
        tf.keras.layers.Dense(2)], name='mobility_lstm_v1')
    model.compile(optimizer=tf.keras.optimizers.Adam(learning_rate=0.001,clipnorm=1.0),
                  loss='mse',jit_compile=False)
    return model


def train(windows, output, smoke=False):
    started = time.monotonic()
    windows,output = windows.resolve(),output.resolve()
    require(not output.exists() and output != windows and windows not in output.parents,
            'Use a new output directory outside the source windows')
    manifest, data = load_windows(windows)
    import tensorflow as tf
    tf.config.threading.set_inter_op_parallelism_threads(1)
    tf.config.threading.set_intra_op_parallelism_threads(2)
    tf.keras.utils.set_random_seed(42)
    tf.config.experimental.enable_op_determinism()
    scaler = fit_scaler(data['train'])
    x_train,x_val = (transform_x(data[s]['x'],scaler) for s in ('train','validation'))
    y_scale = scaler['y_scale_m']
    y_train = (data['train']['y']/y_scale).astype(np.float32)
    y_val = data['validation']['y']/y_scale
    output.mkdir(parents=True,exist_ok=False)
    config = {'schema_version':1,'status':'RUNNING','model':'MobilityLSTM-v1',
              'mode':'smoke' if smoke else 'pilot_training','test_evaluated':False,
              'source_windows_path':str(windows),'source_windows_sha256':sha256(windows/'windows.json'),
              'read_splits':['train','validation'],'window_file_sha256':{s:manifest['files_sha256'][s+'.jsonl'] for s in ('train','validation')},
              'source_batch_sha256':manifest['source_batch_sha256'],
              'seed':42,'device':'CPU','max_epochs':2 if smoke else 60,'batch_size':64,
              'early_stopping_patience':8,'selection_metric':'validation_scaled_mse',
              'learning_rate':0.001,'gradient_clipnorm':1.0,'wall_time_limit_s':600,
              'architecture':['Input(8,6)','LSTM(32)','Dense(16,relu)','Dense(2,linear)'],
              'created_at_utc':datetime.now(timezone.utc).isoformat(),
              'versions':{'python':sys.version.split()[0],'numpy':np.__version__,'tensorflow':tf.__version__,
                          'keras':importlib.metadata.version('keras')},
              'deployment_status':'OFFLINE_ONLY_NOT_ACTIVATED'}
    write_json(output/'training.json',config)
    write_json(output/'preprocessing.json',dict(scaler,feature_names=manifest['feature_names'],
                                              target_names=manifest['target_names'],projection=manifest['projection']))
    shutil.copyfile(windows/'windows.json',output/'source-windows.json')
    source=output/'source';source.mkdir()
    for name in ('train_mobility_lstm.py','mobility_learning.py','build_mobility_windows.py',
                 'check_mobility_run.py','check_dataset_plan.py'):
        shutil.copyfile(Path(__file__).parent/name,source/name)
    try:
        model = make_model(tf)
        config['parameters'] = model.count_params()
        with (output/'model-summary.txt').open('w',encoding='utf-8') as stream:
            model.summary(print_fn=lambda line:stream.write(line+'\n'))
        rng = np.random.default_rng(42)
        best, best_epoch, stale = float('inf'),0,0
        stop_reason='max_epochs'
        print('train_windows=2688 validation_windows=672 test=NOT_READ',flush=True)
        print('model=LSTM32-Dense16-Dense2 device=CPU scaler_fit=TRAIN_ONLY',flush=True)
        with (output/'history.csv').open('w',encoding='utf-8',newline='') as stream:
            writer=csv.DictWriter(stream,fieldnames=['epoch','train_scaled_mse','validation_scaled_mse','validation_planar_rmse_m'])
            writer.writeheader()
            for epoch in range(1,config['max_epochs']+1):
                total=0.0
                order=rng.permutation(len(x_train))
                for start in range(0,len(order),64):
                    if time.monotonic()-started > 600:
                        raise TimeoutError('Training time limit exceeded; retain partial output')
                    idx=order[start:start+64]
                    model.reset_metrics()
                    loss=float(model.train_on_batch(x_train[idx],y_train[idx],return_dict=True)['loss'])
                    require(np.isfinite(loss),'Non-finite training loss')
                    total+=loss*len(idx)
                prediction=infer(model,x_val)
                val_loss=float(np.mean((prediction-y_val)**2))
                require(np.isfinite(val_loss),'Non-finite validation loss')
                row={'epoch':epoch,'train_scaled_mse':total/len(x_train),
                     'validation_scaled_mse':val_loss,'validation_planar_rmse_m':np.sqrt(2*val_loss)*y_scale}
                writer.writerow(row);stream.flush()
                print('epoch=%02d train_mse=%.6f val_mse=%.6f val_planar_rmse_m=%.3f' %
                      (epoch,row['train_scaled_mse'],val_loss,row['validation_planar_rmse_m']),flush=True)
                if val_loss < best:
                    best,best_epoch,stale=val_loss,epoch,0
                    model.save(output/'best.keras')
                else:
                    stale+=1
                if stale>=config['early_stopping_patience']:
                    stop_reason='early_stopping';break
        saved=tf.keras.models.load_model(output/'best.keras',compile=False,safe_mode=True)
        best_predictions=infer(saved,x_val)*y_scale
        selected_loss=float(np.mean((best_predictions/y_scale-y_val)**2))
        require(np.isclose(selected_loss,best,rtol=1e-5,atol=1e-7),'Saved checkpoint differs from selected epoch')
        # Reload the entire prediction bundle: persisted scaler + persisted model.
        persisted=read_json(output/'preprocessing.json')
        bundle_predictions=infer(saved,transform_x(data['validation']['x'],persisted))*persisted['y_scale_m']
        require(np.allclose(best_predictions,bundle_predictions,rtol=1e-6,atol=1e-6),'Bundle reload prediction mismatch')
        validation=data['validation'];reference=[manifest['projection']['reference_latitude_deg'],manifest['projection']['reference_longitude_deg']]
        predictions=dict(baseline_displacements(validation['x']),LSTM=best_predictions)
        metrics={};detail=[];per_case=[]
        for name,delta in predictions.items():
            gps=displacement_to_gps(delta,validation['anchor'],reference)
            errors=position_errors(gps,validation['target'])
            metrics[name]=error_metrics(errors)
            for i,w in enumerate(validation['records']):
                detail.append({'model':name,'window_id':w['window_id'],'case_id':w['case_id'],
                               'origin_ms':w['origin_ms'],'target_ms':w['target_ms'],'origin_node':w['origin_node'],
                               'predicted_latitude_deg':gps[i,0],'predicted_longitude_deg':gps[i,1],
                               'actual_latitude_deg':validation['target'][i,0],'actual_longitude_deg':validation['target'][i,1],
                               'position_error_m':errors[i]})
            for case in sorted({w['case_id'] for w in validation['records']}):
                mask=np.array([w['case_id']==case for w in validation['records']])
                per_case.append(dict(model=name,case_id=case,**error_metrics(errors[mask])))
        for filename,records in [('validation_predictions.csv',detail),('validation_by_case.csv',per_case)]:
            with (output/filename).open('w',encoding='utf-8',newline='') as stream:
                writer=csv.DictWriter(stream,fieldnames=list(records[0]));writer.writeheader();writer.writerows(records)
        write_json(output/'validation_metrics.json',{'split':'validation','position_metric':'great_circle_distance_m',
                   'earth_radius_m':6378137.0,'coverage_metrics':'NOT_COMPUTED_IN_THIS_STEP','test_evaluated':False,
                   'models':metrics,'note':'All three methods use the same 672 windows, including disconnected origins.'})
        require(sha256(windows/'windows.json')==config['source_windows_sha256'],'Source manifest changed')
        for split in ('train','validation'):
            require(sha256(windows/(split+'.jsonl'))==config['window_file_sha256'][split],'Source window file changed')
        config.update(status='COMPLETE',best_epoch=best_epoch,epochs_run=epoch,stop_reason=stop_reason,
                      best_validation_scaled_mse=best,reload_check='PASSED',elapsed_seconds=time.monotonic()-started)
        config['artifact_sha256']={str(p.relative_to(output)).replace(os.sep,'/'):sha256(p)
                                  for p in sorted(output.rglob('*')) if p.is_file() and p.name!='training.json'}
        write_json(output/'training.json',config)
        print('model,validation_mean_error_m,validation_rmse_m',flush=True)
        for name,m in metrics.items():print('%s,%.3f,%.3f'%(name,m['mean_error_m'],m['rmse_m']),flush=True)
        print('best_epoch=%d reload_check=PASSED test=NOT_READ'%best_epoch,flush=True)
        print('MOBILITY_LSTM_SMOKE_OK' if smoke else 'MOBILITY_LSTM_TRAINING_OK',flush=True)
        print('Training manifest: '+str(output/'training.json'),flush=True)
        return config
    except BaseException as error:
        config.update(status='FAILED',error=str(error),elapsed_seconds=time.monotonic()-started)
        write_json(output/'training.json',config)
        (output/'error.log').write_text(traceback.format_exc(),encoding='utf-8')
        raise


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--windows',required=True,type=Path,help='Exact completed window directory')
    parser.add_argument('--output',type=Path,help='New training directory, outside source windows')
    parser.add_argument('--smoke',action='store_true',help='Only two epochs, for runtime validation')
    args=parser.parse_args()
    output=args.output or (args.windows.resolve().parent.parent/'mobility-training'/
                         (datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ')+'-'+uuid.uuid4().hex[:12]))
    try:train(args.windows,output,args.smoke)
    except Exception as error:
        print('MOBILITY_LSTM_FAILED: '+str(error),file=sys.stderr)
        return 1
    return 0


if __name__=='__main__':
    sys.exit(main())
