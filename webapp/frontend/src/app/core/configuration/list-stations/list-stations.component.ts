import { Component, OnDestroy, Input } from '@angular/core';
import { Station } from 'src/app/models/station';
import { Subscription } from 'rxjs';
import { ConfigurationStateService } from 'src/app/services/configuration/configuration-state/configuration-state.service';
import { RestartConfigurationService } from 'src/app/services/configuration/restart-configuration/restart-configuration.service';
import { StepperService } from 'src/app/services/configuration/stepper/stepper.service';

@Component({
  selector: 'app-list-stations',
  templateUrl: './list-stations.component.html',
  styleUrls: ['./list-stations.component.css']
})
export class ListStationsComponent implements OnDestroy {
  @Input() public stations: Station[] = [];
  public stationIndex = 0;
  public isValidConfiguration = false;
  private restartSubscription: Subscription;

  constructor(
    public configurationService: ConfigurationStateService,
    private restartConfService: RestartConfigurationService,
    public stepperService: StepperService
  ) {
    this.createStation();

    this.restartSubscription = this.restartConfService.restartConfiguration$.subscribe(() => {
      this.stationIndex = 0;
      this.isValidConfiguration = false;
      this.createStation();
    });
  }

  public ngOnDestroy(): void {
    this.restartSubscription?.unsubscribe();
  }

  public addStation(): void {
    this.createStation();
    this.checkIsValidConfiguration();
  }

  private createStation(): void {
    this.stationIndex += 1;
    const stationId = 'station' + this.stationIndex;
    const station = new Station();
    station.id = stationId;
    this.configurationService.stationNodes[station.id] = station;
    this.stations.push(station);
  }

  public getStationFromEmitter(station: Station): void {
    this.configurationService.stationNodes[station.id] = station;
    this.checkIsValidConfiguration();
  }

  public checkReadyAndNext(): void {
    if (this.isValidConfiguration) {
      this.configurationService.generateGraph();
      this.stepperService.stepForward();
    }
  }

  public removeStation(stationId: string): void {
    delete this.configurationService.stationNodes[stationId];
    const arrayIndex = this.stations.findIndex(station => station.id === stationId);
    this.stations.splice(arrayIndex, 1);
    this.checkIsValidConfiguration();
  }

  private checkIsValidConfiguration(): void {
    this.isValidConfiguration =
      Object.values(this.configurationService.stationNodes).length > 0
        ? !Object.values(this.configurationService.stationNodes).some(node => node.valid === false)
        : false;
  }
}
