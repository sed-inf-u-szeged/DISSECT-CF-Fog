package hu.u_szeged.inf.fog.simulator.iot.mobility.forecasting;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.IntStream;

/**
 * Helper class for useful operations regarding the direction prediction (eg.:
 * Matrix multiplication)
 */
public class Utils {

    private static final int LEAF_SIZE = 120;

    /**
     * Calculates the sum of integers within an interval.
     *
     * @param a Start of the interval (include)
     * @param b End of the interval (include)
     * @return Sum of elements [A, B]
     */
    public static int sumAtoB(int a, int b) {
        return ((a + b) * (b - a + 1)) / 2;
    }

    /**
     * Calculates the sum of elements within a given interval, while applying a
     * transform function to the elements. Basically, gives the sum of f(x), x :=
     * [A, B].
     *
     * @param a         Start of the interval (included)
     * @param b         End of the interval (included)
     * @param transform The function that applies to each element (f(x))
     * @return The sum of the transformed elements
     */
    public static double sumIntToIntTransform(int a, int b, Function<Integer, Double> transform) {
        return IntStream.rangeClosed(a, b).mapToObj(transform::apply).mapToDouble(Double::doubleValue).sum();
    }

    /**
     * Calculates the power to the i of a matrix.
     *
     * @param m Matrix to be powered
     * @param i Exponent
     * @return M^i
     */
    public static double[][] pow(double[][] m, int i) {
        if (i == 0) {
            return identityM(m);
        }
        double[][] tmp1 = Arrays.stream(m).map(double[]::clone).toArray(double[][]::new);
        double[][] tmp2 = Arrays.stream(m).map(double[]::clone).toArray(double[][]::new);

        for (int j = 1; j < i; j++) {
            tmp1 = strassenMultiplication(tmp1, tmp2);
        }
        return tmp1;
    }

    /**
     * Naive O(n^3) matrix multiplication.
     *
     * @param a First matrix
     * @param b Second matrix
     * @return A * B
     */
    public static double[][] basicMultiplication(double[][] a, double[][] b) {
        double[][] result = new double[a.length][b[0].length];

        for (int row = 0; row < result.length; row++) {
            for (int col = 0; col < result[row].length; col++) {
                result[row][col] = multiplyMatricesCell(a, b, row, col);
            }
        }
        return result;
    }

    /**
     * Calculates the value of a cell during matrix multiplication (row x col
     * composition).
     *
     * @param a   The left side matrix
     * @param b   The right side matrix
     * @param row The current row
     * @param col The current column
     * @return The value of multiplying row and col
     */
    private static double multiplyMatricesCell(double[][] a, double[][] b, int row, int col) {
        double cell = 0;
        for (int i = 0; i < b.length; i++) {
            cell += a[row][i] * b[i][col];
        }
        return cell;
    }

    /**
     * Matrix multiplication using Strassen algorithm.
     *
     * @param a - Matrix with dimension of NxN
     * @param b - Matrix with dimension of NxN
     * @return A * B result matrix with dimension of NxN
     */
    public static double[][] strassenMultiplication(double[][] a, double[][] b) {
        if (!canMultiply(a, b)) {
            throw new RuntimeException("Cannot multiply matrices with incompatible dimensions");
        }
        int n = a.length;
        double[][] r = new double[n][n];
        if (n <= LEAF_SIZE) {
            r = basicMultiplication(a, b);
        } else {
            double[][] a11 = new double[n / 2][n / 2];
            double[][] a12 = new double[n / 2][n / 2];
            double[][] a21 = new double[n / 2][n / 2];
            double[][] a22 = new double[n / 2][n / 2];
            
            split(a, a11, 0, 0);
            split(a, a12, 0, n / 2);
            split(a, a21, n / 2, 0);
            split(a, a22, n / 2, n / 2);

            double[][] b11 = new double[n / 2][n / 2];
            double[][] b12 = new double[n / 2][n / 2];
            double[][] b21 = new double[n / 2][n / 2];
            double[][] b22 = new double[n / 2][n / 2];
            
            split(b, b11, 0, 0);
            split(b, b12, 0, n / 2);
            split(b, b21, n / 2, 0);
            split(b, b22, n / 2, n / 2);

            double[][] m1 = strassenMultiplication(add(a11, a22), add(b11, b22));
            double[][] m2 = strassenMultiplication(add(a21, a22), b11);
            double[][] m3 = strassenMultiplication(a11, sub(b12, b22));
            double[][] m4 = strassenMultiplication(a22, sub(b21, b11));
            double[][] m5 = strassenMultiplication(add(a11, a12), b22);
            double[][] m6 = strassenMultiplication(sub(a21, a11), add(b11, b12));
            double[][] m7 = strassenMultiplication(sub(a12, a22), add(b21, b22));
            double[][] c11 = add(sub(add(m1, m4), m5), m7);
            double[][] c12 = add(m3, m5);
            double[][] c21 = add(m2, m4);
            double[][] c22 = add(sub(add(m1, m3), m2), m6);

            join(c11, r, 0, 0);
            join(c12, r, 0, n / 2);
            join(c21, r, n / 2, 0);
            join(c22, r, n / 2, n / 2);
        }
        return r;
    }

    /**
     * Split parent matrix into child matrix.
     *
     * @param p  Parent matrix
     * @param c  Child matrix
     * @param ib sub index
     * @param jb sub index
     */
    private static void split(double[][] p, double[][] c, int ib, int jb) {
        for (int i1 = 0, i2 = ib; i1 < c.length; i1++, i2++) {
            for (int j1 = 0, j2 = jb; j1 < c.length; j1++, j2++) {
                c[i1][j1] = p[i2][j2];
            }
        }
    }

    /**
     * Adds two matrices.
     *
     * @param a First matrix
     * @param b Second matrix
     * @return A + B matrix
     */
    private static double[][] add(double[][] a, double[][] b) {
        int n = a.length;
        double[][] c = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                c[i][j] = a[i][j] + b[i][j];
            }
        }
        return c;
    }

    /**
     * Subtract two matrices.
     *
     * @param a First matrix
     * @param b Second matrix
     * @return A - B matrix
     */
    private static double[][] sub(double[][] a, double[][] b) {
        int n = a.length;
        double[][] c = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                c[i][j] = a[i][j] - b[i][j];
            }
        }
            
        return c;
    }

    /**
     * Join a child matrix into a parent matrix.
     *
     * @param c  Child matrix
     * @param p  Parent matrix
     * @param ib Sub index
     * @param jb Sub index
     */
    private static void join(double[][] c, double[][] p, int ib, int jb) {
        for (int i1 = 0, i2 = ib; i1 < c.length; i1++, i2++) {
            for (int j1 = 0, j2 = jb; j1 < c.length; j1++, j2++) {
                p[i2][j2] = c[i1][j1];
            }
        }
    }

    private static double[][] identityM(double[][] m) {
        double[][] id = new double[m.length][m[0].length];
        for (int i = 0; i < m.length; i++) {
            for (int j = 0; j < m[0].length; j++) {
                if (i == j) {
                    id[i][j] = 1;
                }
            }
        }
        return id;
    }

    /**
     * Decides whether two matrices can be multiplied.
     *
     * @param a First matrix
     * @param b Second matrix
     * @return True if two matrices can be multiplied, false otherwise
     */
    private static boolean canMultiply(double[][] a, double[][] b) {
        return Objects.equals(a.length, b[0].length);
    }
}
