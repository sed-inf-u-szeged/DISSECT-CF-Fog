import {
  Component,
  Input,
  Output,
  EventEmitter,
  OnChanges,
  ChangeDetectionStrategy,
  SimpleChanges,
  OnDestroy
} from '@angular/core';
import { UntypedFormGroup, UntypedFormBuilder, UntypedFormControl, Validators, ValidatorFn } from '@angular/forms';
import { Subscription } from 'rxjs';
import { Station } from 'src/app/models/station';
import { ConfigurationStateService } from 'src/app/services/configuration/configuration-state/configuration-state.service';
import { ResourceSelectionService } from 'src/app/services/configuration/resource-selection/resource-selection.service';
import { PanelService } from 'src/app/services/panel/panel.service';
import { INPUT_VALIDATION_CPU_CORE, INPUT_VALIDATION_POSITIVE_FLOAT, INPUT_VALIDATION_POSITIVE_NUMBER } from '../../utils/constants';

@Component({
  selector: 'app-configurable-station',
  templateUrl: './configurable-station.component.html',
  styleUrls: ['./configurable-station.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ConfigurableStationComponent implements OnChanges, OnDestroy {
  @Input() public station: Station;
  @Output() public stationEmitter = new EventEmitter<Station>();
  @Output() public removeEmitter = new EventEmitter<string>();

  public stationFormGroup: UntypedFormGroup;
  public strategy: string[];

  private formChangeSub: Subscription;

  constructor(
    private formBuilder: UntypedFormBuilder,
    public configurationService: ConfigurationStateService,
    public panelService: PanelService,
    public resourceSelectionService: ResourceSelectionService
  ) {
    this.createForm();
    this.formChangeSub = this.stationFormGroup.valueChanges.subscribe(() => {
      this.saveStation();
    });
  }

  public ngOnDestroy(): void {
    this.formChangeSub?.unsubscribe();
  }

  public ngOnChanges(changes: SimpleChanges): void {
    if (changes.station) {
      this.updateForm();
    }
  }

  public onStrategyChange(): void {
    this.saveStation();
  }

  private saveStation() {
    this.setStationValues();
    if (this.stationFormGroup.valid && this.strategy.length !== 0) {
      this.station.valid = true;
    } else {
      this.station.valid = false;
    }
    this.stationEmitter.emit(this.station);
  }

  private createForm(): void {
    this.stationFormGroup = this.formBuilder.group({
      starttime: this.createFormControl(INPUT_VALIDATION_POSITIVE_NUMBER),
      stoptime: this.createFormControl(INPUT_VALIDATION_POSITIVE_NUMBER),
      filesize: this.createFormControl(INPUT_VALIDATION_POSITIVE_NUMBER),
      freq: this.createFormControl(INPUT_VALIDATION_POSITIVE_NUMBER),
      sensorCount: this.createFormControl(INPUT_VALIDATION_POSITIVE_NUMBER),
      //maxinbw: this.createFormControl(INPUT_VALIDATION_POSITIVE_NUMBER),
      maxoutbw: this.createFormControl(INPUT_VALIDATION_POSITIVE_NUMBER),
      //diskbw: this.createFormControl(INPUT_VALIDATION_POSITIVE_NUMBER),
      radius: this.createFormControl(INPUT_VALIDATION_POSITIVE_NUMBER),
      speed: this.createFormControl(INPUT_VALIDATION_POSITIVE_FLOAT),
      cores: this.createFormControl(INPUT_VALIDATION_POSITIVE_NUMBER),
      ram: this.createFormControl(INPUT_VALIDATION_POSITIVE_NUMBER),
      perCoreProcessing: this.createFormControl(INPUT_VALIDATION_CPU_CORE),
      minpower: this.createFormControl(INPUT_VALIDATION_POSITIVE_FLOAT),
      maxpower: this.createFormControl(INPUT_VALIDATION_POSITIVE_FLOAT),
      idlepower: this.createFormControl(INPUT_VALIDATION_POSITIVE_FLOAT),
      capacity: this.createFormControl(INPUT_VALIDATION_POSITIVE_NUMBER),
      latency: this.createFormControl(INPUT_VALIDATION_POSITIVE_NUMBER),
      //ond: this.createFormControl(INPUT_VALIDATION_POSITIVE_NUMBER),
      //offd: this.createFormControl(INPUT_VALIDATION_POSITIVE_NUMBER),
      quantity: this.createFormControl(INPUT_VALIDATION_POSITIVE_NUMBER),
      //range: this.createFormControl(INPUT_VALIDATION_POSITIVE_NUMBER)
    });
  }

  private createFormControl(validation: ValidatorFn[]): UntypedFormControl {
    return new UntypedFormControl('', validation);
  }

  private updateForm(): void {
    if (this.station) {
      this.stationFormGroup?.patchValue(this.station, { emitEvent: false });
    }
    this.strategy = this.station.strategy ? this.station.strategy : [];
  }

  public setStationValues() {
    const id = this.station.id;
    this.station = this.stationFormGroup.value;
    this.station.id = id;
    this.station.strategy = this.strategy;
    return this.station;
  }

  public openInfoPanelForStations(): void {
    this.panelService.getStationData();
    this.panelService.toogle();
  }

  /**
   * Sets the config variables of this phase to their default values
   */
  defaultConfiguration() {
    this.stationFormGroup.get('starttime').setValue(1);
    this.stationFormGroup.get('stoptime').setValue(1_200_000);
    this.stationFormGroup.get('filesize').setValue(50);
    this.stationFormGroup.get('sensorCount').setValue(1);
    this.stationFormGroup.get('freq').setValue(60000);
    this.stationFormGroup.get('maxoutbw').setValue(3250);
    this.stationFormGroup.get('latency').setValue(50);
    this.stationFormGroup.get('radius').setValue(1000);
    this.stationFormGroup.get('speed').setValue(0.0025);
    this.stationFormGroup.get('cores').setValue(1);
    this.stationFormGroup.get('ram').setValue(1073741824);
    this.stationFormGroup.get('perCoreProcessing').setValue(0.001);
    this.stationFormGroup.get('minpower').setValue(0.025);
    this.stationFormGroup.get('maxpower').setValue(0.225);
    this.stationFormGroup.get('idlepower').setValue(0.155);
    this.stationFormGroup.get('capacity').setValue(1073741824);
    this.stationFormGroup.get('quantity').setValue(10);
    this.strategy = ['RandomDeviceStrategy'];
    this.onStrategyChange();
  }
}
