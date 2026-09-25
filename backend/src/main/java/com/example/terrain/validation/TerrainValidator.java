package com.example.terrain.validation;

import com.example.terrain.erosion.ErosionParams;
import com.example.terrain.noise.NoiseParams;

/**
 * Central parameter validation. Every check runs up front, before noise or
 * erosion computation, so illegal inputs are rejected with a clear reason rather
 * than causing division-by-zero or array-index failures mid-simulation.
 */
public final class TerrainValidator {

    public static final int MIN_SIZE = 16;
    public static final int MAX_SIZE = 200;
    public static final int MIN_OCTAVES = 1;
    public static final int MAX_OCTAVES = 12;
    public static final double MIN_PERSISTENCE = 0.05;
    public static final double MAX_PERSISTENCE = 0.95;
    public static final double MIN_LACUNARITY = 1.05;
    public static final double MAX_LACUNARITY = 4.0;
    public static final double MIN_BASE_FREQUENCY = 0.005;
    public static final double MAX_BASE_FREQUENCY = 0.3;

    public static final int MIN_DROPS = 1;
    public static final int MAX_DROPS = 200_000;
    public static final int MIN_STEPS = 1;
    public static final int MAX_STEPS = 512;

    private TerrainValidator() {
    }

    public static void validateNoise(NoiseParams p) {
        if (p == null) {
            throw new InvalidParamsException("Noise parameters are required.");
        }
        if (p.size() < MIN_SIZE || p.size() > MAX_SIZE) {
            throw new InvalidParamsException(
                    "Resolution size must be between " + MIN_SIZE + " and " + MAX_SIZE
                            + ", got " + p.size() + ".");
        }
        if (p.octaves() < MIN_OCTAVES || p.octaves() > MAX_OCTAVES) {
            throw new InvalidParamsException(
                    "Octaves must be between " + MIN_OCTAVES + " and " + MAX_OCTAVES
                            + ", got " + p.octaves() + ".");
        }
        if (Double.isNaN(p.persistence()) || Double.isInfinite(p.persistence())
                || p.persistence() < MIN_PERSISTENCE || p.persistence() > MAX_PERSISTENCE) {
            throw new InvalidParamsException(
                    "Amplitude decay (persistence) must be between " + MIN_PERSISTENCE
                            + " and " + MAX_PERSISTENCE + ", got " + p.persistence() + ".");
        }
        if (Double.isNaN(p.lacunarity()) || Double.isInfinite(p.lacunarity())
                || p.lacunarity() < MIN_LACUNARITY || p.lacunarity() > MAX_LACUNARITY) {
            throw new InvalidParamsException(
                    "Frequency multiplier (lacunarity) must be between " + MIN_LACUNARITY
                            + " and " + MAX_LACUNARITY + ", got " + p.lacunarity() + ".");
        }
        if (Double.isNaN(p.baseFrequency()) || Double.isInfinite(p.baseFrequency())
                || p.baseFrequency() < MIN_BASE_FREQUENCY || p.baseFrequency() > MAX_BASE_FREQUENCY) {
            throw new InvalidParamsException(
                    "Base frequency must be between " + MIN_BASE_FREQUENCY + " and "
                            + MAX_BASE_FREQUENCY + ", got " + p.baseFrequency() + ".");
        }
    }

    public static void validateErosion(ErosionParams p) {
        if (p == null) {
            throw new InvalidParamsException("Erosion parameters are required.");
        }
        requireRange(p.raindrops(), MIN_DROPS, MAX_DROPS, "raindrops");
        requireRange(p.maxSteps(), MIN_STEPS, MAX_STEPS, "maxSteps");
        requireNonNegativeRate(p.inertia(), "inertia");
        requireNonNegativeRate(p.sedimentCapacity(), "sedimentCapacity");
        requireNonNegativeRate(p.depositRate(), "depositRate");
        requireNonNegativeRate(p.erosionRate(), "erosionRate");
        requireNonNegativeRate(p.evaporateRate(), "evaporateRate");
        requireNonNegativeRate(p.gravity(), "gravity");
        requireNonNegativeRate(p.startSpeed(), "startSpeed");
        requireNonNegativeRate(p.startWater(), "startWater");
        requireNonNegativeRate(p.minWater(), "minWater");
        requireNonNegativeRate(p.carveFraction(), "carveFraction");

        // Structural constraints needed for the physics to be well defined.
        if (p.inertia() >= 1.0) {
            throw new InvalidParamsException(
                    "inertia must be smaller than 1, got " + p.inertia() + ".");
        }
        if (p.evaporateRate() >= 1.0) {
            throw new InvalidParamsException(
                    "evaporateRate must be smaller than 1, got " + p.evaporateRate() + ".");
        }
        if (p.minWater() > p.startWater()) {
            throw new InvalidParamsException(
                    "minWater (" + p.minWater() + ") must not exceed startWater ("
                            + p.startWater() + "), otherwise every drop dies immediately.");
        }
        if (p.depositRadius() < 0 || p.depositRadius() > 10) {
            throw new InvalidParamsException(
                    "depositRadius must be between 0 and 10, got " + p.depositRadius() + ".");
        }
        if (p.smoothPasses() < 0 || p.smoothPasses() > 6) {
            throw new InvalidParamsException(
                    "smoothPasses must be between 0 and 6, got " + p.smoothPasses() + ".");
        }
    }

    private static void requireRange(int value, int min, int max, String name) {
        if (value < min || value > max) {
            throw new InvalidParamsException(
                    name + " must be between " + min + " and " + max + ", got " + value + ".");
        }
    }

    private static void requireNonNegativeRate(double value, String name) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value < 0.0) {
            throw new InvalidParamsException(
                    name + " must be a finite non-negative number, got " + value + ".");
        }
    }
}
