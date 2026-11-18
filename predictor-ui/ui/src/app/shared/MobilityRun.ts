import { NodeLoadRun } from './NodeLoadRun';

export interface MobilityObservation {
  device_id: number; time_ms: number; latitude: number; longitude: number;
  previous_node: string; current_node: string;
}
export interface MobilityForecast {
  origin_ms: number; target_ms: number; origin_node: string;
  node_latitude: number; node_longitude: number; radius_m: number;
  hold_latitude: number; hold_longitude: number;
  velocity_latitude: number; velocity_longitude: number;
}
export interface MobilityEvaluation {
  origin_ms: number; target_ms: number; origin_node: string; actual_node: string;
  actual_latitude: number; actual_longitude: number;
  actual_exit: boolean; hold_exit: boolean; velocity_exit: boolean;
}
export interface MobilityNode { role?: 'fog' | 'cloud'; serviceable?: boolean; name: string; latitude: number; longitude: number; radius: number; }
export interface MobilityLoad {
  node: string;
  device_upload_completed_bytes_derived: number; offload_sent_bytes: number;
  offload_received_bytes: number; waiting_assignment_bytes: number;
  running_tasks: number; completed_tasks: number;
  device_generated_bytes: number; device_upload_completed_bytes_total: number;
  offload_event_sequence: number;
}
export interface MobilityFrame { time_ms: number; sample_kind: string; nodes: MobilityLoad[]; }
export interface MobilityTransfer {
  transfer_id: number; event_sequence: number; time_ms: number; event: string;
  source_node: string; destination_node: string; bytes: number;
}
export interface MobilitySummary { model: string; evaluated: number; tp: number; fn: number; fp: number; tn: number; }
export interface MobilityRun {
  label: string; props: Record<string, string>; nodes: MobilityNode[];
  observations: MobilityObservation[]; predictions: MobilityForecast[];
  evaluations: MobilityEvaluation[]; frames: MobilityFrame[];
  transfers: MobilityTransfer[]; summary: MobilitySummary[];
  baselines?: {source: string; predictions: PositionPrediction[]; evaluations: PositionEvaluation[]};
  learning?: MobilityLearningRun;
  comparison?: MobilityComparisonRun;
  devices?: { id: number; data: MobilityRun }[];
  multi?: { run_id: string; device_count: number; status: string };
  nodeLoad?: NodeLoadRun;
}

export type ComparisonMethod = 'IMM/Kalman' | 'XGBoost' | 'TCN';
export type MobilityMethod = 'HoldPosition' | 'ConstantVelocity' | 'LSTM' | ComparisonMethod;
export interface MobilityComparisonRun {
  methods: ComparisonMethod[]; predictions: PositionPrediction[]; evaluations: PositionEvaluation[];
  versions: {model: ComparisonMethod; model_version: string; shared_training_windows: number; fit_windows: number;
    training_cutoff_sim_ms: number; train_wall_seconds: number; status: string;}[];
}
export interface PositionPrediction {
  model: MobilityMethod; model_version: string; device_id: number;
  origin_ms: number; target_ms: number; origin_node: string;
  predicted_latitude_deg: number; predicted_longitude_deg: number; predict_wall_ms?: number;
}
export interface PositionEvaluation {
  model: MobilityMethod; model_version: string; origin_ms: number; target_ms: number;
  actual_latitude_deg: number; actual_longitude_deg: number; position_error_m: number; predict_wall_ms?: number;
}
export interface MobilityLifecycleEvent {
  sequence: number; sim_time_ms: number; event: string; model_version?: string;
  model?: MobilityMethod; state?: string; decision?: string;
  available_windows?: number; window_size?: number; reason?: string;
}
export interface MobilityVersion {
  model_version: string; training_windows: number; training_cutoff_sim_ms: number;
  matched_origins: number; selected_origins: number; quality_decision: string;
  decided_at_sim_ms: number; state: string; training_reason?: string;
}
export interface MobilityLearningRun {
  run_id: string; device_id: number;
  samples: {time_ms: number; latitude_deg: number; longitude_deg: number}[];
  predictions: PositionPrediction[]; evaluations: PositionEvaluation[];
  selectedPredictions: PositionPrediction[]; selectedEvaluations: PositionEvaluation[];
  events: MobilityLifecycleEvent[]; versions: MobilityVersion[];
  summary: {promotions: number; fallbacks: number; active_lstm: boolean;
    selected_matched: number; lstm_selected_origins: number; degradation_retrainings?: number};
}
