import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { ModalService } from 'src/app/services/modal-service/modal.service';
import { Prediction } from 'src/app/shared/Prediction';

@Component({
  selector: 'app-simulation-view-modal',
  templateUrl: './simulation-view-modal.component.html',
  styleUrls: ['./simulation-view-modal.component.scss']
})
export class SimulationViewModalComponent implements OnInit {
  form: FormGroup;
  currentPredictionIndex: number = 0;
  playing: boolean = false;
  playInterval: any;

  currentPrediction: Prediction

  constructor(
    private modalService: ModalService,
    private changeDetectorRef: ChangeDetectorRef,
    ) {}

  ngOnInit(): void {
    this.form = new FormGroup({
      feature: new FormControl(this.getFeatureNames()[0]),
      speed: new FormControl(1000, [Validators.required])
    });

    this.currentPrediction = this.getCurrentPrediction(this.currentPredictionIndex);
  }

  getFeatureNames() {
    return Array.from(this.modalService.data.predictions.keys());
  }

  getPredictions(): Prediction[] {
    return this.modalService.data.predictions.get(this.form.value.feature);
  }

  getCurrentPrediction(index: number) {
    const predictions: Prediction[] = this.getPredictions();
    return predictions[index];
  }

  togglePlay() {
    if (!this.playing) {
      this.play();
    } else {
      this.stop();
    }
    this.changeDetectorRef.detectChanges();
  }

  play() {
    if (this.form.invalid) {
      return;
    } 

    this.playing = true;
    this.currentPredictionIndex = 0;
    this.currentPrediction = this.getCurrentPrediction(this.currentPredictionIndex);

    this.playInterval = setInterval(() => {
      if (this.getCurrentPrediction(this.currentPredictionIndex + 1) === undefined) {
        this.stop();
      } else {
        this.currentPredictionIndex += 1;
        this.currentPrediction = this.getCurrentPrediction(this.currentPredictionIndex);
      }
      this.changeDetectorRef.detectChanges();
    }, this.form.value.speed)
  }

  stop() {
    this.playing = false;
    clearInterval(this.playInterval);
  }

  onChangeFeature(event: any) {
    this.currentPredictionIndex = 0;
    this.currentPrediction = this.getCurrentPrediction(this.currentPredictionIndex);
    this.changeDetectorRef.detectChanges();
  }

}
