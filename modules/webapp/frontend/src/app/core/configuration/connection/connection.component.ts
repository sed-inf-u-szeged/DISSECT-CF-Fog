import { Component, OnInit, OnDestroy, OnChanges, SimpleChanges, ChangeDetectorRef } from '@angular/core';
import * as jQuery from 'jquery';
import * as joint from 'jointjs';
import { FogNodesObject, CloudNodesObject } from 'src/app/models/computing-nodes-object';
import { StationsObject } from 'src/app/models/station';
import { ConfigurationObject, Neighbour, NODETYPES, Node, ServerSideConfigurationObject } from 'src/app/models/configuration';
import { omit, cloneDeep, first } from 'lodash'
import { Subscription } from 'rxjs';
import { UntypedFormBuilder, UntypedFormGroup, UntypedFormControl, Validators } from '@angular/forms';
import { MatSnackBar } from '@angular/material/snack-bar';
import { PanelService } from 'src/app/services/panel/panel.service';
import { StepBackDialogService } from 'src/app/services/configuration/step-back/step-back-dialog.service';
import { ConfigurationStateService } from 'src/app/services/configuration/configuration-state/configuration-state.service';
import { StepperService } from 'src/app/services/configuration/stepper/stepper.service';
import { UserConfigurationService } from 'src/app/services/configuration/user-configuration/user-configuration.service';
import { MatDialogRef } from '@angular/material/dialog';
import { StepBackDialogComponent } from '../step-back-dialog/step-back-dialog.component';
import { latLng, tileLayer } from 'leaflet';
import * as L from 'leaflet';

@Component({
  selector: 'app-connection',
  templateUrl: './connection.component.html',
  styleUrls: ['./connection.component.css']
})
export class ConnectionComponent implements OnInit, OnDestroy, OnChanges {

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
   * So the user can select only 2 elements for adding connection between them.
   */
  private selectedNodeQueue: Node[] = [{ id: '', nodeType: '', latLANG: L.LatLng[''], parent: '' }, { id: '', nodeType: '', latLANG: L.LatLng[''], parent: '' }];

  /**
   * They are used for the map, and to store data related to configuration
   */
  map: any;
  markers: any[] = [];
  layers: L.Layer[] = [];
  markerList: L.LatLng[] = [];
  index = 0;
  clickedCircules: any[] = [];
  connections: any;
  nodesIDAndRangeAndNeighbours: any[] = [];
  existingConnections: string[] = [];
  existingConnectionsOnMap: any[] = [];
  /**
   * These are used for data store between functions
   */
  currentId: string;
  currentRange: number
  currlats: L.LatLng[] = [];
  currentChoosenMarker: any[] = [];
  /**
   * The layergroup that contains every placed object on the map
   */
  layerGroup = new L.FeatureGroup();

  //Map definiton
  options = {
    layers: [
      tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', { attribution: '&copy; OpenStreetMap contributors' }),
    ],
    zoom: 8,
    center: latLng([46.253, 20.14824])
  };


  nodesFromConfig: any
  stations: any
  constructor(
    private formBuilder: UntypedFormBuilder,
    private stepBackDialogService: StepBackDialogService,
    public configurationService: ConfigurationStateService,
    public stepperService: StepperService,
    public panelService: PanelService,
    public userConfigurationService: UserConfigurationService,
    public changeDetector: ChangeDetectorRef
  ) {
    this.initForm();
  }
  ngOnChanges(): void {
    this.loadDatasToTable();
  }

  public ngOnInit(): void {
    this.generationSubscription = this.configurationService.generateGraph$.subscribe(() => this.loader());


  }
  public ngOnDestroy(): void {
    if (this.generationSubscription) {
      this.generationSubscription.unsubscribe();
    }
  }

