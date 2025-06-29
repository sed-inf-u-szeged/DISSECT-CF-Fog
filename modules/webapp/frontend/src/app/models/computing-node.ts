import {Application, ApplicationsObject, ServerSideApplicationsObject} from './application';
import { Resource } from './server-api/server-api';

export class ComputingNode {
  id: string;
  x: number;
  y: number;
  range: number;
  resource: Resource;
  applications: ApplicationsObject;
  isCloud: boolean;
  isConfigured: boolean;
  quantity?: number;
}

export class ServerSideComputingNode {
  id: string;
  x: number;
  y: number;
  range: number;
  resource: Resource;
  applications: ServerSideApplicationsObject;
  isCloud: boolean;
  isConfigured: boolean;
  quantity?: number;
}
