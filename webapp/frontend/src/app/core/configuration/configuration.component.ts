import { Component, ViewChild, AfterViewChecked, ChangeDetectorRef, AfterViewInit, OnDestroy } from '@angular/core';
import { MatStepper } from '@angular/material/stepper';
import { MatLegacyDialog as MatDialog } from '@angular/material/legacy-dialog';
import { MatDrawer } from '@angular/material/sidenav';
import { PanelService } from '../../services/panel/panel.service';
import { Station } from '../../models/station';
import { BehaviorSubject, Subscription } from 'rxjs';
import { StepperService } from '../../services/configuration/stepper/stepper.service';
import { ConfigurationStateService } from '../../services/configuration/configuration-state/configuration-state.service';
import { UserConfigurationService } from '../../services/configuration/user-configuration/user-configuration.service';
import { ResourceSelectionService } from 'src/app/services/configuration/resource-selection/resource-selection.service';

@Component({
  selector: 'app-configuration',
  templateUrl: './configuration.component.html',
  styleUrls: ['./configuration.component.css']
})
export class ConfigurationComponent implements AfterViewInit, AfterViewChecked, OnDestroy {
  public readonly isLinear = true;
  public stations: Station[] = [];

  @ViewChild('stepper') public stepper: MatStepper;
  @ViewChild('drawer') public drawer: MatDrawer;

  public stepperAnimationDoneSubject = new BehaviorSubject<boolean>(false);
  /**
   * Observalbe when the stepper's animation is done on the
   */
  public stepperAnimationDone$ = this.stepperAnimationDoneSubject.asObservable();
  private stationsChangedSubscription: Subscription;

  constructor(
    private changeDetect: ChangeDetectorRef,
    public dialog: MatDialog,
    public stepperService: StepperService,
    public configurationStateService: ConfigurationStateService,
    public configService: UserConfigurationService,
    public configurationService: ConfigurationStateService,
    public panelService: PanelService,
    public resourceSelectionService: ResourceSelectionService
  ) {
    this.resourceSelectionService.refreshResources();
    this.stationsChangedSubscription = this.configurationStateService.stationsChanged$.subscribe(
      () => (this.stations = this.configurationStateService.getStationArray())
    );
  }

  public ngOnDestroy(): void {
    this.configurationService.setNodesQuantity(undefined);
    this.configurationService.computingNodes = { clouds: {}, fogs: {} };
    this.configurationService.stationNodes = {};
    if (this.stationsChangedSubscription) {
      this.stationsChangedSubscription.unsubscribe();
    }
  }

  public ngAfterViewInit(): void {
    if (this.stepper) {
      this.stepperService.setStepper(this.stepper);
    }
    if (this.drawer) {
      this.panelService.setDrawerAsMainDrawer(this.drawer);
    }
  }

  public ngAfterViewChecked(): void {
    this.changeDetect.detectChanges();
  }

  public openInfoPanelForResources(): void {
    this.panelService.getResourceData();
    this.panelService.open();
  }

  public openInfoPanelForApplications(): void {
    this.panelService.getApplicationData();
    this.panelService.open();
  }

  public stepperDone(): void {
    if (this.stepper.selectedIndex === 4) {
      this.stepperAnimationDoneSubject.next(true);
    } else {
      this.stepperAnimationDoneSubject.next(false);
    }
  }
}
