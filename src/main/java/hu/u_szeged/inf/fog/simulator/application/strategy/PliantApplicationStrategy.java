package hu.u_szeged.inf.fog.simulator.application.strategy;

import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;
import hu.u_szeged.inf.fog.simulator.application.Application;
import hu.u_szeged.inf.fog.simulator.physical.ComputingAppliance;
import hu.u_szeged.inf.fog.simulator.pliant.FuzzyIndicators;
import hu.u_szeged.inf.fog.simulator.pliant.Sigmoid;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Vector;

public class PliantApplicationStrategy extends ApplicationStrategy {

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

    private Application decisionMaker(ArrayList<ComputingAppliance> availableCompAppliances) {

        ComputingAppliance currentCa = this.application.computingAppliance;

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

            double unprocesseddata = (ca.applications.get(0).receivedData - ca.applications.get(0).receivedData)
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
        }

        Vector<Integer> score = new Vector<Integer>();
        for (int i = 0; i < availableCompAppliances.size(); ++i) {
            Vector<Double> temp = new Vector<Double>();
            temp.add(loadOfResource.get(i));

            temp.add(price.get(i));

            temp.add(unprocesseddata.get(i));
            score.add((int) (FuzzyIndicators.getAggregation(temp) * 100));
        }
        // System.out.println("Pontozás: " + score);

        Integer currentCaScore;
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
          

        Collections.shuffle(finaldecision);
        int chooseIdx = SeedSyncer.centralRnd.nextInt(finaldecision.size());

        if (finaldecision.get(chooseIdx) != -1) {
            ComputingAppliance ca = availableCompAppliances.get(finaldecision.get(chooseIdx));
            return ca.applications.get(SeedSyncer.centralRnd.nextInt(ca.applications.size()));

        }
        return application;
    }

}
