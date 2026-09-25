package com.example.terrain.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ErosionRequest(
        int size,
        double[] heights,
        NoiseRequest noise,
        Integer raindrops,
        Integer maxSteps,
        Double inertia,
        Double sedimentCapacity,
        Double depositRate,
        Double erosionRate,
        Double evaporateRate,
        Double gravity,
        Double startSpeed,
        Double startWater,
        Double minWater,
        Double depositRadius,
        Integer smoothPasses,
        Double carveFraction
) {
}
