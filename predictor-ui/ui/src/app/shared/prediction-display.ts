import { Prediction } from './Prediction';

type Series = { timestamp: unknown[]; data: unknown[] } | null | undefined;
export type FrameMetrics = {
  matched: number;
  expected: number;
  hits: number | null;
  accuracy: number | null;
  mae: number | null;
  mse: number | null;
  rmse: number | null;
  smape: number | null;
};
export type ChartBounds = { history: number; future: number; min: number; max: number };

function finite(value: unknown): value is number {
  return typeof value === 'number' && Number.isFinite(value);
}

// Match by timestamp, not array position. Ambiguous duplicates are excluded.
function uniqueValues(series: Series): Map<number, number> {
  const values = new Map<number, number>();
  const seen = new Set<number>();
  (series?.timestamp ?? []).forEach((time, index) => {
    if (!finite(time)) { return; }
    if (seen.has(time)) {
      values.delete(time);
      return;
    }
    seen.add(time);
    const value = series?.data[index];
    if (finite(value)) { values.set(time, value); }
  });
  return values;
}

export function frameMetrics(prediction: Prediction): FrameMetrics {
  const actual = uniqueValues(prediction.test_data_end);
  const predicted = uniqueValues(prediction.prediction_test);
  const tolerance = prediction.run_info?.tolerance;
  const hasTolerance = !!tolerance && finite(tolerance.relative) && tolerance.relative >= 0
    && finite(tolerance.absolute) && tolerance.absolute >= 0;
  let matched = 0, hits = 0, abs = 0, squared = 0, smape = 0;
  actual.forEach((value, time) => {
    const estimate = predicted.get(time);
    if (estimate === undefined) { return; }
    const error = Math.abs(estimate - value);
    if (!Number.isFinite(error) || !Number.isFinite(error * error)) { return; }
    matched++;
    abs += error;
    squared += error * error;
    const denominator = Math.abs(value) + Math.abs(estimate);
    if (denominator > 0) { smape += 200 * (error / denominator); }
    if (hasTolerance && error <= Math.max(tolerance!.absolute, tolerance!.relative * Math.abs(value))) {
      hits++;
    }
  });
  return {
    matched,
    expected: Math.max(prediction.test_data_end?.timestamp?.length ?? 0,
      prediction.prediction_test?.timestamp?.length ?? 0),
    hits: hasTolerance && matched > 0 ? hits : null,
    accuracy: hasTolerance && matched > 0 ? 100 * hits / matched : null,
    mae: matched > 0 ? abs / matched : null,
    mse: matched > 0 ? squared / matched : null,
    rmse: matched > 0 ? Math.sqrt(squared / matched) : null,
    smape: matched > 0 ? smape / matched : null
  };
}

export function forecastOrigin(prediction: Prediction): number {
  const future = prediction.prediction_future?.timestamp?.filter(finite) ?? [];
  if (future.length) { return Math.min(...future); }
  const history = prediction.original_data?.timestamp?.filter(finite) ?? [];
  return history.length ? Math.max(...history) + 1 : 0;
}

export function relativePoints(series: Series, origin: number): { x: number; y: number | null }[] {
  return (series?.timestamp ?? []).flatMap((time, index) => finite(time)
    ? [{ x: time - origin, y: finite(series?.data[index]) ? series!.data[index] as number : null }]
    : []);
}

// Common visual scale only. Request numbers are NOT proof of a common absolute time.
export function displayBounds(predictions: Prediction[]): Map<string, ChartBounds> {
  const result = new Map<string, ChartBounds>();
  for (const prediction of predictions) {
    const bounds = result.get(prediction.feature_name)
      ?? { history: 256, future: 64, min: 0, max: 1 };
    const origin = forecastOrigin(prediction);
    for (const series of [prediction.original_data, prediction.test_data_beginning,
      prediction.test_data_end, prediction.prediction_test, prediction.prediction_future]) {
      for (const point of relativePoints(series, origin)) {
        bounds.history = Math.max(bounds.history, -point.x);
        bounds.future = Math.max(bounds.future, point.x + 1);
        if (point.y !== null) {
          bounds.min = Math.min(bounds.min, point.y);
          bounds.max = Math.max(bounds.max, point.y);
        }
      }
    }
    result.set(prediction.feature_name, bounds);
  }
  result.forEach(bounds => {
    const padding = Math.max(1, (bounds.max - bounds.min) * 0.05);
    if (bounds.min < 0) { bounds.min -= padding; }
    bounds.max += padding;
    const step = Math.pow(10, Math.floor(Math.log10(bounds.max - bounds.min)) - 1);
    bounds.min = Math.floor(bounds.min / step) * step;
    bounds.max = Math.ceil(bounds.max / step) * step;
  });
  return result;
}
