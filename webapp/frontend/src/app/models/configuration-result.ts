export interface Configuration {
  _id: any;
  user: any;
  time: any;
  jobs: Simulation[];
}

export interface Simulation {
  _id: any;
  user: any;
  simulatorJobStatus: string;
  configFiles: any;
  createdDate: any;
  lastModifiedDate: any;
  results: any;
  simulatorJobResult: any;
}
