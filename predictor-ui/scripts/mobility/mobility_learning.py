"""Strict train/validation loading, train-only scaling and position metrics."""
from collections import Counter
import json
from pathlib import Path
import numpy as np
from build_mobility_windows import FEATURES, RADIUS, read_json, require, sha256


def load_windows(directory):
    directory = Path(directory).resolve()
    manifest = read_json(directory/'windows.json')
    require(manifest['status'] == 'COMPLETE' and manifest['window_schema_version'] == 1
            and manifest['dataset_id'] == 'mobility-pilot-v1', 'Unsupported/incomplete windows')
    require(manifest['feature_names'] == FEATURES and manifest['target_names'] == ['delta_east_m', 'delta_north_m']
            and manifest['history_points'] == 8 and manifest['horizon_ms'] == 60000
            and manifest['stride_ms'] == 60000 and manifest['preprocessing'] == 'raw_units_no_scaler_fitted',
            'Feature/target protocol mismatch')
    projection = manifest['projection']
    require(projection['name'] == 'fixed_reference_equirectangular' and projection['earth_radius_m'] == RADIUS,
            'Projection mismatch')
    reference = [float(projection['reference_latitude_deg']), float(projection['reference_longitude_deg'])]
    require(reference == [47.73, 18.27], 'Unexpected pilot reference')
    cases = {c['case_id']: c for c in manifest['cases']}
    require(len(cases) == len(manifest['cases']) == 36, 'Case count mismatch')
    require(Counter(c['split'] for c in cases.values()) == {'train': 24, 'validation': 6, 'test': 6}, 'Split counts mismatch')
    require(manifest['counts'] == {'train': 2688, 'validation': 672, 'test': 672}, 'Window count protocol mismatch')
    result, all_ids, route_splits = {}, set(), {}
    # Intentionally no access to test.jsonl, even for hashing. Test is held out.
    for split in ('train', 'validation'):
        path = directory/(split+'.jsonl')
        require(sha256(path) == manifest['files_sha256'][path.name], 'Window checksum mismatch: '+split)
        with path.open(encoding='utf-8') as stream:
            records = [json.loads(line) for line in stream]
        require(len(records) == manifest['counts'][split], 'Window row count mismatch')
        counts = Counter()
        for w in records:
            case = cases[w['case_id']]
            require(w['split'] == case['split'] == split and w['run_id'] == case['run_id']
                    and w['device_id'] == '0', 'Mixed split/run/device')
            require(w['window_id'] not in all_ids, 'Duplicate window')
            all_ids.add(w['window_id']); counts[w['case_id']] += 1
            previous_split = route_splits.setdefault(w['route_id'], split)
            require(previous_split == split, 'Route crosses splits')
            origin = w['origin_index']
            require(isinstance(origin, int) and 8 <= origin <= 119 and w['history_start_index'] == origin-7
                    and w['target_index'] == origin+1, 'Window indices mismatch')
            require(w['history_time_ms'] == list(range(w['origin_ms']-420000, w['origin_ms']+1, 60000))
                    and w['target_ms'] == w['origin_ms']+60000, 'Window timing mismatch')
            require(w['window_id'] == w['case_id']+':'+str(w['origin_ms']), 'Window ID mismatch')
            require(w['origin_node'] in ('NONE','mobility-west','mobility-east'), 'Unexpected origin node')
        require(counts == {k:112 for k,c in cases.items() if c['split']==split}, 'Per-case window counts mismatch')
        x = np.asarray([w['x'] for w in records], dtype=np.float64)
        y = np.asarray([w['y'] for w in records], dtype=np.float64)
        require(x.shape == (len(records),8,6) and y.shape == (len(records),2), 'Tensor shape mismatch')
        require(np.isfinite(x).all() and np.isfinite(y).all(), 'Non-finite tensor')
        require(np.all(x[:,-1,:2] == 0) and np.all(x[:,:,2] >= 0), 'Invalid anchor/speed')
        require(np.isin(x[:,:,5], [0,1]).all(), 'Invalid heading mask')
        good = x[:,:,5] == 1
        require(np.allclose(np.sum(x[:,:,3:5][good]**2,axis=-1),1,atol=1e-6), 'Invalid direction encoding')
        require(np.all(x[:,:,3:5][~good]==0), 'Invalid masked direction')
        anchor = np.array([[w['anchor_latitude_deg'],w['anchor_longitude_deg']] for w in records])
        target = np.array([[w['target_latitude_deg'],w['target_longitude_deg']] for w in records])
        require(np.isfinite(anchor).all() and np.isfinite(target).all(), 'Invalid coordinate metadata')
        require(np.allclose(displacement_to_gps(y,anchor,reference),target,atol=1e-10,rtol=0), 'Target/GPS mismatch')
        result[split] = {'x':x,'y':y,'records':records,'anchor':anchor,'target':target}
    return manifest, result


def fit_scaler(train):
    require(all(r['split']=='train' for r in train['records']), 'Scaler may only fit training records')
    flat = train['x'][:,:,:3].reshape(-1,3)
    mean, std = flat.mean(axis=0), flat.std(axis=0)
    std = np.where(std < 1e-8, 1.0, std)
    scale = max(float(np.sqrt(np.mean(train['y']**2))), 1.0)
    return {'schema_version':1,'fit_split':'train','fit_windows':len(train['x']),
            'x_standardized_columns':FEATURES[:3], 'x_mean':mean.tolist(),'x_std':std.tolist(),
            'x_unchanged_columns':FEATURES[3:], 'y_scale_m':scale,
            'y_transform':'divide_by_shared_train_rms_no_centering',
            'note':'X statistics count all history slots, including overlapping windows; no validation/test fitting.'}


def transform_x(x, scaler):
    result = np.asarray(x,dtype=np.float64).copy()
    result[:,:,:3] = (result[:,:,:3]-np.asarray(scaler['x_mean']))/np.asarray(scaler['x_std'])
    require(np.isfinite(result).all(), 'Non-finite transformed input')
    return result.astype(np.float32)


def displacement_to_gps(displacement, anchor, reference):
    result = np.asarray(anchor,dtype=np.float64).copy()
    result[:,0] += np.degrees(np.asarray(displacement)[:,1]/RADIUS)
    result[:,1] += np.degrees(np.asarray(displacement)[:,0]/(RADIUS*np.cos(np.radians(reference[0]))))
    require(np.isfinite(result).all() and np.all(np.abs(result[:,0])<=90)
            and np.all(np.abs(result[:,1])<=180), 'Prediction outside supported GPS range')
    return result


def position_errors(predicted, actual):
    a,b = np.radians(predicted),np.radians(actual)
    dl = b[:,1]-a[:,1]
    h = np.sin((b[:,0]-a[:,0])/2)**2+np.cos(a[:,0])*np.cos(b[:,0])*np.sin(dl/2)**2
    return 2*RADIUS*np.arctan2(np.sqrt(np.clip(h,0,1)),np.sqrt(np.clip(1-h,0,1)))


def error_metrics(errors):
    errors = np.asarray(errors,dtype=np.float64)
    require(len(errors)>0 and np.isfinite(errors).all(), 'Invalid position errors')
    return {'count':len(errors),'mean_error_m':float(errors.mean()),
            'median_error_m':float(np.median(errors)), 'p90_error_m':float(np.percentile(errors,90)),
            'rmse_m':float(np.sqrt(np.mean(errors**2)))}


def baseline_displacements(x):
    # Fixed projection is linear in GPS: this matches Java 2*current - previous.
    return {'HoldPosition':np.zeros((len(x),2)), 'ConstantVelocity':-np.asarray(x)[:,-2,:2]}
