import { Component, Input, Output, EventEmitter, OnChanges, SimpleChanges, OnInit, OnDestroy } from '@angular/core';
import { UntypedFormBuilder, FormGroupDirective, ControlContainer, Validators, UntypedFormGroup, UntypedFormControl } from '@angular/forms';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { ApplicationsDialogComponent } from './applications-dialog/applications-dialog.component';
import { ApplicationsObject } from 'src/app/models/application';
import { ComputingNode } from 'src/app/models/computing-node';
import { StringUtlis } from '../../utils/string-utlis';
import { PanelService } from 'src/app/services/panel/panel.service';
import { WindowSizeService } from 'src/app/services/window-size/window-size.service';
import { QuantityCounterService } from 'src/app/services/configuration/quantity-counter/quantity-counter.service';
import { Resource } from 'src/app/models/server-api/server-api';
import { Subscription } from 'rxjs';
import { INPUT_VALIDATION_POSITIVE_NUMBER, MAX_NUM_OF_APPLICATIONS } from '../../utils/constants';

@Component({
  selector: 'app-configurable-node',
  templateUrl: './configurable-node.component.html',
  styleUrls: ['./configurable-node.component.css'],
  viewProviders: [{ provide: ControlContainer, useExisting: FormGroupDirective }]
})
export class ConfigurableNodeComponent implements OnChanges, OnDestroy {
  @Input() public resources: Resource[];
  @Input() public node: ComputingNode;
  /**
   *  This emits a node to the parent to save it.
   */
  @Output() public readonly setComputingNode = new EventEmitter<ComputingNode>();
  /**
   *  This emits node id when the user want to remove this node from the list.
   */
  @Output() public readonly removeEmitter = new EventEmitter<string>();

  /**
   * This tells that the whole node configuration can be finished.
   */
  public statusIcon: string;
  /**
   * This tells that the node's configured applications can be finished, and saved.
   */
  public appsStatusIcon: string;
  public selectedResource: Resource;
  public nodeCardForm: UntypedFormGroup;
  public nodeIcon: string;
  public errorTooltip: string;
  public showErrorTooltip = true;
  public readonly maxTooltipp: string;

  private appsNumberInputSub: Subscription;
  private formChangeSub: Subscription;
  private dialogCloseSub: Subscription;
  private dialogRef: MatDialogRef<ApplicationsDialogComponent, any>;
  private readonly ASSESTS_URL = '../../../../assets/';
  private readonly maxApplicationsQuantity = MAX_NUM_OF_APPLICATIONS;

  constructor(
    private formBuilder: UntypedFormBuilder,
    public dialog: MatDialog,
    public quantityCounterService: QuantityCounterService,
    public panelService: PanelService,
    public windowService: WindowSizeService
  ) {
    this.maxTooltipp = StringUtlis.MAX_TOOLTIP.replace('{0}', '' + this.maxApplicationsQuantity);
    this.initForm();
    this.appsNumberInputSub = this.nodeCardForm.controls.numOfApplications.valueChanges.subscribe(
      (newValue: number) => {
        if (this.node.applications) {
          const oldValue = this.nodeCardForm.value.numOfApplications;
          const isNewSmallerThanOldAndNotZero = oldValue > newValue && newValue !== 0;
          const isTheOldZeroAndThereAreMoreAppsThanTheNew =
            oldValue === 0 && Object.keys(this.node.applications).length > newValue;
          if (isNewSmallerThanOldAndNotZero || isTheOldZeroAndThereAreMoreAppsThanTheNew) {
            this.node.applications = this.updateAppsObject(this.node.applications, newValue);
          }
        }
      }
    );

    this.formChangeSub = this.nodeCardForm.valueChanges.subscribe(value => {
      this.saveNodeInParent(value.allAppsConfigured);
    });
  }

  public ngOnDestroy(): void {
    this.appsNumberInputSub?.unsubscribe();
    this.formChangeSub?.unsubscribe();
    this.dialogCloseSub?.unsubscribe();
  }

