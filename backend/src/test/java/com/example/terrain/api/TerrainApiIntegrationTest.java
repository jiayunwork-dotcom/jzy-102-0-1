package com.example.terrain.api;

import com.example.terrain.snapshot.SnapshotStore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TerrainApiIntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private SnapshotStore store;

    @BeforeEach
    void clearSnapshots() {
        store.list().forEach(info -> store.delete(info.name()));
    }

    @Test
    void generateRejectsIllegalResolutionWith400() throws Exception {
        String body = """
                {"size": 8, "seed": 1, "octaves": 6, "persistence": 0.5,
                 "lacunarity": 2.0, "baseFrequency": 0.05}
                """;
        mvc.perform(post("/api/terrain/generate")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_parameters"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void erosionRejectsNegativeRateWith400() throws Exception {
        // First generate a small valid terrain.
        String gen = """
                {"size": 16, "seed": 3, "octaves": 3, "persistence": 0.5,
                 "lacunarity": 2.0, "baseFrequency": 0.05}
                """;
        String genResponse = mvc.perform(post("/api/terrain/generate")
                        .contentType(MediaType.APPLICATION_JSON).content(gen))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode terrain = mapper.readTree(genResponse);

        String erode = """
                {"size": 16,
                 "heights": %s,
                 "noise": {"size": 16, "seed": 3, "octaves": 3, "persistence": 0.5,
                           "lacunarity": 2.0, "baseFrequency": 0.05},
                 "raindrops": 500, "erosionRate": -0.5}
                """.formatted(terrain.get("heights").toString());
        mvc.perform(post("/api/erosion")
                        .contentType(MediaType.APPLICATION_JSON).content(erode))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void generateErodeSaveAndReloadSnapshot() throws Exception {
        int size = 16;
        String gen = """
                {"size": %d, "seed": 9, "octaves": 3, "persistence": 0.5,
                 "lacunarity": 2.0, "baseFrequency": 0.05}
                """.formatted(size);
        String genJson = mvc.perform(post("/api/terrain/generate")
                        .contentType(MediaType.APPLICATION_JSON).content(gen))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode terrain = mapper.readTree(genJson);

        String erode = """
                {"size": %d, "heights": %s,
                 "noise": {"size": %d, "seed": 9, "octaves": 3, "persistence": 0.5,
                           "lacunarity": 2.0, "baseFrequency": 0.05},
                 "raindrops": 500, "erosionRate": 0.3}
                """.formatted(size, terrain.get("heights").toString(), size);
        String erodedJson = mvc.perform(post("/api/erosion")
                        .contentType(MediaType.APPLICATION_JSON).content(erode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.variance").exists())
                .andReturn().getResponse().getContentAsString();
        JsonNode eroded = mapper.readTree(erodedJson);

        String name = "snap-" + System.nanoTime();
        String save = """
                {"name": "%s", "size": %d, "heights": %s,
                 "noise": {"size": %d, "seed": 9, "octaves": 3, "persistence": 0.5,
                           "lacunarity": 2.0, "baseFrequency": 0.05},
                 "erosion": {"raindrops": 500, "erosionRate": 0.3}}
                """.formatted(name, size, eroded.get("heights").toString(), size);
        mvc.perform(post("/api/snapshots")
                        .contentType(MediaType.APPLICATION_JSON).content(save))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saved").value(true));

        mvc.perform(get("/api/snapshots")).andExpect(status().isOk());

        mvc.perform(get("/api/snapshots/" + name))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(size))
                .andExpect(jsonPath("$.heights[0]").exists());

        mvc.perform(get("/api/snapshots/does-not-exist"))
                .andExpect(status().isNotFound());
    }
}
