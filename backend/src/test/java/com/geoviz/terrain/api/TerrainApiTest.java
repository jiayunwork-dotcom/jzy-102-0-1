package com.geoviz.terrain.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 接口层测试：合法请求正常返回、非法参数在计算前被 400 打回且带原因、
 * 快照保存/列表/取回往返。
 */
@SpringBootTest
@AutoConfigureMockMvc
class TerrainApiTest {

    @Autowired
    private MockMvc mvc;

    private static final String VALID_NOISE_JSON = """
            {"resolution": 64, "octaves": 4, "lacunarity": 2.0,
             "persistence": 0.5, "baseFrequency": 3.0, "seed": 42}
            """;

    @Test
    void generateReturnsHeightmap() throws Exception {
        mvc.perform(post("/api/terrain/generate")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_NOISE_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolution").value(64))
                .andExpect(jsonPath("$.heightmap", hasSize(64 * 64)));
    }

    @Test
    void invalidResolutionIsRejectedBeforeComputation() throws Exception {
        String body = """
                {"resolution": 4, "octaves": 4, "lacunarity": 2.0,
                 "persistence": 0.5, "baseFrequency": 3.0, "seed": 42}
                """;
        mvc.perform(post("/api/terrain/generate")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("resolution")));
    }

    @Test
    void invalidPersistenceIsRejectedWithReason() throws Exception {
        String body = """
                {"resolution": 64, "octaves": 4, "lacunarity": 2.0,
                 "persistence": 1.5, "baseFrequency": 3.0, "seed": 42}
                """;
        mvc.perform(post("/api/terrain/generate")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("persistence")));
    }

    @Test
    void negativeErosionRateIsRejectedWithReason() throws Exception {
        String body = """
                {"resolution": 32, "heightmap": [%s],
                 "erosion": {"dropletCount": 100, "erodeRate": -0.5, "depositRate": 0.3,
                             "evaporateRate": 0.02, "gravity": 4.0, "capacityFactor": 4.0,
                             "baseFlow": 0.01, "inertia": 0.05, "maxSteps": 32,
                             "minWater": 0.01, "seed": 7}}
                """.formatted("0.5,".repeat(32 * 32 - 1) + "0.5");
        mvc.perform(post("/api/terrain/erode")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("erodeRate")));
    }

    @Test
    void heightmapLengthMismatchIsRejected() throws Exception {
        String body = """
                {"resolution": 32, "heightmap": [0.1, 0.2, 0.3],
                 "erosion": {"dropletCount": 100, "erodeRate": 0.3, "depositRate": 0.3,
                             "evaporateRate": 0.02, "gravity": 4.0, "capacityFactor": 4.0,
                             "baseFlow": 0.01, "inertia": 0.05, "maxSteps": 32,
                             "minWater": 0.01, "seed": 7}}
                """;
        mvc.perform(post("/api/terrain/erode")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("高度场长度")));
    }

    @Test
    void erodeReturnsHeightmapAndConservedTotalHeight() throws Exception {
        String body = """
                {"resolution": 32, "heightmap": [%s],
                 "erosion": {"dropletCount": 500, "erodeRate": 0.3, "depositRate": 0.3,
                             "evaporateRate": 0.02, "gravity": 4.0, "capacityFactor": 4.0,
                             "baseFlow": 0.01, "inertia": 0.05, "maxSteps": 32,
                             "minWater": 0.01, "seed": 7}}
                """.formatted("0.5,".repeat(32 * 32 - 1) + "0.5");
        mvc.perform(post("/api/terrain/erode")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.heightmap", hasSize(32 * 32)))
                .andExpect(jsonPath("$.stats.dropletsSimulated").value(500))
                .andExpect(jsonPath("$.stats.totalHeightBefore").value(0.5 * 32 * 32))
                // 质量守恒：总高度只允许浮点误差量级的偏差
                .andExpect(jsonPath("$.stats.totalHeightAfter", closeTo(0.5 * 32 * 32, 1e-6)));
    }

    @Test
    void snapshotSaveListAndGetRoundTrip() throws Exception {
        String saveBody = """
                {"name": "api-test-snapshot", "resolution": 32, "heightmap": [%s],
                 "noise": {"resolution": 32, "octaves": 4, "lacunarity": 2.0,
                           "persistence": 0.5, "baseFrequency": 3.0, "seed": 42},
                 "erosion": null}
                """.formatted("0.25,".repeat(32 * 32 - 1) + "0.25");

        mvc.perform(post("/api/snapshots")
                        .contentType(MediaType.APPLICATION_JSON).content(saveBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("api-test-snapshot"));

        mvc.perform(get("/api/snapshots"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'api-test-snapshot')]").exists());

        mvc.perform(get("/api/snapshots/api-test-snapshot"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolution").value(32))
                .andExpect(jsonPath("$.heightmap", hasSize(32 * 32)))
                .andExpect(jsonPath("$.noise.seed").value(42));

        mvc.perform(get("/api/snapshots/no-such-snapshot"))
                .andExpect(status().isNotFound());
    }

    @Test
    void blankSnapshotNameIsRejected() throws Exception {
        String body = """
                {"name": "  ", "resolution": 32, "heightmap": [%s], "noise": null, "erosion": null}
                """.formatted("0.25,".repeat(32 * 32 - 1) + "0.25");
        mvc.perform(post("/api/snapshots")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("快照名称")));
    }
}
