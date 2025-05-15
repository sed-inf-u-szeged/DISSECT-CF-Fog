package hu.u_szeged.inf.fog.simulator.distributed_ledger.utils;

import org.apache.commons.math3.analysis.interpolation.LinearInterpolator;
import org.apache.commons.math3.analysis.polynomials.PolynomialSplineFunction;

public class InterpolationUtils {

    private InterpolationUtils(){}
    private static final LinearInterpolator interpolator = new LinearInterpolator();

    /**
     * Generates a polynomial spline function for the given data points.
     *
     * @param xValues The x-axis values (independent variable).
     * @param yValues The y-axis values (dependent variable).
     * @return A PolynomialSplineFunction representing the interpolation.
     */
    public static PolynomialSplineFunction createSplineFunction(double[] xValues, double[] yValues) {
        return interpolator.interpolate(xValues, yValues);
    }

    /**
     * Interpolates a value for the given input using the provided spline function.
     *
     * @param splineFunction The spline function created from data points.
     * @param xValue         The input value to interpolate.
     * @param xRangeStart    The minimum x-value allowed.
     * @param xRangeEnd      The maximum x-value allowed.
     * @return The interpolated y-value.
     */
    public static double interpolate(PolynomialSplineFunction splineFunction, double xValue, double xRangeStart, double xRangeEnd) {
        if (xValue < xRangeStart || xValue > xRangeEnd) {
            throw new IllegalArgumentException("Value out of range. Must be between " + xRangeStart + " and " + xRangeEnd + ".");
        }
        return splineFunction.value(xValue);
    }

}
