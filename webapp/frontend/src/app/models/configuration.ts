import {ComputingNode, ServerSideComputingNode} from './computing-node';
import {ServerSideStationsObject, StationsObject} from './station';
import {ConfiguredComputingNodesObject, ConfiguredServerSideComputingNodesObject} from './computing-nodes-object';
import { InstanceObject } from './instance';

export interface ConfigurationObject {
  nodes: ConfiguredComputingNodesObject;
  stations: StationsObject;
  instances: InstanceObject;
}

export interface ServerSideConfigurationObject {
  nodes: ConfiguredServerSideComputingNodesObject;
  stations: ServerSideStationsObject;
  instances: InstanceObject;
}

export interface ConfiguredComputingNode extends ComputingNode {
  neighbours?: NeighboursObject;
}

export interface ConfiguredServerSideComputingNode extends ServerSideComputingNode {
  neighbours?: NeighboursObject;
}

export interface NeighboursObject { //Ez tartalmazza az összekapcsolásokat
  [id: string]: Neighbour;
}

export interface Neighbour { //Ez pedig 1 db összekapcsolás
  name: string;
  latency: number;
  parent?: boolean;
}

export interface Node {
  id: string;
  //nodeId: string; It was used in the earlier version of the visualization, not used anymore
  nodeType: string;
  latLANG: L.LatLng;
  parent?: string;
}

export const NODETYPES = {
  CLOUD: 'cloud',
  FOG: 'fog',
  STATION: 'station'
} as const;
