"""Causal mobility candidates. Shadow comparison; never changes serving ownership."""
import csv
import importlib.metadata
import json
import time
from pathlib import Path

import numpy as np
from build_mobility_windows import read_json, require, sha256, write_json
from mobility_learning import fit_scaler, transform_x, displacement_to_gps, position_errors
from mobility_online_buffer import REFERENCE
from mobility_online_lstm import training_data, version_name

METHODS = ('IMM/Kalman', 'XGBoost', 'TCN')
SLUGS = {'IMM/Kalman': 'imm-kalman', 'XGBoost': 'xgboost', 'TCN': 'tcn'}
IDENTITY = ['run_id', 'device_id', 'model', 'model_version', 'origin_ms', 'target_ms', 'origin_node']
PRED_FIELDS = IDENTITY + ['predicted_latitude_deg', 'predicted_longitude_deg', 'predict_wall_ms']
EVAL_FIELDS = PRED_FIELDS + ['evaluated_at_sim_ms', 'actual_latitude_deg', 'actual_longitude_deg', 'position_error_m']


def validate_methods(value):
    require(isinstance(value, list) and all(isinstance(m, str) and m in METHODS for m in value)
            and len(value) == len(set(value)), 'Select distinct supported comparison models')
    return [m for m in METHODS if m in value]


def preflight(methods):
    """Fail before simulation if a selected optional dependency is missing."""
    try:
        if 'IMM/Kalman' in methods:
            from filterpy.kalman import IMMEstimator, KalmanFilter  # noqa: F401
        if 'XGBoost' in methods:
            import xgboost  # noqa: F401
    except ImportError as error:
        raise ValueError('Install selected model dependencies: python -m pip install -r '
                         'predictor-ui/scripts/mobility/requirements-models.txt') from error


IMM_CONFIG = dict(state=['east_m', 'east_m_s', 'north_m', 'north_m_s'],
                  modes=['smooth_velocity', 'maneuvering_velocity'],
                  acceleration_std_m_s2=[0.03, 0.6], measurement_std_m=5.0,
                  initial_position_std_m=10.0, initial_velocity_std_m_s=10.0,
                  transition=[[0.95, 0.05], [0.05, 0.95]], initial_probability=[0.5, 0.5],
                  sample_seconds=60, history_points=8, horizon_seconds=60,
                  history_scope='LAST_8_OBSERVATIONS_PER_FORECAST')


def imm_predict(x, config=IMM_CONFIG):
    """Two FilterPy Kalman filters, mixed by the standard IMM estimator.

    Reconstruct from the same eight past positions for each origin. Noise values
    are fixed configuration, not estimated from later targets or the route.
    """
    from filterpy.kalman import IMMEstimator, KalmanFilter
    from filterpy.common import Q_discrete_white_noise
    result = []
    dt = config['sample_seconds']
    for history in np.asarray(x):
        filters = []
        for noise in config['acceleration_std_m_s2']:
            k = KalmanFilter(dim_x=4, dim_z=2)
            k.x = np.array([history[0, 0], 0, history[0, 1], 0], dtype=float).reshape(4, 1)
            k.F = np.array([[1, dt, 0, 0], [0, 1, 0, 0], [0, 0, 1, dt], [0, 0, 0, 1]], dtype=float)
            k.H = np.array([[1, 0, 0, 0], [0, 0, 1, 0]], dtype=float)
            k.P = np.diag([config['initial_position_std_m'] ** 2, config['initial_velocity_std_m_s'] ** 2] * 2)
            k.R = np.eye(2) * config['measurement_std_m'] ** 2
            k.Q = Q_discrete_white_noise(dim=2, dt=dt, var=noise ** 2, block_size=2)
            filters.append(k)
        estimator = IMMEstimator(filters, np.array(config['initial_probability']), np.array(config['transition']))
        for point in history[1:]:
            estimator.predict()
            estimator.update(point[:2].reshape(2, 1))
        estimator.predict()
        result.append(estimator.x[[0, 2], 0])
    return np.asarray(result)


def make_tcn(tf):
    """Compact residual TCN, causal padding and dilations 1,2,4; no custom layers."""
    layers = tf.keras.layers
    inputs = layers.Input((8, 6))
    value = layers.Conv1D(16, 1)(inputs)
    for dilation in (1, 2, 4):
        residual = value
        value = layers.Conv1D(16, 3, padding='causal', dilation_rate=dilation, activation='relu')(value)
        value = layers.Conv1D(16, 3, padding='causal', dilation_rate=dilation)(value)
        value = layers.Activation('relu')(layers.Add()([value, residual]))
    # Take the final causal step without Lambda/custom deserialization.
    value = layers.Flatten()(layers.Cropping1D((7, 0))(value))
    value = layers.Dense(16, activation='relu')(value)
    model = tf.keras.Model(inputs, layers.Dense(2)(value), name='mobility_tcn_v1')
    model.compile(optimizer=tf.keras.optimizers.Adam(learning_rate=0.001, clipnorm=1.0), loss='mse', jit_compile=False)
    return model


