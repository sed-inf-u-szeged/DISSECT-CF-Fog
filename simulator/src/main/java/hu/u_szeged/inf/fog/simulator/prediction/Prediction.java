package hu.u_szeged.inf.fog.simulator.prediction;

import hu.u_szeged.inf.fog.simulator.prediction.parser.annotations.FromJsonFieldAliases;
import hu.u_szeged.inf.fog.simulator.prediction.parser.annotations.ToJsonFieldName;
import hu.u_szeged.inf.fog.simulator.prediction.settings.SimulationSettings;

import java.util.List;

import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Represents a prediction with its associated data, error metrics, and settings.
 */
@Getter
@NoArgsConstructor
public class Prediction {
    
    /**
     * Represents error metrics including RMSE, MSE, and MAE.
     */
    @Getter
    @NoArgsConstructor
    public class ErrorMetrics {
        @FromJsonFieldAliases(fieldNames = {"RMSE"})
        private double rmse;
        @FromJsonFieldAliases(fieldNames = {"MSE"})
        private double mse;
        @FromJsonFieldAliases(fieldNames = {"MAE"})
        private double mae;
    }
    
    /**
     * Represents a dataset with timestamps and data points.
     */
    @Getter
    @NoArgsConstructor
    public class Data {
        private List<Integer> timestamp;
        private List<Double> data;
    }
    @ToJsonFieldName(value = "feature_name")
    @FromJsonFieldAliases(fieldNames = {"feature_name"})
    private String featureName;
    @ToJsonFieldName(value = "prediction_number")
    @FromJsonFieldAliases(fieldNames = {"prediction_number"})
    private int predictionNumber;
    @ToJsonFieldName(value = "simulation_settings")
    @FromJsonFieldAliases(fieldNames = {"simulation_settings"})
    private SimulationSettings simulationSettings;
    @ToJsonFieldName(value = "original_data")
    @FromJsonFieldAliases(fieldNames = {"original_data"})
    private Data originalData;
    @ToJsonFieldName(value = "preprocessed_data")
    @FromJsonFieldAliases(fieldNames = {"preprocessed_data"})
    private Data preprocessedData;
    @ToJsonFieldName(value = "test_data_beginning")
    @FromJsonFieldAliases(fieldNames = {"test_data_beginning"})
    private Data testDataBeginning;
    @ToJsonFieldName(value = "test_data_end")
    @FromJsonFieldAliases(fieldNames = {"test_data_end"})
    private Data testDataEnd;
    @ToJsonFieldName(value = "prediction_future")
    @FromJsonFieldAliases(fieldNames = {"prediction_future"})
    private Data predictionFuture;
    @ToJsonFieldName(value = "prediction_test")
    @FromJsonFieldAliases(fieldNames = {"prediction_test"})
    private Data predictionTest;
    @ToJsonFieldName(value = "error_metrics")
    @FromJsonFieldAliases(fieldNames = {"error_metrics"})
    private ErrorMetrics errorMetrics;
    @ToJsonFieldName(value = "prediction_time")
    @FromJsonFieldAliases(fieldNames = {"prediction_time"})
    private double predictionTime;
}