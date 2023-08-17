import { Prediction } from "../shared/Prediction";
import { getRandomString } from "../shared/utils";

export const getMockPredictions = ({ numOfPredictions, numOfFeatures, batchSize, testLength, predictionLength }: 
    { numOfPredictions: number, numOfFeatures: number, batchSize: number, testLength: number, predictionLength: number }) => {
    const mocks: Map<number, Prediction[]> = new Map();
    const featureNames = [];
    for (let i = 0; i < numOfFeatures; i++) {
        featureNames.push(getRandomString(8));
    }

    for (let i = 0; i < numOfPredictions; i++) {
        const predictionNumber = Math.floor(Math.random() * numOfFeatures);

        const prediction: Prediction = {
            feature_name: featureNames[Math.floor(Math.random() * numOfFeatures)],
            prediction_number: predictionNumber,
            original_data: getData(0, batchSize),
            preprocessed_data: getData(0, batchSize),
            test_data_beginning: getData(0, batchSize - testLength),
            test_data_end: getData(batchSize - testLength, batchSize),
            prediction_test: getData(batchSize - testLength, batchSize),
            prediction_future: getData(batchSize, batchSize + predictionLength),
            simulation_settings: {
                prediction: {
                    batchSize: batchSize,
                    length: predictionLength
                }
            },
            error_metrics: {
                RMSE: Math.random() * 3,
                MSE: Math.random() * 3,
                MAE: Math.random() * 3
            }
        };

        if (!mocks.has(prediction.prediction_number)) {
            mocks.set(prediction.prediction_number, []);
        }
        mocks.get(prediction.prediction_number)?.push(prediction);
        mocks.get(prediction.prediction_number)?.sort((a, b) => a.feature_name.localeCompare(b.feature_name));
    }
    
    return mocks;
}

const getData = (from: number, to: number) => {
    const result: { data: number[], timestamp: number[] } = { data: [], timestamp: [] };
    let prevValue: number = Math.random();
    for (let i = from; i <= to; i++) {
        const value: number = Math.random() * 2 - 1;
        result.data.push(prevValue + value);
        result.timestamp.push(i);
        prevValue += value;
    }
    return result;
}