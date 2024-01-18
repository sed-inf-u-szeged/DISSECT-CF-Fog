package hu.u_szeged.inf.fog.simulator.iot.mobility.forecasting;

public class Backlog {

    final int k;
    LimitedQueue<Integer> directionQueue;
    LimitedQueue<Double> weightQueue;

    public Backlog(int k) {
        this.k = k;
        directionQueue = new LimitedQueue<>(k);
    }

    public LimitedQueue<Double> applyWeights() {
        weightQueue = new LimitedQueue<>(k);
        for (int i = 0; i < directionQueue.size(); i++) {
            weightQueue.add(i, WeightCoefficient.transitionWeight(i + 1, k));
        }
        return weightQueue;
    }

    public void addDirection(Integer v) {
        directionQueue.add(v);
    }

}
