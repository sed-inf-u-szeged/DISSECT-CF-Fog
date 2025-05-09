import { Component, Input, OnChanges, SimpleChanges, OnDestroy, SimpleChange } from '@angular/core';
import { ComputingNode } from 'src/app/models/computing-node';
import { CloudNodesObject, FogNodesObject } from 'src/app/models/computing-nodes-object';
import { Subscription } from 'rxjs';
import { QuantityCounterService } from 'src/app/services/configuration/quantity-counter/quantity-counter.service';
import { RestartConfigurationService } from 'src/app/services/configuration/restart-configuration/restart-configuration.service';
import { ConfigurationStateService } from 'src/app/services/configuration/configuration-state/configuration-state.service';
import { StepperService } from 'src/app/services/configuration/stepper/stepper.service';
import { ResourceSelectionService } from 'src/app/services/configuration/resource-selection/resource-selection.service';

@Component({
  selector: 'app-list-configurable-nodes',
  templateUrl: './list-configurable-nodes.component.html',
  styleUrls: ['./list-configurable-nodes.component.css']
})
export class ListConfigurableNodesComponent implements OnChanges, OnDestroy {
  @Input() public readonly numOfClouds: number;
  @Input() public readonly numOfFogs: number;

  /**
   * This tells that the form and a configuration ready to save in the specified number,
   * and all the nesessary data are filled and it can be finished.
   */
  public readyToSave = false;
  public cloudIndex = 1;
  public fogIndex = 1;

  private restartSubscription: Subscription;

  constructor(
    public quantityCounterService: QuantityCounterService,
    private restartConfService: RestartConfigurationService,
    public configurationService: ConfigurationStateService,
    public stepperService: StepperService,
    public resourceSelectionService: ResourceSelectionService
  ) {
    this.restartSubscription = this.restartConfService.restartConfiguration$.subscribe(() => {
      this.cloudIndex = 1;
      this.fogIndex = 1;
      this.readyToSave = false;
    });
  }

  public ngOnDestroy(): void {
    this.restartSubscription?.unsubscribe();
  }

  public ngOnChanges(changes: SimpleChanges): void {
    if (changes.numOfClouds || changes.numOfFogs) {
      this.initNodesIfneeded(changes);
      this.updateNodesByInputFormChanges(changes);
      this.checkIsReadyToSave();
      this.updateRequiredQuantites();
    }
  }

  private initNodesIfneeded(changes: SimpleChanges) {
    if (this.isQuantityOfCloudWasZero(changes)) {
      this.createFirstCloud();
    }
    if (this.isQuantityOfFogWasZero(changes)) {
      this.createFirstFog();
    }
  }

  private isQuantityOfFogWasZero(changes: SimpleChanges): boolean {
    return (
      changes.numOfFogs &&
      Object.keys(this.configurationService.computingNodes.fogs).length === 0 &&
      changes.numOfFogs.currentValue > 0
    );
  }

  private isQuantityOfCloudWasZero(changes: SimpleChanges): boolean {
    return (
      changes.numOfClouds &&
      Object.keys(this.configurationService.computingNodes.clouds).length === 0 &&
      changes.numOfClouds.currentValue > 0
    );
  }

  private updateRequiredQuantites() {
    this.quantityCounterService.setNodeQuantities(
      this.numOfClouds,
      this.numOfFogs,
      this.getNumberOfConfigurabledNodes(this.configurationService.computingNodes.clouds),
      this.getNumberOfConfigurabledNodes(this.configurationService.computingNodes.fogs)
    );
  }

  private createFirstFog() {
    const firstFogId = 'fog' + this.fogIndex;
    const firstFog = new ComputingNode();
    firstFog.id = firstFogId;
    firstFog.isCloud = false;
    firstFog.quantity = 1;
    this.configurationService.computingNodes.fogs[firstFog.id] = firstFog;
  }

  private createFirstCloud() {
    const firstCloudId = 'cloud' + this.cloudIndex;
    const firstCloud = new ComputingNode();
    firstCloud.id = firstCloudId;
    firstCloud.isCloud = true;
    firstCloud.quantity = 1;
    this.configurationService.computingNodes.clouds[firstCloud.id] = firstCloud;
  }

  public addCloud() {
    if (this.quantityCounterService.increaseClouds()) {
      this.cloudIndex += 1;
      const cloudId = 'cloud' + this.cloudIndex;
      const cloud = new ComputingNode();
      cloud.id = cloudId;
      cloud.isCloud = true;
      cloud.quantity = 1;
      this.configurationService.computingNodes.clouds[cloud.id] = cloud;
    }
  }

  public addFog() {
    if (this.quantityCounterService.increaseFogs()) {
      this.fogIndex += 1;
      const fogId = 'fog' + this.fogIndex;
      const fog = new ComputingNode();
      fog.id = fogId;
      fog.isCloud = false;
      fog.quantity = 1;
      this.configurationService.computingNodes.fogs[fog.id] = fog;
    }
  }

