package hu.u_szeged.inf.fog.simulator.iot.mobility.forecasting;

public class Backlog {

    final int kk;
    LimitedQueue<Integer> directionQueue;
    LimitedQueue<Double> weightQueue;

    public Backlog(int k) {
        this.kk = k;
        directionQueue = new LimitedQueue<>(k);
    }

    public LimitedQueue<Double> applyWeights() {
        weightQueue = new LimitedQueue<>(kk);
        for (int i = 0; i < directionQueue.size(); i++) {
            weightQueue.add(i, WeightCoefficient.transitionWeight(i + 1, kk));
        }
        return weightQueue;
    }

    public void addDirection(Integer v) {
        directionQueue.add(v);
    }

}
