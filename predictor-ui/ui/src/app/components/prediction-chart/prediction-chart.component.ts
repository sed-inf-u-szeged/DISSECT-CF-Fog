import { AfterViewInit, Component, Input, OnChanges, OnDestroy, ViewChild } from '@angular/core';
import Chart from 'chart.js/auto';
import annotationPlugin from 'chartjs-plugin-annotation';
import { Prediction } from '../../shared/Prediction';
import { ChartBounds, displayBounds, forecastOrigin, FrameMetrics, frameMetrics, relativePoints } from '../../shared/prediction-display';

Chart.register(annotationPlugin);

@Component({
  selector: 'app-prediction-chart',
  templateUrl: './prediction-chart.component.html',
  styleUrls: ['./prediction-chart.component.scss']
})
export class PredictionChartComponent implements AfterViewInit, OnChanges, OnDestroy {
  @Input() prediction: Prediction;
  @Input() chartFilters: any;
  @Input() bounds?: ChartBounds;
  @ViewChild('predictionCanvas') predictionCanvas!: { nativeElement: HTMLCanvasElement };
  chartPrediction: any;
  metrics: FrameMetrics;

  ngOnChanges(): void {
    if (!this.prediction) { return; }
    this.metrics = frameMetrics(this.prediction);
    if (this.chartPrediction) {
      this.chartPrediction.data.datasets = this.createPredictionDatasets();
      this.chartPrediction.options.scales = this.createScales();
      this.chartPrediction.options.plugins.annotation.annotations = this.createAnnotations();
      this.chartPrediction.update('none');
    }
  }

  ngAfterViewInit(): void {
    if (!this.prediction) { return; }
    this.metrics = frameMetrics(this.prediction);
    this.chartPrediction = new Chart(this.predictionCanvas.nativeElement, {
      type: 'line',
      data: { datasets: this.createPredictionDatasets() },
      options: {
        animation: false, responsive: true, maintainAspectRatio: false,
        elements: { point: { radius: 0, hitRadius: 5 } },
        scales: this.createScales(),
        plugins: {
          annotation: { annotations: this.createAnnotations() },
          legend: { display: false },
          tooltip: { callbacks: { title: items => `Step ${items[0]?.parsed.x ?? ''} from this forecast origin` } }
        }
      }
    });
  }

  ngOnDestroy(): void { this.chartPrediction?.destroy(); }

  get modelName(): string {
    return this.prediction.predictor_settings?.predictor_name ?? 'Unknown model';
  }

  get featureName(): string {
    return this.prediction.feature_name.replace(/_feature_prediction$/, '').replace(/_{2,}/g, '::');
  }

  get smoothingLabel(): string {
    return this.prediction.predictor_settings?.prediction?.smoothing ? 'Enabled' : 'Disabled';
  }

  formatMetric(value: number | null | undefined): string {
    return typeof value === 'number' && Number.isFinite(value)
      ? value.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) : '—';
  }

  get toleranceLabel(): string {
    const tolerance = this.prediction.run_info?.tolerance;
    return tolerance ? `±${this.formatMetric(tolerance.relative * 100)}% / ±${tolerance.absolute} absolute`
      : 'Tolerance not recorded';
  }

  getLifecycleState(): string { return this.prediction.lifecycle?.state || 'NOT AVAILABLE'; }

  createPredictionDatasets() {
    const origin = forecastOrigin(this.prediction);
    const raw = new Map(relativePoints(this.prediction.original_data, origin).map(point => [point.x, point.y]));
    const processed = relativePoints(this.prediction.test_data_beginning, origin);
    const duplicatesRaw = processed.length > 0 && processed.every(point => point.y === raw.get(point.x));
    const dataset = (label: string, series: any, color: string, hidden: boolean, dashed = false) => ({
      label, data: relativePoints(series, origin), borderColor: color,
      borderWidth: 1, borderDash: dashed ? [4, 3] : [],
      hidden, fill: false, spanGaps: false
    });
    return [
      dataset('Actual data', this.prediction.original_data, '#e8be6d', this.chartFilters?.original === false),
      dataset('Backtest input (processed)', this.prediction.test_data_beginning, '#55cfb9aa',
        this.chartFilters?.preprocessedBeginning !== true || (duplicatesRaw && this.chartFilters?.original !== false), true),
      dataset('Backtest prediction', this.prediction.prediction_test, '#7185ef', this.chartFilters?.predictionTest === false),
      dataset('Future prediction', this.prediction.prediction_future, '#ef5b7d', this.chartFilters?.predictionFuture === false)
    ];
  }

  createScales() {
    const bounds = this.bounds ?? displayBounds([this.prediction]).get(this.prediction.feature_name)!;
    const commonY = this.chartFilters?.commonY !== false;
    return {
      x: {
        type: 'linear' as const, min: -bounds.history, max: bounds.future - 1,
        title: { display: true, text: 'Relative steps · 0 = this forecast origin', color: '#aeb2bc', font: { size: 10 } },
        ticks: { color: '#969ba6', font: { size: 10 }, maxTicksLimit: 11, maxRotation: 0 },
        grid: { color: '#ffffff08' }
      },
      y: {
        min: commonY ? bounds.min : undefined, max: commonY ? bounds.max : undefined,
        ticks: { color: '#969ba6', font: { size: 10 }, maxTicksLimit: 6 }, grid: { color: '#ffffff0b' }
      }
    };
  }

  createAnnotations() {
    const origin = forecastOrigin(this.prediction);
    const times = this.prediction.prediction_test?.timestamp?.filter(Number.isFinite) ?? [];
    const annotations: any = {
      forecast: { type: 'line', xMin: 0, xMax: 0, borderColor: '#b4b7c1', borderWidth: 1, borderDash: [5, 5] }
    };
    if (times.length) {
      const start = Math.min(...times) - origin;
      annotations.backtest = { type: 'line', xMin: start, xMax: start,
        borderColor: '#8498ff88', borderWidth: 1, borderDash: [5, 5] };
    }
    return annotations;
  }
}
