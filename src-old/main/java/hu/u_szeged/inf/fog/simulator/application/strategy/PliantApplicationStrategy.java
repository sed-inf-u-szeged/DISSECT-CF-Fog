package hu.u_szeged.inf.fog.simulator.application.strategy;

import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;
import hu.u_szeged.inf.fog.simulator.application.Application;
import hu.u_szeged.inf.fog.simulator.node.ComputingAppliance;
import hu.u_szeged.inf.fog.simulator.pliant.FuzzyIndicators;
import hu.u_szeged.inf.fog.simulator.pliant.Sigmoid;
import hu.u_szeged.inf.fog.simulator.prediction.Feature;
import hu.u_szeged.inf.fog.simulator.prediction.FeatureManager;
import hu.u_szeged.inf.fog.simulator.prediction.Prediction;
import hu.u_szeged.inf.fog.simulator.prediction.PredictionConfigurator;
import hu.u_szeged.inf.fog.simulator.prediction.settings.SimulationSettings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

/**
 * This class represents an application strategy based on Fuzzy logic and Pliant system.
 */
public class PliantApplicationStrategy extends ApplicationStrategy {

    /**
     * Constructs a new strategy with the specified activation ratio and transfer divider.
     *
     * @param activationRatio triggers offloading if it is larger than the unprocessed data / tasksize ratio 
     * @param transferDivider determining the ratio of the data to be transferred
     */
    public PliantApplicationStrategy(double activationRatio, double transferDivider) {
        this.activationRatio = activationRatio;
        this.transferDivider = transferDivider;
    }

    /**
     * If there are computing appliances available,it starts data transfer to the 
     * appropriate application, which is determined by the decision maker method.
     *
     * @param dataForTransfer the data to be transferred
     */
    @Override
    public void findApplication(long dataForTransfer) {
        if (this.getComputingAppliances().size() > 0) {
            this.startDataTranfer(decisionMaker(this.getComputingAppliances()), dataForTransfer);
        }
    }

