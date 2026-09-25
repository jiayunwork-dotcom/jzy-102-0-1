package com.example.terrain.noise;

/**
 * Parameters controlling layered fractal-noise terrain generation.
 *
 * @param size         grid edge length; the field is size x size
 * @param seed         master random seed (fully controls the result)
 * @param octaves      number of stacked noise layers
 * @param persistence  amplitude multiplier per layer (0..1)
 * @param lacunarity   frequency multiplier per layer (>1)
 * @param baseFrequency frequency of the first layer
 */
public record NoiseParams(
        int size,
        long seed,
        int octaves,
        double persistence,
        double lacunarity,
        double baseFrequency
) {
}
