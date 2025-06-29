import { ChangeDetectionStrategy, Component, EventEmitter, Input, OnChanges, OnDestroy, Output, SimpleChanges } from '@angular/core';
import { UntypedFormBuilder, UntypedFormControl, UntypedFormGroup, ValidatorFn, Validators } from '@angular/forms';
import { Subscription } from 'rxjs';
import { INPUT_VALIDATION_CPU_CORE, INPUT_VALIDATION_NAME, INPUT_VALIDATION_NETWORK_LOAD, INPUT_VALIDATION_POSITIVE_NUMBER, INPUT_VALIDATION_PRICE_PER_TICK } from 'src/app/core/configuration/utils/constants';
import { Instance } from 'src/app/models/instance';
import { PanelService } from 'src/app/services/panel/panel.service';

@Component({
  selector: 'app-configurable-instance',
  templateUrl: './configurable-instance.component.html',
  styleUrls: ['./configurable-instance.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ConfigurableInstanceComponent implements OnChanges, OnDestroy {
  @Input() public instance: Instance;
  @Output() public instanceEmitter = new EventEmitter<Instance>();
  @Output() public removeEmitter = new EventEmitter<string>();

  public instanceFormGroup: UntypedFormGroup;
  public quantity = 1;

  private formChangeSub: Subscription;

  constructor(
    private formBuilder: UntypedFormBuilder,
    public panelService: PanelService
  ) {
    this.createForm();
    this.formChangeSub = this.instanceFormGroup.valueChanges.subscribe(() => {
      this.saveInstance();
    });
  }

  public ngOnDestroy(): void {
    this.formChangeSub?.unsubscribe();
  }

  public ngOnChanges(changes: SimpleChanges): void {
    if (changes.instance) {
      this.updateForm();
    }
  }

  public onStrategyChange(): void {
    this.saveInstance();
  }

  private createForm(): void {
    this.instanceFormGroup = this.formBuilder.group({
      name: this.createFormControl(INPUT_VALIDATION_NAME),
      ram: this.createFormControl(INPUT_VALIDATION_POSITIVE_NUMBER),
      cpuCores: this.createFormControl(INPUT_VALIDATION_POSITIVE_NUMBER),
      cpuProcessingPower: this.createFormControl(INPUT_VALIDATION_CPU_CORE),
      startupProcess: this.createFormControl(INPUT_VALIDATION_POSITIVE_NUMBER),
      //networkLoad: this.createFormControl(INPUT_VALIDATION_NETWORK_LOAD),
      reqDisk: this.createFormControl(INPUT_VALIDATION_POSITIVE_NUMBER),
      hourlyPrice: this.createFormControl(INPUT_VALIDATION_PRICE_PER_TICK)
    });
  }

  public decrease(): void {
    if (this.quantity > 1) {
      this.quantity--;
      this.instance.quantity = this.quantity;
      this.saveInstance();
    }
  }

  public increase(): void {
    this.quantity++;
    this.instance.quantity = this.quantity;
    this.saveInstance();
  }

  private saveInstance() {
    this.setInstanceValues();
    this.instance.valid = this.instanceFormGroup.valid && this.quantity >= 1;

    this.instanceEmitter.emit(this.instance);
  }

  public setInstanceValues() {
    const id = this.instance.id;
    this.instance = this.instanceFormGroup.value;
    this.instance.id = id;

    return this.instance;
  }

  private createFormControl(validation: ValidatorFn[]): UntypedFormControl {
    return new UntypedFormControl('', validation);
  }

  private updateForm(): void {
    if (this.instance) {
      this.instanceFormGroup?.patchValue(this.instance, { emitEvent: false });
    }
    this.quantity = this.instance.quantity ? this.instance.quantity : 1;
  }

  public openInfoPanelForInstances(): void {
    this.panelService.getInstanceData();
    this.panelService.toogle();
  }

  /**
   * Sets the config variables to their default values
   */
  defaultConfiguration(): void {
    this.instanceFormGroup.get('ram').setValue(4294967296);
    this.instanceFormGroup.get('cpuCores').setValue(1);
    this.instanceFormGroup.get('cpuProcessingPower').setValue(0.001);
    this.instanceFormGroup.get('startupProcess').setValue(100);
    this.instanceFormGroup.get('reqDisk').setValue(1073741824);
    this.instanceFormGroup.get('hourlyPrice').setValue(0.204);
    this.instanceFormGroup.get('name').setValue("a1.large");
  }
}
