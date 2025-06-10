import { AfterViewInit, Component, Input, OnChanges, OnInit, ViewChild } from '@angular/core';
import Chart from 'chart.js/auto';
import annotationPlugin from 'chartjs-plugin-annotation';
import { Prediction } from "../../shared/Prediction";
Chart.register(annotationPlugin);

@Component({
  selector: 'app-prediction-chart',
  templateUrl: './prediction-chart.component.html',
  styleUrls: ['./prediction-chart.component.scss']
})
export class PredictionChartComponent implements OnInit, AfterViewInit, OnChanges {
  @Input() prediction: Prediction;
  @Input() chartFilters: any;

  @ViewChild('predictionCanvas') predictionCanvas!: { nativeElement: any };
  ctxPrediction: any;
  chartPrediction: any;

  @ViewChild('errorCanvas') errorCanvas!: { nativeElement: any };
  ctxError: any;
  chartError: any;
  errorColors: string[] = ['#ff5479', '#98ff54', '#546eff', '#fcff54', '#f154ff'] // TODO add more colors

  ngOnInit() {

  }

  ngOnChanges(changes: any) {
    if (changes?.chartFilters?.currentValue) {
      if (this.chartPrediction && this.chartFilters) {
        const filters = changes.chartFilters.currentValue;
        this.filterDataset('original_data').hidden = !filters.original;
        this.filterDataset('test_data_beginning').hidden = !filters.preprocessedBeginning;
        this.filterDataset('test_data_end').hidden = !filters.preprocessedEnd;
        this.filterDataset('prediction_test').hidden = !filters.predictionTest;
        this.filterDataset('prediction_future').hidden = !filters.predictionFuture;
        this.chartPrediction.update();
      }
    }

    if (changes.prediction && this.chartPrediction) {
      this.chartPrediction.data.datasets = this.createPredictionDatasets();
      this.chartPrediction.options.plugins.title.text = this.getName();
      this.chartPrediction.update();
    }

    if (changes.prediction && this.chartError) {
      this.chartError.data.datasets = this.createErrorDatasets();
      this.chartError.update();
    }
  }

  ngAfterViewInit() {
    this.createPredictionChart();
    this.createErrorChart();
  }

  getName() {
    return `Feature: ${this.prediction.feature_name}\npredicted with: ${this.prediction.predictor_settings.predictor_name}`;
  }

  getId() {
    return this.getName()
      .replaceAll(' ', '_')
      .replaceAll('(', '')
      .replaceAll(')', '');
  }

  filterDataset(label: string) {
    return this.chartPrediction.data.datasets.find((dataset: any) => dataset.label === label)
  }

  createPredictionDatasets() {
    let datasets = [];

    if (this.prediction.test_data_beginning) {
      datasets.push({
        label: 'test_data_beginning',
        data: this.createCoordinateArray(
          this.prediction.test_data_beginning.timestamp,
          this.prediction.test_data_beginning.data
        ),
        borderColor: '#ffd174',
        borderWidth: 1,
        hidden: this.chartFilters ? !this.chartFilters.preprocessedBeginning : false
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
        hidden: this.chartFilters ? !this.chartFilters.preprocessedEnd : false
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
        hidden: this.chartFilters ? !this.chartFilters?.predictionTest : false,
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
        hidden: this.chartFilters ? !this.chartFilters?.predictionFuture : false
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
        hidden: this.chartFilters ? !this.chartFilters?.original : false
      });
    }

    return datasets;
  }

  createPredictionChart() {
    this.ctxPrediction = this.predictionCanvas.nativeElement.getContext('2d');

    this.chartPrediction = new Chart(this.ctxPrediction, {
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
            annotations: {
              line1: {
                type: 'line',
                xMin: this.prediction.test_data_beginning.timestamp.length,
                xMax: this.prediction.test_data_beginning.timestamp.length,
                borderColor: '#ffd17444',
                borderDash: [5, 5]
              },
              line2: {
                type: 'line',
                xMin: this.prediction.original_data.timestamp.length,
                xMax: this.prediction.original_data.timestamp.length,
                borderColor: '#ffd17444',
                borderDash: [5, 5]
              }
            }
          },
          title: {
            display: true,
            text: this.getName(),
            color: '#ffd174'
          },
          legend: {
            display: false
          }
        },
      }
    });
  }

  createErrorDatasets() {
    const data: number[] = [];
    const backgroundColor: string[] = [];

    if (this.prediction.error_metrics) {
      Object.keys(this.prediction.error_metrics).forEach((key, i) => {
        data.push(this.prediction.error_metrics[key]);
        backgroundColor.push(this.errorColors[i]);
      });
    }

    return [{ data, backgroundColor }];
  }

  createErrorChart() {
    this.ctxError = this.errorCanvas.nativeElement.getContext('2d');

    

    this.chartError = new Chart(this.ctxError, {
      type: 'bar',
      data: {
        labels: this.prediction.error_metrics ? Object.keys(this.prediction.error_metrics) : ['No error metrics'],
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
                size: 9,
              }
            }
          }
        },
        layout: {
          padding: 8
        },
        elements: {
          point: {
            radius: 0
          }
        },
        plugins: {
          title: {
            display: true,
            text: 'Error metrics'
          },
          legend: {
            display: false
          }
        },
      }
    });
  }

  createLabels() {
    const labels: string[] = [];
    const len = this.prediction.predictor_settings['prediction']['batchSize'] + this.prediction.predictor_settings['prediction']['length'];
    for (let i = 0; i < len; i++) {
      labels.push(i.toString());
    }
    return labels;
  }

  createCoordinateArray(x: number[], y: number[]) {
    const res: { x: number, y: number | null }[] = [];

    if (x[0] > 0) {
      for (let i = 0; i < x[0]; i++) {
        res.push({
          x: i,
          y: null
        });
      }
    }

    for (let i = 0; i < x.length; i++) {
      res.push({
        x: x[i],
        y: y[i]
      });
    }

    return res;
  }
}