  public ngOnChanges(changes: SimpleChanges): void {
    if (changes.node) {
      this.adjestNodeProperties();
      if (this.nodeCardForm) {
        this.updateForm();
      }
    }
  }

  private updateForm(): void {
    const newValue = {
      numOfApplications: this.node.applications ? Object.keys(this.node.applications).length : 0,
      allAppsConfigured: this.checkAllAppsAreConfigured(),
      quantity: this.node.quantity,
      range: this.node.range
    };
    this.nodeCardForm.patchValue(newValue);
  }

  private setNumOfApplications(value: number): void {
    this.nodeCardForm.get('numOfApplications').setValue(value);
  }

  private getNumOfApplications(): number {
    return this.nodeCardForm.get('numOfApplications').value;
  }

  private adjestNodeProperties() {
    this.setNumOfApplications(this.node.applications ? Object.keys(this.node.applications).length : 0);
    if (!this.node.applications) {
      this.node.applications = {};
    }
    this.selectedResource = this.node.resource ? this.node.resource : undefined;
    this.nodeIcon = this.node.isCloud
      ? this.ASSESTS_URL + StringUtlis.CLOUD_ICON
      : this.ASSESTS_URL + StringUtlis.FOG_ICON;
    this.statusIcon = this.isNodeValid()
      ? this.ASSESTS_URL + StringUtlis.CONFIGURED_ICON
      : this.ASSESTS_URL + StringUtlis.NOT_CONFIGURED_ICON;
    this.appsStatusIcon = this.checkAllAppsAreConfigured() ? StringUtlis.SET_APPS_ICON : StringUtlis.UNSET_APPS_ICON;
  }

  public onChange(): void {
    if (this.areAppsValidOnInputChange()) {
      this.appsStatusIcon = StringUtlis.SET_APPS_ICON;
      this.node.isConfigured = true;
      this.nodeCardForm.controls.allAppsConfigured.setValue(true);
      this.showErrorTooltip = false;
    } else {
      this.appsStatusIcon = StringUtlis.UNSET_APPS_ICON;
      this.node.isConfigured = false;
      this.nodeCardForm.controls.allAppsConfigured.setValue(false);
      this.showErrorTooltip = true;
      if (this.getNumOfApplications() > this.maxApplicationsQuantity) {
        this.errorTooltip = this.maxTooltipp;
      } else if (this.getNumOfApplications() === 0) {
        this.errorTooltip = StringUtlis.INVALID_TOOLTIP;
      } else {
        this.errorTooltip = StringUtlis.UNSET_APPS_TOOLTIP;
      }
    }
  }

  private areAppsValidOnInputChange() {
    const numOfApps = this.getNumOfApplications();
    return (
      (numOfApps === Object.keys(this.node.applications).length ||
        numOfApps < Object.keys(this.node.applications).length) &&
      this.nodeCardForm.valid &&
      !Object.values(this.node.applications).find(app => !app.isConfigured)
    );
  }

  public openDialog(): void {
    this.dialogCloseSub?.unsubscribe();
    this.dialogRef = this.dialog.open(ApplicationsDialogComponent, {
      panelClass: 'applications-dialog-panel',
      disableClose: true,
      maxWidth: '100%',
      width: this.windowService.calculateWidthForApplicationDialog(),
      height: '90%',
      data: { nodeId: this.node.id, numOfApps: this.getNumOfApplications(), applications: this.node.applications }
    });
    this.dialogClose();
  }

  private dialogClose(): void {
    this.dialogCloseSub = this.dialogRef
      ?.afterClosed()
      .subscribe((result: { applications: ApplicationsObject; valid: boolean }) => {
        this.panelService.setSelectedDrawerBacktoMainDrawer();
        if (result) {
          this.node.applications = result.applications;
          if (this.allAppsConfiguredAfterDialogClose(result)) {
            this.nodeCardForm.controls.allAppsConfigured.setValue(false);
            this.appsStatusIcon = StringUtlis.UNSET_APPS_ICON;
            this.showErrorTooltip = true;
          } else {
            this.nodeCardForm.controls.allAppsConfigured.setValue(true);
            this.appsStatusIcon = StringUtlis.SET_APPS_ICON;
            this.showErrorTooltip = false;
          }
        }
      });
  }

