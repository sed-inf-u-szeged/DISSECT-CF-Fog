"""Evaluate a frozen Mobility LSTM v1 and two baselines on held-out windows."""
import argparse
from collections import Counter
import csv
from datetime import datetime, timezone
import json
import os
from pathlib import Path, PurePosixPath
import shutil
import sys
import traceback
import uuid

os.environ['CUDA_VISIBLE_DEVICES'] = '-1'
os.environ['TF_ENABLE_ONEDNN_OPTS'] = '0'
os.environ['KERAS_BACKEND'] = 'tensorflow'
os.environ.setdefault('TF_CPP_MIN_LOG_LEVEL', '2')
import numpy as np
from build_mobility_windows import FEATURES, RADIUS, read_json, write_json, sha256, require
from mobility_learning import (transform_x, displacement_to_gps, position_errors,
                               error_metrics, baseline_displacements)


def child(root, name):
    relative = PurePosixPath(name.replace('\\', '/'))
    require(not relative.is_absolute() and '..' not in relative.parts and ':' not in name,
            'Unsafe artifact path')
    path = root.joinpath(*relative.parts).resolve()
    require(root.resolve() in path.parents, 'Artifact escapes directory')
    return path


def text_hash(path):
    import hashlib
    return hashlib.sha256(path.read_text(encoding='utf-8').replace('\r\n','\n').rstrip('\n').encode()).hexdigest()


def verify_bundle(training, windows):
    """Verify identities and protocol before opening test.jsonl."""
    t = read_json(training/'training.json')
    require(t['status'] == 'COMPLETE' and t['schema_version'] == 1
            and t['model'] == 'MobilityLSTM-v1' and t['mode'] == 'pilot_training',
            'Expected complete v1 training (not smoke)')
    require(t['test_evaluated'] is False and t['read_splits'] == ['train','validation']
            and t['reload_check'] == 'PASSED', 'Training provenance mismatch')
    require(t['architecture'] == ['Input(8,6)','LSTM(32)','Dense(16,relu)','Dense(2,linear)']
            and t['selection_metric'] == 'validation_scaled_mse', 'Unexpected frozen model protocol')
    required = {'best.keras','preprocessing.json','source-windows.json'}
    require(required <= t['artifact_sha256'].keys(), 'Incomplete model bundle')
    for name, digest in t['artifact_sha256'].items():
        require(sha256(child(training,name)) == digest, 'Training artifact changed: '+name)
    for name in ('mobility_learning.py','build_mobility_windows.py'):
        require(text_hash(training/'source'/name) == text_hash(Path(__file__).parent/name),
                'Installed preprocessing code differs from training: '+name)
    require(sha256(windows/'windows.json') == t['source_windows_sha256']
            == sha256(training/'source-windows.json'), 'Use the exact original window directory')
    w = read_json(windows/'windows.json')
    require(w['status'] == 'COMPLETE' and w['window_schema_version'] == 1
            and w['dataset_id'] == 'mobility-pilot-v1' and w['split_policy'] == 'route_family_holdout',
            'Unexpected dataset protocol')
    require(w['feature_names'] == FEATURES and w['target_names'] == ['delta_east_m','delta_north_m']
            and w['history_points'] == 8 and w['horizon_ms'] == 60000 and w['stride_ms'] == 60000,
            'Unexpected input/target protocol')
    require(w['counts'] == {'train':2688,'validation':672,'test':672}
            and w['source_batch_sha256'] == t['source_batch_sha256'], 'Source dataset mismatch')
    require(all(w['files_sha256'][s+'.jsonl'] == t['window_file_sha256'][s]
                for s in ('train','validation')), 'Training window identity mismatch')
    proj = w['projection']
    require(proj['name'] == 'fixed_reference_equirectangular'
            and proj['earth_radius_m'] == RADIUS and proj['reference_latitude_deg'] == 47.73
            and proj['reference_longitude_deg'] == 18.27, 'Projection mismatch')
    cases = {c['case_id']:c for c in w['cases']}
    require(len(cases) == len(w['cases']) == 36
            and Counter(c['split'] for c in cases.values()) == {'train':24,'validation':6,'test':6},
            'Case split mismatch')
    route_splits = {}
    for c in cases.values():
        route = c['case_id'].rsplit('_s',1)[0]
        require(route_splits.setdefault(route,c['split']) == c['split'], 'Route crosses splits')
    expected = {f'poly_{route:02d}_s{speed}' for route in (1,2) for speed in (20,41,60)}
    require({k for k,c in cases.items() if c['split']=='test'} == expected, 'Unexpected held-out cases')
    sc = read_json(training/'preprocessing.json')
    require(sc['schema_version'] == 1 and sc['fit_split'] == 'train' and sc['fit_windows'] == 2688
            and sc['feature_names'] == FEATURES and sc['target_names'] == w['target_names']
            and sc['projection'] == proj and sc['x_standardized_columns'] == FEATURES[:3]
            and sc['x_unchanged_columns'] == FEATURES[3:]
            and sc['y_transform'] == 'divide_by_shared_train_rms_no_centering', 'Frozen scaler mismatch')
    for k in ('x_mean','x_std'):
        require(np.asarray(sc[k]).shape == (3,) and np.isfinite(sc[k]).all(), 'Invalid scaler')
    require(np.all(np.asarray(sc['x_std'])>0) and np.isfinite(sc['y_scale_m'])
            and sc['y_scale_m']>0, 'Invalid scaler scale')
    return t,w,sc


