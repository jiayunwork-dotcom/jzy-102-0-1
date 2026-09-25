package com.example.terrain.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record NoiseRequest(
        Integer size,
        Long seed,
        Integer octaves,
        Double persistence,
        Double lacunarity,
        Double baseFrequency
) {
}
