package com.example.terrain.erosion;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Once a drop's water evaporates to the threshold it must terminate; it must
 * not continue eroding. We assert this structurally by making minWater equal
 * to startWater (so the very first evaporation kills every drop) and checking
 * that each observed drop lives at most one erosion-eligible step and the
 * resulting field differs from the source only through terminal deposition,
 * i.e. nothing is eroded (net sediment detachment is non-positive).
 */
class WaterTerminationTest {

    @Test
    void dropsDieImmediatelyWhenWaterStartsAtThreshold() {
        int size = 64;
        double[] base = new double[size * size];
        // Gentle ramp so slopes exist and drops would otherwise erode.
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                base[y * size + x] = (double) (x + y) / (2 * size);
            }
        }
        double[] bed = BedSurface.smooth(base, size, 1);

        // minWater == startWater: after the first evaporation, water <= minWater.
        ErosionParams params = new ErosionParams(
                2000, 64, 0.05, 1.2, 0.1, 0.9, 0.02, 4.0,
                1.0, 1.0, 1.0, 3.0, 1, 0.05);

        Set<Integer> stepsObserved = new HashSet<>();
        double[] result = ErosionSimulator.erode(base, bed, size, params, 7,
                (water, dropIndex, step) -> {
                    synchronized (stepsObserved) {
                        stepsObserved.add(step);
                    }
                });

        // Every drop is observed at step 0 (then evaporates below threshold and
        // stops), so no step index >= 1 is ever reached.
        assertTrue(stepsObserved.stream().allMatch(step -> step == 0),
                "No drop should survive past its first step when minWater=startWater");

        // No erosion possible: result equals source (no sediment was ever picked
        // up, nothing deposited at step 0 before death).
        for (int i = 0; i < result.length; i++) {
            assertTrue(Math.abs(result[i] - base[i]) < 1e-12,
                    "Dead drops must not change the terrain; diff at " + i);
        }
    }
}
