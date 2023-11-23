package hu.u_szeged.inf.fog.simulator.iot.mobility.prediction;

import hu.u_szeged.inf.fog.simulator.iot.Device;
import hu.u_szeged.inf.fog.simulator.iot.mobility.GeoLocation;
import hu.u_szeged.inf.fog.simulator.physical.ComputingAppliance;

/**
 * This class predicts helps to predict the device's next location based on
 * weighted Markov prediction model
 */
public class Predictor {
    final int k;
    final Backlog backlog;
    int prevAngle = -1;
    ProbabilityMatrix probabilityMatrix;

    /**
     * Instantiates a predictor object
     *
     * @param k The maximum number of previous directions to take into consideration
     *          when predicting
     */
    public Predictor(int k) {
        this.k = k;
        this.backlog = new Backlog(k);
        probabilityMatrix = new ProbabilityMatrix();
    }

    /**
     * Updates the backlog of the previous directions of the device.
     *
     * @param oldLocation The previous location of the device
     * @param newLocation The new location of the device
     */
    public void updateBacklog(GeoLocation oldLocation, GeoLocation newLocation) {
        int angle = (int) oldLocation.angle(newLocation);
        backlog.addDirection(angle);
        if (prevAngle != -1) {
            // Update matrix
            probabilityMatrix.updateP(prevAngle, angle);
        }
        prevAngle = angle;
    }

    /**
     * Calculated the predicted direction based on maximum of k previous steps using
     * Markov prediction model and the transition matrix.
     *
     * @return The predicted direction [0, 359]
     */
    public int predictDirection() {
        LimitedQueue<Double> weights = backlog.applyWeights();
        int dirQueueSize = backlog.directionQueue.size();
        double[][][] cache = new double[dirQueueSize][ProbabilityMatrix.POSSIBLE_DIR][ProbabilityMatrix.POSSIBLE_DIR];
        for (int i = 0; i < dirQueueSize; i++) {
            cache[i] = Utils.pow(probabilityMatrix.getTransitionMatrix(), i + 1);
        }
        int predicted = -1;
        double max = Double.MIN_VALUE;
        for (int n = 0; n < ProbabilityMatrix.POSSIBLE_DIR; n++) {
            double product = 1.0;
            for (int i = 0; i < dirQueueSize; i++) {
                // double[][] POW_MATRIX = Utils.pow(probabilityMatrix.getTransitionMatrix(), i
                // + 1);
                double[][] POW_MATRIX = cache[i];
                Integer curr_dir = backlog.directionQueue.get(i);
                product += POW_MATRIX[curr_dir][n] * weights.get(i);
            }
            if (product > max) {
                max = product;
                predicted = n;
            }
        }
        return predicted;
    }

    /**
     * Predicts if a device moving in the predicted direction will connect to a
     * computing appliance or not
     * 
     * @param device    The device that moves
     * @param direction The predicted direction
     * @return true if it will likely connect, false otherwise
     */
    public boolean predictConnection(Device device, int direction, double speed) {
        if (direction < 0) {
            return false;
        }
            
        GeoLocation futureLocation = future(device, direction, speed);
        for (ComputingAppliance ca : ComputingAppliance.allComputingAppliances) {
            double futureDistance = futureLocation.calculateDistance(ca.geoLocation);
            int futureLatency = device.latency + (int) (futureDistance / 1000);
            if (futureDistance <= ca.range) {
                return true;
            }
        }
        return false;
    }

    /**
     * Predicts if a device moving in the predicted direction will disconnect from a
     * computing appliance or not
     * 
     * @param device    The device that moves
     * @param direction The predicted direction
     * @return true if it will likely disconnect, false otherwise
     */
    public boolean predictDisconnection(Device device, int direction, double speed) {
        if (direction < 0) {
            return false;
        }
            
        if (device.application == null) {
            return false;
        }
            
        GeoLocation futureLocation = future(device, direction, speed);
        double futureDistance = futureLocation.calculateDistance(device.application.computingAppliance.geoLocation);
        int futureLatency = device.latency + (int) (futureDistance / 1000);
        return futureDistance > device.application.computingAppliance.range;
    }

    private GeoLocation future(Device device, int direction, double speed) {
        GeoLocation currentPosition = device.mobilityStrategy.currentPosition;
        double distance = speed * device.freq;
        return currentPosition.nextLocation(distance, direction);
    }
}
