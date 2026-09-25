package com.example.terrain.api;

import com.example.terrain.api.dto.ErosionRequest;
import com.example.terrain.api.dto.TerrainResponse;
import com.example.terrain.service.TerrainService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/erosion")
public class ErosionController {

    private final TerrainService service;

    public ErosionController(TerrainService service) {
        this.service = service;
    }

    /** Run one erosion round on top of the supplied height field. */
    @PostMapping
    public TerrainResponse erode(@RequestBody ErosionRequest request) {
        TerrainService.ErosionResult result = service.erode(request);
        return TerrainController.buildResponse(
                result.heights(), result.noiseParams(), result.erosionParams());
    }
}
