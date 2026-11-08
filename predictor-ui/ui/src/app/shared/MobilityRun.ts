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
export interface MobilityNode { name: string; latitude: number; longitude: number; radius: number; }
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
}
