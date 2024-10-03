import { ComputingNode } from './computing-node';
import {ConfiguredComputingNode, ConfiguredServerSideComputingNode} from './configuration';

export interface CloudNodesObject {
  [nodeId: string]: ComputingNode;
}

export interface FogNodesObject {
  [nodeId: string]: ComputingNode;
}

export interface ComputingNodesObject {
  clouds: CloudNodesObject;
  fogs: FogNodesObject;
}

export interface ConfiguredComputingNodesObject {
  [nodeId: string]: ConfiguredComputingNode;
}

export interface ConfiguredServerSideComputingNodesObject {
  [nodeId: string]: ConfiguredServerSideComputingNode;
}
