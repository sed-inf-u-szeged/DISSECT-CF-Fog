import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { ElectronService } from 'src/app/services/electron/electron.service';
import { Stopwatch } from 'src/app/shared/Stopwatch';
import { SocketService } from "../../services/socket/socket.service";
import { PredictorModel } from "../../shared/PredictorModel";
import { Parameter } from "../../shared/Parameter";
import { Prediction } from "../../shared/Prediction";
import { PanelService } from 'src/app/services/panel-service/panel.service';
import { LstmTrainModalComponent } from 'src/app/components/lstm-train-modal/lstm-train-modal.component';
import { BATCH_SIZES, SMOOTHING_POLYNOMIAL_DEGREES, SMOOTHING_WINDOW_SIZES } from 'src/app/shared/settings';
import { getMockPredictions } from 'src/app/mocks/predictions.mock';
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
    private socket: SocketService, 
    private changeDetectorRef: ChangeDetectorRef,
    private panelService: PanelService,
    private modalService: ModalService
  ) {}

  ngOnInit(): void {
    this.form = new FormGroup({
      export: new FormGroup({
        enabled: new FormControl(false),
        location: new FormControl(''),
        saveDataset: new FormControl(true),
        savePredictionSettings: new FormControl(false),
        savePredictions: new FormControl(false),
        saveMetrics: new FormControl(false),
      }),
      prediction: new FormGroup({
        length: new FormControl('64', [Validators.required]),
        testSize: new FormControl('64', [Validators.required]),
        batchSize: new FormControl('256', [Validators.required]),
        smoothing: new FormGroup({
          windowSize: new FormControl('48', [Validators.required]),
          polynomialDegree: new FormControl('5', [Validators.required])
        }),
        scale: new FormControl(true),
        minPredictionTime: new FormControl('0', [Validators.required])
      }),
      predictor: new FormGroup({
        predictor: new FormControl('ARIMA', [Validators.required]),
        hyperparameters: new FormGroup({}),
        options: new FormGroup({})
      })
    });

    this.formChartFilters = new FormGroup({
      original: new FormControl(true),
      preprocessedBeginning: new FormControl(true),
      preprocessedEnd: new FormControl(true),
      predictionTest: new FormControl(true),
      predictionFuture: new FormControl(true),
    });

    this.stopWatch = new Stopwatch();

    if (this.electron.hasApi()) {
      this.socket.connect();

      this.electron.receive('simulation-ended').subscribe((res: any) => {
        this.simulationStarted = false;
        this.setStatusText(`Simulation has ended! (${this.stopWatch.getFormatedTime()})`);
        this.stopWatch.stop();
        this.changeDetectorRef.detectChanges();
      });

      this.socket.receive().subscribe((res: { data: any, event: string }) => {
        switch (res.event) {
          case 'set-ui-predictors':
            this.predictors = res.data.predictors;
            this.onPredictorChange({ target: { value: this.predictors[0].id } });
            break;
          case 'prediction':
            const prediction: Prediction = res.data.prediction;

            if (!this.predictions.has(prediction.prediction_number)) {
              this.predictions.set(prediction.prediction_number, []);
            }
            this.predictions.get(prediction.prediction_number)?.push(prediction);
            this.predictions.get(prediction.prediction_number)?.sort((a, b) => a.feature_name.localeCompare(b.feature_name));

            if (!this.predictionIndexMoved) {
              this.currentPredictionIndex = this.predictions.size - 1;
            }

            break;
        }
        this.changeDetectorRef.detectChanges();
      });

      this.electron.receive('open-directory-result-output').subscribe((res: any) => {
        if (res.result) {
          ((this.form.controls['export'] as FormGroup).controls['location'] as FormControl).setValue(res.result);
          this.changeDetectorRef.detectChanges();
        }
      });

      this.electron.receive(`open-json-result-prediction-settings`).subscribe((res: any) => {
        if (res.result) {
          this.form.patchValue(res.result);
          this.onPredictorChange({ target: { value: res.result.predictor.predictor } });
          this.changeDetectorRef.detectChanges();
        }
      });
    }

    /*this.predictions = getMockPredictions({
      batchSize: 256,
      numOfFeatures: 8,
      numOfPredictions: 64,
      predictionLength: 64,
      testLength: 64
    });

    this.currentPredictionIndex = 0;*/
  }

  onViewSimulation() {
    this.modalService.create(SimulationViewModalComponent, { title: 'Simulation view', predictions: this.getGrouppedPredictions() })
  }

  getGrouppedPredictions() {
    const res: Map<string, Prediction[]> = new Map();
    this.predictions.forEach((predictionIteration: Prediction[]) => {
      predictionIteration.forEach((prediction: Prediction) => {
        if (!res.has(prediction.feature_name)) {
          res.set(prediction.feature_name, []);
        }
        res.get(prediction.feature_name)?.push(prediction);
      });
    });

    res.forEach((predictions: Prediction[]) => {
      predictions.sort((a, b) => a.prediction_number - b.prediction_number);
    });

    return res;
  }

  onPredictorChange(event: any) {
    if (this.predictors.length < 1) {
      return;
    }

    const predictorId: string = event.target.value
    const predictor: PredictorModel | undefined = this.predictors.find(p => p.id == predictorId);
    this.hyperparameters = predictor!.hyperparameters;
    this.options = predictor!.options;

    const getFormValue = (param: Parameter) => {
      if (param.defaultValue) {
        return param.defaultValue;
      }

      switch (param.type.type) {
        case 'select': return param.options?.[0].id;
        case 'text': return '';
        case 'openFile': return '';
        case 'boolean': return false;
      }

      return null;
    }

    const hyperparametersForm = ((this.form.controls['predictor'] as FormGroup).controls['hyperparameters'] as FormGroup);
    Object.keys(hyperparametersForm.controls).forEach(key => hyperparametersForm.removeControl(key));
    if (this.hyperparameters) {
      this.hyperparameters.forEach(hyperparameter => {
        hyperparametersForm.addControl(hyperparameter.id,
          new FormControl(
            getFormValue(hyperparameter),
            hyperparameter.required ? [Validators.required] : null
          )
        )
      });

      this.hyperparameters.forEach(hyperparameter => {
        if (hyperparameter.type.type === 'openFile') {
          this.electron.receive(`open-file-result-${hyperparameter.id}`).subscribe((res: any) => {
            hyperparametersForm.controls[hyperparameter.id].setValue(res.result || '');
            this.changeDetectorRef.detectChanges();
          });
        }
      });
    }

    const optionsForm = ((this.form.controls['predictor'] as FormGroup).controls['options'] as FormGroup);
    Object.keys(optionsForm.controls).forEach(key => optionsForm.removeControl(key));
    if (this.options) {
      this.options.forEach(option => {
        if (option.type.type !== 'button') {
          optionsForm.addControl(option.id,
            new FormControl(
              getFormValue(option),
              option.required ? [Validators.required] : null
            )
          )
        }
      });
    }
  }

  onToggleSimulation() {
    this.simulationStarted = !this.simulationStarted;
    if (this.simulationStarted) {
      if (this.form.valid) {
        this.stopWatch.start();
        this.setStatusText('Simulation has started!');
        this.socket.send({
          event: 'simulation-settings',
          data: { 'simulation-settings': this.form.value }
        });
      } else {
        this.simulationStarted = false;
      }
    } else {
      this.setStatusText(`Simulation has ended! (${this.stopWatch.getFormatedTime()})`);
      this.stopWatch.stop();
      this.socket.send({ // TODO
        event: 'prediction-stop',
        data: this.form.value
      });
    }
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

  onOpenOutputDirectory() {
    this.electron.send('open-directory', { id: 'output' });
  }

  onOpenFile(id: string, options: any) {
    this.electron.send('open-file', { id, options });
  }

  onOpenPredictionSettings() {
    this.electron.send('open-json', { id: 'prediction-settings' });
  }

  showExportSettings() {
    return (this.form.controls['export'] as FormGroup).controls['enabled'].value;
  }

  isExportEnabledAndOutputEmpty() {
    return ((this.form.controls['export'] as FormGroup).controls['location'] as FormControl).value.trim() === '' &&
      ((this.form.controls['export'] as FormGroup).controls['enabled'] as FormControl).value === true;
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

  onGeneralButton(parameter: Parameter) {
    switch (parameter.id) {
      case 'train_lstm_model':
        this.panelService.create(LstmTrainModalComponent, { title: 'LSTM model trainer' });
      break;
    }
  }

}
