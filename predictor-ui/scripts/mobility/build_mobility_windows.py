"""Build audited 8-point / 60-second mobility windows; standard library only."""
import argparse
from collections import Counter
from datetime import datetime, timezone
import hashlib
import json
import math
from pathlib import Path, PurePosixPath
import sys
import uuid

from check_dataset_plan import require, text_hash, validate
from check_mobility_run import rows, sha256, validate_run

HISTORY = 8
STEP = 60000
RADIUS = 6378137.0
FEATURES = ['relative_east_m', 'relative_north_m', 'observed_speed_m_s',
            'heading_sin', 'heading_cos', 'heading_valid']
SPLITS = ('train', 'validation', 'test')


def read_json(path):
    return json.loads(path.read_text(encoding='utf-8-sig'))


def write_json(path, data):
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2, allow_nan=False) + '\n', encoding='utf-8')


def inside(root, name):
    """Resolve only existing relative paths inside the supplied batch."""
    p = PurePosixPath(str(name).replace('\\', '/'))
    require(not p.is_absolute() and '..' not in p.parts and ':' not in str(p), 'Unsafe batch path')
    result = root.joinpath(*p.parts).resolve()
    require(root.resolve() in result.parents and (result.is_file() or result.is_dir()),
            'Missing or escaping batch path: ' + str(name))
    return result


def project(latitude, longitude, reference):
    """Fixed equirectangular chart; local metre approximation, not a fitted transform."""
    lat0, lon0 = reference
    return [RADIUS * math.radians(longitude - lon0) * math.cos(math.radians(lat0)),
            RADIUS * math.radians(latitude - lat0)]


def unproject(east, north, reference):
    lat0, lon0 = reference
    return [lat0 + math.degrees(north / RADIUS),
            lon0 + math.degrees(east / (RADIUS * math.cos(math.radians(lat0))))]


def finite(value):
    number = float(value)
    require(math.isfinite(number), 'Non-finite numeric sample')
    return number


def audit_batch(batch):
    state = read_json(batch / 'batch.json')
    require(state['status'] == 'COMPLETE' and state['mode'] == 'all'
            and state['training'] == 'NOT_STARTED' and state['selected_cases'] == 36,
            'Expected a completed, full, untrained pilot batch')
    plan_path = batch / 'plan/dataset-plan.json'
    validate(plan_path)
    require(sha256(plan_path) == state['manifest_sha256'], 'Plan hash mismatch')
    plan = read_json(plan_path)
    protocol = plan['protocol']
    require(protocol['history_points'] == HISTORY and protocol['horizon_ms'] == STEP
            and protocol['interval_ms'] == STEP, 'Window protocol mismatch')
    expected = {r['case_id']: r for r in plan['runs']}
    runs = state['runs']
    require(len(runs) == len(expected) == 36 and
            {r['case_id'] for r in runs} == set(expected), 'Case set mismatch')
    require(len({r['run_id'] for r in runs}) == 36, 'Duplicate run identity')
    sources = []
    for run in runs:
        require(all(run.get(k) == v for k, v in expected[run['case_id']].items()), 'Split/config identity mismatch')
        require(run['status'] == 'PASSED' and run['exit_code'] == 0, 'Unsuccessful source run')
        directory = inside(batch, run['output_directory'])
        config = inside(batch, 'plan/' + run['config_path'])
        require(text_hash(config.read_text(encoding='utf-8-sig')) == run['config_text_sha256'], 'Config hash mismatch')
        checks = validate_run(directory, run, config)
        require(all(run.get(k) == v for k, v in checks.items()), 'Export hash or recorded validation mismatch')
        samples, _ = rows(directory / 'mobility_samples.csv')
        for sample in samples:
            for key in ('latitude_deg', 'longitude_deg', 'delta_time_s', 'displacement_m',
                        'observed_speed_m_s', 'heading_deg'):
                if sample[key] != '':
                    finite(sample[key])
        sources.append((run, samples))
    require(sum(len(s) for _, s in sources) == state['sample_rows'] == 4356, 'Source row total mismatch')
    return state, plan, sources


