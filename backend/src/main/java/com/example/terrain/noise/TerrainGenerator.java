package com.example.terrain.noise;

/**
 * Generates a height field by summing several octaves of value noise.
 *
 * <p>Each successive octave raises the frequency by {@code lacunarity} and
 * lowers the amplitude by {@code persistence}:
 * <pre>
 *     h(x,y) = sum_o persistence^o * noise_o(x * baseFrequency * lacunarity^o)
 * </pre>
 * Each octave gets its own noise lattice derived from the master seed, so extra
 * octaves only add high-frequency detail on top of the lower layers.
 */
public final class TerrainGenerator {

    private TerrainGenerator() {
    }

    public static double[] generate(NoiseParams params) {
        int size = params.size();
        double[] heights = new double[size * size];

        double amplitude = 1.0;
        double frequency = params.baseFrequency();

        for (int octave = 0; octave < params.octaves(); octave++) {
            // Independent, seed-derived lattice for each layer.
            long octaveSeed = (params.seed() + 1) * 7919L + octave * 104729L;
            ValueNoise noise = new ValueNoise(octaveSeed);
            double freq = frequency;
            double amp = amplitude;

            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    heights[y * size + x] += amp * noise.sample(x * freq, y * freq);
                }
            }

            amplitude *= params.persistence();
            frequency *= params.lacunarity();
        }
        return heights;
    }
}
