package com.example.terrain.api;

import com.example.terrain.api.dto.NoiseRequest;
import com.example.terrain.api.dto.SaveSnapshotRequest;
import com.example.terrain.api.dto.TerrainResponse;
import com.example.terrain.noise.NoiseParams;
import com.example.terrain.service.TerrainStats;
import com.example.terrain.snapshot.Snapshot;
import com.example.terrain.snapshot.SnapshotStore;
import com.example.terrain.validation.InvalidParamsException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/snapshots")
public class SnapshotController {

    private final SnapshotStore store;

    public SnapshotController(SnapshotStore store) {
        this.store = store;
    }

    /** Persist the current height field plus its parameters under a name. */
    @PostMapping
    public Map<String, Object> save(@RequestBody SaveSnapshotRequest request) {
        if (request.name() == null || request.name().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Snapshot name is required.");
        }
        if (request.size() == null || request.heights() == null
                || request.heights().length != request.size() * request.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "heights must contain size*size values.");
        }
        if (store.exists(request.name())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Snapshot '" + request.name() + "' already exists.");
        }

        NoiseParams noise = toNoiseParams(request.noise());
        if (noise.size() != request.size()) {
            throw new InvalidParamsException(
                    "Snapshot size does not match its noise parameters.");
        }

        Map<String, Object> erosionMap = erosionSettingsToMap(request.erosion());

        Snapshot snapshot = new Snapshot(
                request.name(),
                request.size(),
                request.heights().clone(),
                TerrainController.noiseMap(noise),
                erosionMap,
                System.currentTimeMillis()
        );
        try {
            store.save(snapshot);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("saved", true);
        response.put("name", snapshot.name());
        response.put("size", snapshot.size());
        response.put("createdAt", snapshot.createdAt());
        return response;
    }

    /** List snapshots (metadata only, no height payload). */
    @GetMapping
    public List<SnapshotStore.SnapshotInfo> list() {
        return store.list();
    }

    /** Fetch one snapshot's full height data so it can be rendered again. */
    @GetMapping("/{name}")
    public TerrainResponse get(@PathVariable String name) {
        Snapshot snapshot;
        try {
            snapshot = store.get(name);
        } catch (NoSuchElementException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
        TerrainStats.Stats stats = TerrainStats.compute(snapshot.heights(), snapshot.size());
        List<Double> heights = new ArrayList<>(snapshot.heights().length);
        for (double v : snapshot.heights()) {
            heights.add(v);
        }
        Object seed = snapshot.noiseParams() == null ? null
                : snapshot.noiseParams().get("seed");
        return new TerrainResponse(
                snapshot.size(),
                heights,
                seed instanceof Number n ? n.longValue() : 0L,
                snapshot.noiseParams(),
                snapshot.erosionParams(),
                stats.mean(),
                stats.sum(),
                stats.variance(),
                stats.gradientEnergy(),
                stats.min(),
                stats.max()
        );
    }

    private NoiseParams toNoiseParams(NoiseRequest r) {
        if (r == null) {
            throw new InvalidParamsException("Noise parameters are required for a snapshot.");
        }
        return new NoiseParams(
                r.size(),
                r.seed() == null ? 0L : r.seed(),
                r.octaves(),
                r.persistence(),
                r.lacunarity(),
                r.baseFrequency()
        );
    }

    private Map<String, Object> erosionSettingsToMap(SaveSnapshotRequest.ErosionSettings e) {
        if (e == null) {
            return null;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        putIfNotNull(m, "raindrops", e.raindrops());
        putIfNotNull(m, "maxSteps", e.maxSteps());
        putIfNotNull(m, "inertia", e.inertia());
        putIfNotNull(m, "sedimentCapacity", e.sedimentCapacity());
        putIfNotNull(m, "depositRate", e.depositRate());
        putIfNotNull(m, "erosionRate", e.erosionRate());
        putIfNotNull(m, "evaporateRate", e.evaporateRate());
        putIfNotNull(m, "gravity", e.gravity());
        putIfNotNull(m, "startSpeed", e.startSpeed());
        putIfNotNull(m, "startWater", e.startWater());
        putIfNotNull(m, "minWater", e.minWater());
        putIfNotNull(m, "depositRadius", e.depositRadius());
        putIfNotNull(m, "smoothPasses", e.smoothPasses());
        putIfNotNull(m, "carveFraction", e.carveFraction());
        return m;
    }

    private static void putIfNotNull(Map<String, Object> m, String key, Object value) {
        if (value != null) {
            m.put(key, value);
        }
    }
}
