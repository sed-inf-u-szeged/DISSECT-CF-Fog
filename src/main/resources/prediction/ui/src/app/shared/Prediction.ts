export type Prediction = {
  feature_name: string,
  prediction_number: number;
  simulation_settings: any;
  original_data: { timestamp: any[], data: any[] };
  preprocessed_data: { timestamp: any[], data: any[] };
  test_data_beginning: { timestamp: any[], data: any[] };
  test_data_end: { timestamp: any[], data: any[] };
  prediction_future: { timestamp: any[], data: any[] };
  prediction_test: { timestamp: any[], data: any[] };
  error_metrics: any;
};
