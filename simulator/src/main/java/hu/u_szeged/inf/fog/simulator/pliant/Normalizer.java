package hu.u_szeged.inf.fog.simulator.pliant;

import java.util.Vector;

public interface Normalizer {

    public Vector<Double> normalizeIncrement(Vector<Double> sourceVector);

    public Vector<Double> normalizeDecrement(Vector<Double> sourceVector);
}