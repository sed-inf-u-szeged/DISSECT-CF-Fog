import { ChangeDetectorRef } from '@angular/core';
import { Router } from '@angular/router';
import { Subject } from 'rxjs';
import { ElectronService } from '../../services/electron/electron.service';
import { ModalService } from '../../services/modal-service/modal.service';
import { Prediction } from '../../shared/Prediction';
import { ScenarioComponent } from './scenario.component';

describe('ScenarioComponent data refresh', () => {
  let component: ScenarioComponent;
  let updates: Subject<Map<string, Prediction[]>>;

  function prediction(
    run: string,
    number: number,
    mae = 10,
    state = 'ACTIVE'
  ): Prediction {
    return {
      feature_name: 'test_feature',
      prediction_number: number,
      predictor_settings: { predictor_name: 'LSTM256-v1' },
      original_data: { timestamp: [], data: [] },
      preprocessed_data: { timestamp: [], data: [] },
      test_data_beginning: { timestamp: [], data: [] },
      test_data_end: { timestamp: [], data: [] },
      prediction_future: { timestamp: [], data: [] },
      prediction_test: { timestamp: [], data: [] },
      error_metrics: { mae },
      lifecycle: {
        state,
        is_active: state === 'ACTIVE',
        updated_at: 'test',
        metrics: {
          matched_samples: 64,
          accuracy: 90,
          mae,
          mse: mae * mae,
          rmse: mae,
          smape: 10
        }
      },
      run_info: {
        id: run,
        profile: 'test',
        settings_file: null,
        note: '',
        thresholds: []
      }
    };
  }

  function send(...rows: Prediction[]): void {
    updates.next(new Map([['test_feature', rows]]));
  }

  function current(): Prediction {
    return component.predictions.get(component.currentPredictionIndex)![0];
  }

  beforeEach(() => {
    updates = new Subject<Map<string, Prediction[]>>();

    const electron = {
      hasApi: () => true,
      dataUpdates$: updates.asObservable()
    } as unknown as ElectronService;

    const detector = {
      detectChanges: jasmine.createSpy('detectChanges')
    } as unknown as ChangeDetectorRef;

    component = new ScenarioComponent(
      {} as Router,
      electron,
      detector,
      {} as ModalService
    );
    component.ngOnInit();
  });

  afterEach(() => {
    component.ngOnDestroy();
    updates.complete();
  });

  it('updates metrics and state for an existing prediction', () => {
    send(prediction('run-a', 10, 5, 'ACTIVE'));
    send(prediction('run-a', 10, 30, 'DEGRADED'));

    expect(component.predictions.size).toBe(1);
    expect(component.predictions.get(0)!.length).toBe(1);
    expect(current().lifecycle!.state).toBe('DEGRADED');
    expect(current().lifecycle!.is_active).toBeFalse();
    expect(current().lifecycle!.metrics!.mae).toBe(30);
  });

  it('removes old predictions when the run changes', () => {
    send(prediction('run-a', 10), prediction('run-a', 20));
    component.onPrevPrediction();

    send(prediction('run-b', 10, 3));

    expect(component.predictions.size).toBe(1);
    expect(component.currentPredictionIndex).toBe(0);
    expect(component.predictionIndexMoved).toBeFalse();
    expect(current().run_info!.id).toBe('run-b');
    expect(current().lifecycle!.metrics!.mae).toBe(3);
  });

  it('sorts nonconsecutive prediction numbers and supports navigation', () => {
    send(
      prediction('run-a', 42),
      prediction('run-a', 7),
      prediction('run-a', 19)
    );

    expect(current().prediction_number).toBe(42);
    component.onPrevPrediction();
    expect(current().prediction_number).toBe(19);
    component.onPrevPrediction();
    expect(current().prediction_number).toBe(7);
    component.onNextPrediction();
    expect(current().prediction_number).toBe(19);
    component.onCurrentPrediction();
    expect(current().prediction_number).toBe(42);
  });

  it('keeps the selected prediction while refreshing its metrics', () => {
    send(prediction('run-a', 10), prediction('run-a', 20));
    component.onPrevPrediction();

    send(
      prediction('run-a', 5),
      prediction('run-a', 10, 2),
      prediction('run-a', 20),
      prediction('run-a', 30)
    );

    expect(current().prediction_number).toBe(10);
    expect(current().lifecycle!.metrics!.mae).toBe(2);
    expect(component.predictionIndexMoved).toBeTrue();
  });

  it('clears the page when the incoming snapshot is empty', () => {
    send(prediction('run-a', 10));
    send();

    expect(component.predictions.size).toBe(0);
    expect(component.currentPredictionIndex).toBe(-1);
    expect(component.predictionIndexMoved).toBeFalse();
  });

  it('stops receiving updates after the component is destroyed', () => {
    send(prediction('run-a', 10, 5));
    component.ngOnDestroy();
    send(prediction('run-a', 10, 30));

    expect(current().lifecycle!.metrics!.mae).toBe(5);
  });
});