  /**
   * This function puts the circules on the map and deciedes its color
   * @param e  the event that cames from the click event
   * @param radius the radius which comes from the configuration
   * @param label the id of the element
   */
  addNewLocationToMap(e: any, radius: number, label: string) {
    let isok = true
    if (this.currentRange != null) {
      this.markers.forEach(element => {
        if (element.id == label) {
          isok = false;
        }
      });
      if (isok) {
        let item = L.circle(e.latlng, { radius: radius })
        if (label.includes('cloud')) {
          item.setStyle({ color: 'blue' })
        } else if (label.includes('fog')) {
          item.setStyle({ color: 'darkgrey' })
        } else if (label.includes('station')) {
          item.setStyle({ color: 'darkgreen' })
        }
        let tip = L.tooltip({ permanent: true, direction: 'center' }).setContent(label).setLatLng(item.getLatLng()).addTo(this.map);
        tip.addTo(this.layerGroup);
        item.addEventListener('click', (e) => { this.clickHandle(e) })
        this.markers.push({ latl: item.getLatLng(), id: this.currentId });
        item.addTo(this.map)
        item.addTo(this.layerGroup)
        this.currentId = '';
        this.currentRange = null;
      } else {
        alert('You already added to the map this node: ' + label);
      }
    }
  }

  /**
   * This function was added to the placed circule and saves the circules data 
   * @param e the event that cames from the click event
   */
  clickHandle(e: any) {
    let tempid;
    let clickedObject = e.target.getLatLng();
    this.markers.forEach(element => {
      if (element.latl == clickedObject) { tempid = element.id }
    });

    this.clickedCircules.push({ id: tempid, lat: clickedObject });
    this.currlats.push(clickedObject);
    if (this.clickedCircules.length > 1) {
      this.saveInNodeQueue(this.clickedCircules, this.index)
      this.createConnection(this.currlats, this.clickedCircules);
      this.clickedCircules.pop()
      this.clickedCircules.pop()
      this.currlats.pop()
      this.currlats.pop()
      this.index = 0;
    } else {
      this.saveInNodeQueue(this.clickedCircules, this.index)
      this.index++;
    }
  }

  /**
   * This is a brute force solution, later it will need a fix. 
   * Saves the circules data to selectedNodeQueue variable beacause of the legacy codes
   * @param array stores the id and the latlng of the circules
   * @param index 
   */
  saveInNodeQueue(array: any, index: number) {
    if (index == 0) {
      if (array[0].id.includes('fog')) {
        this.selectedNodeQueue[0].id = array[0].id
        this.selectedNodeQueue[0].nodeType = NODETYPES.FOG;
        this.selectedNodeQueue[0].latLANG = array[0].lat
      } else if (array[0].id.includes('cloud')) {
        this.selectedNodeQueue[0].id = array[0].id
        this.selectedNodeQueue[0].nodeType = NODETYPES.CLOUD;
        this.selectedNodeQueue[0].latLANG = array[0].lat

      } else {
        this.selectedNodeQueue[0].id = array[0].id
        this.selectedNodeQueue[0].nodeType = NODETYPES.STATION;
        this.selectedNodeQueue[0].latLANG = array[0].lat
      }
    } else if (index == 1) {

      if (array[1].id.includes('fog')) {
        this.selectedNodeQueue[1].id = array[1].id
        this.selectedNodeQueue[1].nodeType = NODETYPES.FOG;
        this.selectedNodeQueue[1].latLANG = array[1].lat
      } else if (array[1].id.includes('cloud')) {
        this.selectedNodeQueue[1].id = array[1].id
        this.selectedNodeQueue[1].nodeType = NODETYPES.CLOUD;
        this.selectedNodeQueue[1].latLANG = array[1].lat
      } else {
        this.selectedNodeQueue[1].id = array[1].id
        this.selectedNodeQueue[1].nodeType = NODETYPES.STATION;
        this.selectedNodeQueue[1].latLANG = array[1].lat
      }
    }
  }

