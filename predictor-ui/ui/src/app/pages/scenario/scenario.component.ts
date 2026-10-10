import { ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core';
import { FormControl, FormGroup } from '@angular/forms';
import { Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { ElectronService } from 'src/app/services/electron/electron.service';
import { Stopwatch } from 'src/app/shared/Stopwatch';
import { PredictorModel } from '../../shared/PredictorModel';
import { Parameter } from '../../shared/Parameter';
import { Prediction } from '../../shared/Prediction';
import {
  BATCH_SIZES,
  SMOOTHING_POLYNOMIAL_DEGREES,
  SMOOTHING_WINDOW_SIZES
} from 'src/app/shared/settings';
import { ModalService } from 'src/app/services/modal-service/modal.service';
import { SimulationViewModalComponent } from 'src/app/components/simulation-view-modal/simulation-view-modal.component';

@Component({
  selector: 'app-scenario',
  templateUrl: './scenario.component.html',
  styleUrls: ['./scenario.component.scss']
})
export class ScenarioComponent implements OnInit, OnDestroy {
  batchSizes: number[] = BATCH_SIZES;
  smoothingWindowSizes: number[] = SMOOTHING_WINDOW_SIZES;
  smoothingPolynomialDegrees: number[] = SMOOTHING_POLYNOMIAL_DEGREES;

  predictors: PredictorModel[] = [];
  hyperparameters: Parameter[] = [];
  options: Parameter[] = [];

  form = new FormGroup({});
  formChartFilters = new FormGroup({
    original: new FormControl(true),
    preprocessedBeginning: new FormControl(true),
    preprocessedEnd: new FormControl(true),
    predictionTest: new FormControl(true),
    predictionFuture: new FormControl(true)
  });
  stopWatch = new Stopwatch();

  statusText = '-';
  currentPredictionIndex = -1;
  predictionIndexMoved = false;
  simulationStarted = false;
  predictions: Map<number, Prediction[]> = new Map();

  private predictionNumbers: number[] = [];
  private currentRunId: string | null = null;
  private dataSubscription?: Subscription;
  private statusTimer?: ReturnType<typeof setTimeout>;

  constructor(
    private router: Router,
    private electron: ElectronService,
    private changeDetectorRef: ChangeDetectorRef,
    private modalService: ModalService
  ) {}

  ngOnInit(): void {
    if (this.electron.hasApi()) {
      this.dataSubscription = this.electron.dataUpdates$.subscribe(
        data => this.updatePredictions(data)
      );
    }
  }

  ngOnDestroy(): void {
    this.dataSubscription?.unsubscribe();
    if (this.statusTimer !== undefined) {
      clearTimeout(this.statusTimer);
    }
  }

  private updatePredictions(data: Map<string, Prediction[]>): void {
    const selectedNumber = this.predictionNumbers[this.currentPredictionIndex];
    const grouped = new Map<number, Map<string, Prediction>>();
    let incomingRunId: string | null = null;

    for (const predictionList of data.values()) {
      for (const prediction of predictionList) {
        incomingRunId ??= prediction.run_info?.id ?? null;

        const number = prediction.prediction_number;
        if (!grouped.has(number)) {
          grouped.set(number, new Map());
        }

        const key = JSON.stringify([
          prediction.feature_name,
          prediction.predictor_settings.predictor_name
        ]);
        grouped.get(number)!.set(key, prediction);
      }
    }

    const runChanged = incomingRunId !== this.currentRunId;
    this.currentRunId = incomingRunId;

    this.predictionNumbers = Array.from(grouped.keys()).sort((a, b) => a - b);
    const refreshed = new Map<number, Prediction[]>();

    this.predictionNumbers.forEach((number, index) => {
      const rows = Array.from(grouped.get(number)!.values());
      rows.sort((a, b) =>
        a.feature_name.localeCompare(b.feature_name) ||
        (a.predictor_settings.predictor_name ?? '').localeCompare(
          b.predictor_settings.predictor_name ?? ''
        )
      );
      refreshed.set(index, rows);
    });

    this.predictions = refreshed;

    if (runChanged || refreshed.size === 0) {
      this.predictionIndexMoved = false;
    }

    if (!this.predictionIndexMoved) {
      this.currentPredictionIndex = refreshed.size - 1;
    } else {
      const retainedIndex = this.predictionNumbers.indexOf(selectedNumber);
      this.currentPredictionIndex = retainedIndex >= 0
        ? retainedIndex
        : Math.min(
            Math.max(this.currentPredictionIndex, 0),
            refreshed.size - 1
          );
    }

    this.changeDetectorRef.detectChanges();
  }

  onViewSimulation(): void {
    if (this.predictions.size === 0) {
      return;
    }
    this.modalService.create(SimulationViewModalComponent, {
      title: 'Simulation view',
      predictions: this.getGrouppedPredictions()
    });
  }

  getGrouppedPredictions(): Map<string, Prediction[]> {
    const result = new Map<string, Prediction[]>();

    this.predictions.forEach(rows => {
      rows.forEach(prediction => {
        const key = prediction.feature_name
          + prediction.predictor_settings.predictor_name;

        if (!result.has(key)) {
          result.set(key, []);
        }
        result.get(key)!.push(prediction);
      });
    });

    result.forEach(rows =>
      rows.sort((a, b) => a.prediction_number - b.prediction_number)
    );
    return result;
  }

  setStatusText(text: string): void {
    if (this.statusTimer !== undefined) {
      clearTimeout(this.statusTimer);
    }
    this.statusText = text;
    this.statusTimer = setTimeout(() => {
      this.statusText = '-';
    }, 2000);
  }

  async onBack(): Promise<void> {
    await this.router.navigate(['scenarios']);
  }

  onNextPrediction(): void {
    if (this.currentPredictionIndex < this.predictions.size - 1) {
      this.currentPredictionIndex++;
      this.predictionIndexMoved = true;
    }
  }

  onPrevPrediction(): void {
    if (this.currentPredictionIndex > 0) {
      this.currentPredictionIndex--;
      this.predictionIndexMoved = true;
    }
  }

  onCurrentPrediction(): void {
    this.currentPredictionIndex = this.predictions.size - 1;
    this.predictionIndexMoved = false;
  }
}