    /**
     * Makes a decision about which application to use based on the available computing appliances.
     * It considers various metrics such as load of resource, cost, latency, unprocessed data, etc.
     * It also integrates predictions.
     *
     * @param availableCompAppliances the list of available computing appliances
     * @return the chosen application based on the decision-making process
     */
    private Application decisionMaker(ArrayList<ComputingAppliance> availableCompAppliances) {
        // TODO: reduce complexity of the method
        ComputingAppliance currentCa = this.application.computingAppliance;
        List<Prediction> predictions = new ArrayList<>();
        if (PredictionConfigurator.PREDICTION_ENABLED) {
            List<Feature> features = FeatureManager.getInstance().getFeaturesWithEnoughData(
                    SimulationSettings.get().getPrediction().getBatchSize()
            );
            if (features.size() > 0) {
                try {
                    predictions = FeatureManager.getInstance().predict(
                            features,
                            SimulationSettings.get().getPrediction().getBatchSize()
                    );
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        double minLoadOfResource = currentCa.getLoadOfResource();
        double maxLoadOfResource = currentCa.getLoadOfResource();
        int deviceMin = currentCa.applications.get(0).deviceList.size();
        int deviceMax = currentCa.applications.get(0).deviceList.size();
        double minPrice = currentCa.applications.get(0).instance.pricePerTick * 100000000;
        double maxPrice = currentCa.applications.get(0).instance.pricePerTick * 100000000;
        double minLatency = currentCa.applications.get(0).computingAppliance.iaas.repositories.get(0).getLatencies()
                .get(currentCa.iaas.repositories.get(0).getName());
        double maxLatency = currentCa.applications.get(0).computingAppliance.iaas.repositories.get(0).getLatencies()
                .get(currentCa.iaas.repositories.get(0).getName());
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

            double unprocesseddata = (ca.applications.get(0).receivedData - ca.applications.get(0).processedData)
                    / ca.applications.get(0).tasksize;
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
            /*
             * System.out.println(ca.name + " Load Resource " + ca.getLoadOfResource() +
             * " Price: " + ca.applications.get(0).instance.pricePerTick * 100000000 +
             * " UnprocessedData: " + (ca.applications.get(0).receivedData -
             * ca.applications.get(0).receivedData) / ca.applications.get(0).tasksize);
             */
            sig = new Sigmoid(Double.valueOf(4.0 / 1.0), Double.valueOf((minPrice)));
            price.add(sig.getAt(ca.applications.get(0).instance.pricePerTick * 100000000));

            // System.out.println(ca.applications.get(0).instance.pricePerTick * 100000000);

            sig = new Sigmoid(Double.valueOf(-1.0 / 8.0), Double.valueOf((Math.abs((maxLatency - minLatency)) / 2.0)));

            sig = new Sigmoid(Double.valueOf(-1.0 / 4.0), Double.valueOf((maxUnprocessedData - minUnprocessedData)));
            unprocesseddata.add(
                    sig.getAt((double) ((ca.applications.get(0).receivedData - ca.applications.get(0).processedData)
                            / ca.applications.get(0).tasksize)));

            //if we have prediction
            //find the element from prediction.
            if (predictions.size() > 0) {
                for (Prediction prediction : predictions) {
                    String[] name = prediction.getFeatureName().split("::");

                    //Find the relevant compuerappliant
                    if (name[0].equals(ca.name)) {
                        double tmpavg = 0.0;
                        int num = 0;
                        for (int k = 0; k < 10; k++) {
                            if (prediction.getPredictionFuture() == null) {
                                continue;
                            }
                            tmpavg += prediction.getPredictionFuture().getData().get(k);
                            num++;
                        }
                        tmpavg /= num;
                        double actUd = (double) ((ca.applications.get(0).receivedData 
                                - ca.applications.get(0).processedData)
                                / ca.applications.get(0).tasksize);
                        //pred_unprocesseddata.add(p.getData().get(0));
                        sig = new Sigmoid(Double.valueOf(-1.0 / 32768.0), Double.valueOf(actUd));
                        //unprocesseddata.add(sig.getAt((double) tmpavg));
                        predUnprocesseddata.put(name[0], sig.getAt((double) tmpavg));

                    }
                }
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
        // System.out.println("Pontozás: " + score);

        Vector<Double> temp = new Vector<Double>();

        Sigmoid sig = new Sigmoid(Double.valueOf(-1.0 / 8.0),
                Double.valueOf((maxLoadOfResource + minLoadOfResource) / 2.0));
        temp.add(sig.getAt(currentCa.getLoadOfResource()));

        sig = new Sigmoid(Double.valueOf(4.0 / 1.0), Double.valueOf((minPrice)));
        temp.add(sig.getAt(currentCa.applications.get(0).instance.pricePerTick * 100000000));

        sig = new Sigmoid(Double.valueOf(-1.0 / 8.0), Double.valueOf((Math.abs((maxLatency - minLatency)) / 2.0)));

        sig = new Sigmoid(Double.valueOf(-1.0 / 4.0), Double.valueOf((maxUnprocessedData - minUnprocessedData)));
        temp.add(sig.getAt(
                (double) ((currentCa.applications.get(0).receivedData - currentCa.applications.get(0).processedData)
                        / currentCa.applications.get(0).tasksize)));
        Integer currentCaScore;
        currentCaScore = (int) (FuzzyIndicators.getAggregation(temp) * 100);
        /*
         * System.out.println(currentCA.name + " Load Resource " +
         * currentCA.getLoadOfResource() + " Price: " +
         * currentCA.applications.get(0).instance.pricePerTick * 100000000 +
         * " UnprocessedData: " + (currentCA.applications.get(0).receivedData -
         * currentCA.applications.get(0).processedData) /
         * currentCA.applications.get(0).tasksize); System.out.println("Score " +
         * currentCAscore);
         */
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