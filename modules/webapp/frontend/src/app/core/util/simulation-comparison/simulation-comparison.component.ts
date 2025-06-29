import {
  AfterViewInit,
  ChangeDetectorRef,
  Component,
  EventEmitter,
  Input,
  OnDestroy,
  OnInit,
  Output,
} from '@angular/core';
import { Observable, Subscription } from 'rxjs';
import { ConfigurationResult } from 'src/app/models/server-api/server-api';
import { UserConfigurationService } from 'src/app/services/configuration/user-configuration/user-configuration.service';
import {Chart} from "chart.js";
import {HttpClient} from "@angular/common/http";

@Component({
  selector: 'app-simulation-comparison',
  templateUrl: './simulation-comparison.component.html',
  styleUrls: ['./simulation-comparison.component.css']
})
export class SimulationComparisonComponent implements OnInit, OnDestroy {
  private resultSub: Subscription;
  private configurationResult: ConfigurationResult;


  public selectedChartNumber: number;
  public chartId: string;
  public chartName: string;
  public chart: Chart;

  @Input() public configId: any;
  @Input() public showSpinner = false;
  @Input() public contentHeight: number;
  @Output() showActions = new EventEmitter<void>();
  @Output() public goBack = new EventEmitter<void>();

  constructor(
    private http: HttpClient,
    private userConfigurationService: UserConfigurationService,
    private changeDetector: ChangeDetectorRef
  ) {}

  public async ngOnInit(): Promise<void> {
    console.log('Onint');
    console.log("CONFIG ID SIMU=" + this.configId);
    this.chartId = "ActuatorEventsChart";

    await this.userConfigurationService.getConfig(this.configId).toPromise().then(res => {
      this.configurationResult = res;
    });
    console.log('-------- Simulation Comparison --------');
    console.log(this.configurationResult);
    console.log('----------------');

    this.chartId = "ActuatorEventsChart";
    this.chartName = "Actuator Events Chart";
    this.createActuatorEventsChart();
    this.selectedChartNumber = 0;

    this.changeDetector.detectChanges();
  }

  public ngOnDestroy(): void {
    this.resultSub?.unsubscribe();
  }

  public createActuatorEventsChart() {
    const jobDataList = [];
    let counter = 0;
    this.configurationResult.config.jobs.forEach((job) => {
        console.log(job);

        const jobData = {
          label: `Job ${++counter} (id: ${job._id})`,
          data: [],
          backgroundColor: 'blue'
        };
        for (const property in job.simulatorJobResult.actuatorEvents) {
          console.log(`${property}: ${job.simulatorJobResult.actuatorEvents[property]}`);
          jobData.data.push(job.simulatorJobResult.actuatorEvents[property]);
        }
        jobDataList.push(jobData);
      }
    );

    console.log('-------- JOB DATA LIST ----------');
    console.log(jobDataList);
    console.log('-------- JOB DATA LIST -----------');

    this.chart = new Chart('ActuatorEventsChart', {
        type: 'bar',

        data: {// values on X-Axis
          labels: ['Event Count', 'Node Change', 'Position Change', 'Connect To Node Event Count', 'Disconnect From Node Event Count'],
          datasets: jobDataList
        },
        options: {
          scales: {
            yAxes: [{
              type: 'logarithmic',
              ticks: {
                callback: function (value, index, values) {
                  return Number(value.toString());//pass tick values as a string into Number function
                }
              },
              afterBuildTicks: function (chartObj) {
                const ticks = [1, 10, 100, 1000, 10000, 100000, 1000000, 10000000, 100000000, 1000000000];
                chartObj.ticks.splice(0, chartObj.ticks.length);
                chartObj.ticks.push(...ticks);
              }
            }]
          },
          aspectRatio: 2.5
        }

      }
    );
  }

