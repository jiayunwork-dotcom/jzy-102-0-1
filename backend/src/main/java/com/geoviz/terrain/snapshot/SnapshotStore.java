package com.geoviz.terrain.snapshot;

import com.geoviz.terrain.erosion.ErosionParams;
import com.geoviz.terrain.noise.NoiseParams;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 快照存取模块：一个简单的内存键值存储。
 *
 * 按名字存取"高度场 + 参数"，同名覆盖；不涉及事务日志或恢复机制，
 * 进程重启后数据丢失，但同一进程运行期间稳定可读可写。
 */
@Component
public class SnapshotStore {

    private final ConcurrentMap<String, Snapshot> snapshots = new ConcurrentHashMap<>();

    /** 保存（或同名覆盖）一份快照，返回实际存储的内容。 */
    public Snapshot save(String name, int resolution, double[] heightmap,
                         NoiseParams noise, ErosionParams erosion) {
        Snapshot snapshot = new Snapshot(name, Instant.now(), resolution, heightmap, noise, erosion);
        snapshots.put(name, snapshot);
        return snapshot;
    }

    public Optional<Snapshot> find(String name) {
        return Optional.ofNullable(snapshots.get(name));
    }

    /** 按保存时间升序列出全部快照的元信息。 */
    public List<SnapshotMeta> list() {
        return snapshots.values().stream()
                .map(s -> new SnapshotMeta(s.name(), s.savedAt(), s.resolution()))
                .sorted(Comparator.comparing(SnapshotMeta::savedAt))
                .toList();
    }
}