def windows_for_run(run, samples, reference, *, expected_observations=121):
    """No input access after origin. Labels are constructed separately."""
    require(type(expected_observations) is int and 11 <= expected_observations <= 1441
            and len(samples) == expected_observations, 'Configured observation count differs')
    for i, row in enumerate(samples):
        require(row['run_id'] == run['run_id'] and row['device_id'] == '0', 'Mixed run/device')
        require(i == 0 or int(row['time_ms']) - int(samples[i-1]['time_ms']) == STEP, 'Sampling gap')
    windows, skipped = [], 0
    for origin_index in range(HISTORY - 1, len(samples) - 1):
        start = origin_index - HISTORY + 1
        history = samples[start:origin_index + 1]
        if any(r['motion_valid'] != 'true' for r in history):
            skipped += 1
            continue
        origin, target = samples[origin_index], samples[origin_index + 1]
        anchor_latlon = [finite(origin['latitude_deg']), finite(origin['longitude_deg'])]
        anchor = project(*anchor_latlon, reference)
        features = []
        for row in history:
            position = project(finite(row['latitude_deg']), finite(row['longitude_deg']), reference)
            speed = finite(row['observed_speed_m_s'])
            require(speed >= 0, 'Negative speed')
            require(row['heading_valid'] in ('true', 'false'), 'Invalid heading validity flag')
            valid = row['heading_valid'] == 'true'
            if valid:
                heading = finite(row['heading_deg'])
                require(0 <= heading < 360, 'Heading outside [0,360)')
                sine, cosine = math.sin(math.radians(heading)), math.cos(math.radians(heading))
            else:
                require(row['heading_deg'] == '', 'Invalid heading must be empty')
                sine, cosine = 0.0, 0.0
            features.append([position[0] - anchor[0], position[1] - anchor[1], speed,
                             sine, cosine, int(valid)])
        target_latlon = [finite(target['latitude_deg']), finite(target['longitude_deg'])]
        target_xy = project(*target_latlon, reference)
        windows.append({
            'window_id': run['case_id'] + ':' + str(origin['time_ms']),
            'case_id': run['case_id'], 'route_id': run['route_id'], 'split': run['split'],
            'run_id': run['run_id'], 'device_id': origin['device_id'],
            'history_start_index': start, 'origin_index': origin_index, 'target_index': origin_index + 1,
            'history_time_ms': [int(r['time_ms']) for r in history],
            'origin_ms': int(origin['time_ms']), 'target_ms': int(target['time_ms']),
            'anchor_latitude_deg': anchor_latlon[0], 'anchor_longitude_deg': anchor_latlon[1],
            'origin_node': origin['current_node'],
            'x': features, 'y': [target_xy[0] - anchor[0], target_xy[1] - anchor[1]],
            'target_latitude_deg': target_latlon[0], 'target_longitude_deg': target_latlon[1]})
    return windows, skipped


def verify_output(output, source_by_id, reference, counts):
    """Read back serialized windows and check source indices, causality and inverse projection."""
    seen = set()
    actual = Counter()
    for split in SPLITS:
        with (output / (split + '.jsonl')).open(encoding='utf-8') as stream:
            for line in stream:
                w = json.loads(line)
                require(w['window_id'] not in seen and w['split'] == split, 'Duplicate or misplaced window')
                seen.add(w['window_id'])
                run, samples = source_by_id[w['case_id']]
                require(run['split'] == split and run['run_id'] == w['run_id'], 'Cross-run/split window')
                i = w['origin_index']
                history = samples[i-7:i+1]
                require(w['history_time_ms'] == [int(r['time_ms']) for r in history], 'History alignment failed')
                require(len(w['x']) == 8 and all(len(x) == 6 for x in w['x']), 'Incorrect X shape')
                require(w['target_ms'] == w['origin_ms'] + STEP and
                        w['target_ms'] == int(samples[i+1]['time_ms']), 'Target alignment failed')
                require(all(math.isfinite(v) for row in w['x'] for v in row) and
                        all(math.isfinite(v) for v in w['y']), 'Non-finite window')
                anchor = project(w['anchor_latitude_deg'], w['anchor_longitude_deg'], reference)
                reconstructed = unproject(anchor[0]+w['y'][0], anchor[1]+w['y'][1], reference)
                require(abs(reconstructed[0]-float(samples[i+1]['latitude_deg'])) < 1e-10 and
                        abs(reconstructed[1]-float(samples[i+1]['longitude_deg'])) < 1e-10,
                        'Target reconstruction failed')
                for x, raw in zip(w['x'], history):
                    require(raw['motion_valid'] == 'true', 'Missing motion entered inputs')
                    restored = unproject(anchor[0]+x[0], anchor[1]+x[1], reference)
                    require(abs(restored[0]-float(raw['latitude_deg'])) < 1e-10 and
                            abs(restored[1]-float(raw['longitude_deg'])) < 1e-10, 'History reconstruction failed')
                actual[split] += 1
    require(dict(actual) == counts, 'Serialized window count mismatch')


