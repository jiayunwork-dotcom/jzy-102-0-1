package com.example.terrain.service;

import com.example.terrain.api.dto.ErosionRequest;
import com.example.terrain.api.dto.NoiseRequest;
import com.example.terrain.erosion.BedSurface;
import com.example.terrain.erosion.ErosionParams;
import com.example.terrain.erosion.ErosionSimulator;
import com.example.terrain.noise.NoiseParams;
import com.example.terrain.noise.TerrainGenerator;
import com.example.terrain.validation.InvalidParamsException;
import com.example.terrain.validation.TerrainValidator;
import org.springframework.stereotype.Service;

/**
 * Orchestrates noise generation and erosion: applies default parameter values,
 * converts API DTOs into validated domain parameters, and rebuilds the fixed
 * routing surface from the original noise terrain before each erosion round.
 */
@Service
public class TerrainService {

    // ---- Defaults (match the panel defaults in the UI) ----
    public static final int DEFAULT_SIZE = 128;
    public static final long DEFAULT_SEED = 42L;
    public static final int DEFAULT_OCTAVES = 6;
    public static final double DEFAULT_PERSISTENCE = 0.5;
    public static final double DEFAULT_LACUNARITY = 2.0;
    public static final double DEFAULT_BASE_FREQUENCY = 0.05;

    public static final int DEFAULT_DROPS = 9000;
    public static final int DEFAULT_MAX_STEPS = 64;
    public static final double DEFAULT_INERTIA = 0.05;
    public static final double DEFAULT_CAPACITY = 1.2;
    public static final double DEFAULT_DEPOSIT_RATE = 0.1;
    public static final double DEFAULT_EROSION_RATE = 0.15;
    public static final double DEFAULT_EVAPORATE = 0.02;
    public static final double DEFAULT_GRAVITY = 4.0;
    public static final double DEFAULT_START_SPEED = 1.0;
    public static final double DEFAULT_START_WATER = 1.0;
    public static final double DEFAULT_MIN_WATER = 0.01;
    public static final double DEFAULT_DEPOSIT_RADIUS = 3.0;
    public static final int DEFAULT_SMOOTH_PASSES = 2;
    public static final double DEFAULT_CARVE_FRACTION = 0.05;

    /** Result bundle for an erosion round. */
    public record ErosionResult(double[] heights, NoiseParams noiseParams,
                                ErosionParams erosionParams, long seed) {
    }

    public GeneratedTerrain generate(NoiseRequest request) {
        NoiseParams noiseParams = toNoiseParams(request);
        TerrainValidator.validateNoise(noiseParams);
        double[] heights = TerrainGenerator.generate(noiseParams);
        return new GeneratedTerrain(heights, noiseParams);
    }

    public record GeneratedTerrain(double[] heights, NoiseParams noiseParams) {
    }

    public ErosionResult erode(ErosionRequest request) {
        NoiseParams noiseParams = toNoiseParams(request.noise());
        TerrainValidator.validateNoise(noiseParams);

        if (request.heights() == null || request.heights().length != noiseParams.size()
                * noiseParams.size()) {
            throw new InvalidParamsException(
                    "heights must contain exactly size*size ("
                            + noiseParams.size() * noiseParams.size() + ") values.");
        }
        ErosionParams erosionParams = toErosionParams(request);
        TerrainValidator.validateErosion(erosionParams);

        double[] source = request.heights().clone();

        // The fixed routing surface is always rebuilt from the ORIGINAL generated
        // noise terrain for these noise parameters, never from the current
        // (possibly already eroded) field.
        double[] original = TerrainGenerator.generate(noiseParams);
        double[] routingBed = BedSurface.smooth(original, noiseParams.size(),
                erosionParams.smoothPasses());

        // Reusing the noise seed for drops keeps a fixed rainstorm across rounds,
        // which makes each round add a deterministic increment and stays bounded.
        double[] result = ErosionSimulator.erode(source, routingBed,
                noiseParams.size(), erosionParams, noiseParams.seed());

        return new ErosionResult(result, noiseParams, erosionParams, noiseParams.seed());
    }

    private NoiseParams toNoiseParams(NoiseRequest r) {
        if (r == null) {
            throw new InvalidParamsException("Noise parameters are required.");
        }
        return new NoiseParams(
                orDefault(r.size(), DEFAULT_SIZE),
                r.seed() == null ? DEFAULT_SEED : r.seed(),
                orDefault(r.octaves(), DEFAULT_OCTAVES),
                orDefault(r.persistence(), DEFAULT_PERSISTENCE),
                orDefault(r.lacunarity(), DEFAULT_LACUNARITY),
                orDefault(r.baseFrequency(), DEFAULT_BASE_FREQUENCY)
        );
    }

    private ErosionParams toErosionParams(ErosionRequest r) {
        return new ErosionParams(
                orDefault(r.raindrops(), DEFAULT_DROPS),
                orDefault(r.maxSteps(), DEFAULT_MAX_STEPS),
                orDefault(r.inertia(), DEFAULT_INERTIA),
                orDefault(r.sedimentCapacity(), DEFAULT_CAPACITY),
                orDefault(r.depositRate(), DEFAULT_DEPOSIT_RATE),
                orDefault(r.erosionRate(), DEFAULT_EROSION_RATE),
                orDefault(r.evaporateRate(), DEFAULT_EVAPORATE),
                orDefault(r.gravity(), DEFAULT_GRAVITY),
                orDefault(r.startSpeed(), DEFAULT_START_SPEED),
                orDefault(r.startWater(), DEFAULT_START_WATER),
                orDefault(r.minWater(), DEFAULT_MIN_WATER),
                orDefault(r.depositRadius(), DEFAULT_DEPOSIT_RADIUS),
                orDefault(r.smoothPasses(), DEFAULT_SMOOTH_PASSES),
                orDefault(r.carveFraction(), DEFAULT_CARVE_FRACTION)
        );
    }

    private static int orDefault(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    private static double orDefault(Double value, double fallback) {
        return value == null ? fallback : value;
    }
}
