package com.example.terrain.snapshot;

import java.util.Map;

/**
 * A named, in-memory snapshot of a terrain together with the parameters used to
 * generate and erode it. Keeping the original noise parameters lets the service
 * rebuild the fixed routing surface when an eroded snapshot is loaded.
 *
 * @param name          snapshot name (unique key)
 * @param size          grid edge length
 * @param heights       row-major height field
 * @param noiseParams   noise generation parameters
 * @param erosionParams erosion parameters of the last round (may be null)
 * @param createdAt     creation timestamp (epoch millis)
 */
public record Snapshot(
        String name,
        int size,
        double[] heights,
        Map<String, Object> noiseParams,
        Map<String, Object> erosionParams,
        long createdAt
) {
}
