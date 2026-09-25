package com.example.terrain.erosion;

import com.example.terrain.noise.NoiseParams;
import com.example.terrain.noise.TerrainGenerator;

/** Shared helpers and the calibrated default erosion configuration for tests. */
final class TestFixtures {

    static final int SIZE = 96;
    static final int OCTAVES = 6;
    static final double PERSISTENCE = 0.5;
    static final double LACUNARITY = 2.0;
    static final double BASE_FREQUENCY = 0.05;

    private TestFixtures() {
    }

    static NoiseParams noise(long seed) {
        return new NoiseParams(SIZE, seed, OCTAVES, PERSISTENCE, LACUNARITY, BASE_FREQUENCY);
    }

    static double[] terrain(long seed) {
        return TerrainGenerator.generate(noise(seed));
    }

    static double[] bed(double[] terrain) {
        return BedSurface.smooth(terrain, SIZE, 2);
    }

    static ErosionParams erosionWithRate(double erosionRate) {
        return new ErosionParams(
                9000,   // raindrops
                64,     // maxSteps
                0.05,   // inertia
                1.2,    // sedimentCapacity
                0.1,    // depositRate
                erosionRate,
                0.02,   // evaporateRate
                4.0,    // gravity
                1.0,    // startSpeed
                1.0,    // startWater
                0.01,   // minWater
                3.0,    // depositRadius
                2,      // smoothPasses
                0.05    // carveFraction
        );
    }

    static double sum(double[] h) {
        double s = 0.0;
        for (double v : h) {
            s += v;
        }
        return s;
    }

    static double variance(double[] h) {
        double mean = sum(h) / h.length;
        double sq = 0.0;
        for (double v : h) {
            double d = v - mean;
            sq += d * d;
        }
        return sq / h.length;
    }
}
