export type LifecycleMetrics = {
  matched_samples: number;
  accuracy: number;
  mae: number;
  mse: number;
  rmse: number;
  smape: number;
};

export type LifecycleData = {
  state: string;
  is_active: boolean;
  metrics: LifecycleMetrics | null;
  updated_at: string;
};

export type RunInfo = {
  id: string;
  profile: string;
  settings_file: string | null;
  note: string;
  thresholds: {
    label: string;
    value: string;
  }[];
};

export type Prediction = {
  feature_name: string;
  prediction_number: number;
  predictor_settings: any;
  original_data: { timestamp: any[]; data: any[] };
  preprocessed_data: { timestamp: any[]; data: any[] };
  test_data_beginning: { timestamp: any[]; data: any[] };
  test_data_end: { timestamp: any[]; data: any[] };
  prediction_future: { timestamp: any[]; data: any[] };
  prediction_test: { timestamp: any[]; data: any[] };
  error_metrics: any;
  lifecycle: LifecycleData | null;
  run_info?: RunInfo;
};