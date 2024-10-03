import { Component, Input, Output, EventEmitter, OnInit } from '@angular/core';
import { UntypedFormGroup, UntypedFormBuilder, Validators, UntypedFormControl } from '@angular/forms';
import { Application } from 'src/app/models/application';
import { Instance } from 'src/app/models/server-api/server-api';
import { ConfigurationStateService } from 'src/app/services/configuration/configuration-state/configuration-state.service';
import { ResourceSelectionService } from 'src/app/services/configuration/resource-selection/resource-selection.service';
import { PanelService } from 'src/app/services/panel/panel.service';

@Component({
  selector: 'app-application-card',
  templateUrl: './application-card.component.html',
  styleUrls: ['./application-card.component.css']
})
export class ApplicationCardComponent implements OnInit {
  @Input() public application: Application;
  @Output() public removeEmitter = new EventEmitter<string>();

  public appFormGroup: UntypedFormGroup;
  public canJoin: boolean;
  public instance: Instance;
  public strategy: string[];
  public userInstanceInputs: Instance[] = [];

  constructor(
    private formBuilder: UntypedFormBuilder,
    public panelService: PanelService,
    public resourceSelectionService: ResourceSelectionService,
    public configurationStateService: ConfigurationStateService
  ) {}

  public ngOnInit(): void {
    this.createForm();
    this.initForm();
    this.getUserInstances();
  }

  private createForm(): void {
    this.appFormGroup = this.formBuilder.group({
      id: [this.application.id],
      tasksize: this.createNumberFormControl(),
      freq: this.createNumberFormControl(),
      numOfInstruction: this.createNumberFormControl(),
      activationRatio: this.createNumberFormControl2(),
      transferDevider: this.createNumberFormControl()
    });
  }

  private createNumberFormControl(): UntypedFormControl {
    return new UntypedFormControl('', [Validators.required, Validators.pattern('^[0-9]*$'), Validators.min(1)]);
  }

  private createNumberFormControl2(): UntypedFormControl { // TODO!
    return new UntypedFormControl('', [Validators.required, /*Validators.pattern('^[0-9]*$')*/, Validators.min(0.1)]);
  }

  private initForm(): void {
    if (this.application) {
      this.appFormGroup.patchValue(this.application);
    }
    this.canJoin = this.application.canJoin ? this.application.canJoin : false;
    this.instance = this.application.instance ? this.application.instance : undefined;
    this.strategy = this.application.strategy ? this.application.strategy : [];
  }

  public getValidApplication(): Application {
    const isConfigured = this.application.isConfigured;
    this.application = this.appFormGroup.value;
    this.application.isConfigured = isConfigured;
    this.application.canJoin = this.canJoin;
    this.application.strategy = this.strategy;
    this.application.instance = this.instance;
    return this.application;
  }


  public checkValidation(): boolean {
    return this.appFormGroup.valid && this.instance && this.strategy.length !== 0;
  }

  public openInfoPanelForApplications(): void {
    this.panelService.getApplicationData();
    this.panelService.toogle();
  }

  private getUserInstances(): void {
    Object.values(this.configurationStateService.instanceNodes).forEach( instance => {
      if(instance.valid && instance.name && instance.name.length > 0) {
        this.userInstanceInputs.push(Object.assign(instance) as Instance)
      }
    })
  }


  /**
   * Sets the config variables to their default values
   */
  defaultConfiguration(): void {
    this.appFormGroup.get('tasksize').setValue(50000);
    this.appFormGroup.get('freq').setValue(60000);
    this.appFormGroup.get('numOfInstruction').setValue(1000);
    this.appFormGroup.get('activationRatio').setValue(0.9);
    this.appFormGroup.get('transferDevider').setValue(2.0);
    this.canJoin = true;
    this.strategy = ['RandomApplicationStrategy'];
  }

}
