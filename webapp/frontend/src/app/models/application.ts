import { Instance } from './server-api/server-api';

export class Application {
  id: string;
  tasksize: number;
  freq: number;
  instance: Instance;
  numOfInstruction: number;
  activationRatio: number;
  transferDevider: number;
  strategy: string[];
  canJoin: boolean;
  isConfigured: boolean;
}

export class ApplicationsObject {
  [id: string]: Application;
}

export class ServerSideApplication {
  id: string;
  tasksize: number;
  freq: number;
  instance: Instance;
  numOfInstruction: number;
  activationRatio: number;
  transferDevider: number;
  strategy: string;
  canJoin: boolean;
  isConfigured: boolean;
}

export class ServerSideApplicationsObject {
  [id: string]: ServerSideApplication;
}
