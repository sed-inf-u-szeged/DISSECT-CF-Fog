import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { ElectronService } from 'src/app/services/electron/electron.service';
import { PanelService } from 'src/app/services/panel-service/panel.service';
import { ToastService } from 'src/app/services/toast-service/toast.service';
import { BATCH_SIZES, LOSS_FUNCTIONS, OPTIMIZERS, SMOOTHING_POLYNOMIAL_DEGREES, SMOOTHING_WINDOW_SIZES } from 'src/app/shared/settings';

@Component({
  selector: 'app-lstm-train-modal',
  templateUrl: './lstm-train-modal.component.html',
  styleUrls: ['./lstm-train-modal.component.scss']
})
export class LstmTrainModalComponent implements OnInit {
  smoothingWindowSizes: number[] = SMOOTHING_WINDOW_SIZES;
  smoothingPolynomialDegrees: number[] = SMOOTHING_POLYNOMIAL_DEGREES;
  inputSizes: number[] = BATCH_SIZES;
  outputSizes: number[] = BATCH_SIZES;
  lossFunctions: any[] = LOSS_FUNCTIONS;
  optimizers: any[] = OPTIMIZERS;

  form: FormGroup;

  trainingStarted: boolean = false;

  trainingProgress: number = 0;
  maxTrainingNumber: number = 0;

  constructor(
    private electron: ElectronService,
    private changeDetectorRef: ChangeDetectorRef,
    private toastService: ToastService,
    private panelService: PanelService
  ) {}

  ngOnInit(): void {
    this.form = new FormGroup({
      modelName: new FormControl('', [Validators.required]),
      datasetsDirectory: new FormControl('', [Validators.required]),
      modelOutputLocation: new FormControl('', [Validators.required]),
      smoothing: new FormGroup({
        windowSize: new FormControl('48', [Validators.required]),
        polynomialDegree: new FormControl('5', [Validators.required])
      }),
      scale: new FormControl(true),
      inputSize: new FormControl('256', [Validators.required]),
      outputSize: new FormControl('64', [Validators.required]),
      lossFunction: new FormControl('mean_squared_error', [Validators.required]),
      optimizer: new FormControl('Adam', [Validators.required]),
      epochs: new FormControl('10', [Validators.required])
    });

    this.electron.receive('open-directory-result-training-datasets').subscribe((res: any) => {
      if (res.result) {
        this.form.controls['datasetsDirectory'].setValue(res.result);
        this.changeDetectorRef.detectChanges();
      }
    });

    this.electron.receive('open-directory-result-output').subscribe((res: any) => {
      if (res.result) {
        this.form.controls['modelOutputLocation'].setValue(res.result);
        this.changeDetectorRef.detectChanges();
      }
    });

    this.electron.receive('train-lstm-model-result').subscribe((res: any) => {
      if (res.result) {
        this.toastService.create('Training completed!', 2000);
        this.trainingStarted = false;
        this.trainingProgress = 0;
        this.panelService
        this.changeDetectorRef.detectChanges();
      } 

      if (res.error) {
        this.toastService.create(res?.error?.message || 'Something went wrong while training!', 2000);
        this.trainingStarted = false;
        this.trainingProgress = 0;
        this.changeDetectorRef.detectChanges();
      }

      this.panelService.enableClose();
    });

    this.electron.receive('train-lstm-model-progress').subscribe((res: any) => {
      if (res.result) {
        this.trainingProgress = res.result['progress'];
        this.changeDetectorRef.detectChanges();
      }
    });
  }

  openDatasetsDirectory() {
    this.electron.send('open-directory', { id: 'training-datasets' });
  }

  openModelOutputLocation() {
    this.electron.send('open-directory', { id: 'output' });
  }

  startTraining() {
    if (this.form.invalid) {
      return;
    }

    console.log(JSON.stringify(this.form.value));
    this.maxTrainingNumber = Number.parseInt(this.form.value.epochs) + 7;
    this.panelService.disableClose();
    this.electron.send('train-lstm-model', this.form.value);
    this.trainingStarted = true;
  }
}
