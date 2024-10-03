import { Component, OnInit, OnDestroy } from '@angular/core';
import * as jQuery from 'jquery';
import * as joint from 'jointjs';
import { FogNodesObject, CloudNodesObject } from 'src/app/models/computing-nodes-object';
import { StationsObject } from 'src/app/models/station';
import { ConfigurationObject, Neighbour, NODETYPES, Node, ServerSideConfigurationObject } from 'src/app/models/configuration';
import { omit, cloneDeep } from 'lodash';
import { Subscription } from 'rxjs';
import { UntypedFormBuilder, UntypedFormGroup, UntypedFormControl, Validators } from '@angular/forms';
import { MatLegacySnackBar as MatSnackBar } from '@angular/material/legacy-snack-bar';
import { PanelService } from 'src/app/services/panel/panel.service';
import { StepBackDialogService } from 'src/app/services/configuration/step-back/step-back-dialog.service';
import { ConfigurationStateService } from 'src/app/services/configuration/configuration-state/configuration-state.service';
import { StepperService } from 'src/app/services/configuration/stepper/stepper.service';
import { UserConfigurationService } from 'src/app/services/configuration/user-configuration/user-configuration.service';
import { MatLegacySliderChange as MatSliderChange } from '@angular/material/legacy-slider';
import { StringUtlis } from '../utils/string-utlis';
import { MatLegacyDialogRef as MatDialogRef } from '@angular/material/legacy-dialog';
import { StepBackDialogComponent } from '../step-back-dialog/step-back-dialog.component';
import { CIRCLE_RANGE_COLOR_CLOUD, CIRCLE_RANGE_COLOR_FOG, CIRCLE_RANGE_COLOR_STATION } from '../utils/constants';

@Component({
  selector: 'app-connection',
  templateUrl: './connection.component.html',
  styleUrls: ['./connection.component.css']
})
export class ConnectionComponent implements OnInit, OnDestroy {
  public clouds: CloudNodesObject = {};
  public fogs: FogNodesObject = {};
  public configuration: ConfigurationObject = { nodes: {}, stations: {}, instances: {} };
  public multipleStationNodes: StationsObject;

  public numOfClouds = 1;
  public numOfFogs = 0;
  public numOfStations = 2;
  public graphScale = 1;
  public sliderValue = 50;
  public numOfLayers: number;
  public verticalSpaceBetweenLayers: number;

  public simpleConnectionForm: UntypedFormGroup;
  public parentConnectionForm: UntypedFormGroup;

  public paper: joint.dia.Paper;
  public graph: joint.dia.Graph;

  public nodeWidth: number;
  public nodeHeight: number;
  public paperWidth: number;
  public paperHeight: number;
  public sapceForClouds: number;
  public sapceForFogs: number;
  public sapceForStations: number;
  public cloudsStartYpos: number;
  public fogsStartYpos: number;
  public stationsStartYpos: number;

  private generationSubscription: Subscription;
  private dialogCloseSub: Subscription;
  private dialogRef: MatDialogRef<StepBackDialogComponent, any>;
  /**
   * This queue for storing the selected nodes attributes.
   * It has max 2 elements. If a new one is selected the first of the queue will be throw away.
   * So the user can select only 2 elements for adding connection between them.
   */
  private selectedNodeQueue: Node[] = [];

  private readonly ASSESTS_URL = '../../../assets/';
  private readonly cloudImageSrcURL = this.ASSESTS_URL + StringUtlis.CLOUD_ICON;
  private readonly fogImageSrcURL = this.ASSESTS_URL + StringUtlis.FOG_ICON;
  private readonly stationImageSrcURL = this.ASSESTS_URL + StringUtlis.IOT_ICON;

  private readonly MAX_NODE_WIDTH = 75;
  private readonly MAX_NODE_HEIGHT = 50;
  /**
   * The static 24px on left and add +24 for the right
   */
  private readonly PAPER_MARGIN_SUM_VALUE = 48;
  private readonly SIDENAV_HEIGHT = 110;

  constructor(
    private formBuilder: UntypedFormBuilder,
    private stepBackDialogService: StepBackDialogService,
    public configurationService: ConfigurationStateService,
    public stepperService: StepperService,
    private snackBar: MatSnackBar,
    public panelService: PanelService,
    public userConfigurationService: UserConfigurationService
  ) {
    this.initForm();
  }

  public ngOnInit(): void {
    this.generationSubscription = this.configurationService.generateGraph$.subscribe(() => this.createGraph());
  }

