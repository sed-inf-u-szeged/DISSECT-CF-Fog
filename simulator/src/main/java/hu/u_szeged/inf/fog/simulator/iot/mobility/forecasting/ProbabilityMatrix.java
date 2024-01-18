package hu.u_szeged.inf.fog.simulator.iot.mobility.forecasting;

import java.util.Arrays;
import org.apache.commons.lang3.ArrayUtils;

/**
 * This class represents the direction transition probabilities.
 */
public class ProbabilityMatrix {
    public static final int POSSIBLE_DIR = 360;
    private static final double STEP_WEIGHT = 0.025;
    private final double[][] P_TRANSITION = new double[POSSIBLE_DIR][POSSIBLE_DIR];
    private final double[] P_TRANSITION_I = new double[POSSIBLE_DIR];

    public ProbabilityMatrix() {
        createP();
    }

    /**
     * Gets the transition matrix
     * 
     * @return The 360x360 transition matrix
     */
    public double[][] getTransitionMatrix() {
        return P_TRANSITION;
    }

    /**
     * Increases the probability of Matrix[row][column] by a predefined weight,
     * while keeping the sum of the row 1.
     * 
     * @param row Row index (eg. current direction)
     * @param col Column index (eg. next direction)
     */
    public void updateP(int row, int col) {
        double[] r = P_TRANSITION[row];
        r[col] += STEP_WEIGHT;
        updateRow(r);
    }

    /**
     * Updates a row of the matrix to maintain the sum of the row 1.
     * 
     * @param row The index of the row to be maintained
     */
    private void updateRow(double[] row) {
        double v = 1 / (1 + STEP_WEIGHT);
        for (int i = 0; i < row.length; i++) {
            row[i] *= v;
        }
    }

    /**
     * Creates the initial probability matrix. The probability matrix is 360x360.
     * Each element means P(dir_x | dir_y) where dir_x is the current direction of
     * the mobile device and dir_y is the previous. Directions closer to each other
     * have higher possibilities initially.
     */
    private void createP() {
        initPi();
        for (int i = 1; i <= POSSIBLE_DIR / 2; i++) {
            P_TRANSITION[i - 1] = offset(P_TRANSITION_I, i);
        }
        for (int i = POSSIBLE_DIR / 2; i < POSSIBLE_DIR; i++) {
            P_TRANSITION[i] = P_TRANSITION[POSSIBLE_DIR - i - 1];
        }
    }

    /**
     * Initiates a row of the matrix. The sum of the elements in a row is 1, and the
     * values follow a bell-like graph (not Gaussian distribution - but similar).
     */
    private void initPi() {
        double initSum = Utils.sumAtoBTransform(1, POSSIBLE_DIR / 2, WeightCoefficient::dirWeight) * 2;
        for (int i = 1; i <= POSSIBLE_DIR / 2; i++) {
            double v = WeightCoefficient.dirWeight(i) / initSum;
            P_TRANSITION_I[i - 1] = v;
            P_TRANSITION_I[POSSIBLE_DIR - i] = v;
        }
    }

    /**
     * Dividing an array into two separate arrays. The first array is in range of
     * [half length - offset, length). The second array is in range [0, half length
     * - offset). Finally, the first array is added at the end of the second array.
     * 
     * @param array     The base array of the operation
     * @param offsetVal Sets the offset value of the algorithm
     * @return The two sub arrays combined
     */
    private double[] offset(double[] array, int offsetVal) {
        int half = array.length / 2;
        double[] first = Arrays.copyOfRange(array, half - offsetVal, array.length);
        double[] second = Arrays.copyOfRange(array, 0, half - offsetVal);
        return ArrayUtils.addAll(first, second);
    }

    @Override
    public String toString() {
        StringBuilder s = new StringBuilder();
        for (double[] doubles : P_TRANSITION) {
            for (double aDouble : doubles) {
                s.append(aDouble);
            }
            s.append("\n");
        }
        return "Probability Matrix:\n" + s;
    }
}