  private allAppsConfiguredAfterDialogClose(result: { applications: ApplicationsObject; valid: boolean }): boolean {
    return (
      !result.valid ||
      !this.nodeCardForm.valid ||
      Object.keys(this.node.applications).length !== this.getNumOfApplications()
    );
  }

  private initForm(): void {
    this.nodeCardForm = this.formBuilder.group({
      numOfApplications: new UntypedFormControl(0, INPUT_VALIDATION_POSITIVE_NUMBER),
      allAppsConfigured: false,
      quantity: [1, [Validators.min(1)]],
      range: new UntypedFormControl('', INPUT_VALIDATION_POSITIVE_NUMBER)
    });
  }

  private checkAllAppsAreConfigured(): boolean {
    const numOfApps = this.getNumOfApplications();
    return (
      this.node.applications &&
      numOfApps > 0 &&
      Object.keys(this.node.applications).length === numOfApps &&
      !Object.values(this.node.applications).some(app => !app.isConfigured)
    );
  }

  private isNodeValid(): boolean {
    return this.checkAllAppsAreConfigured() && this.node.isConfigured && this.nodeCardForm.valid;
  }

  private saveNodeInParent(allAppsConfigured: boolean) {
    if (allAppsConfigured && this.selectedResource) {
      this.node.isConfigured = true;
      this.node.resource = this.selectedResource;
      this.node.range = this.nodeCardForm.value.range;
      this.setComputingNode.emit(this.node);
      this.statusIcon = this.ASSESTS_URL + StringUtlis.CONFIGURED_ICON;
    } else {
      this.node.isConfigured = false;
      this.node.resource = this.selectedResource;
      this.node.range = this.nodeCardForm.value.range;
      this.setComputingNode.emit(this.node);
      this.statusIcon = this.ASSESTS_URL + StringUtlis.NOT_CONFIGURED_ICON;
    }
  }

  public onResourceChange() {
    this.saveNodeInParent(this.nodeCardForm.controls.allAppsConfigured.value);
  }

  /**
   * It goes through the object and retains as many apps as the given value.
   * @param nodes - object which contains apps
   * @param currentValue - the entered number for apps
   */
  private updateAppsObject(apps: ApplicationsObject, currentValue: number): ApplicationsObject {
    const restOfTheNodes = {};
    let index = 0;
    for (const [id, app] of Object.entries(apps)) {
      if (index === currentValue) {
        break;
      }
      if (index <= currentValue) {
        restOfTheNodes[id] = app;
        index++;
      }
    }
    return restOfTheNodes;
  }

  public decrease(): void {
    if (this.node.isCloud) {
      if (this.quantityCounterService.decreseClouds(this.node.quantity)) {
        this.node.quantity--;
        this.nodeCardForm.get('quantity').setValue(this.node.quantity);
      }
    } else {
      if (this.quantityCounterService.decreseFogs(this.node.quantity)) {
        this.node.quantity--;
        this.nodeCardForm.get('quantity').setValue(this.node.quantity);
      }
    }
  }

  public increase(): void {
    if (this.node.isCloud) {
      if (this.quantityCounterService.increaseClouds()) {
        this.node.quantity++;
        this.nodeCardForm.get('quantity').setValue(this.node.quantity);
      }
    } else {
      if (this.quantityCounterService.increaseFogs()) {
        this.node.quantity++;
        this.nodeCardForm.get('quantity').setValue(this.node.quantity);
      }
    }
  }

  public openInfoPanelForResources(): void {
    this.panelService.getResourceData();
    this.panelService.toogle();
  }

  /**
   * Sets the config variables of this phase to their default values
   */
  defaultConfiguration() {
    this.nodeCardForm.get('numOfApplications').setValue(1);
    this.nodeCardForm.get('quantity').setValue(1);
    this.nodeCardForm.get('range').setValue(100);
  }
}
