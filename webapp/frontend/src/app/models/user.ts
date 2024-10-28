export interface User {
  email: string;
  password: string;
  university: string;
  simulationsRun: number;   //Number of simulations run in the current period
  maxSimulations: number;   //Max number of simulations that can be run in the current period
  totalRuntime: number;     //Total runtime in the current period in seconds
  maxRuntime: number;       //Maximum runtime in the current period in seconds
  resetPeriod: number;      //Reset period in days
  lastReset: number;        //Timestamp of the last reset
}