  public createArchitectureChart() {
    const jobDataList = [];
    let counter = 0;
    this.configurationResult.config.jobs.forEach((job) => {
        console.log(job);

        const jobData = {
          label: `Job ${++counter} (id: ${job._id})`,
          data: [],
          backgroundColor: 'blue'
        };
        for (const property in job.simulatorJobResult.architecture) {
          console.log(`${property}: ${job.simulatorJobResult.architecture[property]}`);
          jobData.data.push(job.simulatorJobResult.architecture[property]);
        }
        jobDataList.push(jobData);
      }
    );

    console.log('-------- JOB DATA LIST ----------');
    console.log(jobDataList);
    console.log('-------- JOB DATA LIST -----------');


    this.chart = new Chart('ArchitectureChart', {
        type: 'bar',

        data: {// values on X-Axis
          labels: ['Used Virtual Machines', 'Tasks', 'Total Energy Consumption Of Nodes (Watt)', 'Total Energy Consumption Of Devices (Watt)', 'Sum Of Milliseconds On Network', 'Sum Of Byte On Network', 'Highest App Stop Time (Hour)', 'Highest Device Stop Time (Hour)'],
          datasets: jobDataList
        },
        options: {
          scales: {
            yAxes: [{
              type: 'logarithmic',
              ticks: {
                callback: function (value, index, values) {
                  return Number(value.toString());
                }
              },
              afterBuildTicks: function (chartObj) {
                const ticks = [1, 10, 100, 1000, 10000, 100000, 1000000, 10000000, 100000000, 1000000000];
                chartObj.ticks.splice(0, chartObj.ticks.length);
                chartObj.ticks.push(...ticks);
              }
            }]
          },
          aspectRatio: 2.5
        }
      }
    );
  }

  public createCostChart() {
    const jobDataList = [];
    let counter = 0;
    this.configurationResult.config.jobs.forEach((job) => {
        console.log(job);

        const jobData = {
          label: `Job ${++counter} (id: ${job._id})`,
          data: [],
          backgroundColor: 'blue'
        };
        for (const property in job.simulatorJobResult.cost) {
          console.log(`${property}: ${job.simulatorJobResult.cost[property]}`);
          jobData.data.push(job.simulatorJobResult.cost[property]);
        }
        jobDataList.push(jobData);
      }
    );

    console.log('-------- JOB DATA LIST ----------');
    console.log(jobDataList);
    console.log('-------- JOB DATA LIST -----------');


    this.chart = new Chart('CostChart', {
        type: 'bar',
        data: {// values on X-Axis
          labels: ['Total Cost (EUR)', 'Bluemix (EUR)', 'Amazon (EUR)', 'Azure (EUR)', 'Oracle (EUR)'],
          datasets: jobDataList
        },
      }
    );
  }

  public createDataVolumeChart() {
    const jobDataList = [];
    let counter = 0;
    this.configurationResult.config.jobs.forEach((job) => {
        console.log(job);

        const jobData = {
          label: `Job ${++counter} (id: ${job._id})`,
          data: [],
          backgroundColor: 'blue'
        };
        for (const property in job.simulatorJobResult.dataVolume) {
          console.log(`${property}: ${job.simulatorJobResult.dataVolume[property]}`);
          jobData.data.push(job.simulatorJobResult.dataVolume[property]);
        }
        jobDataList.push(jobData);
      }
    );

    console.log('-------- JOB DATA LIST ----------');
    console.log(jobDataList);
    console.log('-------- JOB DATA LIST -----------');


    this.chart = new Chart('DataVolumeChart', {
        type: 'bar',

        data: {// values on X-Axis
          labels: ['Generated Data (bytes)', 'Processed Data (bytes)', 'Arrived Data (Bytes)'],
          datasets: jobDataList
        },
        options: {
          yAxes: [{
            type: 'logarithmic',
            ticks: {
              beginAtZero: true,
              callback: function (value, index, values) {
                return Number(value.toString());
              }
            },
            afterBuildTicks: function (chartObj) {
              const ticks = [1, 10, 100, 1000, 10000, 100000, 1000000, 10000000, 100000000, 1000000000];
              chartObj.ticks.splice(0, chartObj.ticks.length);
              chartObj.ticks.push(...ticks);
            }
          }],
          aspectRatio: 2.5
        }
      }
    );
  }

