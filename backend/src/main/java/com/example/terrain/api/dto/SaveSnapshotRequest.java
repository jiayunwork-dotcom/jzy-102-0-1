package com.example.terrain.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SaveSnapshotRequest(
        String name,
        Integer size,
        double[] heights,
        NoiseRequest noise,
        ErosionSettings erosion
) {
    /** Erosion settings echoed from the UI (optional). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ErosionSettings(
            Integer raindrops, Integer maxSteps, Double inertia, Double sedimentCapacity,
            Double depositRate, Double erosionRate, Double evaporateRate, Double gravity,
            Double startSpeed, Double startWater, Double minWater, Double depositRadius,
            Integer smoothPasses, Double carveFraction
    ) {
    }
}