  public ngOnDestroy(): void {
    if (this.generationSubscription) {
      this.generationSubscription.unsubscribe();
    }
  }

  public stepBack(): void {
    this.dialogCloseSub?.unsubscribe();
    this.dialogRef = this.stepBackDialogService.openDialog();
    this.dialogCloseSub = this.dialogRef.afterClosed().subscribe((result: { okAction: boolean }) => {
      if (result.okAction) {
        this.stepperService.stepBack();
        this.graphScale = 1;
        this.sliderValue = 50;
      }
    });
  }

  private createGraph(): void {
    this.setNodeObjectsWithQuantities();
    this.setConfigurationRelatedWidthsAndHeights();
    this.calculateSpaceBetweenNodes();
    this.numOfLayers = this.countNodeLayers();
    this.verticalSpaceBetweenLayers = (this.paperHeight / this.numOfLayers - this.nodeHeight) / 2;

    this.calculateNodesStarterYPosition();
    this.initCongifurationStorageObject();
    this.graph = new joint.dia.Graph();
    this.initConnectionPaper();

    this.setListenerForPointerClickOnElement();
    this.setListenerForPointerClickOnBlank();
    this.setListenerForPointerMoveOnElement();
    this.setListenerForGraphRemove();
    this.createNodeCellsIntoTheGraph();
  }

  private initForm(): void {
    this.simpleConnectionForm = this.formBuilder.group({
      latency: new UntypedFormControl('', [Validators.required, Validators.min(1)])
    });
    this.parentConnectionForm = this.formBuilder.group({
      parentLatency: new UntypedFormControl('', [Validators.required, Validators.min(1)])
    });
  }

  public onInputChange(event: MatSliderChange): void {
    if (event.value > this.sliderValue) {
      this.graphScale += 0.01 * (event.value - this.sliderValue);
      this.paper.scale(this.graphScale, this.graphScale);
    } else {
      this.graphScale -= 0.01 * (this.sliderValue - event.value);
      this.paper.scale(this.graphScale, this.graphScale);
    }
    this.sliderValue = event.value;
    this.graph.getCells().forEach(cell => {
      if (!cell.isLink() && !cell.isEmbedded()) {
        this.writeOutOnPaperNodeDetails(cell);
      }
    });
  }

  private createNodeCellsIntoTheGraph(): void {
    const graphElements: (joint.dia.Link | joint.shapes.standard.Image | joint.dia.Cell)[] = [];
    graphElements.push(
      ...this.createNodesInARow(
        this.clouds,
        this.sapceForClouds,
        this.cloudsStartYpos,
        this.cloudImageSrcURL,
        NODETYPES.CLOUD
      )
    );
    graphElements.push(
      ...this.createNodesInARow(
        this.fogs,
        this.sapceForFogs,
        this.fogsStartYpos,
        this.fogImageSrcURL,
        NODETYPES.FOG)
    );
    graphElements.push(
      ...this.createNodesWithRangeInARow(
        this.multipleStationNodes,
        this.sapceForStations,
        this.stationsStartYpos,
        this.stationImageSrcURL,
        NODETYPES.STATION
      )
    );
    this.graph.addCells(this.inintCells(graphElements));
  }

  /**
   * Sets a listener for removing elements on paper.
   * Only links (connections between nodes) are allowed to remove. If the removed link is parent link,
   * the source node's (always it is fog node) metadata (parent) should be updated to none value
   * and also the selectedNodeQueue and the nodes's neighbours should be updated.
   */
  private setListenerForGraphRemove(): void {
    this.graph.on('remove', cell => {
      if (cell.isLink()) {
        const sourceCell = this.graph.getCells().find(c => c.id === cell.attributes.source.id);
        const targetCell = this.graph.getCells().find(c => c.id === cell.attributes.target.id);
        const sourceNodeId = sourceCell.attributes.attrs.nodeId;
        const targetNodeId = targetCell.attributes.attrs.nodeId;

        if (cell.attributes.attrs.isParentLink && cell.attributes.attrs.isParentLink === 'true') {
          this.addAttributeToCell(String(sourceCell.attributes.attrs.nodeId), 'parent', 'none');
          const fogIndex = this.selectedNodeQueue.findIndex(node => node.nodeType === NODETYPES.FOG);
          if (fogIndex !== -1) {
            this.selectedNodeQueue[fogIndex].parent = 'none';
          }
        }

        if (this.configuration.nodes[String(sourceNodeId)] && this.configuration.nodes[String(targetNodeId)]) {
          const sourceNeighbours = this.configuration.nodes[String(sourceNodeId)].neighbours;
          const targetNeighbours = this.configuration.nodes[String(targetNodeId)].neighbours;
          this.configuration.nodes[String(sourceNodeId)].neighbours = omit(sourceNeighbours, targetNodeId);
          this.configuration.nodes[String(targetNodeId)].neighbours = omit(targetNeighbours, sourceNodeId);
        }
      }
    });
  }

