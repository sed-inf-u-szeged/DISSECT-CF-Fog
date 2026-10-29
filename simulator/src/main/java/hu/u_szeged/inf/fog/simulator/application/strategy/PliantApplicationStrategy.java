package hu.u_szeged.inf.fog.simulator.application.strategy;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;
import hu.u_szeged.inf.fog.simulator.application.Application;
import hu.u_szeged.inf.fog.simulator.node.ComputingAppliance;
import hu.u_szeged.inf.fog.simulator.pliant.FuzzyIndicators;
import hu.u_szeged.inf.fog.simulator.pliant.Sigmoid;
import hu.u_szeged.inf.fog.simulator.prediction.Feature;
import hu.u_szeged.inf.fog.simulator.prediction.FeatureManager;
import hu.u_szeged.inf.fog.simulator.prediction.Prediction;
import hu.u_szeged.inf.fog.simulator.prediction.PredictionConfigurator;
import hu.u_szeged.inf.fog.simulator.prediction.PredictionLogger;
import hu.u_szeged.inf.fog.simulator.prediction.PredictionSelector;
import hu.u_szeged.inf.fog.simulator.prediction.OnlineModelLifecycleManager;
import hu.u_szeged.inf.fog.simulator.prediction.lifecycle.LifecycleManager;
import hu.u_szeged.inf.fog.simulator.prediction.settings.PairPredictionSettings;
import hu.u_szeged.inf.fog.simulator.prediction.settings.SimulationSettings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Vector;

/**
 * Application strategy based on Fuzzy logic and Pliant scoring.
 */
public class PliantApplicationStrategy extends ApplicationStrategy {

    /**
     * Constructs an application strategy.
     *
     * @param activationRatio the offloading activation ratio
     * @param transferDivider the data transfer divider
     */
    public PliantApplicationStrategy(double activationRatio, double transferDivider) {
        this.activationRatio = activationRatio;
        this.transferDivider = transferDivider;
    }

    @Override
    public void findApplication(long dataForTransfer) {
        if (this.getComputingAppliances().size() > 0) {
            this.startDataTranfer(decisionMaker(this.getComputingAppliances()), dataForTransfer);
        }
    }

    /**
     * Averages up to the requested number of future values.
     * Missing or invalid values make the prediction unusable for scoring.
     *
     * @param prediction the prediction to read
     * @param limit the maximum number of future values
     * @return the average, or an empty result if unavailable
     */
    static OptionalDouble averageFuture(Prediction prediction, int limit) {
        if (prediction == null || limit <= 0
                || prediction.getPredictionFuture() == null
                || prediction.getPredictionFuture().getData() == null) {
            return OptionalDouble.empty();
        }

        List<?> values = prediction.getPredictionFuture().getData();
        if (values.isEmpty()) {
            return OptionalDouble.empty();
        }

        for (Object value : values) {
            if (!(value instanceof Number)
                    || !Double.isFinite(((Number) value).doubleValue())) {
                return OptionalDouble.empty();
            }
        }

        int count = Math.min(limit, values.size());
        double average = 0.0;
        for (int i = 0; i < count; i++) {
            average += ((Number) values.get(i)).doubleValue() / count;
        }

        return Double.isFinite(average)
                ? OptionalDouble.of(average) : OptionalDouble.empty();
    }

    /**
     * Returns the node backlog in bytes, matching notYetProcessedData.
     *
     * @param appliance the node whose applications are counted
     * @return the total unprocessed data in bytes
     */
    static double unprocessedBytes(ComputingAppliance appliance) {
        double total = 0.0;
        for (Application app : appliance.applications) {
            total += app.receivedData - app.processedData;
        }
        return total;
    }

