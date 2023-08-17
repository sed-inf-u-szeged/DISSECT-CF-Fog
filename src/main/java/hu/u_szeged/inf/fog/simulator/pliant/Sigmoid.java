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

    public Vector<Double> normalizeIncrement(Vector<Double> source_vector) {

        Vector<Double> result = new Vector<Double>(source_vector.size());

        for (int i = 0; i < source_vector.size(); i++) {
            Double value = null;
            if (source_vector.get(i) instanceof Double) {
                value = (Double) source_vector.get(i);
            }
                
            result.add(getAt(value));
        }
        return result;
    }

    public Vector<Double> normalizeDecrement(Vector<Double> source_vector) {
        Vector<Double> result = new Vector<Double>(source_vector.size());

        for (int i = 0; i < source_vector.size(); i++) {
            Double value = null;
            if (source_vector.get(i) instanceof Double) {
                value = (Double) source_vector.get(i);
            }
                
            result.add(getAt((-1) * value));
        }
        return result;
    }

    public Double getAt(Double x) {
        return 1.0 / (1.0 + Math.pow(Math.E, (-1.0) * lambda * (x - shift)));
    }
}