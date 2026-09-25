package com.example.terrain.erosion;

/**
 * The smoothed "routing surface". Drops read slopes, directions and height
 * drops from this surface rather than the actively eroding field. Because the
 * routing surface stays fixed for the lifetime of a generated terrain (it is
 * rebuilt only when brand-new noise terrain is generated), carving a narrow
 * trench cannot attract more flow into itself: the incision feedback is
 * removed, so repeated erosion rounds remain bounded and reproducible.
 */
public final class BedSurface {

    private BedSurface() {
    }

    /** Repeated 3x3 normalized box blur (a cheap Gaussian approximation). */
    public static double[] smooth(double[] src, int size, int passes) {
        double[] current = src.clone();
        for (int p = 0; p < passes; p++) {
            double[] next = new double[current.length];
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    double sum = 0.0;
                    int count = 0;
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dx = -1; dx <= 1; dx++) {
                            int xx = x + dx;
                            int yy = y + dy;
                            if (xx >= 0 && xx < size && yy >= 0 && yy < size) {
                                sum += current[yy * size + xx];
                                count++;
                            }
                        }
                    }
                    next[y * size + x] = sum / count;
                }
            }
            current = next;
        }
        return current;
    }

    /** Bilinearly interpolated bed height at fractional position (px, py). */
    public static double bilinear(double[] bed, int size, double px, double py) {
        int x0 = (int) Math.floor(px);
        int y0 = (int) Math.floor(py);
        double fx = px - x0;
        double fy = py - y0;
        double h00 = bed[y0 * size + x0];
        double h10 = bed[y0 * size + x0 + 1];
        double h01 = bed[(y0 + 1) * size + x0];
        double h11 = bed[(y0 + 1) * size + x0 + 1];
        return h00 * (1 - fx) * (1 - fy)
                + h10 * fx * (1 - fy)
                + h01 * (1 - fx) * fy
                + h11 * fx * fy;
    }
}
