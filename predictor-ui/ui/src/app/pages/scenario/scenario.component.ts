import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { FormControl, FormGroup } from '@angular/forms';
import { Router } from '@angular/router';
import { ElectronService } from 'src/app/services/electron/electron.service';
import { Stopwatch } from 'src/app/shared/Stopwatch';
import { PredictorModel } from "../../shared/PredictorModel";
import { Parameter } from "../../shared/Parameter";
import { Prediction } from "../../shared/Prediction";
import { PanelService } from 'src/app/services/panel-service/panel.service';
import { BATCH_SIZES, SMOOTHING_POLYNOMIAL_DEGREES, SMOOTHING_WINDOW_SIZES } from 'src/app/shared/settings';
import { ModalService } from 'src/app/services/modal-service/modal.service';
import { SimulationViewModalComponent } from 'src/app/components/simulation-view-modal/simulation-view-modal.component';

@Component({
  selector: 'app-scenario',
  templateUrl: './scenario.component.html',
  styleUrls: ['./scenario.component.scss']
})
export class ScenarioComponent implements OnInit {
  batchSizes: number[] = BATCH_SIZES;
  smoothingWindowSizes: number[] = SMOOTHING_WINDOW_SIZES;
  smoothingPolynomialDegrees: number[] = SMOOTHING_POLYNOMIAL_DEGREES;

  predictors: PredictorModel[] = [];
  hyperparameters: Parameter[] = []
  options: Parameter[] = [];

  form: FormGroup;
  formChartFilters: FormGroup;
  stopWatch: Stopwatch;

  statusText: string = '-';

  currentPredictionIndex: number = -1;
  predictionIndexMoved: boolean = false;
  simulationStarted: boolean = false;

  predictions: Map<number, Prediction[]> = new Map();

  constructor(
    private router: Router, 
    private electron: ElectronService, 
    private changeDetectorRef: ChangeDetectorRef,
    private panelService: PanelService,
    private modalService: ModalService
  ) {}

  ngOnInit(): void {
    this.formChartFilters = new FormGroup({
      original: new FormControl(true),
      preprocessedBeginning: new FormControl(true),
      preprocessedEnd: new FormControl(true),
      predictionTest: new FormControl(true),
      predictionFuture: new FormControl(true),
    });

    this.stopWatch = new Stopwatch();

    if (this.electron.hasApi()) {
      this.electron.dataUpdates$.subscribe(
        (data: Map<string, Prediction[]>) => {
          for (let [_, predictionList] of data.entries()) {
            for (let prediction of predictionList) {
                if (!this.predictions.has(prediction.prediction_number))
                  this.predictions.set(prediction.prediction_number, []);
                else if (this.predictions.get(prediction.prediction_number)
                  ?.some(pred => pred.feature_name + pred.predictor_settings.predictor_name === prediction.feature_name + prediction.predictor_settings.predictor_name))
                  continue;

                this.predictions.get(prediction.prediction_number)?.push(prediction);
                this.predictions.get(prediction.prediction_number)?.sort((a, b) => a.feature_name.localeCompare(b.feature_name));

                if (!this.predictionIndexMoved) {
                  this.currentPredictionIndex = this.predictions.size - 1;
                }
              }
            
          }
          this.changeDetectorRef.detectChanges();
        }
      )
    }
  }

  onViewSimulation() {
    this.modalService.create(SimulationViewModalComponent, { title: 'Simulation view', predictions: this.getGrouppedPredictions() })
  }

  getGrouppedPredictions() {
    const res: Map<string, Prediction[]> = new Map();
    this.predictions.forEach((predictionIteration: Prediction[]) => {
      predictionIteration.forEach((prediction: Prediction) => {
        if (!res.has(prediction.feature_name + prediction.predictor_settings.predictor_name)) {
          res.set(prediction.feature_name + prediction.predictor_settings.predictor_name, []);
        }
        res.get(prediction.feature_name + prediction.predictor_settings.predictor_name)?.push(prediction);
      });
    });

    res.forEach((predictions: Prediction[]) => {
      predictions.sort((a, b) => a.prediction_number - b.prediction_number);
    });

    return res;
  }

  setStatusText(text: string) {
    this.statusText = text;
    setTimeout(() => {
      this.statusText = '-';
    }, 2000);
  }

  async onBack() {
    await this.router.navigate(['scenarios']);
  }

  onNextPrediction() {
    if (this.currentPredictionIndex < this.predictions.size - 1) {
      this.currentPredictionIndex += 1;
      this.predictionIndexMoved = true;
    }
  }

  onPrevPrediction() {
    if (this.currentPredictionIndex > 0) {
      this.currentPredictionIndex -= 1;
      this.predictionIndexMoved = true;
    }
  }

  onCurrentPrediction() {
    this.currentPredictionIndex = this.predictions.size - 1;
    this.predictionIndexMoved = false;
  }

}
