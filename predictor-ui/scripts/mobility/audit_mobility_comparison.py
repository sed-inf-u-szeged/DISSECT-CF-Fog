"""Replay saved candidates against independently verified causal input windows."""
import csv
import json
from pathlib import Path
import numpy as np
from build_mobility_windows import read_json, require, sha256
from mobility_comparison_models import Candidate, validate_methods, SLUGS, IMM_CONFIG
from mobility_learning import displacement_to_gps, position_errors, fit_scaler
from mobility_online_buffer import REFERENCE


def audit_comparison(child, tf):
    marker = child / 'comparison.json'
    if not marker.exists():
        require(not any((child / f).exists() for f in ('comparison_predictions.csv', 'comparison_evaluations.csv', 'comparison-models')),
                'Comparison manifest missing')
        return None
    meta = read_json(marker)
    methods = validate_methods(meta['methods'])
    require(methods and meta['schema_version'] == 1 and meta['status'] == 'COMPLETE'
            and meta['role'] == 'SHADOW_COMPARISON' and meta['serving_control'] is False,
            'Invalid comparison manifest')
    with (child / 'comparison_predictions.csv').open(encoding='utf-8', newline='') as f:
        predictions = list(csv.DictReader(f))
    with (child / 'comparison_evaluations.csv').open(encoding='utf-8', newline='') as f:
        evaluations = list(csv.DictReader(f))
    inputs = {w['origin_ms']: w for w in map(json.loads, (child / 'online_inputs.jsonl').read_text().splitlines())}
    count = len(inputs) - 32
    require(len(predictions) == len(evaluations) == count * len(methods) and meta['matched_per_method'] == count,
            'Comparison row counts differ')
    expected_origins = set(sorted(inputs)[32:])
    for method in methods:
        rows = [p for p in predictions if p['model'] == method]
        require(len(rows) == count and {int(p['origin_ms']) for p in rows} == expected_origins,
                'Comparison methods do not share the same targets')
    seen = set()
    for version in meta['versions']:
        key = version['model'], version['model_version']
        require(key not in seen and version['model'] in methods, 'Duplicate comparison version')
        seen.add(key)
        relative = Path('comparison-models') / SLUGS[version['model']] / version['model_version']
        require(version['model_version'].startswith('v') and version['model_version'][1:].isdigit()
                and version['path'] == relative.as_posix(), 'Unsafe comparison model path')
        model = Candidate.load(tf, child / relative)
        require(model.metadata == {k: v for k, v in version.items() if k != 'path'}, 'Comparison model metadata differs')
        request = child / ('scheduled-request-%04d.json' % int(version['model_version'][1:]))
        snapshot = read_json(request)
        require(version['snapshot_sha256'] == sha256(request)
                and version['training_cutoff_sim_ms'] == snapshot['requested_at_sim_ms']
                and version['shared_training_windows'] == len(snapshot['windows'])
                and (version['run_id'], version['device_id']) == (meta['run_id'], meta['device_id']),
                'Comparison training provenance differs')
        if version['model'] != 'IMM/Kalman':
            windows = snapshot['windows']
            data = dict(x=np.asarray([w['x'] for w in windows]), y=np.asarray([w['y'] for w in windows]),
                        records=[dict(w, split='train') for w in windows])
            require(model.scaler == fit_scaler(data) and version['fit_windows'] == len(windows), 'Comparison scaler used different data')
        else:
            require(version['fit_windows'] == 0 and model.model == IMM_CONFIG, 'IMM must use fixed parameters')
        rows = [p for p in predictions if (p['model'], p['model_version']) == key]
        require(rows and all(int(p['origin_ms']) >= version['training_cutoff_sim_ms'] for p in rows), 'Forecast before training')
        windows = [inputs[int(p['origin_ms'])] for p in rows]
        coords = displacement_to_gps(model.predict([w['x'] for w in windows]),
                                    [[w['anchor_latitude_deg'], w['anchor_longitude_deg']] for w in windows], REFERENCE)
        logged = np.array([[float(p['predicted_latitude_deg']), float(p['predicted_longitude_deg'])] for p in rows])
        require(np.all(position_errors(coords, logged) < 0.01), 'Saved comparison model replay differs by >= 1 cm')
    require(set(p['model'] for p in predictions) == set(methods), 'Unexpected comparison method')
    samples = {}
    with (child / 'mobility_samples.csv').open(encoding='utf-8', newline='') as f:
        samples = {int(s['time_ms']): s for s in csv.DictReader(f)}
    for p, e in zip(predictions, evaluations):
        require(all(e[k] == v for k, v in p.items()), 'Comparison evaluation does not match issued forecast')
        origin, target = int(p['origin_ms']), int(p['target_ms'])
        actual = samples[target]
        require(p['run_id'] == actual['run_id'] == meta['run_id']
                and p['device_id'] == actual['device_id'] == meta['device_id']
                and target == origin + 60000 and int(e['evaluated_at_sim_ms']) == target
                and p['origin_node'] == inputs[origin]['origin_node'], 'Comparison forecast time/identity differs')
        error = position_errors([[float(p['predicted_latitude_deg']), float(p['predicted_longitude_deg'])]],
                                [[float(actual['latitude_deg']), float(actual['longitude_deg'])]])[0]
        require(float(e['actual_latitude_deg']) == float(actual['latitude_deg'])
                and float(e['actual_longitude_deg']) == float(actual['longitude_deg'])
                and np.isclose(error, float(e['position_error_m']), rtol=1e-8, atol=1e-5), 'Comparison error differs')
    return dict(status='PASSED', methods=methods, matched_per_method=count, saved_model_replay='PASSED')