    private Application decisionMaker(ArrayList<ComputingAppliance> availableCompAppliances) {
        ComputingAppliance currentCa = this.application.computingAppliance;
        List<Prediction> predictions = new ArrayList<>();

        if (PredictionConfigurator.PREDICTION_ENABLED) {
            List<Feature> predictedFeatures = new ArrayList<>();
            List<String> invokedPredictors = new ArrayList<>();

            for (var predictor : SimulationSettings.get().getPredictionSettings()) {
                List<Feature> features = FeatureManager.getInstance().getFeaturesWithEnoughData(
                        predictor.getPredictionSettings().getBatchSize());

                String modelType = predictor.getPredictorSettings().getPredictor();
                if (OnlineModelLifecycleManager.getInstance().isManagedModelType(modelType)) {
                    features.removeIf(feature -> !LifecycleManager.getInstance()
                            .isPredictionAllowed(predictor.getPredictorName(), feature.getName()));
                }

                if (!features.isEmpty()) {
                    try {
                        predictions.addAll(FeatureManager.getInstance().predict(
                                features, predictor.getPredictorName()));
                        invokedPredictors.add(predictor.getPredictorName());
                        predictedFeatures.addAll(features);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }

            if (!invokedPredictors.isEmpty()) {
                PredictionLogger.info("Pliant formal prediction", "time=" + Timed.getFireCount()
                        + " predictors=" + String.join(", ", invokedPredictors));
            }
            predictedFeatures.forEach(feature -> feature.setHasNewValue(false));
        }

        String baselineName = null;
        if (SimulationSettings.get() != null
                && SimulationSettings.get().getLifecycleSettings() != null) {
            baselineName = SimulationSettings.get()
                    .getLifecycleSettings().getBaselinePredictorName();
        }

        Map<String, Prediction> selectedPredictions =
                PredictionSelector.select(predictions, baselineName);
        PairPredictionSettings.increaseBestPredictorCounterFromPredictions(
                selectedPredictions.values());

        double minLoadOfResource = currentCa.getLoadOfResource();
        double maxLoadOfResource = currentCa.getLoadOfResource();
        int deviceMin = currentCa.applications.get(0).deviceList.size();
        int deviceMax = currentCa.applications.get(0).deviceList.size();
        double minPrice = currentCa.applications.get(0).instance.pricePerTick * 100000000;
        double maxPrice = currentCa.applications.get(0).instance.pricePerTick * 100000000;
        double minLatency = currentCa.applications.get(0).computingAppliance.iaas.repositories.get(0)
                .getLatencies().get(currentCa.iaas.repositories.get(0).getName());
        double maxLatency = currentCa.applications.get(0).computingAppliance.iaas.repositories.get(0)
                .getLatencies().get(currentCa.iaas.repositories.get(0).getName());
        double minUnprocessedData = (currentCa.applications.get(0).receivedData
                - currentCa.applications.get(0).processedData) / currentCa.applications.get(0).tasksize;
        double maxUnprocessedData = (currentCa.applications.get(0).receivedData
                - currentCa.applications.get(0).processedData) / currentCa.applications.get(0).tasksize;

        for (int i = 0; i < availableCompAppliances.size(); i++) {
            ComputingAppliance ca = availableCompAppliances.get(i);

            double loadofresource = ca.getLoadOfResource();
            if (loadofresource < minLoadOfResource) {
                minLoadOfResource = loadofresource;
            }
            if (loadofresource > maxLoadOfResource) {
                maxLoadOfResource = loadofresource;
            }

            int deviceSize = ca.applications.get(0).deviceList.size();
            if (deviceSize < deviceMin) {
                deviceMin = deviceSize;
            }
            if (deviceSize > deviceMax) {
                deviceMax = deviceSize;
            }

            double priceperTick = ca.applications.get(0).instance.pricePerTick * 100000000;
            if (priceperTick < minPrice) {
                minPrice = priceperTick;
            }
            if (priceperTick > maxPrice) {
                maxPrice = priceperTick;
            }

            double latency = this.application.computingAppliance.iaas.repositories.get(0).getLatencies()
                    .get(availableCompAppliances.get(i).iaas.repositories.get(0).getName());
            if (latency < minLatency) {
                minLatency = latency;
            }
            if (latency > maxLatency) {
                maxLatency = latency;
            }

            double unprocesseddata = (ca.applications.get(0).receivedData
                    - ca.applications.get(0).processedData) / ca.applications.get(0).tasksize;
            if (unprocesseddata < minUnprocessedData) {
                minUnprocessedData = unprocesseddata;
            }
            if (unprocesseddata > maxUnprocessedData) {
                maxUnprocessedData = unprocesseddata;
            }
        }

        Vector<Double> loadOfResource = new Vector<Double>();
        Vector<Double> price = new Vector<Double>();
        Vector<Double> unprocesseddata = new Vector<Double>();
        Map<String, Double> predUnprocesseddata = new HashMap<>();

        for (int i = 0; i < availableCompAppliances.size(); i++) {
            ComputingAppliance ca = availableCompAppliances.get(i);
            Sigmoid sig = new Sigmoid(Double.valueOf(-1.0 / 8.0),
                    Double.valueOf((maxLoadOfResource + minLoadOfResource) / 2.0));
            loadOfResource.add(sig.getAt(ca.getLoadOfResource()));

            sig = new Sigmoid(Double.valueOf(4.0 / 1.0), Double.valueOf(minPrice));
            price.add(sig.getAt(ca.applications.get(0).instance.pricePerTick * 100000000));

            sig = new Sigmoid(Double.valueOf(-1.0 / 8.0),
                    Double.valueOf(Math.abs(maxLatency - minLatency) / 2.0));

            sig = new Sigmoid(Double.valueOf(-1.0 / 4.0),
                    Double.valueOf(maxUnprocessedData - minUnprocessedData));
            unprocesseddata.add(sig.getAt(
                    (double) ((ca.applications.get(0).receivedData
                            - ca.applications.get(0).processedData) / ca.applications.get(0).tasksize)));

            String expectedFeature = ca.name + "::notYetProcessedData";
            for (Prediction prediction : selectedPredictions.values()) {
                if (!expectedFeature.equals(prediction.getFeatureName())) {
                    continue;
                }

                OptionalDouble futureAverage = averageFuture(prediction, 10);
                if (futureAverage.isEmpty()) {
                    continue;
                }

                double currentBytes = unprocessedBytes(ca);
                if (!Double.isFinite(currentBytes)) {
                    continue;
                }

                // Both the prediction and the reference value are node totals in bytes.
                sig = new Sigmoid(Double.valueOf(-1.0 / 32768.0),
                        Double.valueOf(currentBytes));
                predUnprocesseddata.put(ca.name, sig.getAt(futureAverage.getAsDouble()));
            }
        }

        Vector<Integer> score = new Vector<Integer>();
        for (int i = 0; i < availableCompAppliances.size(); ++i) {
            Vector<Double> temp = new Vector<Double>();
            temp.add(loadOfResource.get(i));
            temp.add(price.get(i));
            temp.add(unprocesseddata.get(i));

            if (predUnprocesseddata.containsKey(availableCompAppliances.get(i).name)) {
                temp.add(predUnprocesseddata.get(availableCompAppliances.get(i).name));
            }
            score.add((int) (FuzzyIndicators.getAggregation(temp) * 100));
        }

        Vector<Double> temp = new Vector<Double>();

        Sigmoid sig = new Sigmoid(Double.valueOf(-1.0 / 8.0),
                Double.valueOf((maxLoadOfResource + minLoadOfResource) / 2.0));
        temp.add(sig.getAt(currentCa.getLoadOfResource()));

        sig = new Sigmoid(Double.valueOf(4.0 / 1.0), Double.valueOf(minPrice));
        temp.add(sig.getAt(currentCa.applications.get(0).instance.pricePerTick * 100000000));

        sig = new Sigmoid(Double.valueOf(-1.0 / 8.0),
                Double.valueOf(Math.abs(maxLatency - minLatency) / 2.0));

        sig = new Sigmoid(Double.valueOf(-1.0 / 4.0),
                Double.valueOf(maxUnprocessedData - minUnprocessedData));
        temp.add(sig.getAt(
                (double) ((currentCa.applications.get(0).receivedData
                        - currentCa.applications.get(0).processedData) / currentCa.applications.get(0).tasksize)));

        Integer currentCaScore = (int) (FuzzyIndicators.getAggregation(temp) * 100);
        Vector<Integer> finaldecision = new Vector<Integer>();

        for (int i = 0; i < availableCompAppliances.size(); ++i) {
            finaldecision.add(i);
        }
        finaldecision.add(-1);

        for (int i = 0; i < score.size(); ++i) {
            for (int j = 0; j < score.get(i); j++) {
                finaldecision.add(i);
            }
        }

        for (int j = 0; j < currentCaScore; j++) {
            finaldecision.add(-1);
        }

        Collections.shuffle(finaldecision, SeedSyncer.centralRnd);
        int chooseIdx = SeedSyncer.centralRnd.nextInt(finaldecision.size());

        if (finaldecision.get(chooseIdx) != -1) {
            ComputingAppliance ca = availableCompAppliances.get(finaldecision.get(chooseIdx));
            return ca.applications.get(SeedSyncer.centralRnd.nextInt(ca.applications.size()));
        }
        return application;
    }
}