class Candidate:
    def __init__(self, method, model, scaler, directory, metadata):
        self.method, self.model, self.scaler, self.directory, self.metadata = method, model, scaler, directory, metadata

    def predict(self, x):
        x = np.asarray(x, dtype=float)
        require(x.ndim == 3 and x.shape[1:] == (8, 6) and np.isfinite(x).all(), 'Invalid comparison input')
        if self.method == 'IMM/Kalman':
            result = imm_predict(x, self.model)
        else:
            scaled = transform_x(x, self.scaler)
            if self.method == 'XGBoost':
                import xgboost as xgb
                matrix = xgb.DMatrix(scaled.reshape(len(x), -1), nthread=1)
                result = np.column_stack([m.predict(matrix) for m in self.model])
            else:
                result = np.asarray(self.model(scaled, training=False))
            result = result * self.scaler['y_scale_m']
        require(result.shape == (len(x), 2) and np.isfinite(result).all(), 'Invalid comparison prediction')
        return result

    @classmethod
    def fit(cls, method, tf, request, snapshot, directory):
        require(method in METHODS, 'Unknown comparison model')
        data = training_data(request)
        saved_request = read_json(snapshot)
        require(saved_request['request_number'] == request.request_number
                and saved_request['requested_at_sim_ms'] == request.requested_at_sim_ms
                and saved_request['training_windows'] == len(request.windows)
                and saved_request['windows'] == json.loads(json.dumps([w.record() for w in request.windows])),
                'Comparison snapshot differs from the live request')
        require(not directory.exists(), 'Refusing existing comparison model')
        directory.mkdir(parents=True)
        started = time.perf_counter()
        meta = dict(schema_version=1, model=method, model_version=version_name(request.request_number),
                    status='TRAINING', role='SHADOW_COMPARISON', run_id=request.windows[0].window.run_id,
                    device_id=request.windows[0].window.device_id, training_cutoff_sim_ms=request.requested_at_sim_ms,
                    shared_training_windows=len(request.windows), fit_windows=0 if method == 'IMM/Kalman' else len(request.windows),
                    snapshot_sha256=sha256(snapshot), history_points=8, horizon_ms=60000, seed=42,
                    device='CPU', versions={})
        write_json(directory / 'training.json', meta)
        scaler = None
        try:
            if method == 'IMM/Kalman':
                model = dict(IMM_CONFIG)
                meta.update(training='FIXED_PARAMETERS_NO_FIT', architecture=IMM_CONFIG['modes'])
                meta['versions']['filterpy'] = importlib.metadata.version('filterpy')
                write_json(directory / 'model.json', model)
            else:
                scaler = fit_scaler(data)
                x = transform_x(data['x'], scaler)
                y = (data['y'] / scaler['y_scale_m']).astype(np.float32)
                write_json(directory / 'preprocessing.json', scaler)
                if method == 'XGBoost':
                    import xgboost as xgb
                    settings = dict(max_depth=3, eta=0.05, subsample=1.0,
                                    colsample_bytree=1.0, objective='reg:squarederror', tree_method='hist',
                                    nthread=1, seed=42, device='cpu', verbosity=0)
                    model = []
                    for axis in range(2):
                        regressor = xgb.train(settings, xgb.DMatrix(x.reshape(len(x), -1), label=y[:, axis], nthread=1),
                                              num_boost_round=100)
                        regressor.save_model(directory / ('axis-%d.json' % axis))
                        model.append(regressor)
                    meta.update(architecture='Two independent 48-feature boosted-tree regressors',
                                hyperparameters=dict(settings, num_boost_round=100))
                    meta['versions']['xgboost'] = xgb.__version__
                else:
                    require(method == 'TCN', 'Unknown model')
                    tf.keras.utils.set_random_seed(42)
                    model = make_tcn(tf)
                    meta.update(architecture='TCN16: 3 residual blocks, two causal k3 convolutions each, dilation 1/2/4, last step, Dense16, Dense2',
                                epochs=20, batch_size=32, parameters=model.count_params())
                    meta['versions']['tensorflow'] = tf.__version__
                    rng = np.random.default_rng(42)
                    with (directory / 'history.csv').open('x', newline='', encoding='utf-8') as stream:
                        writer = csv.writer(stream); writer.writerow(['epoch', 'train_scaled_mse'])
                        for epoch in range(1, 21):
                            order, total = rng.permutation(len(x)), 0.0
                            for offset in range(0, len(x), 32):
                                require(time.perf_counter() - started < 120, 'TCN training time limit exceeded')
                                indices = order[offset:offset + 32]
                                model.reset_metrics()
                                loss = float(model.train_on_batch(x[indices], y[indices], return_dict=True)['loss'])
                                require(np.isfinite(loss), 'Non-finite TCN loss')
                                total += loss * len(indices)
                            writer.writerow([epoch, total / len(x)]); stream.flush()
                    model.save(directory / 'model.keras')
            candidate = cls(method, model, scaler, directory, meta)
            meta.update(status='COMPLETE', train_wall_seconds=time.perf_counter() - started,
                        artifacts={p.name: sha256(p) for p in directory.iterdir() if p.name != 'training.json'})
            write_json(directory / 'training.json', meta)
            restored = cls.load(tf, directory)
            require(np.allclose(candidate.predict(data['x']), restored.predict(data['x']), rtol=1e-5, atol=1e-5),
                    'Comparison model reload differs')
            return restored
        except BaseException as error:
            meta.update(status='FAILED', error=str(error))
            write_json(directory / 'training.json', meta)
            raise

    @classmethod
    def load(cls, tf, directory):
        meta = read_json(directory / 'training.json')
        require(meta['status'] == 'COMPLETE' and meta['model'] in METHODS, 'Incomplete comparison model')
        for name, digest in meta['artifacts'].items():
            require(Path(name).name == name and sha256(directory / name) == digest, 'Comparison artifact changed')
        method = meta['model']
        scaler = None
        if method == 'IMM/Kalman':
            model = read_json(directory / 'model.json')
            require(model == IMM_CONFIG, 'IMM configuration differs')
        else:
            scaler = read_json(directory / 'preprocessing.json')
            if method == 'XGBoost':
                from xgboost import Booster
                model = [Booster(params={'nthread': 1}) for _ in range(2)]
                for axis, regressor in enumerate(model):
                    regressor.load_model(directory / ('axis-%d.json' % axis))
            else:
                model = tf.keras.models.load_model(directory / 'model.keras', compile=False, safe_mode=True)
        return cls(method, model, scaler, directory, meta)