  public createTimeoutInMinutesChart() {
    const jobDataList = [];
    let counter = 0;
    this.configurationResult.config.jobs.forEach((job) => {
        console.log(job);

        const jobData = {
          label: `Job ${++counter} (id: ${job._id})`,
          data: [job.simulatorJobResult.timeoutInMinutes],
          backgroundColor: 'blue'
        };
        jobDataList.push(jobData);
      }
    );

    console.log('-------- JOB DATA LIST ----------');
    console.log(jobDataList);
    console.log('-------- JOB DATA LIST -----------');


    this.chart = new Chart('TimeoutInMinutesChart', {
        type: 'bar',

        data: {// values on X-Axis
          labels: ['Timeout (minutes)'],
          datasets: jobDataList
        },
        options: {
          yAxes: [{
            type: 'logarithmic',
            ticks: {
              callback: function (value, index, values) {
                return Number(value.toString());
              }
            },
            afterBuildTicks: function (chartObj) {
              const ticks = [1, 10, 100, 1000, 10000, 100000, 1000000, 10000000, 100000000, 1000000000];
              chartObj.ticks.splice(0, chartObj.ticks.length);
              chartObj.ticks.push(...ticks);
            }
          }],
          aspectRatio: 2.5
        }
      }
    );
  }

  public downloadChart(chart: Chart) {
    const a = document.createElement('a');
    a.href = chart.toBase64Image();
    a.download = this.chartName + '.png';
    a.click();
  }

  public stepBackward() {
    if (this.selectedChartNumber > 0) {
      this.selectedChartNumber--;
      this.chart.destroy();
      this.changeDetector.detectChanges();

      switch (this.selectedChartNumber) {
        case 0:
          console.log('BW 0');
          this.chartId = 'ActuatorEventsChart';
          this.changeDetector.detectChanges();
          this.chartName = 'Actuator Events Chart';
          this.createActuatorEventsChart();
          this.changeDetector.detectChanges();

          break;
        case 1:
          console.log('BW 1');
          this.chartId = 'ArchitectureChart';
          this.changeDetector.detectChanges();
          this.chartName = 'Architecture Chart';
          this.createArchitectureChart();
          this.changeDetector.detectChanges();

          break;
        case 2:
          console.log('BW 2');
          this.chartId = 'CostChart';
          this.changeDetector.detectChanges();
          this.chartName = 'Cost Chart';
          this.createCostChart();
          this.changeDetector.detectChanges();

          break;
        case 3:
          console.log('BW 3');
          this.chartId = 'DataVolumeChart';
          this.changeDetector.detectChanges();
          this.chartName = 'Data Volume Chart';
          this.createDataVolumeChart();
          this.changeDetector.detectChanges();

          break;
        case 4:
          console.log('BW 4');
          this.chartId = 'TimeoutInMinutesChart';
          this.changeDetector.detectChanges();
          this.chartName = 'Timeout Chart (minutes)';
          this.createTimeoutInMinutesChart();
          this.changeDetector.detectChanges();
          break;
      }

      this.changeDetector.detectChanges();
    }
  }

  public stepForward() {
    if (this.selectedChartNumber < 4) {
      this.selectedChartNumber++;
      this.chart.destroy();
      this.changeDetector.detectChanges();

      switch (this.selectedChartNumber) {
        case 0:
          console.log('FW 0');
          this.chartId = 'ActuatorEventsChart';
          this.changeDetector.detectChanges();
          this.chartName = 'Actuator Events Chart';
          this.createActuatorEventsChart();
          this.changeDetector.detectChanges();

          break;
        case 1:
          console.log('FW 1');
          this.chartId = 'ArchitectureChart';
          this.changeDetector.detectChanges();
          this.chartName = 'Architecture Chart';
          this.createArchitectureChart();
          this.changeDetector.detectChanges();

          break;
        case 2:
          console.log('FW 2');
          this.chartId = 'CostChart';
          this.changeDetector.detectChanges();
          this.chartName = 'Cost Chart';
          this.createCostChart();
          this.changeDetector.detectChanges();

          break;
        case 3:
          console.log('FW 3');
          this.chartId = 'DataVolumeChart';
          this.changeDetector.detectChanges();
          this.chartName = 'Data Volume Chart';
          this.createDataVolumeChart();
          this.changeDetector.detectChanges();

          break;
        case 4:
          console.log('FW 4');
          this.chartId = 'TimeoutInMinutesChart';
          this.changeDetector.detectChanges();
          this.chartName = 'Timeout Chart (minutes)';
          this.createTimeoutInMinutesChart();
          this.changeDetector.detectChanges();
          break;
      }

      this.changeDetector.detectChanges();
    }
  }
}
