package com.ecosense.app.utils;

/**
 * Ordinary least-squares simple linear regression: y = slope * x + intercept.
 * Implemented from scratch (no libraries).
 */
public class RegressionCalculator {
    private double slope;
    private double intercept;
    private double rSquared;
    private boolean fitted;

    public void fit(double[] x, double[] y) {
        if (x == null || y == null || x.length != y.length || x.length < 2) {
            throw new IllegalArgumentException("Need at least 2 matching data points");
        }
        int n = x.length;
        double meanX = 0, meanY = 0;
        for (int i = 0; i < n; i++) {
            meanX += x[i];
            meanY += y[i];
        }
        meanX /= n;
        meanY /= n;

        double sxx = 0, sxy = 0, syy = 0;
        for (int i = 0; i < n; i++) {
            double dx = x[i] - meanX;
            double dy = y[i] - meanY;
            sxx += dx * dx;
            sxy += dx * dy;
            syy += dy * dy;
        }
        if (sxx < 1e-12) {            // all x identical: flat line through the mean
            slope = 0;
            intercept = meanY;
            rSquared = 0;
        } else {
            slope = sxy / sxx;
            intercept = meanY - slope * meanX;
            rSquared = syy < 1e-12 ? 1.0 : (sxy * sxy) / (sxx * syy);
        }
        fitted = true;
    }

    public double predict(double x) {
        if (!fitted) throw new IllegalStateException("fit() has not been called");
        return slope * x + intercept;
    }

    public double getSlope() { return slope; }
    public double getIntercept() { return intercept; }
    public double getRSquared() { return rSquared; }

    /** Residual standard error: typical distance between the data and the fitted line. */
    public double standardError(double[] x, double[] y) {
        if (!fitted || x.length < 3) return 0;
        double sse = 0;
        for (int i = 0; i < x.length; i++) {
            double r = y[i] - predict(x[i]);
            sse += r * r;
        }
        return Math.sqrt(sse / (x.length - 2));
    }

    /** Human-readable direction using a small dead-band. */
    public String trend(double epsilon) {
        if (slope > epsilon) return "Rising";
        if (slope < -epsilon) return "Falling";
        return "Stable";
    }
}