  /**
   * Sets a listener for moving elements.
   * If the user move an element on the paper, the informations will be updated.
   * If the user move an embedded element, the parent's information must be updated.
   */
  private setListenerForPointerMoveOnElement(): void {
    this.paper.on('element:pointermove', (elementView: joint.dia.ElementView) => {
      const currentElement = elementView.model;
      if (!currentElement.isEmbedded()) {
        this.writeOutOnPaperNodeDetails(currentElement);
      } else {
        const parent = currentElement.getParentCell();
        this.writeOutOnPaperNodeDetails(parent);
      }
    });
  }

  private writeOutOnPaperNodeDetails(currentElement: joint.dia.Element | joint.dia.Cell): void {
    const xPos = currentElement.attributes.position.x;
    const yPos = currentElement.attributes.position.y;
    const [lon, lat] = this.convertXYCoordToLatLon(xPos, yPos);
    const name = currentElement.attributes.attrs.nodeId;
    currentElement.attr(
      'label/text',
      (name as unknown as string).replace('station', 'devices') + '\n[' + `${lon}` + ',' + `${lat}` + ']'
    );
  }

  private convertXYCoordToLatLon(x: number, y: number) {
    const contentWidth = this.paperWidth / this.graphScale;
    const pixelsPerLon = contentWidth / 180;
    const xPos = x + this.nodeWidth / 2;
    let roundedLon = Math.round(xPos / pixelsPerLon) - 90;
    const contentHeight = this.paperHeight / this.graphScale;
    const pixelsPerLat = contentHeight / 180;
    const yPos = y + this.nodeHeight / 2;
    let roundedLat = Math.round(yPos / pixelsPerLat) - 90;
    if (roundedLon > 90) {
      roundedLon = 90;
    }
    if (roundedLon < -90) {
      roundedLon = -90;
    }
    if (roundedLat > 90) {
      roundedLat = 90;
    }
    if (roundedLat < -90) {
      roundedLat = -90;
    }
    return [roundedLon, roundedLat];
  }

  /**
   * Sets a listener for "empty" clicks.
   * If the user clicks somewhere where is no element, all selected nodes will be deselected.
   */
  private setListenerForPointerClickOnBlank(): void {
    this.paper.on('blank:pointerclick', () => {
      const cells = this.graph.getCells();
      cells.forEach(cell => {
        const elView = this.paper.findViewByModel(cell);
        if (this.selectedNodeQueue.some(node => node.id === cell.id)) {
          elView.unhighlight();
        }
      });
      this.selectedNodeQueue = [];
    });
  }

  /**
   * Sets a listener for clicks on elements.
   * If the user clicks on element, which is not an embedded, it will be selected or deselected (if it has been selected before).
   */
  private setListenerForPointerClickOnElement(): void {
    this.paper.on('element:pointerclick', (elementView: joint.dia.ElementView) => {
      const currentElement = elementView.model;
      if (!currentElement.isEmbedded()) {
        if (String(currentElement.attributes.attrs.selected) === 'true') {
          this.deselectNode(elementView);
        } else {
          this.selectNode(elementView);
        }
      }
    });
  }

  private initConnectionPaper(): void {
    this.paper = new joint.dia.Paper({
      el: jQuery('#paper'),
      width: this.paperWidth,
      height: this.paperHeight,
      model: this.graph,
      gridSize: 1,
      /* It sets that the user can not interact with the embedded elements. */
      interactive: cellView => {
        return !cellView.model.isEmbedded();
      }
    });
  }

