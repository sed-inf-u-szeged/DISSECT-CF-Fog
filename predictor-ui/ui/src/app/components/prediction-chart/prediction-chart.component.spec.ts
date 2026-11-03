import { CommonModule } from '@angular/common';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Prediction } from '../../shared/Prediction';
import { PredictionChartComponent } from './prediction-chart.component';

function fixturePrediction(estimate: number): Prediction {
  return {
    feature_name: 'fog_feature_prediction', prediction_number: 1,
    predictor_settings: { predictor_name: 'TCN256-v1', prediction: { smoothing: null } },
    original_data: { timestamp: [0, 1], data: [10, 20] },
    preprocessed_data: { timestamp: [0, 1], data: [10, 20] },
    test_data_beginning: { timestamp: [0], data: [10] },
    test_data_end: { timestamp: [1], data: [20] },
    prediction_test: { timestamp: [1], data: [estimate] },
    prediction_future: { timestamp: [2], data: [20] },
    error_metrics: {}, lifecycle: null,
    run_info: { id: 'fixture', profile: 'observe', thresholds: [], note: '', settings_file: null,
      tolerance: { relative: 0.1, absolute: 0.1 } }
  };
}

describe('Prediction chart replay', () => {
  let fixture: ComponentFixture<PredictionChartComponent>;
  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [CommonModule], declarations: [PredictionChartComponent] }).compileComponents();
    fixture = TestBed.createComponent(PredictionChartComponent);
    fixture.componentRef.setInput('prediction', fixturePrediction(20));
    fixture.detectChanges();
  });
  afterEach(() => fixture.destroy());
  it('shows raw data and collapses secondary metrics by default', () => {
    const component = fixture.componentInstance;
    expect(component.createPredictionDatasets()[0].hidden).toBeFalse();
    expect(component.createPredictionDatasets()[1].hidden).toBeTrue();
    expect(fixture.nativeElement.querySelectorAll('details[open]').length).toBe(0);
    expect(fixture.nativeElement.querySelector('.primary-metrics').textContent).toContain('100.00');
  });
  it('updates main metrics and chart data when the replay frame changes', () => {
    fixture.componentRef.setInput('prediction', fixturePrediction(30));
    fixture.detectChanges();
    expect(fixture.componentInstance.metrics.accuracy).toBe(0);
    expect(fixture.componentInstance.metrics.mae).toBe(10);
    expect(fixture.componentInstance.chartPrediction.data.datasets[2].data[0].y).toBe(30);
  });
});
