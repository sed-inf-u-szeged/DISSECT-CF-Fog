package hu.u_szeged.inf.fog.simulator.iot.mobility.prediction;

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
     * Calculates the sum of integers within an interval
     * 
     * @param A Start of the interval (include)
     * @param B End of the interval (include)
     * @return Sum of elements [A, B]
     */
    public static int sumAtoB(int A, int B) {
        return ((A + B) * (B - A + 1)) / 2;
    }

    /**
     * Calculates the sum of elements within a given interval, while applying a
     * transform function to the elements. Basically, gives the sum of f(x), x :=
     * [A, B].
     * 
     * @param A         Start of the interval (included)
     * @param B         End of the interval (included)
     * @param transform The function that applies to each element (f(x))
     * @return The sum of the transformed elements
     */
    public static double sumAtoBTransform(int A, int B, Function<Integer, Double> transform) {
        return IntStream.rangeClosed(A, B).mapToObj(transform::apply).mapToDouble(Double::doubleValue).sum();
    }

    /**
     * Calculates the power to the i of a matrix
     * 
     * @param M Matrix to be powered
     * @param i Exponent
     * @return M^i
     */
    public static double[][] pow(double[][] M, int i) {
        if (i == 0) {
            return identityM(M);
        }
        double[][] tmp1 = Arrays.stream(M).map(double[]::clone).toArray(double[][]::new);
        double[][] tmp2 = Arrays.stream(M).map(double[]::clone).toArray(double[][]::new);

        for (int j = 1; j < i; j++) {
            tmp1 = strassenMultiplication(tmp1, tmp2);
        }
        return tmp1;
    }

    /**
     * Naive O(n^3) matrix multiplication
     * 
     * @param A First matrix
     * @param B Second matrix
     * @return A * B
     */
    public static double[][] basicMultiplication(double[][] A, double[][] B) {
        double[][] result = new double[A.length][B[0].length];

        for (int row = 0; row < result.length; row++) {
            for (int col = 0; col < result[row].length; col++) {
                result[row][col] = multiplyMatricesCell(A, B, row, col);
            }
        }
        return result;
    }

    /**
     * Calculates the value of a cell during matrix multiplication (row x col
     * composition)
     * 
     * @param A   The left side matrix
     * @param B   The right side matrix
     * @param row The current row
     * @param col The current column
     * @return The value of multiplying row and col
     */
    private static double multiplyMatricesCell(double[][] A, double[][] B, int row, int col) {
        double cell = 0;
        for (int i = 0; i < B.length; i++) {
            cell += A[row][i] * B[i][col];
        }
        return cell;
    }

    /**
     * Matrix multiplication using Strassen algorithm.
     * 
     * @param A - Matrix with dimension of NxN
     * @param B - Matrix with dimension of NxN
     * @return A * B result matrix with dimension of NxN
     */
    public static double[][] strassenMultiplication(double[][] A, double[][] B) {
        if (!canMultiply(A, B)) {
            throw new RuntimeException("Cannot multiply matrices with incompatible dimensions");
        }
        int n = A.length;
        double[][] R = new double[n][n];
        if (n <= LEAF_SIZE) {
            R = basicMultiplication(A, B);
        } else {
            double[][] A11 = new double[n / 2][n / 2];
            double[][] A12 = new double[n / 2][n / 2];
            double[][] A21 = new double[n / 2][n / 2];
            double[][] A22 = new double[n / 2][n / 2];
            double[][] B11 = new double[n / 2][n / 2];
            double[][] B12 = new double[n / 2][n / 2];
            double[][] B21 = new double[n / 2][n / 2];
            double[][] B22 = new double[n / 2][n / 2];

            split(A, A11, 0, 0);
            split(A, A12, 0, n / 2);
            split(A, A21, n / 2, 0);
            split(A, A22, n / 2, n / 2);

            split(B, B11, 0, 0);
            split(B, B12, 0, n / 2);
            split(B, B21, n / 2, 0);
            split(B, B22, n / 2, n / 2);

            double[][] M1 = strassenMultiplication(add(A11, A22), add(B11, B22));
            double[][] M2 = strassenMultiplication(add(A21, A22), B11);
            double[][] M3 = strassenMultiplication(A11, sub(B12, B22));
            double[][] M4 = strassenMultiplication(A22, sub(B21, B11));
            double[][] M5 = strassenMultiplication(add(A11, A12), B22);
            double[][] M6 = strassenMultiplication(sub(A21, A11), add(B11, B12));
            double[][] M7 = strassenMultiplication(sub(A12, A22), add(B21, B22));
            double[][] C11 = add(sub(add(M1, M4), M5), M7);
            double[][] C12 = add(M3, M5);
            double[][] C21 = add(M2, M4);
            double[][] C22 = add(sub(add(M1, M3), M2), M6);

            join(C11, R, 0, 0);
            join(C12, R, 0, n / 2);
            join(C21, R, n / 2, 0);
            join(C22, R, n / 2, n / 2);
        }
        return R;
    }

    /**
     * Split parent matrix into child matrix
     * 
     * @param P  Parent matrix
     * @param C  Child matrix
     * @param iB sub index
     * @param jB sub index
     */
    private static void split(double[][] P, double[][] C, int iB, int jB) {
        for (int i1 = 0, i2 = iB; i1 < C.length; i1++, i2++) {
            for (int j1 = 0, j2 = jB; j1 < C.length; j1++, j2++) {
                C[i1][j1] = P[i2][j2];
            }
        }
    }

    /**
     * Adds two matrices
     * 
     * @param A First matrix
     * @param B Second matrix
     * @return A + B matrix
     */
    private static double[][] add(double[][] A, double[][] B) {
        int n = A.length;
        double[][] C = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                C[i][j] = A[i][j] + B[i][j];
            }
        }
        return C;
    }

    /**
     * Subtract two matrices
     * 
     * @param A First matrix
     * @param B Second matrix
     * @return A - B matrix
     */
    private static double[][] sub(double[][] A, double[][] B) {
        int n = A.length;
        double[][] C = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                C[i][j] = A[i][j] - B[i][j];
            }
        }
            
        return C;
    }

    /**
     * Join a child matrix into a parent matrix
     * 
     * @param C  Child matrix
     * @param P  Parent matrix
     * @param iB Sub index
     * @param jB Sub index
     */
    private static void join(double[][] C, double[][] P, int iB, int jB) {
        for (int i1 = 0, i2 = iB; i1 < C.length; i1++, i2++) {
            for (int j1 = 0, j2 = jB; j1 < C.length; j1++, j2++) {
                P[i2][j2] = C[i1][j1];
            }
        }
    }

    private static double[][] identityM(double[][] M) {
        double[][] I = new double[M.length][M[0].length];
        for (int i = 0; i < M.length; i++) {
            for (int j = 0; j < M[0].length; j++) {
                if (i == j) {
                    I[i][j] = 1;
                }
            }
        }
        return I;
    }

    /**
     * Decides whether two matrices can be multiplied.
     * 
     * @param A First matrix
     * @param B Second matrix
     * @return True if two matrices can be multiplied, false otherwise
     */
    private static boolean canMultiply(double[][] A, double[][] B) {
        return Objects.equals(A.length, B[0].length);
    }
}
