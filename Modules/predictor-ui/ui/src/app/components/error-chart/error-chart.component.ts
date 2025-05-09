import {AfterViewInit, Component, Input, OnChanges, OnInit, ViewChild} from '@angular/core';
import Chart from "chart.js/auto";
import {Prediction} from "../../shared/Prediction";

@Component({
  selector: 'app-error-chart',
  templateUrl: './error-chart.component.html',
  styleUrls: ['./error-chart.component.scss']
})
export class ErrorChartComponent implements OnInit, AfterViewInit {
  @Input() prediction: Prediction;

  @ViewChild('errorCanvas') errorCanvas!: { nativeElement: any };
  ctx: any;
  chart: any;

  colors: string[] = ['#FF0000', '#00FF00', '#0000FF', '#FFFF00', '#00FFFF'] // TODO add more colors

  ngOnInit() {

  }

  ngAfterViewInit() {
    this.createChart();
  }

  getName() {
    return `Prediction ${this.prediction.prediction_number} (${this.prediction.feature_name})`;
  }

  createChart() {
    this.ctx = this.errorCanvas.nativeElement.getContext('2d');

    const data: number[] = [];
    const backgroundColor: string[] = [];

    Object.keys(this.prediction.error_metrics).forEach((key, i) => {
      data.push(this.prediction.error_metrics[key]);
      backgroundColor.push(this.colors[i]);
    });

    this.chart = new Chart(this.ctx, {
      type: 'bar',
      data: {
        labels: Object.keys(this.prediction.error_metrics),
        datasets: [{ data, backgroundColor }]
      },
      options: {
        layout: {
          padding: 8
        },
        elements: {
          point:{
            radius: 0
          }
        },
        plugins: {
          title: {
            display: true,
            text: this.getName()
          },
          legend: {
            display: false
          }
        },
      }
    });
  }
}
