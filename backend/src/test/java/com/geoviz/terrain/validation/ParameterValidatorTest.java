package com.geoviz.terrain.validation;

import com.geoviz.terrain.erosion.ErosionParams;
import com.geoviz.terrain.noise.NoiseParams;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 参数校验测试：非法参数必须在计算之前被带原因地挡下。
 */
class ParameterValidatorTest {

    private static NoiseParams validNoise() {
        return new NoiseParams(128, 5, 2.0, 0.5, 3.0, 42L);
    }

    private static ErosionParams validErosion() {
        return new ErosionParams(10_000, 0.3, 0.3, 0.02, 4.0, 1.5, 0.01, 0.05, 64, 0.01, 7L);
    }

    private static void assertRejectedWithReason(Runnable r, String keyword) {
        InvalidParameterException e = assertThrows(InvalidParameterException.class, r::run);
        assertTrue(e.getMessage().contains(keyword),
                "错误信息应包含「" + keyword + "」，实际: " + e.getMessage());
    }

    @Test
    void validParamsAreAccepted() {
        assertDoesNotThrow(() -> ParameterValidator.validate(validNoise()));
        assertDoesNotThrow(() -> ParameterValidator.validate(validErosion()));
        assertDoesNotThrow(() -> ParameterValidator.validateHeightmap(16, new double[16 * 16]));
    }

    @Test
    void resolutionOutOfRangeIsRejected() {
        NoiseParams tooSmall = new NoiseParams(8, 5, 2.0, 0.5, 3.0, 42L);
        NoiseParams tooLarge = new NoiseParams(1024, 5, 2.0, 0.5, 3.0, 42L);
        assertRejectedWithReason(() -> ParameterValidator.validate(tooSmall), "resolution");
        assertRejectedWithReason(() -> ParameterValidator.validate(tooLarge), "resolution");
        assertRejectedWithReason(() -> ParameterValidator.validateHeightmap(4, new double[16]), "resolution");
    }

    @Test
    void octavesOutOfRangeIsRejected() {
        NoiseParams zero = new NoiseParams(128, 0, 2.0, 0.5, 3.0, 42L);
        NoiseParams tooMany = new NoiseParams(128, 99, 2.0, 0.5, 3.0, 42L);
        assertRejectedWithReason(() -> ParameterValidator.validate(zero), "octaves");
        assertRejectedWithReason(() -> ParameterValidator.validate(tooMany), "octaves");
    }

    @Test
    void persistenceOutOfRangeIsRejected() {
        NoiseParams zero = new NoiseParams(128, 5, 2.0, 0.0, 3.0, 42L);
        NoiseParams one = new NoiseParams(128, 5, 2.0, 1.0, 3.0, 42L);
        NoiseParams negative = new NoiseParams(128, 5, 2.0, -0.5, 3.0, 42L);
        assertRejectedWithReason(() -> ParameterValidator.validate(zero), "persistence");
        assertRejectedWithReason(() -> ParameterValidator.validate(one), "persistence");
        assertRejectedWithReason(() -> ParameterValidator.validate(negative), "persistence");
    }

    @Test
    void lacunarityOutOfRangeIsRejected() {
        NoiseParams belowOne = new NoiseParams(128, 5, 0.5, 0.5, 3.0, 42L);
        assertRejectedWithReason(() -> ParameterValidator.validate(belowOne), "lacunarity");
    }

    @Test
    void negativeErosionRatesAreRejected() {
        ErosionParams badErode = new ErosionParams(1000, -0.1, 0.3, 0.02, 4.0, 1.5, 0.01, 0.05, 64, 0.01, 7L);
        ErosionParams badDeposit = new ErosionParams(1000, 0.3, -0.3, 0.02, 4.0, 1.5, 0.01, 0.05, 64, 0.01, 7L);
        ErosionParams badEvap = new ErosionParams(1000, 0.3, 0.3, -0.02, 4.0, 1.5, 0.01, 0.05, 64, 0.01, 7L);
        assertRejectedWithReason(() -> ParameterValidator.validate(badErode), "erodeRate");
        assertRejectedWithReason(() -> ParameterValidator.validate(badDeposit), "depositRate");
        assertRejectedWithReason(() -> ParameterValidator.validate(badEvap), "evaporateRate");
    }

    @Test
    void otherInvalidErosionParamsAreRejected() {
        ErosionParams noDroplets = new ErosionParams(0, 0.3, 0.3, 0.02, 4.0, 1.5, 0.01, 0.05, 64, 0.01, 7L);
        ErosionParams badGravity = new ErosionParams(1000, 0.3, 0.3, 0.02, -1.0, 1.5, 0.01, 0.05, 64, 0.01, 7L);
        ErosionParams badCapacity = new ErosionParams(1000, 0.3, 0.3, 0.02, 4.0, 0.0, 0.01, 0.05, 64, 0.01, 7L);
        ErosionParams badBaseFlow = new ErosionParams(1000, 0.3, 0.3, 0.02, 4.0, 1.5, -0.01, 0.05, 64, 0.01, 7L);
        ErosionParams badMinWater = new ErosionParams(1000, 0.3, 0.3, 0.02, 4.0, 1.5, 0.01, 0.05, 64, 0.0, 7L);
        assertRejectedWithReason(() -> ParameterValidator.validate(noDroplets), "dropletCount");
        assertRejectedWithReason(() -> ParameterValidator.validate(badGravity), "gravity");
        assertRejectedWithReason(() -> ParameterValidator.validate(badCapacity), "capacityFactor");
        assertRejectedWithReason(() -> ParameterValidator.validate(badBaseFlow), "baseFlow");
        assertRejectedWithReason(() -> ParameterValidator.validate(badMinWater), "minWater");
    }

    @Test
    void heightmapLengthMismatchIsRejected() {
        assertRejectedWithReason(
                () -> ParameterValidator.validateHeightmap(32, new double[32 * 32 + 1]), "高度场长度");
        assertRejectedWithReason(
                () -> ParameterValidator.validateHeightmap(32, null), "heightmap");
        double[] withNaN = new double[32 * 32];
        withNaN[100] = Double.NaN;
        assertRejectedWithReason(
                () -> ParameterValidator.validateHeightmap(32, withNaN), "非法数值");
    }
}
