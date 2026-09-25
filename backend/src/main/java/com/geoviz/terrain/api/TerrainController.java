package com.geoviz.terrain.api;

import com.geoviz.terrain.erosion.ErosionParams;
import com.geoviz.terrain.erosion.ErosionResult;
import com.geoviz.terrain.erosion.ErosionSimulator;
import com.geoviz.terrain.erosion.ErosionStats;
import com.geoviz.terrain.noise.NoiseGenerator;
import com.geoviz.terrain.noise.NoiseParams;
import com.geoviz.terrain.validation.ParameterValidator;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 地形生成与侵蚀接口。
 */
@RestController
@RequestMapping("/api/terrain")
public class TerrainController {

    private final NoiseGenerator noiseGenerator;
    private final ErosionSimulator erosionSimulator;

    public TerrainController(NoiseGenerator noiseGenerator, ErosionSimulator erosionSimulator) {
        this.noiseGenerator = noiseGenerator;
        this.erosionSimulator = erosionSimulator;
    }

    /** 按噪声参数生成一张新高度场。 */
    @PostMapping("/generate")
    public GenerateResponse generate(@RequestBody NoiseParams params) {
        ParameterValidator.validate(params);
        double[] heightmap = noiseGenerator.generate(params);
        return new GenerateResponse(params.resolution(), heightmap, params);
    }

    /** 在客户端提交的高度场上追加跑一轮侵蚀，返回侵蚀后的高度场。 */
    @PostMapping("/erode")
    public ErodeResponse erode(@RequestBody ErodeRequest request) {
        ParameterValidator.validate(request.erosion());
        ParameterValidator.validateHeightmap(request.resolution(), request.heightmap());
        ErosionResult result = erosionSimulator.erode(
                request.heightmap(), request.resolution(), request.erosion(), null);
        return new ErodeResponse(request.resolution(), result.heightmap(), result.stats());
    }

    public record GenerateResponse(int resolution, double[] heightmap, NoiseParams params) {
    }

    public record ErodeRequest(int resolution, double[] heightmap, ErosionParams erosion) {
    }

    public record ErodeResponse(int resolution, double[] heightmap, ErosionStats stats) {
    }
}
