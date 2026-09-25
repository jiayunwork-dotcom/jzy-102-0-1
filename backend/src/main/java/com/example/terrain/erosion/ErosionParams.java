package com.example.terrain.erosion;

/**
 * Parameters for one round of hydraulic erosion.
 *
 * <p>Per raindrop: it spawns at a random point, flows down the steepest slope of
 * a smoothed routing surface, gains speed from the height drop, loses water to
 * evaporation, and either erodes (carries sediment) or deposits it depending on
 * its transport capacity.
 *
 * @param raindrops       number of drops simulated this round
 * @param maxSteps        maximum lifetime (steps) of one drop
 * @param inertia         direction inertia in [0,1)
 * @param sedimentCapacity capacity factor (capacity = fall * speed * water * K)
 * @param depositRate     fraction of excess sediment deposited per step
 * @param erosionRate     fraction of spare capacity used to erode per step
 * @param evaporateRate   fraction of water lost per step
 * @param gravity         speed gain factor from the height drop
 * @param startSpeed      initial drop speed
 * @param startWater      initial drop water
 * @param minWater        water at/below which the drop terminates
 * @param depositRadius   radius of the diffuse deposition brush
 * @param smoothPasses    box-blur passes building the routing surface
 * @param carveFraction   max fraction of a step's fall that may be carved
 */
public record ErosionParams(
        int raindrops,
        int maxSteps,
        double inertia,
        double sedimentCapacity,
        double depositRate,
        double erosionRate,
        double evaporateRate,
        double gravity,
        double startSpeed,
        double startWater,
        double minWater,
        double depositRadius,
        int smoothPasses,
        double carveFraction
) {
}
