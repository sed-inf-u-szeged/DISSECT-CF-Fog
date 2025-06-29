package hu.u_szeged.inf.fog.simulator.pliant;

import java.util.Vector;

public class Sigmoid implements Normalizer {

    private double lambda;
    private double shift;

    public Sigmoid() {
        shift = 0.0;
        lambda = 1.0;
    }

    public Sigmoid(Double lambda, Double shift) {
        this.shift = shift;
        this.lambda = lambda;
    }

    public Vector<Double> normalizeIncrement(Vector<Double> sourceVector) {

        Vector<Double> result = new Vector<Double>(sourceVector.size());

        for (int i = 0; i < sourceVector.size(); i++) {
            Double value = null;
            if (sourceVector.get(i) instanceof Double) {
                value = (Double) sourceVector.get(i);
            }
                
            result.add(getAt(value));
        }
        return result;
    }

    public Vector<Double> normalizeDecrement(Vector<Double> sourceVector) {
        Vector<Double> result = new Vector<Double>(sourceVector.size());

        for (int i = 0; i < sourceVector.size(); i++) {
            Double value = null;
            if (sourceVector.get(i) instanceof Double) {
                value = (Double) sourceVector.get(i);
            }
                
            result.add(getAt((-1) * value));
        }
        return result;
    }

    public Double getAt(Double x) {
        return 1.0 / (1.0 + Math.pow(Math.E, (-1.0) * lambda * (x - shift)));
    }
}