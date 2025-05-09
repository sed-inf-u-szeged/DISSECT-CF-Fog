package hu.u_szeged.inf.fog.simulator.prediction.mobility;

/**
 * This class helps to calculate different type of coefficients.
 */
public class WeightCoefficient {

    /**
     * Calculates the weight of a transition, as: k - i + 1 / sum(m_i), i = 1, 2,
     * ..., k
     *
     * @param iteration  Is the 'i' in the formula above
     * @param transition Is the 'k' in the formulve above
     * @return Weight of the transition based on its historical value (transition)
     */
    public static double transitionWeight(int iteration, int transition) {
        int m = Utils.sumAtoB(1, transition);
        return (transition - iteration + 1.0) / m;
    }

    /**
     * Gives an exponential-like function to represent the weight of direction
     * transitions. If the direction is itself, the function gives a higher value.
     *
     * @param x The direction argument
     * @return The weighted value
     */
    public static double dirWeight(int x) {
        double base = Math.E / Math.PI;
        double exp = -0.2 * x;
        return Math.pow(base, exp);
    }

}
