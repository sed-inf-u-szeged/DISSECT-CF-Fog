"""Score frozen forecasts only when their target observation arrives on the stream."""
import math
from build_mobility_windows import RADIUS, require
from mobility_learning import error_metrics

MODELS = ('HoldPosition', 'ConstantVelocity', 'LSTM')
PREDICTION_FIELDS = ['model', 'device_id', 'origin_ms', 'target_ms', 'origin_node',
                     'predicted_latitude_deg', 'predicted_longitude_deg']
EVALUATION_FIELDS = ['run_id'] + PREDICTION_FIELDS + [
    'evaluated_at_sim_ms', 'actual_latitude_deg', 'actual_longitude_deg', 'position_error_m']


def gps(latitude, longitude):
    lat, lon = float(latitude), float(longitude)
    require(math.isfinite(lat) and math.isfinite(lon) and abs(lat) <= 90 and abs(lon) <= 180,
            'Invalid evaluation coordinates')
    return lat, lon


def distance_m(predicted, actual):
    """Great-circle position error, using the same Earth radius as the dataset."""
    a, b = [math.radians(v) for v in predicted], [math.radians(v) for v in actual]
    h = math.sin((b[0]-a[0])/2)**2 + math.cos(a[0])*math.cos(b[0])*math.sin((b[1]-a[1])/2)**2
    h = min(1., max(0., h))
    return 2*RADIUS*math.atan2(math.sqrt(h), math.sqrt(1-h))


class DelayedEvaluation:
    """One device, fixed 60-second horizon; a missed target is an error, not a late match."""
    def __init__(self):
        self.current = None
        self.pending = []
        self.last_issued = None
        self.issued = 0
        self.errors = {model: [] for model in MODELS}

    def observe(self, observation):
        current = dict(observation)
        now = int(current['time_ms'])
        actual = gps(current['latitude_deg'], current['longitude_deg'])
        require(now >= 0 and current['run_id'] and current['device_id'], 'Invalid observation identity')
        if self.current is not None:
            require((current['run_id'], current['device_id']) ==
                    (self.current['run_id'], self.current['device_id']), 'Evaluation identity changed')
            require(now == int(self.current['time_ms'])+60000, 'Evaluation observation gap or duplicate')
        require(all(p['target_ms'] >= now for p in self.pending), 'Forecast target was missed')
        matured = []
        for p in self.pending:
            if p['target_ms'] == now:
                error = distance_m(gps(p['predicted_latitude_deg'], p['predicted_longitude_deg']), actual)
                matured.append(dict(p, run_id=current['run_id'], evaluated_at_sim_ms=now,
                                    actual_latitude_deg=actual[0], actual_longitude_deg=actual[1],
                                    position_error_m=error))
        self.current = current
        self.pending = [p for p in self.pending if p['target_ms'] != now]
        for row in matured:
            self.errors[row['model']].append(row['position_error_m'])
        return matured

    def issue(self, predictions):
        require(self.current is not None, 'Observe current position before forecasting')
        now = int(self.current['time_ms'])
        require(not self.pending and self.last_issued != now, 'Duplicate or unresolved forecast batch')
        require(len(predictions) == 3 and {p['model'] for p in predictions} == set(MODELS),
                'All three methods must use the same origin')
        checked = []
        for p in predictions:
            require(set(p) == set(PREDICTION_FIELDS), 'Unexpected forecast fields')
            origin, target = int(p['origin_ms']), int(p['target_ms'])
            require(origin == now and target == now+60000, 'Forecast time mismatch')
            require(p['device_id'] == self.current['device_id']
                    and p['origin_node'] == self.current['current_node'], 'Forecast identity/node mismatch')
            lat, lon = gps(p['predicted_latitude_deg'], p['predicted_longitude_deg'])
            checked.append(dict(p, origin_ms=origin, target_ms=target,
                                predicted_latitude_deg=lat, predicted_longitude_deg=lon))
        self.pending = checked
        self.last_issued = now
        self.issued += 1

    def finish(self):
        require(not self.pending, 'Stream ended with forecasts awaiting real target observations')
        require(self.issued > 0 and all(len(v) == self.issued for v in self.errors.values()),
                'Incomplete or unequal evaluation counts')
        return {model: error_metrics(self.errors[model]) for model in MODELS}
