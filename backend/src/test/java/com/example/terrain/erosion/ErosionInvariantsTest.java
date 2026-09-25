package com.example.terrain.erosion;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Core physical invariants of the hydraulic erosion simulation.
 *
 * <p>The three headline requirements are covered first and foremost:
 * erosion-rate vs height-variance ordering, seeded reproducibility, and
 * local mass conservation. Water monotonicity/termination is covered too.
 */
class ErosionInvariantsTest {

    private static final double LOW_RATE = 0.02;
    private static final double HIGH_RATE = 0.30;

    @ParameterizedTest(name = "higher erosion rate => greater height variance (seed {0})")
    @ValueSource(longs = {1, 2, 3, 7, 42})
    void higherErosionRateProducesGreaterVariance(long seed) {
        double[] base = TestFixtures.terrain(seed);
        double[] bed = TestFixtures.bed(base);

        double[] low = ErosionSimulator.erode(base, bed, TestFixtures.SIZE,
                TestFixtures.erosionWithRate(LOW_RATE), seed);
        double[] high = ErosionSimulator.erode(base, bed, TestFixtures.SIZE,
                TestFixtures.erosionWithRate(HIGH_RATE), seed);

        double varLow = TestFixtures.variance(low);
        double varHigh = TestFixtures.variance(high);

        assertTrue(varHigh > varLow,
                "Expected variance(high erosion) > variance(low erosion): "
                        + varHigh + " > " + varLow);
    }

    @Test
    @DisplayName("variance ordering holds across several seeds at once")
    void varianceOrderingAcrossManySeeds() {
        for (long seed = 1; seed <= 12; seed++) {
            double[] base = TestFixtures.terrain(seed);
            double[] bed = TestFixtures.bed(base);
            double varLow = TestFixtures.variance(ErosionSimulator.erode(
                    base, bed, TestFixtures.SIZE,
                    TestFixtures.erosionWithRate(LOW_RATE), seed));
            double varHigh = TestFixtures.variance(ErosionSimulator.erode(
                    base, bed, TestFixtures.SIZE,
                    TestFixtures.erosionWithRate(HIGH_RATE), seed));
            assertTrue(varHigh > varLow,
                    "seed " + seed + ": " + varHigh + " > " + varLow);
        }
    }

    @ParameterizedTest(name = "fixed seed reproduces bit-identical field (seed {0})")
    @ValueSource(longs = {1, 7, 42})
    void fixedSeedReproducesIdenticalField(long seed) {
        double[] base = TestFixtures.terrain(seed);
        double[] bed = TestFixtures.bed(base);

        double[] first = ErosionSimulator.erode(base, bed, TestFixtures.SIZE,
                TestFixtures.erosionWithRate(HIGH_RATE), seed);
        double[] second = ErosionSimulator.erode(base, bed, TestFixtures.SIZE,
                TestFixtures.erosionWithRate(HIGH_RATE), seed);

        // No uncontrolled randomness: the two runs must match exactly.
        assertArrayEquals(first, second);
    }

    @ParameterizedTest(name = "mass is conserved within float epsilon (seed {0})")
    @ValueSource(longs = {1, 2, 3, 7, 42})
    void massConservedToFloatingPointEpsilon(long seed) {
        double[] base = TestFixtures.terrain(seed);
        double[] bed = TestFixtures.bed(base);
        double before = TestFixtures.sum(base);

        double[] eroded = ErosionSimulator.erode(base, bed, TestFixtures.SIZE,
                TestFixtures.erosionWithRate(HIGH_RATE), seed);
        double after = TestFixtures.sum(eroded);

        double tolerance = 1e-6 * Math.max(1.0, Math.abs(before));
        assertEquals(before, after, tolerance,
                "Total height sum changed beyond floating-point error: "
                        + before + " -> " + after);
    }

    @Test
    @DisplayName("raindrop water is strictly non-increasing every step")
    void waterIsNonIncreasing() {
        double[] base = TestFixtures.terrain(5);
        double[] bed = TestFixtures.bed(base);

        final double[] previousWater = new double[9000];
        final boolean[] seen = new boolean[9000];
        final boolean[] violated = {false};

        ErosionSimulator.erode(base, bed, TestFixtures.SIZE,
                TestFixtures.erosionWithRate(HIGH_RATE), 5, (water, dropIndex, step) -> {
                    if (step == 0) {
                        seen[dropIndex] = true;
                        previousWater[dropIndex] = water;
                    } else if (seen[dropIndex]) {
                        if (water > previousWater[dropIndex] + 1e-15) {
                            violated[0] = true;
                        }
                        previousWater[dropIndex] = water;
                    }
                });

        assertTrue(!violated[0], "Water must never increase along a drop's path.");
    }
}
