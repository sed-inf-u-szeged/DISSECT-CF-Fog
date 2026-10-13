import {
  AfterViewInit,
  Component,
  Input,
  OnChanges,
  OnDestroy,
  ViewChild
} from '@angular/core';
import Chart from 'chart.js/auto';
import annotationPlugin from 'chartjs-plugin-annotation';
import { Prediction } from '../../shared/Prediction';

Chart.register(annotationPlugin);

@Component({
  selector: 'app-prediction-chart',
  templateUrl: './prediction-chart.component.html',
  styleUrls: ['./prediction-chart.component.scss']
})
export class PredictionChartComponent
  implements AfterViewInit, OnChanges, OnDestroy {

  @Input() prediction: Prediction;
  @Input() chartFilters: any;

  @ViewChild('predictionCanvas')
  predictionCanvas!: { nativeElement: HTMLCanvasElement };

  @ViewChild('errorCanvas')
  errorCanvas!: { nativeElement: HTMLCanvasElement };

  chartPrediction: any;
  chartError: any;

  errorColors: string[] = [
    '#ff5479',
    '#98ff54',
    '#546eff',
    '#fcff54',
    '#f154ff'
  ];

  ngOnChanges(changes: any): void {
    if (!this.prediction) {
      return;
    }

    if (
      this.chartPrediction &&
      (changes.prediction || changes.chartFilters)
    ) {
      this.chartPrediction.data.labels = this.createLabels();
      this.chartPrediction.data.datasets =
        this.createPredictionDatasets();

      this.chartPrediction.options.plugins.title.text =
        this.getName();

      this.chartPrediction.options.plugins.annotation.annotations =
        this.createAnnotations();

      this.chartPrediction.update();
    }

    if (changes.prediction && this.chartError) {
      this.chartError.data.labels = this.createErrorLabels();
      this.chartError.data.datasets = this.createErrorDatasets();
      this.chartError.update();
    }
  }

  ngAfterViewInit(): void {
    this.createPredictionChart();
    this.createErrorChart();
  }

  ngOnDestroy(): void {
    this.chartPrediction?.destroy();
    this.chartError?.destroy();
  }

  getName(): string {
    return `Feature: ${this.prediction.feature_name}\npredicted with: ${this.prediction.predictor_settings.predictor_name}`;
  }

  getId(): string {
    return this.getName()
      .replaceAll(' ', '_')
      .replaceAll('(', '')
      .replaceAll(')', '');
  }

  getLifecycleState(): string {
    return this.prediction.lifecycle?.state || 'NOT AVAILABLE';
  }

  getLifecycleClass(): string {
    return this.getLifecycleState()
      .toLowerCase()
      .replaceAll('_', '-');
  }

  getActiveLabel(): string {
    return this.prediction.lifecycle?.is_active
      ? 'ACTIVE MODEL'
      : 'NOT ACTIVE';
  }

  formatMetric(value: number | null | undefined): string {
    if (value === undefined || value === null || !Number.isFinite(value)) {
      return '—';
    }

    return value.toFixed(4);
  }

  createPredictionDatasets() {
    const datasets = [];

    if (this.prediction.test_data_beginning) {
      datasets.push({
        label: 'test_data_beginning',
        data: this.createCoordinateArray(
          this.prediction.test_data_beginning.timestamp,
          this.prediction.test_data_beginning.data
        ),
        borderColor: '#ffd174',
        borderWidth: 1,
        hidden: this.chartFilters
          ? !this.chartFilters.preprocessedBeginning
          : false
      });
    }

    if (this.prediction.test_data_end) {
      datasets.push({
        label: 'test_data_end',
        data: this.createCoordinateArray(
          this.prediction.test_data_end.timestamp,
          this.prediction.test_data_end.data
        ),
        borderColor: '#caa65c',
        borderWidth: 1,
        hidden: this.chartFilters
          ? !this.chartFilters.preprocessedEnd
          : false
      });
    }

    if (this.prediction.prediction_test) {
      datasets.push({
        label: 'prediction_test',
        data: this.createCoordinateArray(
          this.prediction.prediction_test.timestamp,
          this.prediction.prediction_test.data
        ),
        borderColor: '#546eff',
        borderWidth: 1,
        hidden: this.chartFilters
          ? !this.chartFilters.predictionTest
          : false,
        backgroundColor: '#0000001A',
        fill: '-1'
      });
    }

    if (this.prediction.prediction_future) {
      datasets.push({
        label: 'prediction_future',
        data: this.createCoordinateArray(
          this.prediction.prediction_future.timestamp,
          this.prediction.prediction_future.data
        ),
        borderColor: '#ff5479',
        borderWidth: 1,
        hidden: this.chartFilters
          ? !this.chartFilters.predictionFuture
          : false
      });
    }

    if (this.prediction.original_data) {
      datasets.push({
        label: 'original_data',
        data: this.createCoordinateArray(
          this.prediction.original_data.timestamp,
          this.prediction.original_data.data
        ),
        borderColor: '#ffd17417',
        borderWidth: 1,
        hidden: this.chartFilters
          ? !this.chartFilters.original
          : false
      });
    }

    return datasets;
  }

  createAnnotations() {
    const testStart =
      this.prediction.test_data_beginning?.timestamp?.length ?? 0;

    const futureStart =
      this.prediction.original_data?.timestamp?.length ?? 0;

    return {
      line1: {
        type: 'line' as const,
        xMin: testStart,
        xMax: testStart,
        borderColor: '#ffd17444',
        borderDash: [5, 5]
      },
      line2: {
        type: 'line' as const,
        xMin: futureStart,
        xMax: futureStart,
        borderColor: '#ffd17444',
        borderDash: [5, 5]
      }
    };
  }

  createPredictionChart(): void {
    this.chartPrediction = new Chart(
      this.predictionCanvas.nativeElement,
      {
        type: 'line',
        data: {
          labels: this.createLabels(),
          datasets: this.createPredictionDatasets()
        },
        options: {
          animation: false,
          responsive: true,
          maintainAspectRatio: false,
          layout: {
            padding: 8
          },
          elements: {
            point: {
              radius: 0
            }
          },
          plugins: {
            annotation: {
              annotations: this.createAnnotations()
            },
            title: {
              display: true,
              text: this.getName(),
              color: '#ffd174'
            },
            legend: {
              display: false
            }
          }
        }
      }
    );
  }

  createErrorLabels(): string[] {
    return this.prediction.error_metrics
      ? Object.keys(this.prediction.error_metrics)
      : ['No error metrics'];
  }

  createErrorDatasets() {
    const data: number[] = [];
    const backgroundColor: string[] = [];

    if (this.prediction.error_metrics) {
      Object.keys(this.prediction.error_metrics).forEach((key, index) => {
        data.push(this.prediction.error_metrics[key]);
        backgroundColor.push(
          this.errorColors[index % this.errorColors.length]
        );
      });
    }

    return [{ data, backgroundColor }];
  }

  createErrorChart(): void {
    this.chartError = new Chart(
      this.errorCanvas.nativeElement,
      {
        type: 'bar',
        data: {
          labels: this.createErrorLabels(),
          datasets: this.createErrorDatasets()
        },
        options: {
          animation: false,
          responsive: true,
          maintainAspectRatio: false,
          scales: {
            x: {
              ticks: {
                font: {
                  size: 9
                }
              }
            }
          },
          layout: {
            padding: 8
          },
          plugins: {
            title: {
              display: true,
              text: 'Error metrics'
            },
            legend: {
              display: false
            }
          }
        }
      }
    );
  }

  createLabels(): string[] {
    const labels: string[] = [];
    const settings = this.prediction.predictor_settings['prediction'];
    const length = settings['batchSize'] + settings['length'];

    for (let index = 0; index < length; index++) {
      labels.push(index.toString());
    }

    return labels;
  }

  createCoordinateArray(
    timestamps: number[],
    values: number[]
  ): { x: number; y: number | null }[] {
    const coordinates: { x: number; y: number | null }[] = [];

    if (timestamps[0] > 0) {
      for (let index = 0; index < timestamps[0]; index++) {
        coordinates.push({
          x: index,
          y: null
        });
      }
    }

    for (let index = 0; index < timestamps.length; index++) {
      coordinates.push({
        x: timestamps[index],
        y: values[index]
      });
    }

    return coordinates;
  }
}