def validate_records(records, manifest):
    cases = {c['case_id']:c for c in manifest['cases'] if c['split']=='test'}
    require(len(records) == 672, 'Expected 672 test windows')
    ids, counts, origins = set(), Counter(), set()
    for r in records:
        require(r['case_id'] in cases and r['split']=='test', 'Non-test case in test file')
        c = cases[r['case_id']]; i = r['origin_index']
        require(r['run_id']==c['run_id'] and r['device_id']=='0'
                and r['route_id']==r['case_id'].rsplit('_s',1)[0], 'Mixed route/run/device')
        require(isinstance(i,int) and 8<=i<=119 and r['history_start_index']==i-7
                and r['target_index']==i+1, 'Window indices mismatch')
        require(r['history_time_ms']==list(range(r['origin_ms']-420000,r['origin_ms']+1,60000))
                and r['target_ms']==r['origin_ms']+60000, 'Window timing mismatch')
        require(r['window_id']==r['case_id']+':'+str(r['origin_ms'])
                and r['window_id'] not in ids and (r['case_id'],i) not in origins, 'Duplicate/invalid window')
        require(r['origin_node'] in ('NONE','mobility-west','mobility-east'), 'Unknown origin node')
        ids.add(r['window_id']); origins.add((r['case_id'],i)); counts[r['case_id']]+=1
    require(counts=={k:112 for k in cases}, 'Missing case windows')
    x=np.asarray([r['x'] for r in records],dtype=np.float64)
    y=np.asarray([r['y'] for r in records],dtype=np.float64)
    require(x.shape==(672,8,6) and y.shape==(672,2)
            and np.isfinite(x).all() and np.isfinite(y).all(), 'Invalid tensors')
    require(np.all(x[:,-1,:2]==0) and np.all(x[:,:,2]>=0)
            and np.isin(x[:,:,5],[0,1]).all(), 'Invalid anchor/speed/mask')
    good=x[:,:,5]==1
    require(np.allclose(np.sum(x[:,:,3:5][good]**2,axis=-1),1,atol=1e-6)
            and np.all(x[:,:,3:5][~good]==0), 'Invalid heading')
    anchor=np.array([[r['anchor_latitude_deg'],r['anchor_longitude_deg']] for r in records])
    target=np.array([[r['target_latitude_deg'],r['target_longitude_deg']] for r in records])
    require(np.isfinite(anchor).all() and np.isfinite(target).all()
            and np.all(np.abs(anchor[:,0])<=90) and np.all(np.abs(anchor[:,1])<=180), 'Invalid GPS')
    require(np.allclose(displacement_to_gps(y,anchor,[47.73,18.27]),target,atol=1e-10,rtol=0),
            'Target/GPS mismatch')
    return x,anchor,target


def load_test(windows, manifest):
    path=windows/'test.jsonl'
    require(sha256(path)==manifest['files_sha256']['test.jsonl'], 'Test checksum mismatch')
    with path.open(encoding='utf-8') as stream: records=[json.loads(line) for line in stream]
    return records,validate_records(records,manifest)


def write_csv(path, rows):
    with path.open('w',encoding='utf-8',newline='') as stream:
        writer=csv.DictWriter(stream,fieldnames=list(rows[0]));writer.writeheader();writer.writerows(rows)


