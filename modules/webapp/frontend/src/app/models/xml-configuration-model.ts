export interface XmlBaseConfiguration {
  configuration: {
    email: string;
    tzOffsetInSec?: number;
    appliances: AppliancesContainerXml;
    devices: StationContainerXml;
    instances: InstanceContainerXml;
  };
}

export interface AppliancesContainerXml {
  appliances: {
    appliance: ApplianceXml[];
  };
}

export interface StationContainerXml {
  devices: {
    device: DeviceXml[];
  };
}

export interface InstanceContainerXml {
  instances: {
    instance: InstanceXml[];
  };
}

export interface ApplianceXml {
  $name: string;
  latitude: number;
  longitude: number;
  range: number;
  file: string;
  applications: { application: ApplicationXml[] };
  neighbours: { neighbour?: NeighbourXml[] };
}

export interface ApplicationXml {
  $name: string;
  freq: number;
  tasksize: number;
  instance: string;
  countOfInstructions: number;
  //threshold: number;
  strategy: string;
  canJoin: boolean;
}

export interface NeighbourXml {
  $name: string;
  latency: number;
  parent?: boolean;
}

export interface DeviceXml {
  $name: string;
  startTime: number;
  stopTime: number;
  fileSize: number;
  sensorCount: number;
  strategy: string;
  freq: number;
  latitude: number;
  longitude: number;
  speed: number;
  radius: number;
  latency: number;
  capacity: number;
  //maxInBW: number;
  maxOutBW: number;
  //diskBW: number;
  cores: number;
  perCoreProcessing: number;
  ram: number;
  //onD: number;
  //offD: number;
  minpower: number;
  idlepower: number;
  maxpower: number;
}

export interface InstanceXml {
  $name: string;
  ram: number;
  'cpu-cores': number;
  'core-processing-power': number;
  'startup-process': number;
  'network-load': number;
  'req-disk': number;
  'price-per-tick': number;
}