  private calculateNodesStarterYPosition(): void {
    if (this.numOfClouds > 0) {
      this.cloudsStartYpos = this.verticalSpaceBetweenLayers;
    }
    if (this.numOfFogs > 0) {
      this.fogsStartYpos =
        this.numOfClouds > 0
          ? this.cloudsStartYpos + this.nodeHeight + this.verticalSpaceBetweenLayers * 2
          : this.cloudsStartYpos;
    }
    if (this.numOfClouds === 0) {
      this.stationsStartYpos = this.fogsStartYpos + this.nodeHeight + this.verticalSpaceBetweenLayers * 2;
    } else if (this.numOfFogs === 0) {
      this.stationsStartYpos = this.cloudsStartYpos + this.nodeHeight + this.verticalSpaceBetweenLayers * 2;
    } else {
      this.stationsStartYpos = this.fogsStartYpos + this.nodeHeight + this.verticalSpaceBetweenLayers * 2;
    }
  }

  private calculateSpaceBetweenNodes(): void {
    if (this.numOfClouds > 0) {
      this.sapceForClouds = (this.paperWidth - this.numOfClouds * this.nodeWidth) / (this.numOfClouds + 1);
    }
    if (this.numOfFogs > 0) {
      this.sapceForFogs = (this.paperWidth - this.numOfFogs * this.nodeWidth) / (this.numOfFogs + 1);
    }
    if (this.numOfStations > 0) {
      this.sapceForStations = (this.paperWidth - this.numOfStations * this.nodeWidth) / (this.numOfStations + 1);
    }
  }

  private setConfigurationRelatedWidthsAndHeights(): void {
    let paperMargin = this.PAPER_MARGIN_SUM_VALUE;
    if (window.innerWidth >= 1000) {
      paperMargin += this.SIDENAV_HEIGHT;
    }
    this.paperWidth = window.innerWidth - paperMargin;
    this.paperHeight = window.innerHeight;
    const largestQuantity = this.getTheMaxQuantityOfNodes();
    const computedNodeWidth = this.paperWidth / (largestQuantity * 2);
    const computedNodeHeight = this.paperHeight / (largestQuantity * 2);
    this.nodeWidth = computedNodeWidth > this.MAX_NODE_WIDTH ? this.MAX_NODE_WIDTH : computedNodeWidth;
    this.nodeHeight = computedNodeHeight > this.MAX_NODE_HEIGHT ? this.MAX_NODE_HEIGHT : computedNodeHeight;
  }

  private setNodeObjectsWithQuantities(): void {
    this.clouds = this.getMultipleNodes(this.configurationService.computingNodes.clouds, NODETYPES.CLOUD) as CloudNodesObject;
    this.fogs = this.getMultipleNodes(this.configurationService.computingNodes.fogs, NODETYPES.FOG) as FogNodesObject;
    this.multipleStationNodes = this.getMultipleNodes(this.configurationService.stationNodes, NODETYPES.STATION) as StationsObject;
    this.numOfClouds = Object.keys(this.clouds).length;
    this.numOfFogs = Object.keys(this.fogs).length;
    this.numOfStations = Object.keys(this.multipleStationNodes).length;
  }

  public createLinkBetweenSelectedNodes(): void {
    if (this.isQueueFull() && this.simpleConnectionForm.controls.latency.valid) {
      if (this.isLinkBetweenNonStationNodes() && this.isFreeToAddSimpleConnection()) {
        const link = new joint.shapes.standard.Link({
          source: { id: this.selectedNodeQueue[0].id },
          target: { id: this.selectedNodeQueue[1].id }
        });
        link.labels([
          {
            attrs: {
              text: {
                text: '' + this.simpleConnectionForm.controls.latency.value
              }
            }
          }
        ]);
        link.attributes.attrs.line.targetMarker = {};
        link.attr('isParentLink', 'false');
        this.createNeighbours(this.simpleConnectionForm.controls.latency.value);
        this.graph.addCell(link);
        this.createLinkTools(link);
      } else {
        this.openSnackBar('You can not create simple connection between these nodes!.', 'OK');
      }
    } else {
      this.openSnackBar('You should add latency to the simple connection!', 'OK');
    }
  }

