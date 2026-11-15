import {ComparisonMethod, PositionPrediction} from './MobilityRun';
export interface MobilityRunConfig {
  models?: ComparisonMethod[];
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
  type: 'META' | 'FRAME' | 'LOAD' | 'POINT' | 'LATEST'; sequence?: number;
  run_id?: string; device_count?: number; expected_observations?: number; duration_ms?: number;
  device_id?: number; time_ms?: number; sampled?: boolean; latitude?: number; longitude?: number; connection?: string; phase?: string;
  nodes?: {name?: string; node?: string; latitude?: number; longitude?: number; radius?: number; waiting_bytes?: number; running_tasks?: number; completed_tasks?: number}[];
  comparison?: {time_ms:number;methods:ComparisonMethod[];matched:number;forecasts:PositionPrediction[];latest:{model:ComparisonMethod;target_ms:number;position_error_m:number}[]};
  lifecycle?: {serving: string; candidate: string; state: string; matched: number; promotions: number; fallbacks: number;
    error_m: number | null; error_target_ms: number; forecast?: {model:string;origin_ms:number;target_ms:number;latitude:number;longitude:number}};
}
export interface MobilityLiveBatch { id: string; events: MobilityLiveEvent[]; }
export interface MobilityJobSnapshot { state: MobilityJobState; events: MobilityLiveEvent[]; configure: boolean; }
