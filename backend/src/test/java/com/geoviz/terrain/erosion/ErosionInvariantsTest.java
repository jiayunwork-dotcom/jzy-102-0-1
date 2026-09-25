package com.geoviz.terrain.erosion;

import com.geoviz.terrain.noise.NoiseGenerator;
import com.geoviz.terrain.noise.NoiseParams;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 侵蚀模拟核心不变量测试：
 * 1. 只放大侵蚀率 → 高度方差增大；
 * 2. 固定种子同参数跑两次 → 高度场逐比特一致；
 * 3. 雨滴水量严格单调不升，耗尽即终止，不会"空水"继续侵蚀；
 * 4. 一轮模拟前后整图总高度和只差浮点误差量级（质量守恒）。
 */
class ErosionInvariantsTest {

    private static final int RES = 128;

    private final NoiseGenerator noiseGenerator = new NoiseGenerator();
    private final ErosionSimulator simulator = new ErosionSimulator();

    private double[] baseTerrain() {
        return noiseGenerator.generate(new NoiseParams(RES, 4, 2.0, 0.5, 3.0, 42L));
    }

    /** 除侵蚀率外其余参数完全相同的侵蚀参数。 */
    private static ErosionParams paramsWithErodeRate(double erodeRate) {
        return new ErosionParams(
                20_000,     // dropletCount
                erodeRate,  // erodeRate
                0.3,        // depositRate
                0.02,       // evaporateRate
                4.0,        // gravity
                1.5,        // capacityFactor
                0.01,       // baseFlow
                0.05,       // inertia
                64,         // maxSteps
                0.01,       // minWater
                7L);        // seed
    }

    private static double variance(double[] h) {
        double mean = 0;
        for (double v : h) {
            mean += v;
        }
        mean /= h.length;
        double var = 0;
        for (double v : h) {
            double d = v - mean;
            var += d * d;
        }
        return var / h.length;
    }

    private static double sum(double[] h) {
        double s = 0;
        for (double v : h) {
            s += v;
        }
        return s;
    }

    @Test
    void higherErosionRateYieldsLargerHeightVariance() {
        double[] base = baseTerrain();
        double[] erodedLow = simulator.erode(base, RES, paramsWithErodeRate(0.05), null).heightmap();
        double[] erodedHigh = simulator.erode(base, RES, paramsWithErodeRate(0.9), null).heightmap();

        double varLow = variance(erodedLow);
        double varHigh = variance(erodedHigh);
        assertTrue(varHigh > varLow,
                "侵蚀率调高后高度方差应更大：var(低侵蚀率)=" + varLow + "，var(高侵蚀率)=" + varHigh);
    }

    @Test
    void sameSeedAndParamsReproduceIdenticalHeightmap() {
        double[] base = baseTerrain();
        ErosionParams params = paramsWithErodeRate(0.3);

        double[] first = simulator.erode(base, RES, params, null).heightmap();
        double[] second = simulator.erode(base, RES, params, null).heightmap();

        assertArrayEquals(first, second,
                "固定种子与参数重复模拟，两次高度场必须完全一致（不允许未受种子控制的随机性）");
    }

    @Test
    void dropletWaterIsMonotonicAndDepletionTerminatesDroplet() {
        double[] base = baseTerrain();
        // 蒸发率与阈值调到让步数耗尽前就能蒸干，确保确实有雨滴死于蒸发
        ErosionParams params = new ErosionParams(
                2_000, 0.3, 0.3, 0.05, 4.0, 1.5, 0.01, 0.05, 64, 0.05, 9L);

        List<String> violations = new ArrayList<>();
        AtomicInteger evaporatedCount = new AtomicInteger();
        // 雨滴是逐颗串行模拟的，dropletIndex 变化即说明上一颗已结束
        int[] currentDroplet = {-1};
        double[] previousWater = {Double.POSITIVE_INFINITY};

        DropletListener listener = new DropletListener() {
            @Override
            public void onStep(int dropletIndex, int step, double water, double sediment) {
                if (dropletIndex != currentDroplet[0]) {
                    currentDroplet[0] = dropletIndex;
                    previousWater[0] = Double.POSITIVE_INFINITY;
                }
                if (water < params.minWater()) {
                    violations.add("雨滴 " + dropletIndex + " 在水量 " + water
                            + " 低于阈值 " + params.minWater() + " 后仍在执行侵蚀步");
                }
                if (water > previousWater[0]) {
                    violations.add("雨滴 " + dropletIndex + " 水量从 " + previousWater[0]
                            + " 上升到 " + water + "，违反单调不升");
                }
                previousWater[0] = water;
            }

            @Override
            public void onDropletEnd(int dropletIndex, int steps, double finalWater, String reason) {
                if ("evaporated".equals(reason)) {
                    evaporatedCount.incrementAndGet();
                    if (finalWater >= params.minWater()) {
                        violations.add("雨滴 " + dropletIndex + " 以 evaporated 结束，但末水量 "
                                + finalWater + " 未低于阈值 " + params.minWater());
                    }
                }
            }
        };

        simulator.erode(base, RES, params, listener);

        assertTrue(evaporatedCount.get() > 0,
                "测试参数下应至少有一部分雨滴因蒸发而终止，否则蒸发终止路径未被覆盖");
        assertTrue(violations.isEmpty(), () -> "雨滴水量不变量被破坏：\n" + String.join("\n", violations));
    }

    @Test
    void totalHeightIsConservedUpToFloatingPointError() {
        double[] base = baseTerrain();
        ErosionParams params = paramsWithErodeRate(0.5);

        ErosionResult result = simulator.erode(base, RES, params, null);

        double before = sum(base);
        double after = sum(result.heightmap());
        // 与统计接口交叉验证
        assertEquals(before, result.stats().totalHeightBefore(), 1e-9);
        assertEquals(after, result.stats().totalHeightAfter(), 1e-9);

        double tolerance = 1e-6 * Math.max(1.0, Math.abs(before));
        assertTrue(Math.abs(after - before) <= tolerance,
                "整图总高度和的变化应只有浮点误差量级：before=" + before
                        + "，after=" + after + "，差值=" + (after - before));
    }
}