  public createParentLinkBetweenSelectedCloudAndFog(): void {
    if (this.isQueueFull() && this.parentConnectionForm.controls.parentLatency.valid) {
      if (this.isLinkBetweenNonStationNodes() && this.isFreeToAddParentConnection()) {
        const fog: Node = this.selectedNodeQueue.filter(node => node.nodeType === NODETYPES.FOG)[0];
        const cloud: Node = this.selectedNodeQueue.filter(node => node.nodeType === NODETYPES.CLOUD)[0];
        const link = new joint.shapes.standard.Link({
          source: { id: fog.id },
          target: { id: cloud.id }
        });
        link.labels([
          {
            attrs: {
              text: {
                text: '' + this.parentConnectionForm.controls.parentLatency.value
              }
            }
          }
        ]);
        link.attr('line/stroke', 'orange');
        link.attr('isParentLink', 'true');
        this.addAttributeToCell(fog.nodeId, 'parent', cloud.nodeId);

        const fogIndex = this.selectedNodeQueue.findIndex(node => node.nodeType === NODETYPES.FOG);
        this.selectedNodeQueue[fogIndex].parent = cloud.nodeId;
        this.createNeighbours(this.parentConnectionForm.controls.parentLatency.value);
        this.graph.addCell(link);
        this.createLinkTools(link);
      } else {
        this.openSnackBar(
          'You have to select 1 cloud and 1 fog! Such nodes which have not parent connection yet.',
          'OK'
        );
      }
    } else {
      this.openSnackBar('You have to select 1 cloud and 1 fog and add latency to the parent connection!', 'OK');
    }
  }

  private isFreeToAddParentConnection(): boolean {
    const fogArray = this.selectedNodeQueue.filter(node => node.nodeType === NODETYPES.FOG);
    if (fogArray.length !== 1) {
      return false;
    }
    return fogArray[0].parent && fogArray[0].parent === 'none';
  }

  private isFreeToAddSimpleConnection(): boolean {
    if (
      this.graph
        .getCells()
        .filter(cell => cell.isLink())
        .some(cell =>
          this.doesThisLinkCointainTheseNodes(cell, this.selectedNodeQueue[0].id, this.selectedNodeQueue[1].id)
        )
    ) {
      return false;
    }
    const fogArray = this.selectedNodeQueue.filter(node => node.nodeType === NODETYPES.FOG);
    if (fogArray.length === 1) {
      return false;
    }
    return true;
  }

  private doesThisLinkCointainTheseNodes(cell: joint.dia.Cell, first: string, second: string): boolean {
    return (
      (cell.attributes.source.id === first || cell.attributes.source.id === second) &&
      (cell.attributes.target.id === first || cell.attributes.target.id === second)
    );
  }

  private addAttributeToCell(nodeId: string, attrKey: string, attrValue: string): void {
    this.graph.getCells().forEach((cell: joint.shapes.standard.Image | joint.dia.Link | joint.dia.Cell) => {
      if (cell.attributes.attrs.nodeId && nodeId === String(cell.attributes.attrs.nodeId)) {
        cell.attr(attrKey, attrValue);
      }
    });
  }

  /**
   * Creates removing tool for the given link. It is needed becuase there are more than one type of link.
   * @param link - connection view between 2 nodes
   */
  private createLinkTools(link: joint.shapes.standard.Link) {
    const removeAction = (evt: joint.dia.Event, view: joint.dia.LinkView) => {
      view.model.remove({ ui: true });
    };
    const removeButton = new joint.linkTools.Remove({
      distance: 20,
      action: removeAction
    });

    const toolsView = new joint.dia.ToolsView({
      tools: [removeButton]
    });
    const linkView = link.findView(this.paper);
    linkView.addTools(toolsView);
    linkView.hideTools();

    this.paper.on('link:mouseenter', view => view.showTools());
    this.paper.on('link:mouseleave', view => view.hideTools());
  }

  private openSnackBar(messageText: string, actionText: string, duration: number = 3000): void {
    this.snackBar.open(messageText, actionText, {
      duration,
      panelClass: ['customer-snack-bar-panel']
    });
  }

  private isLinkBetweenNonStationNodes(): boolean {
    return !(
      this.selectedNodeQueue[0].nodeType === NODETYPES.STATION ||
      this.selectedNodeQueue[1].nodeType === NODETYPES.STATION
    );
  }

  /**
   * Returns one image shape with the right properties and metadata.
   * @param nodeId - the id of the node, it will add into the image shape as a metadata
   * @param x - x position for drawing
   * @param y - y position for drawing
   * @param imageSrc - image
   */
  private createImageNode(
    nodeId: string,
    x: number,
    y: number,
    imageSrc: string,
    width: number,
    height: number,
    nodeType: string
  ): joint.shapes.standard.Image {
    const node = new joint.shapes.standard.Image({
      position: { x, y },
      size: { width, height }
    });
    node.attr('image/xlinkHref', imageSrc);
    const [lon, lat] = this.convertXYCoordToLatLon(x, y);
    node.attr('label/text', nodeId.replace('station', 'devices') + '\n[' + `${lon}` + ',' + `${lat}` + ']');
    node.attr('label/fontSize', '11');
    node.attributes.attrs.label.refY = '100%';
    node.attributes.attrs.label.refY2 = '1';
    node.attr('nodeId', nodeId);
    node.attr('nodeTpye', nodeType);
    if (nodeType === NODETYPES.CLOUD || nodeType === NODETYPES.FOG) {
      node.attr('parent', 'none');
    }
    return node;
  }

