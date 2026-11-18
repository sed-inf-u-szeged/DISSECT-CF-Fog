export type LoadModel = 'LSTM' | 'TCN' | 'GRU' | 'PatchTST';
export type LoadMethod = LoadModel | 'ARIMA' | 'LR' | 'Persistence';
export interface LoadSample { index: number; time_ms: number; value: number; }
export interface LoadForecast {
  node_id: string; model: LoadMethod; version: string;
  origin_index: number; origin_ms: number; target_index: number; target_ms: number;
  lead_steps: number; predicted_bytes: number; persistence_bytes?: number; inference_vector_ms: number;
}
export interface LoadEvaluation extends LoadForecast { actual_bytes: number; absolute_error_bytes: number; }
export interface LoadSelection extends LoadForecast { selection_ms: number; }
export interface LoadVersion { model: LoadModel; version: string; trained_at_ms: number; training_windows: number; training_seconds: number; }
export interface LoadEvent {
  node_id: string; model: LoadModel; version: string; time_ms: number; event: string;
}
export interface LoadNode {
  node_id: string; samples: LoadSample[]; baseline: LoadForecast[]; baselineEvaluations: LoadEvaluation[];
  predictions: LoadForecast[]; evaluations: LoadEvaluation[]; selections: LoadSelection[];
  events: LoadEvent[]; versions: LoadVersion[];
}
export interface LoadPolicy {
  matched_targets: number; minimum_within_tolerance_percent: number;
  relative_tolerance: number; absolute_tolerance_bytes: number;
}
export interface NodeLoadRun {
  run_id: string; interval_ms: number; nodes: LoadNode[]; models: LoadModel[];
  baselines?: ('ARIMA'|'LR')[]; policy?: LoadPolicy; profile?: string; hasBaseline: boolean;
}
