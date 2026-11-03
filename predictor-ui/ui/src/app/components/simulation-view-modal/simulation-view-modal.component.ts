import { Component, OnInit, OnDestroy } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { ModalService } from 'src/app/services/modal-service/modal.service';
import { Prediction } from 'src/app/shared/Prediction';
import { ChartBounds, displayBounds } from '../../shared/prediction-display';

@Component({
  selector: 'app-simulation-view-modal',
  templateUrl: './simulation-view-modal.component.html',
  styleUrls: ['./simulation-view-modal.component.scss']
})
export class SimulationViewModalComponent implements OnInit, OnDestroy {
  form = new FormGroup({
    feature: new FormControl('', { nonNullable: true }),
    speed: new FormControl(1000, [Validators.required, Validators.min(100), Validators.max(60000)])
  });
  currentPredictionIndex = 0;
  playing = false;
  private playInterval?: ReturnType<typeof setInterval>;
  seriesOptions: { key: string; label: string }[] = [];
  private predictions = new Map<string, Prediction[]>();
  frames: Prediction[] = [];
  currentPrediction?: Prediction;
  bounds?: ChartBounds;
  chartFilters = { original: true, preprocessedBeginning: false, predictionTest: true, predictionFuture: true, commonY: false };

  constructor(private modalService: ModalService) {}

  ngOnInit(): void {
    const data = this.modalService.data;
    this.chartFilters = { ...this.chartFilters, ...data?.chartFilters, commonY: false };
    if (data?.predictions instanceof Map) {
      data.predictions.forEach((rows: Prediction[], key: string) => {
        if (!Array.isArray(rows) || !rows.length) { return; }
        this.predictions.set(key, rows);
        const first = rows[0];
        const feature = first.feature_name.replace(/_feature_prediction$/, '').replace(/_{2,}/g, '::');
        this.seriesOptions.push({ key, label: `${feature} · ${first.predictor_settings?.predictor_name ?? 'Unknown model'}` });
      });
    }
    this.form.controls.feature.setValue(this.seriesOptions[0]?.key ?? '');
    this.onChangeFeature();
  }

  ngOnDestroy(): void { this.stop(); }
  updateFilters(): void { this.chartFilters = { ...this.chartFilters }; }

  private showFrame(index: number): void {
    this.currentPredictionIndex = Math.max(0, Math.min(index, this.frames.length - 1));
    this.currentPrediction = this.frames[this.currentPredictionIndex];
    // Only inspect the displayed frame. Large historical runs need no upfront scan.
    this.bounds = this.currentPrediction
      ? displayBounds([this.currentPrediction]).get(this.currentPrediction.feature_name) : undefined;
  }

  onChangeFeature(): void {
    this.stop();
    this.frames = this.predictions.get(this.form.controls.feature.value) ?? [];
    this.showFrame(0);
  }

  goTo(index: number): void {
    this.stop();
    this.showFrame(index);
  }

  togglePlay(): void { this.playing ? this.stop() : this.play(); }

  play(): void {
    if (this.form.invalid || !this.frames.length) { return; }
    this.stop();
    if (this.currentPredictionIndex >= this.frames.length - 1) { this.showFrame(0); }
    if (this.frames.length === 1) { return; }
    this.playing = true;
    this.playInterval = setInterval(() => {
      this.showFrame(this.currentPredictionIndex + 1);
      if (this.currentPredictionIndex >= this.frames.length - 1) { this.stop(); }
    }, this.form.controls.speed.value!);
  }

  stop(): void {
    this.playing = false;
    if (this.playInterval !== undefined) {
      clearInterval(this.playInterval);
      this.playInterval = undefined;
    }
  }
}