  private isQueueFull(): boolean {
    return this.selectedNodeQueue.length === 2;
  }

  /**
   * It highlights the selected element on the paper and saves the data in the selectedNodeQueue.
   * If the selectedNodeQueue has 2 elements, the first element will be throw away and will be unhighlighted.
   * @param elementView - element from the paper, which should be selected
   */
  private selectNode(elementView: joint.dia.ElementView): void {
    if (this.isQueueFull()) {
      const shiftedNode = this.selectedNodeQueue.shift();
      const cells = this.graph.getCells();
      cells.forEach(cell => {
        const elView = this.paper.findViewByModel(cell);
        if (cell.id === shiftedNode.id) {
          elView.unhighlight();
        }
      });
    }
    this.selectedNodeQueue.push({
      id: elementView.model.id as string,
      nodeId: elementView.model.attributes.attrs.nodeId as unknown as string,
      nodeType: elementView.model.attributes.attrs.nodeTpye as unknown as string,
      parent: elementView.model.attributes.attrs.parent
        ? (elementView.model.attributes.attrs.parent as unknown as string)
        : undefined
    });
    elementView.highlight();
    elementView.model.attr('selected', 'true');
  }

  private deselectNode(elementView: joint.dia.ElementView): void {
    elementView.unhighlight();
    elementView.model.attr('selected', 'false');
    if (this.selectedNodeQueue.some(node => node.id === elementView.model.id)) {
      this.selectedNodeQueue = this.selectedNodeQueue.filter(node => node.id !== elementView.model.id);
    }
  }

  private inintCells(
    cells: (joint.shapes.standard.Image | joint.dia.Link | joint.dia.Cell)[]
  ): (joint.shapes.standard.Image | joint.dia.Link | joint.dia.Cell)[] {
    cells.forEach(cell => cell.attr('selected', 'false'));
    return cells;
  }

  private countNodeLayers(): number {
    let numOfLayers = 0;
    if (this.numOfClouds && this.numOfClouds > 0) {
      numOfLayers++;
    }
    if (this.numOfFogs && this.numOfFogs > 0) {
      numOfLayers++;
    }
    if (this.numOfStations && this.numOfStations > 0) {
      numOfLayers++;
    }
    return numOfLayers;
  }

  /**
   * Returns the number which is the largest quantity of the nodes (clouds, fogs, stations).
   */
  private getTheMaxQuantityOfNodes(): number {
    const nums = [];
    if (this.numOfClouds) {
      nums.push(this.numOfClouds);
    }
    if (this.numOfFogs) {
      nums.push(this.numOfFogs);
    }
    if (this.numOfStations) {
      nums.push(this.numOfStations);
    }
    return Math.max(...nums);
  }

  /**
   * It goes through the given object and it will add new elements to it,
   * if the current node's quantity > 1 (as much as the value is greater than 1),
   * to each node's quantity to be 1.
   * @param nodes - Object which stores the nodes
   */
  private getMultipleNodes(
    nodes: StationsObject | FogNodesObject | CloudNodesObject,
    nodeType: string
  ): StationsObject | FogNodesObject | CloudNodesObject {
    const resultObject = {};
    for (const [id, node] of Object.entries(nodes)) {
      if (node.quantity > 1 && nodeType != NODETYPES.STATION) {
        for (let i = 1; i <= node.quantity; i++) {
          const subNodeKey = id + '.' + i;
          resultObject[subNodeKey] = { ...cloneDeep(node), id: subNodeKey };
          resultObject[subNodeKey].quantity = 1;
        }
      } else {
        resultObject[id] = node;
      }
    }
    return resultObject;
  }

