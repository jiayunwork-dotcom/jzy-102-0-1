package com.example.terrain.noise;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerrainGeneratorTest {

    private static double gradientEnergy(double[] h, int size) {
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
        return energy / count;
    }

    @ParameterizedTest(name = "more octaves => more high-frequency detail (seed {0})")
    @ValueSource(longs = {1, 2, 3, 7, 42})
    void moreOctavesIncreaseGradientEnergy(long seed) {
        int size = 96;
        NoiseParams one = new NoiseParams(size, seed, 1, 0.5, 2.0, 0.05);
        NoiseParams many = new NoiseParams(size, seed, 8, 0.5, 2.0, 0.05);

        double lowDetail = gradientEnergy(TerrainGenerator.generate(one), size);
        double highDetail = gradientEnergy(TerrainGenerator.generate(many), size);

        assertTrue(highDetail > lowDetail,
                "Gradient fluctuation should rise with octave count: "
                        + highDetail + " > " + lowDetail);
    }

    @org.junit.jupiter.api.Test
    void sameSeedProducesIdenticalField() {
        NoiseParams params = new NoiseParams(96, 123L, 6, 0.5, 2.0, 0.05);
        assertArrayEquals(TerrainGenerator.generate(params), TerrainGenerator.generate(params));
    }
}