  /**
   *  This function makes the connection between the 2 clicked circules.
   * @param circules 2 clicked circules latlngs (only the latlngs)
   * @param latAndId stores the latlng and ids of the clicked elements
   */
  createConnection(circules: any, latAndId: any) {
    let latency;
    let tempIds = [];

    latAndId.forEach(element => {
      tempIds.push(element.id) //This holds 2 ids
    });
    if (tempIds[0].includes('fog') || tempIds[1].includes('fog') && !this.checkForFog()) {
      latency = this.parentConnectionForm.controls.parentLatency.value;
    } else {
      latency = this.simpleConnectionForm.controls.latency.value
    }
    if (Number(latency) <= 0) {
      alert('Latency must be > 0');
      this.selectedNodeQueue = [{ id: '', nodeType: '', latLANG: L.LatLng[''], parent: '' }, { id: '', nodeType: '', latLANG: L.LatLng[''], parent: '' }];
    } else {
      if (!this.isConnectionBetweenNonStationNodes() || tempIds[0] == tempIds[1]) {
        alert('You can not connect!');
      } else {
        if (!this.checkExistingConnections(tempIds[0], tempIds[1]) ) {
          let line = L.polyline(circules, { color: 'blue' })
          line.bindTooltip(String(latency), { permanent: true });
          line.addTo(this.map);
          line.addTo(this.layerGroup);
          this.createNeighbours(Number(latency));
          this.existingConnections.push(tempIds[0] + ' + ' + tempIds[1]);
          this.existingConnectionsOnMap.push(line)
          this.selectedNodeQueue = [{ id: '', nodeType: '', latLANG: L.LatLng[''], parent: '' }, { id: '', nodeType: '', latLANG: L.LatLng[''], parent: '' }];
          this.changeDetector.detectChanges();
        } else {
          alert('The connection already exists');
          this.selectedNodeQueue = [{ id: '', nodeType: '', latLANG: L.LatLng[''], parent: '' }, { id: '', nodeType: '', latLANG: L.LatLng[''], parent: '' }];
        }
      }
    }
  }

  /**
   * Updates and creates the map
   * @param map the map
   */
  onMapReady(map: any) {
    this.map = map;
    this.layerGroup.addTo(this.map)
    map.addEventListener('click', (e) => { this.addNewLocationToMap(e, this.currentRange, this.currentId); })
  }

  /**
   * Check for existing connections. This functions needs a rewrite
   * @param firstId fist clicked circule
   * @param secondId second clicked circule
   * @returns boolean value
   */
  checkExistingConnections(firstId: string, secondId?: string): boolean {
    for (let con in this.existingConnections) {
      if (con.match(firstId + ' + ' + secondId) || con.match(secondId + ' + ' + firstId)) {
        return true;
      } else {
        return false;
      }
    }
  }
  /**
   * Check if there are a fog in the existingconnection
   * @returns boolean value
   */
  checkForFog() {
    for (let con in this.existingConnections) {
      if (con.includes('fog')) {
        return true;
      } else {
        return false;
      }
    }
  }

  /**
   * Resets the whole map and clears the layerGroup variable.
   */
  hardResetTheMap() {
    this.layerGroup.clearLayers();
    let i = 0;
    while (this.existingConnections.length != 0) {
      this.removeConnection(this.existingConnections[i]);
      this.existingConnections.pop();
      i++;
    }
    while (this.existingConnectionsOnMap.length != 0) {
      this.existingConnectionsOnMap.pop();
    }
  }