  private getCircleRangeForNode(
    node: joint.shapes.standard.Image,
    nodeStartX: number,
    nodeStartY: number,
    radius: number,
    color: string
  ): joint.shapes.standard.Circle {
    const nodeXCenter = nodeStartX + this.nodeWidth / 2;
    const nodeYCenter = nodeStartY + this.nodeHeight / 2;

    const contentWidth = this.paperWidth / this.graphScale;
    const pixelsPerLon = contentWidth / 180;
    const contentHeight = this.paperHeight / this.graphScale;
    const pixelsPerLat = contentHeight / 180;

    const circleXCenter = nodeXCenter - radius * pixelsPerLon;
    const circleYCenter = nodeYCenter - radius * pixelsPerLat;
    const circle = new joint.shapes.standard.Circle();
    circle.resize(pixelsPerLon * radius * 2, pixelsPerLat * radius * 2);
    circle.position(circleXCenter, circleYCenter);
    circle.attr('root/title', 'joint.shapes.standard.Circle');
    circle.attr('body/fill', color);
    circle.attr('body/strokeWidth', '0');
    node.embed(circle);
    return circle;
  }

  /**
   * Returns image shapes which will be in one row on the paper. It calculates the space between the nodes.
   * It also adds a circle shape to the node, under the image shape.
   * The radius value comes from the station object's radius property.
   * @param items - items which should be shown
   * @param space - space between the nodes
   * @param startYpos - Y pos for the nodes
   * @param imageUrl - node's image
   * @param nodeType - the type of the nodes
   */
  private createNodesWithRangeInARow(
    items: StationsObject,
    space: number,
    startYpos: number,
    imageUrl: string,
    nodeType: string
  ): (joint.shapes.standard.Image | joint.shapes.standard.Circle)[] {
    const nodes: (joint.shapes.standard.Image | joint.shapes.standard.Circle)[] = [];
    let counter = 0;
    for (const [stationId, station] of Object.entries(items)) {
      const xPos = space + (counter * this.nodeWidth + space * counter);
      const node = this.createImageNode(
        stationId,
        xPos,
        startYpos,
        imageUrl,
        this.nodeWidth,
        this.nodeHeight,
        nodeType
      );
      if (station.radius > 0) {
        const nodeRange = this.getCircleRangeForNode(node, xPos, startYpos, station.radius, CIRCLE_RANGE_COLOR_STATION);
        nodes.push(...[nodeRange, node]);
      } else {
        nodes.push(node);
      }
      counter++;
    }
    return nodes;
  }

  /**
   * Returns image shapes which will be in one row on the paper. It calculates the space between the nodes.
   * @param items - items which should be shown
   * @param space - space between the nodes
   * @param startYpos - Y pos for the nodes
   * @param imageUrl - node's image
   * @param nodeType - the type of the nodes
   */
  private createNodesInARow(
    items: CloudNodesObject | FogNodesObject,
    space: number,
    startYpos: number,
    imageUrl: string,
    nodeType: string
  ): joint.shapes.standard.Image[] {
    const nodes: joint.shapes.standard.Image[] = [];
    const itemsLength = Object.keys(items).length;
    const color = nodeType === NODETYPES.CLOUD ? CIRCLE_RANGE_COLOR_CLOUD : CIRCLE_RANGE_COLOR_FOG;
    let counter = 0;
    if (itemsLength > 0) {
      for (const [nodeId, node] of Object.entries(items)) {
        const xPos = space + (counter * this.nodeWidth + space * counter);
        const curentNode = this.createImageNode(nodeId, xPos, startYpos, imageUrl, this.nodeWidth, this.nodeHeight, nodeType);
        const nodeRange = this.getCircleRangeForNode(curentNode, xPos, startYpos, node.range, color);
        nodes.push(...[nodeRange, curentNode]);
        counter++;
      }
    }
    return nodes;
  }

  /**
   * Creates the configuration object, which contains all the clouds, fogs, and stations.
   */
  private initCongifurationStorageObject(): void {
    this.configuration.nodes = {};
    for (const [id, node] of Object.entries(this.clouds)) {
      this.configuration.nodes[id] = { ...cloneDeep(node), neighbours: {} };
    }
    for (const [id, node] of Object.entries(this.fogs)) {
      this.configuration.nodes[id] = { ...cloneDeep(node), neighbours: {} };
    }
    this.configuration.stations = this.multipleStationNodes;
  }

