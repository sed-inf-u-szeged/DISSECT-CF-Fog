import {ComparisonMethod, PositionPrediction, PositionEvaluation} from './MobilityRun';
import {NodeLoadLiveRecord} from './node-load-live';
export interface MobilityRunConfig {
  infrastructure?: 'two-node' | 'hungary-cities'; cityPlacement?: string; cityCoverageKm?: number;
  deviceWorkload?: 'constant' | 'periodic-burst';
  models?: ComparisonMethod[];
  loadModels?: ('LSTM' | 'TCN' | 'GRU' | 'PatchTST')[]; loadProfile?: 'candidate' | 'observe'; loadEpochs?: number;
  strategy: 'random-walk' | 'nomadic'; devices: number; minutes: number; drainMinutes: number;
  seed: number; latitude: number; longitude: number; speedMin: number; speedMax: number; radius: number;
  speed: number; waypoints: number[][]; repeatRoute: boolean;
}
export interface MobilityRunRequest { config: MobilityRunConfig; pythonExecutable?: string; }
export interface MobilityJobState {
  id?: string; stage: string; active: boolean; config?: MobilityRunConfig; session?: string;
  output?: string; batch?: string; error?: string; startedAt?: number; finishedAt?: number;
}
export interface MobilityLiveEvent {
  type: 'META' | 'FRAME' | 'LOAD' | 'POINT' | 'LATEST' | 'NODE_LOAD' | 'MOBILITY_STATUS'; sequence?: number;
  kind?:NodeLoadLiveRecord['kind']; data?:Record<string,unknown>;
  run_id?: string; device_count?: number; expected_observations?: number; duration_ms?: number;
  device_id?: number; time_ms?: number; sampled?: boolean; latitude?: number; longitude?: number; connection?: string; phase?: string;
  nodes?: {name?: string; node?: string; role?:'fog'|'cloud'; serviceable?:boolean; latitude?: number; longitude?: number; radius?: number; waiting_bytes?: number; running_tasks?: number; completed_tasks?: number}[];
  comparison?: {time_ms:number;methods:ComparisonMethod[];matched:number;forecasts:PositionPrediction[];latest:{model:ComparisonMethod;target_ms:number;position_error_m:number}[]};
  baselines?: {predictions:PositionPrediction[];evaluations:PositionEvaluation[]};
  lifecycle?: {observations?:number;available_windows?:number;required_windows?:number;serving: string; candidate: string; state: string; matched: number; promotions: number; fallbacks: number;
    error_m: number | null; error_target_ms: number; forecast?: {model:string;origin_ms:number;target_ms:number;latitude:number;longitude:number}};
}
export interface MobilityLiveBatch { id: string; events: MobilityLiveEvent[]; }
export interface MobilityJobSnapshot { state: MobilityJobState; events: MobilityLiveEvent[]; configure: boolean; }
