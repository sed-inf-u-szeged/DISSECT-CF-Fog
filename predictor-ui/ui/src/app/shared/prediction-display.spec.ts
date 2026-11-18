import { Prediction } from './Prediction';
import { displayBounds, forecastOrigin, frameMetrics, relativePoints } from './prediction-display';

function prediction(actual: any[], estimated: any[], tolerance: any = { relative: 0.1, absolute: 0.1 }): Prediction {
  return {
    feature_name: 'fog',
    test_data_end: { timestamp: actual.map((_, i) => i), data: actual },
    prediction_test: { timestamp: estimated.map((_, i) => i), data: estimated },
    run_info: { tolerance }
  } as Prediction;
}

describe('Frame backtest metrics', () => {
  it('uses relative tolerance and the absolute floor, including zero and negative actuals', () => {
    const result = frameMetrics(prediction([0, 100, -100, 100], [0.1, 110, -110, 111]));
    expect(result.matched).toBe(4);
    expect(result.hits).toBe(3);
    expect(result.accuracy).toBe(75);
    expect(result.mae).toBeCloseTo(7.775, 8);
  });
  it('joins timestamps rather than matching array positions', () => {
    const p = prediction([10, 20], [20, 10]);
    p.prediction_test.timestamp = [1, 0];
    expect(frameMetrics(p).mae).toBe(0);
    expect(frameMetrics(p).accuracy).toBe(100);
  });
  it('excludes missing, non-finite and duplicate observations without counting them as zeros', () => {
    const p = prediction([10, 20, null, 30, 40], [10, 20, 0, Infinity, 40]);
    p.test_data_end.timestamp = [0, 0, 2, 3, 4];
    const result = frameMetrics(p);
    expect(result.matched).toBe(1);
    expect(result.expected).toBe(5);
    expect(result.mae).toBe(0);
  });
  it('shows unavailable accuracy for missing settings while retaining error magnitude', () => {
    const result = frameMetrics(prediction([10], [15], null));
    expect(result.accuracy).toBeNull();
    expect(result.mae).toBe(5);
  });
  it('accepts exact zero tolerance, but rejects invalid tolerance values', () => {
    expect(frameMetrics(prediction([0, 10], [0, 11], { relative: 0, absolute: 0 })).accuracy).toBe(50);
    expect(frameMetrics(prediction([0], [0], { relative: -1, absolute: 0 })).accuracy).toBeNull();
  });
  it('reports missing matched observations as unavailable, not 0% accuracy', () => {
    const p = prediction([10], [10]);
    p.prediction_test.timestamp = [20];
    const result = frameMetrics(p);
    expect(result.matched).toBe(0);
    expect(result.accuracy).toBeNull();
    expect(result.mae).toBeNull();
  });
  it('does not replace current frame metrics with the latest lifecycle snapshot', () => {
    const p = prediction([10], [10]);
    p.lifecycle = { metrics: { accuracy: 0, mae: 1000 } } as any;
    expect(frameMetrics(p).accuracy).toBe(100);
    expect(frameMetrics(p).mae).toBe(0);
  });
});

describe('Relative chart display', () => {
  it('places both 128 and 256 histories before their own origins without fabricating older values', () => {
    const make = (length: number) => ({ feature_name: 'fog',
      original_data: { timestamp: Array.from({length}, (_, i) => i), data: Array(length).fill(1) },
      prediction_future: { timestamp: [length, length + 63], data: [2, 3] }
    } as Prediction);
    const small = make(128), large = make(256);
    expect(relativePoints(small.original_data, forecastOrigin(small))[0].x).toBe(-128);
    expect(relativePoints(large.original_data, forecastOrigin(large))[0].x).toBe(-256);
    expect(relativePoints(small.original_data, forecastOrigin(small)).length).toBe(128);
    const bounds = displayBounds([small, large]).get('fog')!;
    expect(bounds.history).toBe(256);
    expect(bounds.future).toBe(64);
    expect(bounds.max).toBeGreaterThan(3);
  });
  it('uses gaps for invalid values and never turns null into zero', () => {
    expect(relativePoints({ timestamp: [0, 1, null], data: [null, 4, 2] }, 2))
      .toEqual([{ x: -2, y: null }, { x: -1, y: 4 }]);
  });
});
