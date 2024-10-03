import { Component } from '@angular/core';
import { UntypedFormGroup, UntypedFormControl, Validators, UntypedFormBuilder } from '@angular/forms';
import { ComputingNodesQuantityData } from 'src/app/models/computing-nodes-quantity-data';
import { ConfigurationStateService } from 'src/app/services/configuration/configuration-state/configuration-state.service';
import { RestartConfigurationService } from 'src/app/services/configuration/restart-configuration/restart-configuration.service';
import { StepperService } from 'src/app/services/configuration/stepper/stepper.service';
import { MAX_NUM_OF_NODES } from '../utils/constants';

@Component({
  selector: 'app-node-quantity-form',
  templateUrl: './node-quantity-form.component.html',
  styleUrls: ['./node-quantity-form.component.css']
})
export class NodeQuantityFormComponent {
  public numOfComputingNodes: UntypedFormGroup;

  constructor(
    private formBuilder: UntypedFormBuilder,
    public restartConfService: RestartConfigurationService,
    public configurationService: ConfigurationStateService,
    public stepperService: StepperService
  ) {
    this.initForm();
  }

  private initForm(): void {
    this.numOfComputingNodes = this.formBuilder.group({
      numOfClouds: new UntypedFormControl('', [
        Validators.required,
        Validators.max(MAX_NUM_OF_NODES),
        Validators.pattern('^[0-9]*$'),
        Validators.min(1)
      ]),
      numOfFogs: new UntypedFormControl('', [
        Validators.max(MAX_NUM_OF_NODES),
        Validators.pattern('^[0-9]*$'),
        Validators.min(1)
      ])
    });
  }

  public sendNodesQuantity(): void {
    if (this.numOfComputingNodes.valid) {
      const nodesQuantity = {
        numberOfClouds: this.numOfComputingNodes.get('numOfClouds').value,
        numberOfFogs: this.numOfComputingNodes.get('numOfFogs').value
          ? this.numOfComputingNodes.get('numOfFogs').value
          : undefined
      } as ComputingNodesQuantityData;

      this.configurationService.setNodesQuantity(nodesQuantity);
      this.stepperService.stepForward();
    }
  }

  public resetConfiguration(): void {
    this.numOfComputingNodes.reset();
    this.configurationService.setNodesQuantity(undefined);
    this.configurationService.computingNodes = { clouds: {}, fogs: {} };
    this.configurationService.stationNodes = {};
    this.restartConfService.restart();
  }

  defaultConfiguration(): void {
    this.numOfComputingNodes.get('numOfClouds').setValue(1);
    this.numOfComputingNodes.get('numOfFogs').setValue(1);
  }
}
