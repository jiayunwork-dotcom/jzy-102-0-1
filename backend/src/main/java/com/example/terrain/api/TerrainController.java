package com.example.terrain.api;

import com.example.terrain.api.dto.NoiseRequest;
import com.example.terrain.api.dto.TerrainResponse;
import com.example.terrain.erosion.ErosionParams;
import com.example.terrain.noise.NoiseParams;
import com.example.terrain.service.TerrainService;
import com.example.terrain.service.TerrainService.GeneratedTerrain;
import com.example.terrain.service.TerrainStats;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/terrain")
public class TerrainController {

    private final TerrainService service;

    public TerrainController(TerrainService service) {
        this.service = service;
    }

    @PostMapping("/generate")
    public TerrainResponse generate(@RequestBody NoiseRequest request) {
        GeneratedTerrain generated = service.generate(request);
        return buildResponse(generated.heights(), generated.noiseParams(), null);
    }

    static TerrainResponse buildResponse(double[] heights, NoiseParams noise,
                                         ErosionParams erosion) {
        TerrainStats.Stats stats = TerrainStats.compute(heights, noise.size());
        List<Double> list = boxed(heights);
        return new TerrainResponse(
                noise.size(),
                list,
                noise.seed(),
                noiseMap(noise),
                erosion == null ? null : erosionMap(erosion),
                stats.mean(),
                stats.sum(),
                stats.variance(),
                stats.gradientEnergy(),
                stats.min(),
                stats.max()
        );
    }

    private static List<Double> boxed(double[] h) {
        Double[] boxed = new Double[h.length];
        for (int i = 0; i < h.length; i++) {
            boxed[i] = h[i];
        }
        return List.of(boxed);
    }

    static Map<String, Object> noiseMap(NoiseParams n) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("size", n.size());
        m.put("seed", n.seed());
        m.put("octaves", n.octaves());
        m.put("persistence", n.persistence());
        m.put("lacunarity", n.lacunarity());
        m.put("baseFrequency", n.baseFrequency());
        return m;
    }

    static Map<String, Object> erosionMap(ErosionParams e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("raindrops", e.raindrops());
        m.put("maxSteps", e.maxSteps());
        m.put("inertia", e.inertia());
        m.put("sedimentCapacity", e.sedimentCapacity());
        m.put("depositRate", e.depositRate());
        m.put("erosionRate", e.erosionRate());
        m.put("evaporateRate", e.evaporateRate());
        m.put("gravity", e.gravity());
        m.put("startSpeed", e.startSpeed());
        m.put("startWater", e.startWater());
        m.put("minWater", e.minWater());
        m.put("depositRadius", e.depositRadius());
        m.put("smoothPasses", e.smoothPasses());
        m.put("carveFraction", e.carveFraction());
        return m;
    }
}
