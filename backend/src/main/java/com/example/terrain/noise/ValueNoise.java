package com.example.terrain.noise;

/**
 * Deterministic 2D value noise on a seeded permutation lattice, with smoothstep
 * interpolation. Every random choice (the lattice-value permutation) derives
 * from the seed via {@link Rng}, so equal seeds always yield equal fields.
 */
public final class ValueNoise {

    private final int[] permutation = new int[512];

    public ValueNoise(long seed) {
        Rng rng = new Rng(seed);
        int[] order = new int[256];
        for (int i = 0; i < 256; i++) {
            order[i] = i;
        }
        // Fisher-Yates shuffle with the seeded generator.
        for (int i = 255; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            int tmp = order[i];
            order[i] = order[j];
            order[j] = tmp;
        }
        for (int i = 0; i < 512; i++) {
            permutation[i] = order[i & 255];
        }
    }

    /** Noise value in [0, 1] at continuous coordinates (x, y). */
    public double sample(double x, double y) {
        int x0 = floor(x);
        int y0 = floor(y);
        double fx = x - x0;
        double fy = y - y0;

        double u = fade(fx);
        double v = fade(fy);

        double n00 = lattice(x0, y0);
        double n10 = lattice(x0 + 1, y0);
        double n01 = lattice(x0, y0 + 1);
        double n11 = lattice(x0 + 1, y0 + 1);

        double nx0 = lerp(n00, n10, u);
        double nx1 = lerp(n01, n11, u);
        return lerp(nx0, nx1, v);
    }

    private double lattice(int ix, int iy) {
        return permutation[(permutation[ix & 255] + iy) & 255] / 255.0;
    }

    private static double fade(double t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static int floor(double v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }
}