def evaluate(training, windows, output):
    training,windows,output=(p.resolve() for p in (training,windows,output))
    require(not output.exists() and all(output!=p and p not in output.parents for p in (training,windows)),
            'Choose a new evaluation directory outside source data/model directories')
    t,w,sc=verify_bundle(training,windows)
    frozen={'schema_version':1,'status':'RUNNING','created_at_utc':datetime.now(timezone.utc).isoformat(),
            'training_path':str(training),'windows_path':str(windows),
            'training_manifest_sha256':sha256(training/'training.json'),
            'windows_manifest_sha256':sha256(windows/'windows.json'),
            'model_sha256':sha256(training/'best.keras'),'scaler_sha256':sha256(training/'preprocessing.json'),
            'expected_test_sha256':w['files_sha256']['test.jsonl'], 'selected_epoch':t['best_epoch'],
            'methods':['HoldPosition','ConstantVelocity','LSTM'], 'split':'test','horizon_ms':60000,
            'primary_metric':'position_rmse_m','secondary_metrics':['mean_error_m','median_error_m','p90_error_m'],
            'model_training':False,'scaler_fitting':False,'test_based_selection':False,
            'deployment_status':'OFFLINE_ONLY_NOT_ACTIVATED',
            'note':'Overlapping windows from six runs / two held-out routes; not 672 independent trajectories.'}
    output.mkdir(parents=True,exist_ok=False)
    write_json(output/'frozen-protocol.json',dict(frozen,status='FROZEN'))
    write_json(output/'evaluation.json',frozen)
    try:
        records,(x,anchor,target)=load_test(windows,w)
        import tensorflow as tf
        tf.config.threading.set_inter_op_parallelism_threads(1)
        tf.config.threading.set_intra_op_parallelism_threads(2)
        model=tf.keras.models.load_model(training/'best.keras',compile=False,safe_mode=True)
        require(model.input_shape==(None,8,6) and model.output_shape==(None,2)
                and model.count_params()==5554, 'Unexpected saved model shape')
        z=transform_x(x,sc)
        delta=np.concatenate([np.asarray(model(z[i:i+128],training=False)) for i in range(0,len(z),128)])*sc['y_scale_m']
        require(delta.shape==(672,2) and np.isfinite(delta).all(), 'Invalid model predictions')
        methods=dict(baseline_displacements(x),LSTM=delta)
        metrics,details,by_case,by_speed={ },[],[],[]
        for name,prediction in methods.items():
            gps=displacement_to_gps(prediction,anchor,[47.73,18.27]);errors=position_errors(gps,target)
            metrics[name]=error_metrics(errors)
            for i,r in enumerate(records):
                details.append(dict(model=name,window_id=r['window_id'],case_id=r['case_id'],
                    origin_ms=r['origin_ms'],target_ms=r['target_ms'],origin_node=r['origin_node'],
                    predicted_latitude_deg=gps[i,0],predicted_longitude_deg=gps[i,1],
                    actual_latitude_deg=target[i,0],actual_longitude_deg=target[i,1],position_error_m=errors[i]))
            for case in sorted({r['case_id'] for r in records}):
                mask=np.array([r['case_id']==case for r in records])
                by_case.append(dict(model=name,case_id=case,**error_metrics(errors[mask])))
            for speed in (20,41,60):
                mask=np.array([r['case_id'].endswith('_s'+str(speed)) for r in records])
                by_speed.append(dict(model=name,configured_speed_m_s=speed/10,**error_metrics(errors[mask])))
        write_json(output/'test_metrics.json',{'split':'test','horizon_s':60,'position_metric':'great_circle_distance_m',
            'earth_radius_m':RADIUS,'coverage_metrics':'NOT_COMPUTED_IN_THIS_STEP','models':metrics,
            'note':'All 672 windows included, including disconnected origins; no model selection on test.'})
        write_csv(output/'test_predictions.csv',details)
        write_csv(output/'test_by_case.csv',by_case);write_csv(output/'test_by_speed.csv',by_speed)
        shutil.copyfile(training/'training.json',output/'source-training.json')
        shutil.copyfile(windows/'windows.json',output/'source-windows.json')
        source=output/'source';source.mkdir()
        for name in ('evaluate_mobility_test.py','mobility_learning.py','build_mobility_windows.py',
                     'check_mobility_run.py','check_dataset_plan.py'):
            shutil.copyfile(Path(__file__).parent/name,source/name)
        verify_bundle(training,windows)
        require(sha256(training/'training.json')==frozen['training_manifest_sha256']
                and sha256(windows/'test.jsonl')==frozen['expected_test_sha256'], 'Source changed during evaluation')
        result=dict(frozen,status='COMPLETE',test_windows=672,test_cases=6,test_routes=2,
                    source_unchanged=True,tensorflow_version=tf.__version__,numpy_version=np.__version__)
        result['artifact_sha256']={p.relative_to(output).as_posix():sha256(p)
            for p in sorted(output.rglob('*')) if p.is_file() and p.name!='evaluation.json'}
        write_json(output/'evaluation.json',result)
        print('test_windows=672 cases=6 routes=2 training=DISABLED scaler=FROZEN')
        print('model,test_mean_error_m,test_rmse_m,test_p90_error_m')
        for name,m in metrics.items():print('%s,%.3f,%.3f,%.3f'%(name,m['mean_error_m'],m['rmse_m'],m['p90_error_m']))
        print('MOBILITY_TEST_EVALUATION_OK')
        print('Evaluation manifest: '+str(output/'evaluation.json'))
        return result
    except BaseException as error:
        write_json(output/'evaluation.json',dict(frozen,status='FAILED',error=str(error)))
        (output/'error.log').write_text(traceback.format_exc(),encoding='utf-8')
        raise


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--training',type=Path,required=True)
    parser.add_argument('--windows',type=Path,required=True)
    parser.add_argument('--output',type=Path)
    args=parser.parse_args()
    output=args.output or args.windows.resolve().parent.parent/'mobility-evaluation'/(
        datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ')+'-'+uuid.uuid4().hex[:12])
    try:evaluate(args.training,args.windows,output)
    except Exception as error:
        print('MOBILITY_TEST_EVALUATION_FAILED: '+str(error),file=sys.stderr);return 1
    return 0


if __name__=='__main__':sys.exit(main())
