import { CommonModule } from '@angular/common';
import { NO_ERRORS_SCHEMA } from '@angular/core';
import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { ModalService } from '../../services/modal-service/modal.service';
import { Prediction } from '../../shared/Prediction';
import { SimulationViewModalComponent } from './simulation-view-modal.component';

function frame(number: number): Prediction {
  return { feature_name: 'fog_feature_prediction', prediction_number: number,
    predictor_settings: { predictor_name: 'TCN256-v1' },
    original_data: { timestamp: [0, 1], data: [5, 10] },
    test_data_beginning: { timestamp: [0], data: [5] },
    test_data_end: { timestamp: [1], data: [10] },
    prediction_test: { timestamp: [1], data: [10] },
    prediction_future: { timestamp: [2], data: [10] }
  } as Prediction;
}

describe('Simulation replay controls', () => {
  let fixture: ComponentFixture<SimulationViewModalComponent>;
  let modal: { data: any };
  beforeEach(async () => {
    modal = { data: { predictions: new Map([['fog-TCN', [frame(1), frame(2), frame(3)]]]) } };
    await TestBed.configureTestingModule({ imports: [CommonModule, FormsModule, ReactiveFormsModule],
      declarations: [SimulationViewModalComponent], providers: [{ provide: ModalService, useValue: modal }], schemas: [NO_ERRORS_SCHEMA] }).compileComponents();
  });
  afterEach(() => fixture?.destroy());
  function open() {
    fixture = TestBed.createComponent(SimulationViewModalComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }
  it('initializes a selected series, speed and first frame', () => {
    const component = open();
    expect(component.currentPrediction?.prediction_number).toBe(1);
    expect(component.form.controls.speed.value).toBe(1000);
    expect(fixture.nativeElement.querySelectorAll('option').length).toBe(1);
  });
  it('opens an empty result safely and disables play', () => {
    modal.data.predictions = new Map();
    const component = open();
    expect(component.currentPrediction).toBeUndefined();
    expect(fixture.nativeElement.querySelector('.empty-state').textContent).toContain('No prediction frames');
    expect(fixture.nativeElement.querySelector('.play-toggle').disabled).toBeTrue();
  });
  it('advances, pauses, resumes and stops at the last frame', fakeAsync(() => {
    const component = open();
    component.form.controls.speed.setValue(100);
    component.play(); tick(100);
    expect(component.currentPredictionIndex).toBe(1);
    component.stop(); tick(200);
    expect(component.currentPredictionIndex).toBe(1);
    component.play(); tick(100);
    expect(component.currentPredictionIndex).toBe(2);
    expect(component.playing).toBeFalse();
  }));
  it('cancels playback when selecting another series or destroying the view', fakeAsync(() => {
    modal.data.predictions.set('second', [frame(11), frame(12)]);
    const component = open();
    component.form.controls.speed.setValue(100);
    component.play();
    component.form.controls.feature.setValue('second'); component.onChangeFeature(); tick(200);
    expect(component.playing).toBeFalse();
    expect(component.currentPrediction?.prediction_number).toBe(11);
    component.play(); fixture.destroy(); tick(300);
    expect(component.currentPrediction?.prediction_number).toBe(11);
  }));
  it('rejects empty, zero and out-of-range playback intervals', () => {
    const component = open();
    for (const value of [null, 0, -1, 60001]) {
      component.form.controls.speed.setValue(value); component.play();
      expect(component.playing).toBeFalse();
    }
  });
  it('opens 315 frames without reading every historical data series', () => {
    const rows = Array.from({length: 315}, (_, index) => frame(index + 1));
    Object.defineProperty(rows[314], 'original_data', { get() { throw new Error('Unnecessary historical scan'); } });
    modal.data.predictions = new Map([['fog-TCN', rows]]);
    const component = open();
    expect(component.frames.length).toBe(315);
    expect(component.currentPrediction?.prediction_number).toBe(1);
  });
});
