package com.example.terrain.validation;

import com.example.terrain.erosion.ErosionParams;
import com.example.terrain.noise.NoiseParams;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerrainValidatorTest {

    private NoiseParams noise(int size, int octaves, double persistence, double lacunarity) {
        return new NoiseParams(size, 1L, octaves, persistence, lacunarity, 0.05);
    }

    @Test
    void acceptsValidNoiseParams() {
        assertDoesNotThrow(() -> TerrainValidator.validateNoise(
                noise(128, 6, 0.5, 2.0)));
    }

    @Test
    void rejectsOutOfRangeResolution() {
        InvalidParamsException tooSmall = assertThrows(InvalidParamsException.class,
                () -> TerrainValidator.validateNoise(noise(8, 6, 0.5, 2.0)));
        InvalidParamsException tooLarge = assertThrows(InvalidParamsException.class,
                () -> TerrainValidator.validateNoise(noise(500, 6, 0.5, 2.0)));
        assertTrue(tooSmall.getMessage().toLowerCase().contains("resolution"));
        assertTrue(tooLarge.getMessage().toLowerCase().contains("resolution"));
    }

    @Test
    void rejectsBadOctaveCount() {
        InvalidParamsException ex = assertThrows(InvalidParamsException.class,
                () -> TerrainValidator.validateNoise(noise(64, 0, 0.5, 2.0)));
        assertTrue(ex.getMessage().toLowerCase().contains("octave"));
    }

    @Test
    void rejectsPersistenceOutOfRange() {
        InvalidParamsException ex = assertThrows(InvalidParamsException.class,
                () -> TerrainValidator.validateNoise(noise(64, 6, 1.5, 2.0)));
        assertTrue(ex.getMessage().contains("persistence"));
    }

    private ErosionParams erosion(double rate) {
        return new ErosionParams(
                1000, 64, 0.05, 1.2, 0.1, rate, 0.02, 4.0,
                1.0, 1.0, 0.01, 3.0, 2, 0.05);
    }

    @Test
    void acceptsValidErosionParams() {
        assertDoesNotThrow(() -> TerrainValidator.validateErosion(erosion(0.2)));
    }

    @Test
    void rejectsNegativeErosionRate() {
        InvalidParamsException ex = assertThrows(InvalidParamsException.class,
                () -> TerrainValidator.validateErosion(erosion(-0.1)));
        assertTrue(ex.getMessage().contains("erosionRate"));
    }

    @Test
    void rejectsNegativeDepositionAndEvaporationRates() {
        assertTrue(assertThrows(InvalidParamsException.class,
                () -> TerrainValidator.validateErosion(
                        new ErosionParams(1000, 64, 0.05, 1.2, -0.1, 0.2,
                                0.02, 4.0, 1.0, 1.0, 0.01, 3.0, 2, 0.05)))
                .getMessage().contains("depositRate"));
        assertTrue(assertThrows(InvalidParamsException.class,
                () -> TerrainValidator.validateErosion(
                        new ErosionParams(1000, 64, 0.05, 1.2, 0.1, 0.2,
                                -0.02, 4.0, 1.0, 1.0, 0.01, 3.0, 2, 0.05)))
                .getMessage().contains("evaporateRate"));
    }

    @Test
    void rejectsIncoherentWaterThresholds() {
        // minWater above startWater would kill every drop immediately.
        ErosionParams bad = new ErosionParams(
                1000, 64, 0.05, 1.2, 0.1, 0.2, 0.02, 4.0,
                1.0, 0.5, 0.8, 3.0, 2, 0.05);
        assertTrue(assertThrows(InvalidParamsException.class,
                () -> TerrainValidator.validateErosion(bad))
                .getMessage().contains("minWater"));
    }
}
