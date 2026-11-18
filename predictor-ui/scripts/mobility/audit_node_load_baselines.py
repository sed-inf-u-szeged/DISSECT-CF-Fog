"""Audit ARIMA/LR chronology, complete blocks, failures and standalone parity."""
import csv
import json
import math
from collections import defaultdict
from pathlib import Path

import numpy as np
from build_mobility_windows import require, read_json


def rows(path):
    with path.open(encoding='utf-8-sig', newline='') as stream:
        return list(csv.DictReader(stream))


def audit(output, replay=False):
    output = Path(output)
    meta = read_json(output / 'node_load_baseline_summary.json')
    require(meta['schema_version']==2 and meta['run_id']==output.name and meta['models']==['ARIMA','LR']
            and meta['arima_order']==[3,0,0] and meta['input_points']==192 and meta['output_points']==64
            and meta['default_service']=='LR' and meta['backup_service']=='ARIMA'
            and meta['first_origin_index']==191 and meta['refit_stride']==64
            and meta['nonnegative_output']=='CLIP_AT_ZERO' and meta['nonconverged_arima']=='SKIP_BLOCK', 'Baseline contract')
    require(not (output/'node_load_predictions.csv').exists(), 'New runs must not emit Persistence')
    samples=defaultdict(dict)
    for r in rows(output/'node_load_samples.csv'):
        require(r['run_id']==output.name,'Mixed baseline sample run')
        samples[r['node_id']][int(r['sample_index'])]=float(r['waiting_assignment_bytes'])
    vectors=defaultdict(list); forecasts={}
    def identity(r):
        require(r['run_id']==output.name and r['node_id'] in samples and r['model'] in meta['models']
                and r['version']=='online-v1','Baseline identity')
        return r['node_id'],r['model'],r['version'],int(r['origin_index'])
    for r in rows(output/'node_load_baseline_predictions.csv'):
        k=identity(r); origin=k[-1]; target=int(r['target_index']); value=float(r['predicted_bytes'])
        require(origin>=191 and origin in samples[k[0]] and target-origin==int(r['lead_steps'])
                and 1<=target-origin<=64 and int(r['origin_ms'])==origin*60000 and int(r['target_ms'])==target*60000
                and math.isfinite(value) and value>=0 and (*k,target) not in forecasts,'Baseline causal forecast')
        vectors[k].append(r);forecasts[(*k,target)]=r
    for v in vectors.values():
        v.sort(key=lambda r:int(r['lead_steps']))
        require([int(r['lead_steps']) for r in v]==list(range(1,65)),'Incomplete baseline vector')
    evaluated=set()
    for r in rows(output/'node_load_baseline_evaluations.csv'):
        k=identity(r); target=int(r['target_index']); p=forecasts.get((*k,target)); actual=samples[k[0]].get(target)
        require(p and actual is not None and (*k,target) not in evaluated and int(r['target_ms'])==target*60000,'Baseline evaluation identity')
        require(float(r['actual_bytes'])==actual and float(r['predicted_bytes'])==float(p['predicted_bytes'])
                and math.isclose(float(r['absolute_error_bytes']),abs(float(p['predicted_bytes'])-actual),abs_tol=1e-8),'Baseline evaluation value')
        evaluated.add((*k,target))
    require(evaluated=={k for k in forecasts if k[-1] in samples[k[0]]},'Mature baseline target missing or future fabricated')
    attempts=set();failed=0;replayed=0
    for line in (output/'node_load_baseline_fits.jsonl').read_text().splitlines():
        r=json.loads(line); k=identity(r);origin=k[-1]
        require(k not in attempts and origin>=191 and origin in samples[k[0]] and r['first_input_index']==origin-191
                and r['last_input_index']==origin and r['usable']==(k in vectors),'Invalid baseline fit')
        attempts.add(k)
        if r['usable']:
            require(r['status'].get('converged') is not False and len(r['raw_values'])==64,'Unusable fit published')
            np.testing.assert_allclose([float(p['predicted_bytes']) for p in vectors[k]], np.maximum(0,r['raw_values']),rtol=1e-10,atol=1e-8)
            require(r['clipped_count']==sum(v<0 for v in r['raw_values']),'Clipping count')
        else: failed+=1
        if replay:
            from node_load_baselines import predict_baseline
            result=predict_baseline(k[1],[samples[k[0]][i] for i in range(origin-191,origin+1)],origin)
            require(result['usable']==r['usable'],'Baseline fit status replay mismatch')
            if r['usable']: np.testing.assert_allclose(result['raw_values'],r['raw_values'],rtol=1e-7,atol=1e-5)
            replayed+=1
    require(set(vectors)<=attempts and failed==meta['failed_fits'] and len(forecasts)==meta['generated']
            and len(evaluated)==meta['matched'] and len(forecasts)-len(evaluated)==meta['unmatched'],'Baseline counts')
    # A fit at every scheduled block plus every neural issue origin; no arbitrary future fits.
    neural=defaultdict(set)
    for r in rows(output/'node_load_model_predictions.csv'):neural[r['node_id']].add(int(r['origin_index']))
    for node, actuals in samples.items():
        expected=[];last=-1000
        for i in range(191,len(actuals)):
            if i-last>=64 or i in neural[node]: expected.append(i);last=i
        for m in meta['models']:
            require(sorted(k[-1] for k in attempts if k[:2]==(node,m))==expected,'Missing or unexpected baseline origins')
    return forecasts,dict(status='PASSED',blocks=len(vectors),failed_fits=failed,forecasts=len(forecasts),matched=len(evaluated),
                         standalone_parity='PASSED' if replay else 'NOT_RUN',replayed_fits=replayed)