  /**
   * It sets each other as a neighbour for the selected nodes
   * @param latency - given value as distance between the nodes
   */
  private createNeighbours(latency: number): void {
    const firstSelectedNodeId = this.selectedNodeQueue[0].nodeId;
    const secondSelectedNodeId = this.selectedNodeQueue[1].nodeId;
    if (this.configuration.nodes[firstSelectedNodeId] && this.configuration.nodes[secondSelectedNodeId]) {
      const firstNodeNeighbour = {
        name: secondSelectedNodeId,
        latency,
        parent: this.selectedNodeQueue[0].parent === secondSelectedNodeId ? true : false
      } as Neighbour;

      const secondNodeNeighbour = {
        name: firstSelectedNodeId,
        latency,
        parent: this.selectedNodeQueue[1].parent === firstSelectedNodeId ? true : false
      } as Neighbour;
      this.configuration.nodes[firstSelectedNodeId].neighbours[secondSelectedNodeId] = firstNodeNeighbour;
      this.configuration.nodes[secondSelectedNodeId].neighbours[firstSelectedNodeId] = secondNodeNeighbour;
    }
  }

  /**
   * Saves the nodes latest positions, sends a configuration request, and moves the stepper.
   */
  public save(): void {
    this.graph.getCells().forEach(cell => {
      if (!cell.isLink() && cell.attributes.attrs.nodeId) {
        const nodeId = cell.attributes.attrs.nodeId;
        const [lon, lat] = this.convertXYCoordToLatLon(cell.attributes.position.x, cell.attributes.position.y);
        if (this.configuration.nodes[String(nodeId)]) {
          this.configuration.nodes[String(nodeId)].x = lon;
          this.configuration.nodes[String(nodeId)].y = lat;
        } else if (this.configuration.stations[String(nodeId)]) {
          this.configuration.stations[String(nodeId)].yCoord = lon;
          this.configuration.stations[String(nodeId)].xCoord = lat;
        }
      }
    });
    this.configuration.instances = this.configurationService.instanceNodes;
    
    const serverSideconfigurations: ServerSideConfigurationObject[] = this.generateAllConfigurations();
    console.log("ServerSideConfig" + serverSideconfigurations);
    this.userConfigurationService.sendConfiguration(serverSideconfigurations);

    this.stepperService.stepForward();
    this.graphScale = 1;
    this.sliderValue = 50;
  }

  public openInfoPanelForConnection(): void {
    this.panelService.getConnectionData();
    this.panelService.toogle();
  }

  /**
   *   Generates all configurations (from the strategies) that will be sent to the server
   *   @returns An array containing ServerSideConfigurationObjects which equal 1:1 to a simulation
   */
  public generateAllConfigurations(): ServerSideConfigurationObject[] {
    const serverSideconfigurations: ServerSideConfigurationObject[] = [];
    const appStrategies = [];
    const stationStrategies = [];

    // Retrieve all strategies from applications
    for (const node of Object.keys(this.configuration.nodes)) {
      for (const app of Object.keys(this.configuration.nodes[node].applications)) {
        appStrategies.push(this.configuration.nodes[node].applications[app].strategy);
      }
    }

    // Retrieve all strategies from stations
    for (const station of Object.keys(this.configuration.stations)) {
      stationStrategies.push(this.configuration.stations[station].strategy);
    }

    // Combine application strategies
    const appStrategyCombos  = appStrategies.reduce((a, b) => a.flatMap(x => b.map(y => x + '-' + y))).map(z => z.split('-'));

    // Combine station strategies
    const stationStrategyCombos = stationStrategies.reduce((a, b) => a.flatMap(x => b.map(y => x + '-' + y))).map(z => z.split('-'));

    // Combine the combined app and station strategies
    const combinedStrategies =  [appStrategyCombos, stationStrategyCombos].reduce((a, b) => a.flatMap(x => b.map(y => x.concat(y))));

    // Generate server side configurations
    for (const combinedStrategy of combinedStrategies) {
      const newServerSideConfig: ServerSideConfigurationObject = JSON.parse(JSON.stringify(this.configuration));

      // Set the strategy for every application
      for (const node of Object.keys(newServerSideConfig.nodes)) {
        for (const app of Object.keys(newServerSideConfig.nodes[node].applications)) {
          newServerSideConfig.nodes[node].applications[app].strategy = combinedStrategy[0];
          combinedStrategy.shift();
        }
      }

      // Set the strategy for every station
      for (const station of Object.keys(newServerSideConfig.stations)) {
        newServerSideConfig.stations[station].strategy = combinedStrategy[0];
        combinedStrategy.shift();
      }

      serverSideconfigurations.push(newServerSideConfig);
    }
    return serverSideconfigurations;
  }
}
