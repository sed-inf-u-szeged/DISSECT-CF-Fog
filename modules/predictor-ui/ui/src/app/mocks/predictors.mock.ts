import {PredictorModel} from "../shared/PredictorModel";

export const getMockPredictors = () => {
  const predictors: PredictorModel[] = [];
  predictors.push({
    id: 'ARIMA',
    label: 'Arima',
    options: [

    ],
    hyperparameters: [

    ]
  });
  return predictors;
}