class ComparisonSession:
    """Models share exactly the LSTM training snapshots and post-fit target times."""
    def __init__(self, output, methods, tf, run_id, device_id):
        self.output, self.methods, self.tf = output, validate_methods(methods), tf
        self.models, self.pending, self.versions, self.latest = {}, [], [], []
        self.run_id, self.device_id, self.count = run_id, device_id, 0
        self.streams = []
        for name, fields in [('comparison_predictions.csv', PRED_FIELDS), ('comparison_evaluations.csv', EVAL_FIELDS)]:
            stream = (output / name).open('x', newline='', encoding='utf-8')
            self.streams.append(stream)
        self.pred = csv.DictWriter(self.streams[0], fieldnames=PRED_FIELDS); self.pred.writeheader()
        self.evals = csv.DictWriter(self.streams[1], fieldnames=EVAL_FIELDS); self.evals.writeheader()
        self.save('RUNNING')

    def save(self, status):
        write_json(self.output / 'comparison.json', dict(schema_version=1, status=status, role='SHADOW_COMPARISON',
                   serving_control=False, run_id=self.run_id, device_id=self.device_id, methods=self.methods,
                   schedule='SHARED_LSTM_TRAINING_SNAPSHOTS', matched_per_method=self.count, versions=self.versions))

    def observe(self, row):
        self.latest = []
        for p in self.pending:
            require(int(row['time_ms']) == p['target_ms'] and row['run_id'] == self.run_id
                    and row['device_id'] == self.device_id, 'Comparison target/identity differs')
            lat, lon = float(row['latitude_deg']), float(row['longitude_deg'])
            error = float(position_errors([[p['predicted_latitude_deg'], p['predicted_longitude_deg']]], [[lat, lon]])[0])
            e = dict(p, evaluated_at_sim_ms=int(row['time_ms']), actual_latitude_deg=lat, actual_longitude_deg=lon, position_error_m=error)
            self.evals.writerow(e)
            self.latest.append(dict(model=p['model'], target_ms=p['target_ms'], position_error_m=error))
        if self.pending:
            self.count += 1
            self.streams[1].flush()
        self.pending = []

    def fit(self, request, snapshot):
        self.models = {}
        for method in self.methods:
            relative = Path('comparison-models') / SLUGS[method] / version_name(request.request_number)
            model = Candidate.fit(method, self.tf, request, snapshot, self.output / relative)
            self.models[method] = model
            self.versions.append(dict(model.metadata, path=relative.as_posix()))
        self.save('RUNNING')

    def predict(self, window):
        for method, model in self.models.items():
            started = time.perf_counter()
            delta = model.predict([window.x])
            gps = displacement_to_gps(delta, [[window.anchor_latitude_deg, window.anchor_longitude_deg]], REFERENCE)[0]
            p = dict(run_id=self.run_id, device_id=self.device_id, model=method, model_version=model.metadata['model_version'],
                     origin_ms=window.origin_ms, target_ms=window.target_ms, origin_node=window.origin_node,
                     predicted_latitude_deg=float(gps[0]), predicted_longitude_deg=float(gps[1]),
                     predict_wall_ms=(time.perf_counter() - started) * 1000)
            self.pred.writerow(p); self.pending.append(p)
        self.streams[0].flush()

    def preview(self, now):
        return dict(time_ms=now, methods=self.methods, matched=self.count, forecasts=self.pending, latest=self.latest)

    def finish(self, expected):
        require(not self.pending and self.count == expected - 41 and self.models, 'Incomplete comparison stream')
        self.save('COMPLETE')

    def close(self):
        for stream in self.streams:
            stream.close()
