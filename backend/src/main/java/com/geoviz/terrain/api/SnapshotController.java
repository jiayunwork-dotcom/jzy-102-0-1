package com.geoviz.terrain.api;

import com.geoviz.terrain.erosion.ErosionParams;
import com.geoviz.terrain.noise.NoiseParams;
import com.geoviz.terrain.snapshot.Snapshot;
import com.geoviz.terrain.snapshot.SnapshotMeta;
import com.geoviz.terrain.snapshot.SnapshotStore;
import com.geoviz.terrain.validation.ParameterValidator;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * 快照存取接口：命名保存当前高度场与参数、列出快照、按名取回。
 */
@RestController
@RequestMapping("/api/snapshots")
public class SnapshotController {

    private final SnapshotStore store;

    public SnapshotController(SnapshotStore store) {
        this.store = store;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SnapshotMeta save(@RequestBody SaveSnapshotRequest request) {
        ParameterValidator.validateSnapshotName(request.name());
        ParameterValidator.validateHeightmap(request.resolution(), request.heightmap());
        Snapshot saved = store.save(request.name(), request.resolution(), request.heightmap(),
                request.noise(), request.erosion());
        return new SnapshotMeta(saved.name(), saved.savedAt(), saved.resolution());
    }

    @GetMapping
    public List<SnapshotMeta> list() {
        return store.list();
    }

    @GetMapping("/{name}")
    public Snapshot get(@PathVariable String name) {
        return store.find(name)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "快照不存在: " + name));
    }

    public record SaveSnapshotRequest(
            String name,
            int resolution,
            double[] heightmap,
            NoiseParams noise,
            ErosionParams erosion
    ) {
    }
}
