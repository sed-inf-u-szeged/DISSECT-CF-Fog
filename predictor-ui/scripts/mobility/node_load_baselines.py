"""Run-local adapter for the original standalone ARIMA(3,0,0) and LR.

Every call fits 192 observed one-minute queue samples, then predicts 64 targets.
No global registry or cached weights. Negative queue forecasts are clipped to zero;
raw values and convergence status are retained for inspection.
"""
from pathlib import Path
import sys
import time


def predict_baseline(method, values, origin):
    import numpy as np
    import pandas as pd
    if method not in ('ARIMA', 'LR') or len(values) != 192 or origin < 191:
        raise ValueError('Baselines require 192 past samples')
    scripts = Path(__file__).resolve().parent
    if not (scripts / 'predictor_models').is_dir():
        scripts = scripts.parent
    if str(scripts) not in sys.path:
        sys.path.insert(0, str(scripts))
    from predictor_models.arima_model import ArimaModel
    from predictor_models.linear_regression_model import LinearRegressionModel
    settings = {'predictor': {'hyperparameters': {'p_value': 3, 'd_value': 0, 'q_value': 0}}}
    model = ArimaModel(settings) if method == 'ARIMA' else LinearRegressionModel(settings)
    data = pd.DataFrame({'timestamp': np.arange(origin-191, origin+1), 'data': values})
    started = time.perf_counter()
    try:
        raw = np.asarray(model.predict('queued_bytes', data, 64, False), dtype=float)
        status = model.get_prediction_status(False) if method == 'ARIMA' else {'status': 'fitted'}
        valid = raw.shape == (64,) and np.isfinite(raw).all()
        # A nonconverged fit is visible as a failed block, never as another model.
        usable = valid and status.get('converged') is not False
        return dict(ok=True, usable=bool(usable), status=status,
                    values=np.maximum(0, raw).tolist() if usable else [],
                    raw_values=raw.tolist() if valid else [],
                    clipped_count=int((raw < 0).sum()) if valid else 0,
                    inference_ms=(time.perf_counter()-started)*1000)
    except Exception as error:
        return dict(ok=True, usable=False, status={'status':'failed', 'error':str(error)},
                    values=[], raw_values=[], clipped_count=0,
                    inference_ms=(time.perf_counter()-started)*1000)
