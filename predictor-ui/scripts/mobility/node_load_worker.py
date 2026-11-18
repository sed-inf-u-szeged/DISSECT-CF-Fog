"""Isolated node-load worker: reuse existing 192 -> 64 load trainers and standalone ARIMA/LR predictors.

The JVM sends only already observed values. Training snapshots, saved models and
reports belong to this run, not the legacy global model registry.
"""
import argparse
import contextlib
import importlib.util
import json
import math
import os
from pathlib import Path
import re
import sys
import time


def validate_options(models, profile, epochs):
    if not isinstance(models, list) or any(m not in ('LSTM', 'TCN', 'GRU', 'PatchTST') for m in models) or len(set(models)) != len(models):
        raise ValueError('Load models must be distinct LSTM / TCN / GRU / PatchTST choices')
    if profile not in ('candidate', 'observe'):
        raise ValueError('Unknown node-load quality profile')
    if type(epochs) is not int or not 1 <= epochs <= 200:
        raise ValueError('Load training epochs must be an integer from 1 to 200')
    return models


def preflight(models):
    dependencies = (('tensorflow',) if models else ()) + ('numpy', 'pandas', 'sklearn', 'scipy', 'statsmodels')
    missing = [m for m in dependencies if importlib.util.find_spec(m) is None]
    if missing:
        raise ValueError('Missing load-model dependencies: ' + ', '.join(missing)
                         + '. Install requirements-node-load.txt in dissect-fog.')


def checked_request(request, length):
    if request.get('model') not in ('LSTM', 'TCN', 'GRU', 'PatchTST', 'ARIMA', 'LR'):
        raise ValueError('Unknown load model')
    for key in ('node', 'version'):
        if not isinstance(request.get(key), str) or not re.fullmatch(r'[A-Za-z0-9_-]{1,100}', request[key]):
            raise ValueError('Invalid ' + key)
    index = request.get('origin_index')
    values = request.get('values')
    if type(index) is not int or index < 0 or not isinstance(values, list) or len(values) != length:
        raise ValueError('Invalid history length or origin index')
    if len(values) > index + 1 or any(type(v) not in (int, float) or not math.isfinite(v) or v < 0 for v in values):
        raise ValueError('History must contain finite, nonnegative past values')
    return values


