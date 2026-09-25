package com.example.terrain.erosion;

import com.example.terrain.noise.Rng;

/**
 * Particle-based hydraulic erosion on a height field.
 *
 * <p>Each raindrop:
 * <ol>
 *   <li>spawns at a random position;</li>
 *   <li>steers down the steepest slope of the fixed routing surface with some
 *       inertia;</li>
 *   <li>accelerates according to the height it drops and loses water to
 *       evaporation every step (water is therefore strictly non-increasing);</li>
 *   <li>terminates when its water falls to/below the minimum threshold or it
 *       leaves the interior or exhausts its step budget — in every case any
 *       carried sediment is deposited back first;</li>
 *   <li>deposits sediment when over capacity or climbing, otherwise erodes the
 *       ground up to both its spare capacity and a fraction of the local fall.</li>
 * </ol>
 *
 * <p>Erosion is concentrated on the single cell under the drop while deposition
 * is spread over a wide cone, so material is detached from narrow thalwegs and
 * re-deposited diffusely downstream: relief sharpens as the erosion rate grows.
 * All sediment removed from the field is added back through deposition, so the
 * total height sum only changes by floating-point rounding.
 */
public final class ErosionSimulator {

    /** Observer used by tests to assert per-drop water behavior. */
    public interface DropObserver {
        /** Called after evaporation each step with the drop's remaining water. */
        void waterStep(double water, int dropIndex, int step);
    }

    private ErosionSimulator() {
    }

    public static double[] erode(double[] source, double[] routingBed, int size,
                                 ErosionParams p, long seed) {
        return erode(source, routingBed, size, p, seed, null);
    }

    public static double[] erode(double[] source, double[] routingBed, int size,
                                 ErosionParams p, long seed, DropObserver observer) {
        double[] height = source.clone();
        double[] bed = routingBed;
        Rng rng = new Rng(seed);

        // Erosion concentrates at the point cell; deposition fans out widely.
        Brush erodeBrush = Brush.cone(0.0);
        Brush depositBrush = Brush.cone(p.depositRadius());

        for (int drop = 0; drop < p.raindrops(); drop++) {
            // Spawn strictly inside the interior so bilinear samples are valid.
            double px = rng.nextDouble() * (size - 2) + 1;
            double py = rng.nextDouble() * (size - 2) + 1;
            double dirX = 0.0;
            double dirY = 0.0;
            double speed = p.startSpeed();
            double water = p.startWater();
            double sediment = 0.0;

            for (int step = 0; step < p.maxSteps(); step++) {
                int x0 = (int) Math.floor(px);
                int y0 = (int) Math.floor(py);
                if (x0 < 1 || x0 >= size - 1 || y0 < 1 || y0 >= size - 1) {
                    depositBrush.apply(height, size, px, py, sediment);
                    sediment = 0.0;
                    break;
                }

                // Steer using the gradient of the fixed routing surface.
                double gradX = bed[y0 * size + x0 + 1] - bed[y0 * size + x0];
                double gradY = bed[(y0 + 1) * size + x0] - bed[y0 * size + x0];
                double oldHeight = BedSurface.bilinear(bed, size, px, py);

                dirX = dirX * p.inertia() - gradX * (1 - p.inertia());
                dirY = dirY * p.inertia() - gradY * (1 - p.inertia());
                double dirLength = Math.hypot(dirX, dirY);
                if (dirLength != 0.0) {
                    dirX /= dirLength;
                    dirY /= dirLength;
                }

                double nx = px + dirX;
                double ny = py + dirY;
                if (nx < 1 || nx >= size - 1 || ny < 1 || ny >= size - 1) {
                    depositBrush.apply(height, size, px, py, sediment);
                    sediment = 0.0;
                    break;
                }

                double newHeight = BedSurface.bilinear(bed, size, nx, ny);
                double deltaHeight = newHeight - oldHeight; // negative downhill

                speed = Math.sqrt(Math.max(0.0,
                        speed * speed + p.gravity() * -deltaHeight));

                // Evaporate FIRST, so water is monotonically non-increasing and
                // no erosion/deposition happens after the drop should be dead.
                water *= (1.0 - p.evaporateRate());
                if (observer != null) {
                    observer.waterStep(water, drop, step);
                }
                if (water <= p.minWater()) {
                    depositBrush.apply(height, size, px, py, sediment);
                    sediment = 0.0;
                    break;
                }

                double fall = Math.max(0.0, -deltaHeight);
                double capacity = fall * speed * water * p.sedimentCapacity();

                if (sediment > capacity || deltaHeight > 0.0) {
                    // Over capacity or moving uphill: drop some/all sediment.
                    double amount;
                    if (deltaHeight > 0.0) {
                        // Climbing: at least enough to fill the rise, capped by load.
                        amount = Math.min(sediment,
                                Math.max(deltaHeight,
                                        (sediment - capacity) * p.depositRate()));
                    } else {
                        amount = (sediment - capacity) * p.depositRate();
                    }
                    depositBrush.apply(height, size, px, py, amount);
                    sediment -= amount;
                } else {
                    // Spare capacity: erode. Never more than carveFraction of the
                    // local fall, which bounds single-step incision and keeps the
                    // process stable and mass-conserving.
                    double amount = Math.max(0.0,
                            Math.min((capacity - sediment) * p.erosionRate(),
                                    fall * p.carveFraction()));
                    erodeBrush.apply(height, size, px, py, -amount);
                    sediment += amount;
                }

                px = nx;
                py = ny;
            }
            // Drops that die by exhausting their step budget must still return
            // all carried sediment (otherwise mass would silently disappear).
            if (sediment > 1e-12) {
                depositBrush.apply(height, size, px, py, sediment);
            }
        }
        return height;
    }
}
