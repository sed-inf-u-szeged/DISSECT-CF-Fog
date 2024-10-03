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

export interface NeighboursObject {
  [nodeId: string]: Neighbour;
}

export interface Neighbour {
  name: string;
  latency: number;
  parent?: boolean;
}

export interface Node {
  id: string;
  nodeId: string;
  nodeType: string;
  parent?: string;
}

export const NODETYPES = {
  CLOUD: 'cloud',
  FOG: 'fog',
  STATION: 'station'
} as const;