class Worker:
    def __init__(self, output, epochs):
        self.output = output
        self.epochs = epochs
        self.models = {}
        self.tf = None

    def runtime(self):
        if self.tf is None:
            # These settings apply only to this owned worker, not to the simulator.
            os.environ.setdefault('CUDA_VISIBLE_DEVICES', '-1')
            os.environ.setdefault('TF_CPP_MIN_LOG_LEVEL', '2')
            os.environ.setdefault('TF_NUM_INTRAOP_THREADS', '1')
            os.environ.setdefault('TF_NUM_INTEROP_THREADS', '1')
            os.environ.setdefault('OMP_NUM_THREADS', '1')
            import tensorflow as tf
            self.tf = tf
        return self.tf

    def handle(self, request):
        op = request.get('op')
        size = request.get('window_points') if op == 'TRAIN' else 192
        if type(size) is not int or (op == 'TRAIN' and not 287 <= size <= 4096):
            raise ValueError('Invalid training window')
        values = checked_request(request, size)
        key = (request['node'], request['model'], request['version'])
        if op == 'BASELINE':
            from node_load_baselines import predict_baseline
            return predict_baseline(request['model'], values, request['origin_index'])
        if request['model'] in ('ARIMA', 'LR'):
            raise ValueError('Baseline requires BASELINE operation')
        if op not in ('TRAIN', 'PREDICT'):
            raise ValueError('Unknown operation')
        started = time.perf_counter()
        tf = self.runtime()
        import numpy as np
        if op == 'TRAIN':
            if key in self.models:
                raise ValueError('Duplicate trained version')
            # In a frozen batch snapshot these modules live next to this worker;
            # in the repository they live in the scripts parent directory.
            scripts = Path(__file__).resolve().parent
            if not (scripts / 'predictor_models').is_dir():
                scripts = scripts.parent
            sys.path.insert(0, str(scripts))
            from predictor_models import lstm_trainer as base
            from predictor_models.lstm_optimized_training import run
            builder = base.get_model
            if request['model'] == 'TCN':
                from predictor_models.tcn_network import get_model
                builder = get_model
            elif request['model'] == 'GRU':
                from predictor_models.gru_network import get_model
                builder = get_model
            elif request['model'] == 'PatchTST':
                from predictor_models.patchtst_network import get_model
                builder = get_model
            folder = self.output / 'node_load_models' / key[0] / key[1] / key[2]
            dataset = folder / 'training'
            dataset.mkdir(parents=True, exist_ok=False)
            (dataset / 'samples.csv').write_text(key[0] + '::notYetProcessedData\n'
                                                + ''.join(str(v) + '\n' for v in values), encoding='utf-8')
            settings = dict(modelName='model.keras', datasetsDirectory=str(dataset),
                            modelOutputLocation=str(folder), scale=False, smoothing=None,
                            inputSize=192, outputSize=64, loss='combined_mae_mean10',
                            optimizer='Adam', epochs=self.epochs,
                            targetColumn=key[0] + '::notYetProcessedData')
            def register_local(_settings, model_path):
                # No ModelRegistry mutation: model identity includes run/node/family/version.
                return str(Path(model_path).resolve())
            model_path = run(settings, (base.load_file_paths, base.load_dataframes,
                            base.prepare_dataframes, base.make_data, builder, base.save_model, register_local))
            model = tf.keras.models.load_model(model_path, compile=False)
            # Superseded versions remain saved, but need not retain TensorFlow objects.
            for old in list(self.models):
                if old[:2] == key[:2]:
                    del self.models[old]
            self.models[key] = model
            record = dict(run_id=self.output.name, node_id=key[0], model=key[1], version=key[2],
                          origin_index=request['origin_index'], first_training_index=request['origin_index'] - size + 1,
                          last_training_index=request['origin_index'], window_points=size,
                          training_windows=size - 255, epochs=self.epochs, input_points=192, output_points=64,
                          target='waiting_assignment_bytes', units='B', registration='RUN_LOCAL',
                          training_seconds=time.perf_counter() - started)
            (folder / 'node_training.json').write_text(json.dumps(record, indent=2, allow_nan=False) + '\n')
            return dict(ok=True, training_seconds=record['training_seconds'], training_windows=record['training_windows'])
        if key not in self.models:
            raise ValueError('Prediction requested before training')
        result = self.models[key](np.asarray(values, dtype=np.float32).reshape(1, 192, 1), training=False).numpy()[0]
        if result.shape != (64,) or not np.isfinite(result).all() or (result < 0).any():
            raise ValueError('Invalid saved-model forecast')
        return dict(ok=True, values=result.astype(float).tolist(), inference_ms=(time.perf_counter() - started) * 1000)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--epochs', type=int, required=True)
    args = parser.parse_args()
    validate_options([], 'candidate', args.epochs)
    worker = Worker(args.output.resolve(), args.epochs)
    print(json.dumps(dict(ok=True, ready=True)), flush=True)
    for line in sys.stdin:
        try:
            request = json.loads(line)
            if request.get('op') == 'CLOSE':
                print(json.dumps(dict(ok=True)), flush=True)
                return
            with contextlib.redirect_stdout(sys.stderr):
                result = worker.handle(request)
            print(json.dumps(result, allow_nan=False), flush=True)
        except Exception as error:
            import traceback
            traceback.print_exc(file=sys.stderr)
            print(json.dumps(dict(ok=False, error=str(error))), flush=True)
            return


if __name__ == '__main__':
    main()
