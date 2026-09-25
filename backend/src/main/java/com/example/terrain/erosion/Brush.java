package com.example.terrain.erosion;

import java.util.ArrayList;
import java.util.List;

/**
 * A normalized, radially symmetric cone brush used to spread sediment changes
 * over grid cells. Deposition uses a wide brush (alluvial fans spread out);
 * erosion concentrates in the single cell under the drop (radius 0), which
 * carves sharp valleys.
 *
 * <p>Weights always sum to 1. Near the border only in-bounds cells are used and
 * the applied weights are renormalized, so boundary handling never creates or
 * destroys sediment (mass conservation).
 */
final class Brush {

    record Offset(int dx, int dy, double weight) {
    }

    private final List<Offset> offsets;

    private Brush(List<Offset> offsets) {
        this.offsets = offsets;
    }

    static Brush cone(double radius) {
        List<Offset> list = new ArrayList<>();
        if (radius <= 0.0) {
            list.add(new Offset(0, 0, 1.0));
            return new Brush(list);
        }
        int r = (int) Math.ceil(radius);
        double rawSum = 0.0;
        List<Offset> tmp = new ArrayList<>();
        for (int dy = -r; dy <= r; dy++) {
            for (int dx = -r; dx <= r; dx++) {
                double d = Math.hypot(dx, dy);
                if (d <= radius) {
                    double w = 1.0 - d / radius; // cone falloff, peaked at center
                    tmp.add(new Offset(dx, dy, w));
                    rawSum += w;
                }
            }
        }
        for (Offset o : tmp) {
            list.add(new Offset(o.dx(), o.dy(), o.weight() / rawSum));
        }
        return new Brush(list);
    }

    /**
     * Add {@code amount} to the height field around the rounded cell (px, py).
     * Renormalizes over the cells that actually fall inside the grid so that the
     * total change equals exactly {@code amount} (no border mass loss).
     */
    void apply(double[] height, int size, double px, double py, double amount) {
        int cx = (int) Math.round(px);
        int cy = (int) Math.round(py);

        double weightSum = 0.0;
        for (Offset o : offsets) {
            int x = cx + o.dx();
            int y = cy + o.dy();
            if (x >= 0 && x < size && y >= 0 && y < size) {
                weightSum += o.weight();
            }
        }
        if (weightSum == 0.0) {
            return;
        }
        for (Offset o : offsets) {
            int x = cx + o.dx();
            int y = cy + o.dy();
            if (x >= 0 && x < size && y >= 0 && y < size) {
                height[y * size + x] += amount * o.weight() / weightSum;
            }
        }
    }
}