  /**
   * Gets the data for the table in the HTML code
   */
  public loadDatasToTable() {
    this.nodesIDAndRangeAndNeighbours = [];
    for (let [key, value] of Object.entries(this.configuration.nodes)) {
      let tempRange = 0;
      for (let [key2, value2] of Object.entries(value)) {
        if (key2 == 'range') {

          tempRange = value2;
        }
        if (key2 == 'neighbours') {

          this.nodesIDAndRangeAndNeighbours.push({ id: key, range: tempRange, neighbours: value2 });
        }
      }
    }
    //get the data of stations, dont have negibours
    for (let [key, value] of Object.entries(this.configuration.stations)) {
      for (let [key2, value2] of Object.entries(value)) {
        if (key2 == 'range') {
          this.nodesIDAndRangeAndNeighbours.push({ id: key, range: value2 });
        }
      }

    }

  }
  /**
   * This functions set the id when tha button in the HTML table was pressed for to be able to place the choosen circule
   * @param id 
   */
  setData(id: string) {
    for (let i = 0; i < this.nodesIDAndRangeAndNeighbours.length; i++) {
      if (this.nodesIDAndRangeAndNeighbours[i].id == id) {

        this.currentId = id;
        this.currentRange = Number(this.nodesIDAndRangeAndNeighbours[i].range)
      }
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

  private loader(): void {
    this.setNodeObjectsWithQuantities();
    this.initCongifurationStorageObject();
    this.graph = new joint.dia.Graph();
    this.loadDatasToTable();

  }

  private initForm(): void {
    this.simpleConnectionForm = this.formBuilder.group({
      latency: new UntypedFormControl('', [Validators.required, Validators.min(1)])
    });
    this.parentConnectionForm = this.formBuilder.group({
      parentLatency: new UntypedFormControl('', [Validators.required, Validators.min(1)])
    });
  }


  public setNodeObjectsWithQuantities(): void {
    this.clouds = this.getMultipleNodes(this.configurationService.computingNodes.clouds, NODETYPES.CLOUD) as CloudNodesObject;
    this.fogs = this.getMultipleNodes(this.configurationService.computingNodes.fogs, NODETYPES.FOG) as FogNodesObject;
    this.multipleStationNodes = this.getMultipleNodes(this.configurationService.stationNodes, NODETYPES.STATION) as StationsObject;
    this.numOfClouds = Object.keys(this.clouds).length;
    this.numOfFogs = Object.keys(this.fogs).length;
    this.numOfStations = Object.keys(this.multipleStationNodes).length;
  }

  private isConnectionBetweenNonStationNodes(): boolean {
    return !(
      this.selectedNodeQueue[0].nodeType === NODETYPES.STATION ||
      this.selectedNodeQueue[1].nodeType === NODETYPES.STATION
    );
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
    const firstSelectedNodeId = this.selectedNodeQueue[0].id;
    const secondSelectedNodeId = this.selectedNodeQueue[1].id;

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
  public save(arrayOfPlacedObjects: any): void {
    for (let [key, value] of Object.entries(arrayOfPlacedObjects)) {
      if (key == 'id' && this.checkExistingConnections(value as string)) {
        const nodeId = value;
        for (let [key, value] of Object.entries(arrayOfPlacedObjects)) {
          if (key == 'lat') {
            const { lat, lng } = value as L.LatLng
            if (this.configuration.nodes[String(nodeId)]) {
              this.configuration.nodes[String(nodeId)].x = lng;
              this.configuration.nodes[String(nodeId)].y = lat;
            } else if (this.configuration.stations[String(nodeId)]) {
              this.configuration.stations[String(nodeId)].yCoord = lng;
              this.configuration.stations[String(nodeId)].xCoord = lat;
            }
          }
        }
      }
    }

    this.configuration.instances = this.configurationService.instanceNodes;
    
    const serverSideconfigurations: ServerSideConfigurationObject[] = this.generateAllConfigurations();
    console.log("ServerSideConfig" + serverSideconfigurations);
    this.userConfigurationService.sendConfiguration(serverSideconfigurations);

    this.stepperService.stepForward();
  }

  removeConnection(param: string) {
    let ids = [];
    ids = param.split(' + ')
    if (this.configuration.nodes[String(ids[0])] && this.configuration.nodes[String(ids[1])]) {
      const sourceNeighbours = this.configuration.nodes[String(ids[0])].neighbours;
      const targetNeighbours = this.configuration.nodes[String(ids[1])].neighbours;
      this.configuration.nodes[String(ids[0])].neighbours = omit(sourceNeighbours, ids[1]);
      this.configuration.nodes[String(ids[1])].neighbours = omit(targetNeighbours, ids[0]);
    }
    this.existingConnections.forEach((p, i) => {
      if (p == param) {
        this.existingConnections.splice(i, 1);
        this.existingConnectionsOnMap[i].removeFrom(this.map)
        this.existingConnectionsOnMap.splice(i, 1);
      }
    })
    this.changeDetector.detectChanges();
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
    const appStrategyCombos = appStrategies.reduce((a, b) => a.flatMap(x => b.map(y => x + '-' + y))).map(z => z.split('-'));

    // Combine station strategies
    const stationStrategyCombos = stationStrategies.reduce((a, b) => a.flatMap(x => b.map(y => x + '-' + y))).map(z => z.split('-'));

    // Combine the combined app and station strategies
    const combinedStrategies = [appStrategyCombos, stationStrategyCombos].reduce((a, b) => a.flatMap(x => b.map(y => x.concat(y))));

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
