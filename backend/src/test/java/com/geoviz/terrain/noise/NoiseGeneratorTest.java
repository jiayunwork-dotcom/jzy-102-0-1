package com.geoviz.terrain.noise;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 噪声生成器测试：层数越多高频细节越多（以地表梯度整体波动幅度衡量）、
 * 同种子结果可复现、输出尺寸与非负性。
 */
class NoiseGeneratorTest {

    private static final int RES = 128;

    private final NoiseGenerator generator = new NoiseGenerator();

    /** 地表梯度幅值（中心差分）的标准差，刻画梯度的整体波动幅度。 */
    private static double gradientStd(double[] h, int res) {
        double[] magnitudes = new double[(res - 2) * (res - 2)];
        int k = 0;
        for (int y = 1; y < res - 1; y++) {
            for (int x = 1; x < res - 1; x++) {
                double gx = (h[y * res + x + 1] - h[y * res + x - 1]) / 2.0;
                double gy = (h[(y + 1) * res + x] - h[(y - 1) * res + x]) / 2.0;
                magnitudes[k++] = Math.hypot(gx, gy);
            }
        }
        double mean = 0;
        for (double m : magnitudes) {
            mean += m;
        }
        mean /= magnitudes.length;
        double var = 0;
        for (double m : magnitudes) {
            double d = m - mean;
            var += d * d;
        }
        return Math.sqrt(var / magnitudes.length);
    }

    @Test
    void moreOctavesIncreaseGradientVariation() {
        double[] few = generator.generate(new NoiseParams(RES, 1, 2.0, 0.5, 3.0, 42L));
        double[] many = generator.generate(new NoiseParams(RES, 8, 2.0, 0.5, 3.0, 42L));

        double stdFew = gradientStd(few, RES);
        double stdMany = gradientStd(many, RES);
        assertTrue(stdMany > stdFew,
                "层数增多后地表梯度波动幅度应上升：std(1 层)=" + stdFew + "，std(8 层)=" + stdMany);
    }

    @Test
    void sameSeedReproducesIdenticalNoise() {
        NoiseParams params = new NoiseParams(RES, 5, 2.0, 0.5, 3.0, 42L);
        assertArrayEquals(generator.generate(params), generator.generate(params),
                "同参数同种子的噪声输出必须完全一致");
    }

    @Test
    void outputHasExpectedSizeAndNonNegativeHeights() {
        double[] h = generator.generate(new NoiseParams(RES, 5, 2.0, 0.5, 3.0, 42L));
        assertEquals(RES * RES, h.length);
        for (double v : h) {
            assertTrue(v >= 0 && Double.isFinite(v), "高度值必须非负且有限，实际: " + v);
        }
    }
}
