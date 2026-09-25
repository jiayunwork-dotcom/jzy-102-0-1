package com.example.terrain.api.dto;

import java.util.List;
import java.util.Map;

/**
 * Result returned for generated/eroded terrain. Statistics (mean, sum,
 * variance and gradient energy) are included so the UI can display what the
 * erosion did and so the core relationships are easy to observe.
 */
public record TerrainResponse(
        int size,
        List<Double> heights,
        long seed,
        Map<String, Object> noiseParams,
        Map<String, Object> erosionParams,
        double mean,
        double sum,
        double variance,
        double gradientEnergy,
        double min,
        double max
) {
}