def build(batch, output):
    batch, output = batch.resolve(), output.resolve()
    require(output != batch and batch not in output.parents, 'Keep derived output outside the source batch')
    require(not output.exists(), 'Output already exists; choose a new directory')
    state, plan, sources = audit_batch(batch)
    reference = [plan['protocol']['start_latitude_deg'], plan['protocol']['start_longitude_deg']]
    by_split = {split: [] for split in SPLITS}
    cases = []
    for run, samples in sources:
        windows, skipped = windows_for_run(run, samples, reference)
        require(len(windows) == 112 and skipped == 1, 'Unexpected pilot window count')
        by_split[run['split']].extend(windows)
        cases.append({'case_id': run['case_id'], 'split': run['split'], 'run_id': run['run_id'],
                      'sample_rows': len(samples), 'windows': len(windows), 'skipped_invalid_motion': skipped,
                      'sample_sha256': run['export_sha256']['mobility_samples.csv']})
    counts = {split: len(w) for split, w in by_split.items()}
    require(counts == {'train': 2688, 'validation': 672, 'test': 672}, 'Unexpected split sizes')
    output.mkdir(parents=True, exist_ok=False)
    manifest = {'window_schema_version': 1, 'status': 'BUILDING', 'training': 'NOT_STARTED',
                'source_batch_path': str(batch), 'source_batch_sha256': sha256(batch/'batch.json'),
                'source_plan_sha256': state['manifest_sha256'], 'dataset_id': state['dataset_id'],
                'created_at_utc': datetime.now(timezone.utc).isoformat(),
                'history_points': HISTORY, 'history_span_ms': 7*STEP, 'horizon_ms': STEP,
                'stride_ms': STEP, 'feature_names': FEATURES, 'target_names': ['delta_east_m', 'delta_north_m'],
                'projection': {'name': 'fixed_reference_equirectangular', 'earth_radius_m': RADIUS,
                               'reference_latitude_deg': reference[0], 'reference_longitude_deg': reference[1],
                               'note': 'Local metre approximation; inverse transform recovers GPS for geodesic evaluation.'},
                'missing_motion_policy': 'skip_window_if_any_history_motion_invalid',
                'missing_heading_policy': 'sin=0,cos=0,heading_valid=0',
                'speed_definition': 'endpoint_displacement_over_previous_60_seconds',
                'preprocessing': 'raw_units_no_scaler_fitted', 'split_policy': plan['split_policy'],
                'model_input_fields': ['x'], 'model_target_fields': ['y'],
                'metadata_is_not_input': True, 'counts': counts, 'cases': cases}
    write_json(output/'windows.json', manifest)
    try:
        for split, windows in by_split.items():
            with (output/(split+'.jsonl')).open('x', encoding='utf-8', newline='\n') as stream:
                for w in windows:
                    stream.write(json.dumps(w, ensure_ascii=False, separators=(',', ':'), allow_nan=False)+'\n')
        verify_output(output, {r['case_id']: (r,s) for r,s in sources}, reference, counts)
        # Re-audit before publishing COMPLETE: detect source mutation during this build.
        require(sha256(batch/'batch.json') == manifest['source_batch_sha256'], 'Source batch changed during build')
        audit_batch(batch)
        manifest['files_sha256'] = {s+'.jsonl': sha256(output/(s+'.jsonl')) for s in SPLITS}
        manifest['builder_sha256'] = sha256(Path(__file__))
        manifest['status'] = 'COMPLETE'
        write_json(output/'windows.json', manifest)
    except BaseException as error:
        manifest['status'] = 'FAILED'; manifest['error'] = str(error)
        write_json(output/'windows.json', manifest)
        raise
    return manifest


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--batch', required=True, type=Path, help='Exact completed batch directory')
    parser.add_argument('--output', type=Path, help='New directory outside the source batch')
    args = parser.parse_args()
    output = args.output or (args.batch.resolve().parent.parent / 'mobility-windows' /
                            (datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ')+'-'+uuid.uuid4().hex[:12]))
    try:
        manifest = build(args.batch, output)
    except (ValueError, OSError, KeyError, TypeError) as error:
        print('MOBILITY_WINDOWS_FAILED: ' + str(error), file=sys.stderr)
        return 1
    print('source_cases=36 source_sample_rows=4356')
    print('history_points=8 horizon_s=60 features=6')
    print('train=2688 validation=672 test=672 total_windows=4032')
    print('skipped_invalid_motion_windows=36 scaler=NOT_FITTED training=NOT_STARTED')
    print('MOBILITY_WINDOWS_OK')
    print('Window manifest: ' + str(output.resolve()/'windows.json'))
    return 0


if __name__ == '__main__':
    sys.exit(main())
