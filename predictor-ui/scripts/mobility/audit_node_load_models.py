"""Independent target, lifecycle, snapshot and saved-model audit for W05."""
import argparse
from collections import defaultdict
import csv
import json
import math
from pathlib import Path

import numpy as np
from build_mobility_windows import require, read_json
from node_load_worker import validate_options


def rows(path):
    with path.open(encoding='utf-8-sig', newline='') as stream:
        return list(csv.DictReader(stream))


def close(a, b, label):
    require(math.isfinite(float(a)) and math.isfinite(float(b))
            and math.isclose(float(a), float(b), rel_tol=1e-8, abs_tol=1e-7), label)


def quality(pairs, relative):
    actual = np.asarray([p[1] for p in pairs], dtype=float)
    predicted = np.asarray([p[0] for p in pairs], dtype=float)
    error = np.abs(predicted - actual)
    denominator = np.abs(actual) + np.abs(predicted)
    smape = np.divide(200 * error, denominator, out=np.zeros_like(error), where=denominator != 0)
    return dict(matched=len(pairs), mae_bytes=float(error.mean()), rmse_bytes=float(np.sqrt(np.mean(error**2))),
                smape_percent=float(smape.mean()),
                within_tolerance_percent=float(100 * np.mean(error <= np.maximum(.1, relative * np.abs(actual)))))


