package hu.u_szeged.inf.fog.simulator.pliant;

import java.util.Collections;
import java.util.Vector;

public class Linear implements Normalizer {

    @Override
    public Vector<Double> normalizeIncrement(Vector<Double> source_vector) {

        Vector<Double> result = new Vector<Double>(source_vector.size());

        double max = (Double) Collections.max(source_vector) + 1.0;
        double min = (Double) Collections.min(source_vector) - 1.0;
        double dist = (double) max - min;
        for (int i = 0; i < source_vector.size(); i++) {
            result.add(((Double) source_vector.get(i) - min) / (dist));
        }

        return result;
    }

    @Override
    public Vector<Double> normalizeDecrement(Vector<Double> source_vector) {
        Vector<Double> result = new Vector<Double>(source_vector.size());

        double max = (Double) Collections.max(source_vector) + 1.0;
        double min = (Double) Collections.min(source_vector) - 1.0;
        double dist = (double) min - max;
        for (int i = 0; i < source_vector.size(); i++) {
            result.add(((Double) source_vector.get(i) - max) / (dist));
        }
        return result;
    }
}