package com.example.terrain.service;

/** Basic descriptive statistics over a height field. */
public final class TerrainStats {

    public record Stats(double min, double max, double mean, double sum,
                        double variance, double gradientEnergy) {
    }

    private TerrainStats() {
    }

    public static Stats compute(double[] h, int size) {
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        double sum = 0.0;
        for (double v : h) {
            sum += v;
            if (v < min) {
                min = v;
            }
            if (v > max) {
                max = v;
            }
        }
        double mean = sum / h.length;

        double sq = 0.0;
        for (double v : h) {
            double d = v - mean;
            sq += d * d;
        }
        double variance = sq / h.length;

        // Mean squared magnitude of central-difference gradients. This is the
        // "overall fluctuation amplitude of the surface gradient": it grows when
        // high-frequency detail (more octaves) is added.
        double energy = 0.0;
        long count = 0;
        for (int y = 1; y < size - 1; y++) {
            for (int x = 1; x < size - 1; x++) {
                double gx = (h[y * size + x + 1] - h[y * size + x - 1]) / 2.0;
                double gy = (h[(y + 1) * size + x] - h[(y - 1) * size + x]) / 2.0;
                energy += gx * gx + gy * gy;
                count++;
            }
        }
        energy /= Math.max(1, count);

        return new Stats(min, max, mean, sum, variance, energy);
    }
}