def audit(output, tf=None):
    output = Path(output)
    summary = read_json(output / 'node_load_model_summary.json')
    require(summary['status'] == 'COMPLETE' and summary['run_id'] == output.name, 'Load summary identity/status')
    validate_options(summary['models'], summary['profile'], summary['epochs'])
    for key, expected in dict(input_points=192, output_points=64, forecast_stride=64, interval_ms=60000,
                              minimum_training_windows=32, minimum_new_points=64, window_growth_points=64,
                              resource_control=False, connection_control=False, offload_control=False,
                              target='waiting_assignment_bytes', units='B',
                              model_scope='PER_RUN_PER_NODE_PER_FAMILY', quality_standard='EXPERIMENTAL_UNCALIBRATED').items():
        require(summary[key] == expected, 'Incorrect load contract: ' + key)
    modern=summary.get('schema_version',1)==2
    baseline_forecasts={}; baseline_report=None
    if modern:
        from audit_node_load_baselines import audit as audit_baselines
        baseline_forecasts,baseline_report=audit_baselines(output,replay=tf is not None)
    baseline_name='LR' if modern else 'Persistence'
    relative = .2 if summary['profile'] == 'candidate' else .1
    threshold = 80 if summary['profile'] == 'candidate' else 0
    policy = summary['quality_policy']
    require(policy == dict(matched_targets=64, minimum_within_tolerance_percent=threshold,
                           relative_tolerance=relative, absolute_tolerance_bytes=.1,
                           maximum_smape_percent=200, mae_mse_rmse_limits='UNBOUNDED'), 'Unexpected quality policy')
    samples = defaultdict(dict)
    for row in rows(output / 'node_load_samples.csv'):
        require(row['run_id'] == output.name, 'Samples from another run')
        index = int(row['sample_index'])
        require(index not in samples[row['node_id']], 'Duplicate node sample')
        require(int(row['time_ms']) == index * 60000, 'Node grid time mismatch')
        samples[row['node_id']][index] = float(row['waiting_assignment_bytes'])
    require(samples and set(samples) == set(summary['final_selected']), 'Node set mismatch')
    for values in samples.values():
        require(sorted(values) == list(range(len(values))), 'Missing grid samples')

    forecasts = rows(output / 'node_load_model_predictions.csv')
    evaluations = rows(output / 'node_load_model_evaluations.csv')
    selections = rows(output / 'node_load_model_selection.csv')
    events = [json.loads(line) for line in (output / 'node_load_model_events.jsonl').read_text().splitlines() if line.strip()]
    versions = {(v['node_id'], v['model'], v['version']): v for v in summary['versions']}
    require(len(versions) == len(summary['versions']), 'Duplicate version summary')
    by_version = defaultdict(list)
    by_origin = defaultdict(list)
    forecast_map = {}
    def identity(row):
        require(row['run_id'] == output.name, 'Load row from another run')
        return row['node_id'], row['model'], row['version']
    for row in forecasts:
        key = identity(row)
        require(key in versions, 'Forecast without version')
        origin, target = int(row['origin_index']), int(row['target_index'])
        require(origin in samples[key[0]] and target - origin == int(row['lead_steps'])
                and 1 <= target - origin <= 64, 'Noncausal forecast horizon')
        require(int(row['origin_ms']) == origin * 60000 and int(row['target_ms']) == target * 60000, 'Forecast clock mismatch')
        value = float(row['predicted_bytes']); ms = float(row['inference_vector_ms'])
        require(math.isfinite(value) and value >= 0 and math.isfinite(ms) and ms >= 0, 'Invalid forecast/timing')
        if not modern: close(row['persistence_bytes'], samples[key[0]][origin], 'Unpaired persistence comparator')
        require((*key, target) not in forecast_map, 'Duplicate version target')
        forecast_map[(*key, target)] = row
        by_version[key].append(row); by_origin[(*key, origin)].append(row)
    eval_map = {}
    for row in evaluations:
        key = identity(row); target = int(row['target_index'])
        forecast = forecast_map.get((*key, target))
        require(forecast is not None and (*key, target) not in eval_map, 'Missing/duplicate evaluated forecast')
        require(row['origin_index'] == forecast['origin_index'] and int(row['target_ms']) == target * 60000,
                'Evaluation identity mismatch')
        require(target in samples[key[0]], 'Unobserved future target was scored')
        actual = samples[key[0]][target]; predicted = float(forecast['predicted_bytes'])
        close(row['actual_bytes'], actual, 'Actual target value mismatch')
        close(row['predicted_bytes'], predicted, 'Scored prediction mismatch')
        close(row['absolute_error_bytes'], abs(predicted - actual), 'Absolute error mismatch')
        if not modern: close(row['persistence_error_bytes'], abs(float(forecast['persistence_bytes']) - actual), 'Baseline error mismatch')
        eval_map[(*key, target)] = row
    for key, forecast in forecast_map.items():
        require((key in eval_map) == (key[-1] in samples[key[0]]), 'Mature target omitted or tail fabricated')

    qualification = {}; family_history = defaultdict(list); trained = {}; last_event = defaultdict(lambda: -1)
    states = {}; trained_done = set()
    routing = defaultdict(dict); active = {node:baseline_name for node in samples}
    version_events = defaultdict(list)
    for event in events:
        key = identity(event); index = event['sample_index']; kind = event['event']
        require(key in versions and event['domain'] == 'NODE_LOAD' and event['time_ms'] == index * 60000,
                'Event identity mismatch')
        require(index in samples[key[0]] and index >= last_event[key[0]], 'Event moved backwards or outside samples')
        last_event[key[0]] = index; version_events[key].append(event)
        if kind == 'TRAINING':
            require(key not in trained, 'Repeated version training')
            trained[key] = index
            states[key] = 'TRAINING'
            family = key[:2]; prior = family_history[family]
            if not prior:
                require(index == 286 and versions[key]['raw_training_points'] == 287, 'First fit boundary mismatch')
            else:
                old = prior[-1]
                endings = [e for e in version_events[old] if e['event'] in ('REJECTED', 'DEGRADED')]
                require(len(endings) == 1 and index >= endings[0]['sample_index'] + 64, 'Retry before fresh observations')
                expected = min(4096, versions[old]['raw_training_points'] + (64 if endings[0]['event'] == 'REJECTED' else 0))
                require(versions[key]['raw_training_points'] == expected, 'Wrong training window growth')
            prior.append(key)
        elif kind == 'TRAINED':
            require(trained.get(key) == index and key not in trained_done, 'Training changed simulation time or repeated')
            trained_done.add(key); states[key] = 'EVALUATING'
        elif kind in ('QUALIFIED', 'REJECTED', 'DEGRADED', 'PROMOTED', 'FALLBACK'):
            matured = sorted((target, row) for (*prefix, target), row in eval_map.items() if tuple(prefix) == key and target <= index)
            require(len(matured) >= 64, 'Decision made before 64 actual targets')
            current = quality([(float(r['predicted_bytes']), float(r['actual_bytes'])) for _, r in matured[-64:]], relative)
            for field, number in current.items(): close(event['metrics'][field], number, 'Event quality mismatch: ' + field)
            passed = current['within_tolerance_percent'] >= threshold and current['smape_percent'] <= 200
            if kind in ('QUALIFIED', 'REJECTED'):
                require(states.get(key) == 'EVALUATING', 'Qualification outside evaluation state')
                require(key not in qualification and len(matured) == 64, 'First qualification not on first 64 targets')
                require(passed == (kind == 'QUALIFIED'), 'Qualification threshold mismatch')
                qualification[key] = 'PASSED' if passed else 'REJECTED'
                states[key] = 'READY' if passed else 'FAILED'
            if kind == 'DEGRADED':
                require(qualification.get(key) == 'PASSED' and not passed and states.get(key) in ('READY','ACTIVE'), 'Invalid degradation')
                states[key] = 'DEGRADED'
            name = key[1] + '-' + key[2]
            if kind == 'PROMOTED':
                require(qualification.get(key) == 'PASSED' and passed and states.get(key) == 'READY', 'Unqualified model selected')
                old = next((v for v in states if v[0] == key[0] and v[1] + '-' + v[2] == active[key[0]]), None)
                if old is not None and states[old] == 'ACTIVE': states[old] = 'READY'
                active[key[0]] = name
                states[key] = 'ACTIVE'
            if kind == 'FALLBACK':
                require(active[key[0]] == name and not passed, 'Invalid fallback')
                active[key[0]] = baseline_name
        else:
            raise ValueError('Unknown load event: ' + kind)
        routing[key[0]][index] = active[key[0]]
    require(active == summary['final_selected'], 'Final routing mismatch')
    require(set(trained) == set(versions) == trained_done, 'Missing training events')
    for node, values in samples.items():
        for model in summary['models']:
            require(bool(family_history[node, model]) == (len(values) >= 287), 'Model never trained despite sufficient history')

    selection_seen = set()
    for row in selections:
        key = identity(row); node = key[0]; index = int(row['selection_index']); target = int(row['target_index'])
        require((node, index) not in selection_seen and index in samples[node] and target == index + 1,
                'Selection grid mismatch')
        selection_seen.add((node, index))
        require(int(row['selection_ms']) == index * 60000 and int(row['target_ms']) == target * 60000, 'Selection clock mismatch')
        decision_indices = [t for t in routing[node] if t <= index]
        expected = routing[node][max(decision_indices)] if decision_indices else baseline_name
        origin = int(row['forecast_origin_index'])
        require(int(row['lead_steps']) == target - origin, 'Selection lost forecast age')
        if modern and expected == baseline_name:
            available=[p for k,p in baseline_forecasts.items() if k[0]==node and k[-1]==target and k[-2]<=index]
            preferred=[p for p in available if p['model']=='LR'] or [p for p in available if p['model']=='ARIMA']
            require(preferred,'Baseline selection has no forecast')
            p=max(preferred,key=lambda r:int(r['origin_index']))
            require(key[1:]==(p['model'],p['version']) and origin==int(p['origin_index']),'Baseline service/fallback differs')
            close(row['predicted_bytes'],p['predicted_bytes'],'Selected baseline differs')
        elif expected == 'Persistence':
            require(key[1:] == ('Persistence', 'fixed-v1') and origin == index, 'Baseline routing mismatch')
            close(row['predicted_bytes'], samples[node][index], 'Selected baseline mismatch')
        else:
            require(key[1] + '-' + key[2] == expected, 'Selection bypasses lifecycle')
            forecast = forecast_map.get((*key, target))
            require(forecast is not None and int(forecast['origin_index']) == origin <= index, 'Selected nonexistent/future forecast')
            close(row['predicted_bytes'], forecast['predicted_bytes'], 'Selected prediction mismatch')
    require(selection_seen == {(n, i) for n, values in samples.items() for i in values if not modern or i>=191}, 'Missing selection samples')

    replay_vectors = 0
    for key, version in versions.items():
        node, model, number = key
        origin = version['trained_at_index']; size = version['raw_training_points']
        require(model in summary['models'] and origin == trained[key], 'Version training metadata mismatch')
        require(size >= 287 and version['training_windows'] == size - 255, 'Wrong supervised window count')
        folder = output / 'node_load_models' / node / model / number
        record = read_json(folder / 'node_training.json')
        require(record['run_id'] == output.name and record['node_id'] == node and record['model'] == model
                and record['version'] == number and record['last_training_index'] == origin
                and record['first_training_index'] == origin - size + 1 and record['epochs'] == summary['epochs'],
                'Training identity/causality mismatch')
        frame = rows(folder / 'training/samples.csv')
        values = [float(r[node + '::notYetProcessedData']) for r in frame]
        expected = [samples[node][i] for i in range(origin - size + 1, origin + 1)]
        require(values == expected, 'Training snapshot is not exactly the observed past')
        report = read_json(folder / 'optimized_training_report.json')
        require(report['training_windows'] == size - 255 and report['training_columns'] == [node + '::notYetProcessedData']
                and report['epochs'] == summary['epochs'], 'Existing trainer report mismatch')
        x_train = np.array([values[i:i+192] for i in range(size - 255)], dtype=np.float64)
        close(report['normalization_mean'], x_train.mean(), 'Future data in normalization mean')
        close(report['normalization_std'], max(x_train.std(), 1) if x_train.std() < 1e-8 else x_train.std(), 'Normalization std mismatch')
        matched = [r for (*prefix, _), r in eval_map.items() if tuple(prefix) == key]
        require(version['forecasts'] == len(by_version[key]) and version['matched'] == len(matched)
                and version['unmatched'] == len(by_version[key]) - len(matched), 'Version forecast counts mismatch')
        require(version['first_qualification'] == qualification.get(key, 'AWAITING_64_TARGETS'), 'Version qualification mismatch')
        require(version['final_state'] == states[key], 'Final lifecycle state mismatch')
        if matched:
            for field, column, rms in [('mae_bytes','absolute_error_bytes',False),('rmse_bytes','absolute_error_bytes',True),
                                       ('paired_persistence_mae_bytes','persistence_error_bytes',False),
                                       ('paired_persistence_rmse_bytes','persistence_error_bytes',True)]:
                if modern and column=='persistence_error_bytes': continue
                values = np.array([float(r[column]) for r in matched]); value = np.sqrt(np.mean(values**2)) if rms else values.mean()
                close(version[field], value, 'Summary metric mismatch: ' + field)
        else:
            require(version['mae_bytes'] is None and version['rmse_bytes'] is None, 'Unmatched forecast given zero error')
        origins = sorted(k[-1] for k in by_origin if k[:3] == key)
        require(origins and origins == list(range(origin, origins[-1] + 1, 64)), 'Forecast block stride mismatch')
        stopped = [e['sample_index'] for e in version_events[key] if e['event'] in ('REJECTED','DEGRADED')]
        require(not stopped or origins[-1] < stopped[0], 'Ineligible version issued a new forecast')
        for at in origins:
            vector = sorted(by_origin[(*key, at)], key=lambda r:int(r['target_index']))
            require([int(r['lead_steps']) for r in vector] == list(range(1, 65)), 'Incomplete forecast vector')
        require(version['inference_calls'] == len(origins), 'Inference call count mismatch')
        close(version['mean_vector_inference_ms'], np.mean([float(by_origin[(*key, at)][0]['inference_vector_ms']) for at in origins]),
              'Vector inference timing mismatch')
        close(version['training_seconds'], record['training_seconds'], 'Training timing mismatch')
        if tf is not None:
            saved = tf.keras.models.load_model(folder / 'model.keras', compile=False)
            inputs = np.asarray([[samples[node][i] for i in range(at - 191, at + 1)] for at in origins], dtype=np.float32)[...,None]
            actual_predictions = saved(inputs, training=False).numpy()
            expected_predictions = np.asarray([[float(r['predicted_bytes']) for r in sorted(by_origin[(*key, at)], key=lambda r:int(r['target_index']))]
                                               for at in origins])
            np.testing.assert_allclose(actual_predictions, expected_predictions, rtol=1e-5, atol=1e-3)
            replay_vectors += len(origins)
    return dict(status='PASSED', run_id=output.name, nodes=len(samples), versions=len(versions), forecasts=len(forecasts),
                matched=len(evaluations), unmatched=len(forecasts)-len(evaluations), selections=len(selections),
                past_only_training='PASSED', target_matching='PASSED', qualification_and_routing='PASSED',
                saved_model_replay=('NOT_APPLICABLE_NO_TRAINED_MODELS' if not versions else 'PASSED' if tf is not None else 'NOT_RUN'),
                replay_vectors=replay_vectors, baselines=baseline_report)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('output', type=Path)
    parser.add_argument('--replay-models', action='store_true')
    args = parser.parse_args()
    tf = None
    if args.replay_models:
        from mobility_online_lstm import tensorflow_runtime
        tf = tensorflow_runtime()
    print(json.dumps(audit(args.output, tf=tf), indent=2))
