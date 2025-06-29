import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { Subscription } from 'rxjs';
import { StepperService } from 'src/app/services/configuration/stepper/stepper.service';
import { PanelService } from 'src/app/services/panel/panel.service';
import { UserConfigurationService } from 'src/app/services/configuration/user-configuration/user-configuration.service';
import { ConfigurationResult } from 'src/app/models/server-api/server-api';

@Component({
  selector: 'app-configuration-end',
  templateUrl: './configuration-end.component.html',
  styleUrls: ['./configuration-end.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ConfigurationEndComponent {
  @Input() public showSpinner = false;
  public configResult: ConfigurationResult;
  public showActions = false;
  private resultSub: Subscription;

  constructor(
    public configService: UserConfigurationService,
    public stepperService: StepperService,
    private panelService: PanelService
  ) {}

  public back(): void {
    this.resultSub?.unsubscribe();
    this.stepperService.stepBack();
  }

  public openPanelInfoForConfigurationError(): void {
    this.panelService.getConfigurationErrorData();
    this.panelService.toogle();
  }

  public showActionsButtons(): void {
    this.showActions = true;
  }
}
