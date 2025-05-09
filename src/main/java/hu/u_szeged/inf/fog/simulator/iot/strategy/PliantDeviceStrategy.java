package hu.u_szeged.inf.fog.simulator.iot.strategy;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;
import hu.u_szeged.inf.fog.simulator.application.Application;
import hu.u_szeged.inf.fog.simulator.iot.Device;
import hu.u_szeged.inf.fog.simulator.iot.mobility.MobilityEvent;
import hu.u_szeged.inf.fog.simulator.pliant.FuzzyIndicators;
import hu.u_szeged.inf.fog.simulator.pliant.Kappa;
import hu.u_szeged.inf.fog.simulator.pliant.Sigmoid;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Vector;

/**
 * This class represents a device strategy based on Fuzzy logic and Pliant system.
 */
public class PliantDeviceStrategy extends DeviceStrategy {

    /**
     * Finds and sets the chosen application for the device, which is 
     * determined by the decision maker method.
     * As it can cause a node change, the latency must be updated. 
     */
    @Override
    public void findApplication() {
        this.chosenApplication = null;
        ArrayList<Application> availableApplications = this.getAvailableApplications();

        if (availableApplications.size() > 0) {
            this.chosenApplication = availableApplications.get(decisionMaker(availableApplications));
        }

        MobilityEvent.refresh(this.device, this.chosenApplication);
    }

    /**
     * Makes a decision on which application to choose from the available applications.
     * It considers various metrics such as load of resource, cost, connections, etc.
     *
     * @param availableApplications the list of available applications to choose from
     * @return the index of the chosen application in the availableApplications list
     */
    private int decisionMaker(ArrayList<Application> availableApplications) {

        Kappa kappa = new Kappa(3.0, 0.4);
        Sigmoid sig = new Sigmoid(Double.valueOf(-1.0 / 96.0), Double.valueOf(15));
        Vector<Double> price = new Vector<Double>();
        for (int i = 0; i < availableApplications.size(); ++i) {
            price.add(kappa.getAt(sig.getAt(availableApplications.get(i).instance.pricePerTick * 1000000000)));

        }

        double minprice = Double.MAX_VALUE;
        double maxprice = Double.MIN_VALUE;
        for (int i = 0; i < availableApplications.size(); ++i) {
            double currentprice = availableApplications.get(i).getCurrentCost();
            if (currentprice > maxprice) {
                maxprice = currentprice;
            }
                
            if (currentprice < minprice) {
                minprice = currentprice;
            }
                
        }

        Vector<Double> currentprice = new Vector<Double>();
        sig = new Sigmoid(Double.valueOf(-1.0), Double.valueOf((maxprice - minprice) / 2.0));
        for (int i = 0; i < availableApplications.size(); ++i) {
            currentprice.add(kappa.getAt(sig.getAt(availableApplications.get(i).getCurrentCost())));
        }

        double minworkload = Double.MAX_VALUE;
        double maxworkload = Double.MIN_VALUE;
        for (int i = 0; i < availableApplications.size(); ++i) {
            double workload = availableApplications.get(i).computingAppliance.getLoadOfResource();
            if (workload > maxworkload) {
                maxworkload = workload;
            }
               
            if (workload < minworkload) {
                minworkload = workload;
            }
                
        }

        Vector<Double> workload = new Vector<Double>();
        sig = new Sigmoid(Double.valueOf(-1.0), Double.valueOf(maxworkload));
        for (int i = 0; i < availableApplications.size(); ++i) {
            workload.add(kappa.getAt(sig.getAt(availableApplications.get(i).computingAppliance.getLoadOfResource())));

        }

        Vector<Double> numberofvm = new Vector<Double>();
        sig = new Sigmoid(Double.valueOf(-1.0 / 8.0), Double.valueOf(3));
        for (int i = 0; i < availableApplications.size(); ++i) {
            numberofvm.add(kappa.getAt(sig.getAt(Double.valueOf(availableApplications.get(i).utilisedVms.size()))));
        }

        double sumStations = 0.0;
        for (int i = 0; i < availableApplications.size(); ++i) {
            sumStations += availableApplications.get(i).deviceList.size();
        }

        Vector<Double> numberofstation = new Vector<Double>();
        sig = new Sigmoid(Double.valueOf(-0.125), Double.valueOf(sumStations / (availableApplications.size())));
        for (int i = 0; i < availableApplications.size(); ++i) {
            numberofstation
                    .add(kappa.getAt(sig.getAt(Double.valueOf(Application.allApplications.get(i).deviceList.size()))));
        }

        Vector<Double> numberofActiveStation = new Vector<Double>();
        for (int i = 0; i < availableApplications.size(); ++i) {
            double sum = 0.0;
            for (int j = 0; j < availableApplications.get(i).deviceList.size(); j++) {
                Device stat = availableApplications.get(i).deviceList.get(j);
                long time = Timed.getFireCount();
                if (stat.startTime >= time && stat.stopTime >= time) {
                    sum += 1;
                }
                    
            }
            numberofActiveStation.add(sum);
        }

        sumStations = 0.0;
        for (int i = 0; i < numberofActiveStation.size(); ++i) {
            sumStations += numberofActiveStation.get(i);
        }

        sig = new Sigmoid(Double.valueOf(-0.125), Double.valueOf(sumStations / (numberofActiveStation.size())));
        for (int i = 0; i < numberofActiveStation.size(); ++i) {
            double a = numberofActiveStation.get(i);
            double b = sig.getAt(a);
            double c = kappa.getAt(b);
            numberofActiveStation.set(i, c);
        }

        Vector<Double> preferVm = new Vector<Double>();
        sig = new Sigmoid(Double.valueOf(1.0 / 32), Double.valueOf(3));
        for (int i = 0; i < availableApplications.size(); ++i) {
            preferVm.add(kappa
                    .getAt(sig.getAt(Double.valueOf(availableApplications.get(i).instance.arc.getRequiredCPUs()))));
        }

        Vector<Double> preferVmMem = new Vector<Double>();
        sig = new Sigmoid(Double.valueOf(1.0 / 256.0), Double.valueOf(350.0));
        for (int i = 0; i < availableApplications.size(); ++i) {
            preferVmMem.add(kappa.getAt(sig
                    .getAt(Double.valueOf(availableApplications.get(i).instance.arc.getRequiredMemory() / 10000000))));
        }

        Vector<Double> score = new Vector<Double>();
        for (int i = 0; i < price.size(); ++i) {
            Vector<Double> temp = new Vector<Double>();
            temp.add(price.get(i));
            temp.add(numberofstation.get(i));
            temp.add(numberofActiveStation.get(i));
            temp.add(preferVm.get(i));
            temp.add(workload.get(i));
            temp.add(currentprice.get(i));
            score.add(FuzzyIndicators.getAggregation(temp) * 100);
        }

        Vector<Integer> finaldecision = new Vector<Integer>();
        for (int i = 0; i < availableApplications.size(); ++i) {
            finaldecision.add(i);
        }

        for (int i = 0; i < score.size(); ++i) {
            for (int j = 0; j < score.get(i); j++) {
                finaldecision.add(i);
            }
        }

        Collections.shuffle(finaldecision, SeedSyncer.centralRnd);
        int temp = SeedSyncer.centralRnd.nextInt(finaldecision.size());

        return finaldecision.elementAt(temp);
    }
}