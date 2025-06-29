import {
  ChangeDetectorRef,
  Component,
  EventEmitter,
  Input,
  OnInit,
  Output,
} from '@angular/core';
import { Observable } from 'rxjs';
import { ConfigurationFile, ConfigurationResult } from 'src/app/models/server-api/server-api';
import { UserConfigurationService } from 'src/app/services/configuration/user-configuration/user-configuration.service';
import { PanelService } from 'src/app/services/panel/panel.service';
import { Simulation } from "../../../models/configuration-result";

@Component({
  selector: 'app-configuration-result',
  templateUrl: './configuration-result.component.html',
  styleUrls: ['./configuration-result.component.css']
})
export class ConfigurationResultComponent implements OnInit {
  @Output() showActions = new EventEmitter<void>();
  @Input() public showSpinner = false;
  @Input() public contentHeight: number;
  @Input() public configResultObservable: Observable<ConfigurationResult>;

  public configurationResult: ConfigurationResult;
  public selectedSimulationNumber: number;
  public selectedSimulation: Simulation;
  public selectedSimulationData: any;
  public simulationError: boolean;

  constructor(
    private changeDetectorRef: ChangeDetectorRef,
    public configService: UserConfigurationService,
    private panelService: PanelService
  ) {}

  /**
   * Initializes the configuration result component.
   */
  public async ngOnInit(): Promise<void> {
    // Checks whether the observable is set from where the data of the newly created configuration will be returned
    if (this.configResultObservable) {
      this.showSpinner = true;

      // await the answer of the observable, so the configuration will be set
      await this.configResultObservable.toPromise().then(
        res => {
          this.configurationResult = res;
        }
      )

      // Check whether all of the simulations are processed or not
      let allSimulationProcessed = this.areAllSimulationProcessed();

      // Counter the counts the number of the attempts to get the created configuration for which all simulations have been completed
      let numberOfAttempts = 0;

      // Get again the configuration from MongoDB while all of its simulations haven't been completed
      while(!allSimulationProcessed) {
        numberOfAttempts++;

        const config = await this.configService.getConfig(this.configurationResult.config._id).toPromise().then(
          res => {

            this.configurationResult = res;

            return res.config;
          }
        );

        allSimulationProcessed = this.areAllSimulationProcessed(config);
        if (!allSimulationProcessed) {
          await this.sleep(2000); // Wait 2000ms if not all simulations have been processed for the given configuration
        }
      }

      // Set the selected simulation to the first one in the row. (number 0)
      this.setSelectedSimulation(this.selectedSimulationNumber = 0);

      this.showSpinner = false;
      this.showActions.emit();
      this.changeDetectorRef.detectChanges();
    }
  }

  public openPanelInfoForConfigurationError() {
    this.panelService.getConfigurationErrorData();
    this.panelService.toogle();
  }

  public downloadTimeline(): void {
    this.downloadEmbeddedHtmlChart(this.selectedSimulation.results.TIMELINE, 'Timeline.html');
  }

  public downloadDevicesEnergy(): void {
    this.downloadEmbeddedHtmlChart(this.selectedSimulation.results.DEVICES_ENERGY, 'Devices Energy.html');
  }

  public downloadNodesEnergy(): void {
    this.downloadEmbeddedHtmlChart(this.selectedSimulation.results.NODES_ENERGY, 'Nodes Energy.html');
  }

  public downloadAppliances(): void {
    this.downloadFile(this.selectedSimulation.configFiles.APPLIANCES_FILE, 'appliances');
  }

  public downloadDevices(): void {
    this.downloadFile(this.selectedSimulation.configFiles.DEVICES_FILE, 'devices');
  }

  public downloadInstances(): void {
    this.downloadFile(this.selectedSimulation.configFiles.INSTANCES_FILE, 'instances');
  }

  private downloadFile(id, type: ConfigurationFile) {
    this.configService.downloadFileMongo(id, type);
  }

  /**
   * Downloads HTMLs to the device of the user
   * @param htmlAsString The requested HTML as a string
   * @param name The name of the downloaded file
   */
  private downloadEmbeddedHtmlChart(htmlAsString, name) {
    const a = document.createElement('a');
    a.href = URL.createObjectURL(new Blob([htmlAsString], {type: 'text/plain'}));
    a.download = name;
    a.click();
  }

  /**
   * The manual implementation of the sleep function, which is already included in the newer JS functions
   * Holds the execution for the given time
   * @param milliseconds For the time it will be held up
   */
  public async sleep(milliseconds){
    await new Promise(resolve => {
      return setTimeout(resolve, milliseconds)
    });
  };

  /**
   * Checks if all simulations of the given configuration are processed.
   * @param config The config that needs to be checked
   */
  public areAllSimulationProcessed (config = this.configurationResult.config) {
    for (const simulation of config.jobs) {
      if (!['PROCESSED', 'FAILED'].includes(simulation.simulatorJobStatus)) {
        return false;
      }
    }
    return true;
  }


  /**
   * Sets the selected simulation/chart to simulation of the given number
   * @param simulationNumber The number of the requested simulation
   */
  public setSelectedSimulation(simulationNumber: number) {
    this.changeDetectorRef.detectChanges();

    this.selectedSimulation = this.configurationResult.config.jobs[simulationNumber];
    this.simulationError = this.selectedSimulation.simulatorJobStatus !== 'PROCESSED';
    this.selectedSimulationData = JSON.stringify(this.selectedSimulation.simulatorJobResult);

    this.changeDetectorRef.detectChanges();
  }

  /**
   * Steps one forward between charts if it is a valid step
   */
  public stepForward() {
    if (this.selectedSimulationNumber < this.configurationResult.config.jobs.length - 1) {
      this.selectedSimulation = null;
      this.setSelectedSimulation(++this.selectedSimulationNumber);
      this.changeDetectorRef.detectChanges();
    }
  }

  /**
   * Steps one back between charts if it is a valid step
   */
  public stepBackward() {
    if (this.selectedSimulationNumber > 0) {
      this.selectedSimulation = null;
      this.setSelectedSimulation(--this.selectedSimulationNumber);
      this.changeDetectorRef.detectChanges();
    }
  }
}
