package hu.u_szeged.inf.fog.simulator.pliant;

import java.util.Collections;
import java.util.Vector;

public class Linear implements Normalizer {

    @Override
    public Vector<Double> normalizeIncrement(Vector<Double> sourceVector) {

        Vector<Double> result = new Vector<Double>(sourceVector.size());

        double max = (Double) Collections.max(sourceVector) + 1.0;
        double min = (Double) Collections.min(sourceVector) - 1.0;
        double dist = (double) max - min;
        for (int i = 0; i < sourceVector.size(); i++) {
            result.add(((Double) sourceVector.get(i) - min) / (dist));
        }

        return result;
    }

    @Override
    public Vector<Double> normalizeDecrement(Vector<Double> sourceVector) {
        Vector<Double> result = new Vector<Double>(sourceVector.size());

        double max = (Double) Collections.max(sourceVector) + 1.0;
        double min = (Double) Collections.min(sourceVector) - 1.0;
        double dist = (double) min - max;
        for (int i = 0; i < sourceVector.size(); i++) {
            result.add(((Double) sourceVector.get(i) - max) / (dist));
        }
        return result;
    }
}