import { Component, Input, OnInit } from '@angular/core';
import { Instance } from 'src/app/models/instance';
import { ConfigurationStateService } from 'src/app/services/configuration/configuration-state/configuration-state.service';
import { StepperService } from 'src/app/services/configuration/stepper/stepper.service';
import { PanelService } from 'src/app/services/panel/panel.service';

@Component({
  selector: 'app-list-instances',
  templateUrl: './list-instances.component.html',
  styleUrls: ['./list-instances.component.css']
})
export class ListInstancesComponent {
  @Input() public instances: Instance[] = [];

  public instanceIndex = 0;
  public isValidConfiguration = false;

  constructor(
    public configurationService: ConfigurationStateService,
    public stepperService: StepperService,
    public panelService: PanelService,
  ) {
    this.createInstance();
    this.panelService.getInstanceData();
   }

  private createInstance(): void {
    this.instanceIndex += 1;
    const instanceId = 'instance' + this.instanceIndex;
    const instance = new Instance();
    instance.id = instanceId;
    this.configurationService.instanceNodes[instance.id] = instance;
    this.instances.push(instance);
  }

  public addInstance(): void {
    this.createInstance();
    this.checkIsValidConfiguration();
  }

  public removeInstance(instanceId: string): void {
    delete this.configurationService.instanceNodes[instanceId];
    const arrayIndex = this.instances.findIndex(station => station.id === instanceId);
    this.instances.splice(arrayIndex, 1);
    this.checkIsValidConfiguration();
  }

  public getInstanceFromEmitter(instance: Instance): void {
    this.configurationService.instanceNodes[instance.id] = instance;
    this.checkIsValidConfiguration();
  }

  public checkReadyAndNext(): void {
    if (this.isValidConfiguration) {
      this.stepperService.stepForward();
    }
  }

  private checkIsValidConfiguration(): void {
    this.isValidConfiguration =
      Object.values(this.configurationService.instanceNodes).length > 0
        ? !Object.values(this.configurationService.instanceNodes).some(node => node.valid === false)
        : false;
  }

}