  /**
   * It sets node list (object) based on the specified data.
   * If the previous value was less than the current value, it is recorded as one that has not yet been selected.
   * If the previous one was larger, it takes items from the end of the list.
   * If an initial value is specified, it initializes a new node.
   * @param changes - Sipmle changes fom NgOnChanges
   */
  private updateNodesByInputFormChanges(changes: SimpleChanges): void {
    if (changes.numOfClouds && !changes.numOfClouds.firstChange) {
      if (this.checkPreviousWasBiggerThanCurrent(changes.numOfClouds)) {
        this.configurationService.computingNodes.clouds = this.updateNodesObject(
          this.configurationService.computingNodes.clouds,
          changes.numOfClouds.currentValue
        );
      } else if (this.checkIsFirstValueWhichBiggerThanZero(changes.numOfClouds)) {
        this.createFirstCloud();
      } else if (this.checkIsFirstValueAndZero(changes.numOfClouds)) {
        this.configurationService.computingNodes.clouds = {};
      }
    }
    if (changes.numOfFogs && !changes.numOfFogs.firstChange) {
      if (this.checkPreviousWasBiggerThanCurrent(changes.numOfFogs)) {
        this.configurationService.computingNodes.fogs = this.updateNodesObject(
          this.configurationService.computingNodes.fogs,
          changes.numOfFogs.currentValue
        );
      } else if (this.checkIsFirstValueWhichBiggerThanZero(changes.numOfFogs)) {
        this.createFirstFog();
      } else if (this.checkIsFirstValueAndZero(changes.numOfFogs)) {
        this.configurationService.computingNodes.fogs = {};
      }
    }
  }

  private checkIsFirstValueAndZero(change: SimpleChange): boolean {
    return change.currentValue === 0 || change.currentValue === undefined;
  }

  private checkIsFirstValueWhichBiggerThanZero(change: SimpleChange): boolean {
    return change.previousValue === 0 && change.currentValue > change.previousValue;
  }

  private checkPreviousWasBiggerThanCurrent(change: SimpleChange): boolean {
    return (
      change.previousValue &&
      change.currentValue !== undefined &&
      change.previousValue > change.currentValue &&
      change.currentValue > 0
    );
  }

  /**
   * This stores the given node into the right service variable. It also checks, that the configuration' state can be finished.
   * @param computingNode - given node from childrens
   */
  public saveComputingNode(computingNode: ComputingNode): void {
    if (computingNode.isCloud) {
      this.configurationService.computingNodes.clouds[computingNode.id] = computingNode;
    } else {
      this.configurationService.computingNodes.fogs[computingNode.id] = computingNode;
    }

    this.checkIsReadyToSave();
  }

  /**
   * Tells that all the configuration can be finished, and sets the readyToSave variable.
   */
  private checkIsReadyToSave(): void {
    const areCloudsConfigured = !Object.values(this.configurationService.computingNodes.clouds).some(
      node => node.isConfigured === false || node.isConfigured === undefined
    );

    const areFogsConfigured =
      Object.values(this.configurationService.computingNodes.fogs).length === 0
        ? true
        : !Object.values(this.configurationService.computingNodes.fogs).some(
            node => node.isConfigured === false || node.isConfigured === undefined
          );

    const isCloudsQuantityOk =
      this.getNumberOfConfigurabledNodes(this.configurationService.computingNodes.clouds) === this.numOfClouds;

    const isFogsQuantityOk =
      this.getNumberOfConfigurabledNodes(this.configurationService.computingNodes.fogs) === 0 ||
      this.getNumberOfConfigurabledNodes(this.configurationService.computingNodes.fogs) === this.numOfFogs;

    this.readyToSave =
      areCloudsConfigured &&
      areFogsConfigured &&
      isCloudsQuantityOk &&
      isFogsQuantityOk &&
      this.quantityCounterService.getUndividedClouds() === 0;
  }

  /**
   * It goes through the object and retains as many nodes as the given value also subtracts from the node quantity if necessary.
   * @param nodes - object which contains fogs or clouds
   * @param currentValue - the entered number for clouds or fogs
   */
  private updateNodesObject(
    nodes: CloudNodesObject | FogNodesObject,
    currentValue: number
  ): CloudNodesObject | FogNodesObject {
    const restOfTheNodes = {};
    let index = 0;
    for (const [id, node] of Object.entries(nodes)) {
      if (index === currentValue) {
        break;
      }
      if (index + node.quantity <= currentValue) {
        restOfTheNodes[id] = node;
        index += node.quantity;
      } else {
        const quantity = currentValue - index;
        node.quantity = quantity;
        restOfTheNodes[id] = node;
        break;
      }
    }
    return restOfTheNodes;
  }

  public checkReadyAndNext(): void {
    if (this.readyToSave) {
      this.configurationService.changeStations();
      this.stepperService.stepForward();
    }
  }

  private getNumberOfConfigurabledNodes(nodes: CloudNodesObject | FogNodesObject): number {
    let sum = 0;
    for (const [id, node] of Object.entries(nodes)) {
      sum += node.quantity;
    }
    return sum;
  }

  /**
   * Remove node by id from the service's variable, which stores the nodes.
   * It also updates the configuration related node's quantities and check the configuration can be finished.
   * @param id - the id of the removed node
   */
  public removeTypeOfNodes(id: string): void {
    if (id.startsWith('cloud')) {
      delete this.configurationService.computingNodes.clouds[id];
    } else {
      delete this.configurationService.computingNodes.fogs[id];
    }
    this.updateRequiredQuantites();
    this.checkIsReadyToSave();
  }
}
