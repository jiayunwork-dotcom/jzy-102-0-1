package com.example.terrain.snapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Minimal in-memory snapshot store: a thread-safe key/value map keyed by name.
 *
 * <p>Persistence is intentionally out of scope — snapshots live for the
 * duration of the process and are lost on restart, as required. Within a single
 * process lifetime saves and loads are stable.
 */
public class SnapshotStore {

    private final Map<String, Snapshot> store = new ConcurrentHashMap<>();

    /**
     * Save a snapshot under a name. The name must be non-empty and not already
     * in use (callers must delete or choose a new name).
     */
    public synchronized Snapshot save(Snapshot snapshot) {
        String name = snapshot.name();
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Snapshot name must not be empty.");
        }
        if (store.containsKey(name)) {
            throw new IllegalStateException("Snapshot '" + name + "' already exists.");
        }
        store.put(name, snapshot);
        return snapshot;
    }

    /** Fetch a snapshot by name. */
    public Snapshot get(String name) {
        Snapshot snapshot = store.get(name);
        if (snapshot == null) {
            throw new NoSuchElementException("Snapshot '" + name + "' not found.");
        }
        return snapshot;
    }

    public boolean exists(String name) {
        return store.containsKey(name);
    }

    /** Remove a snapshot if present. Returns whether one was removed. */
    public boolean delete(String name) {
        return store.remove(name) != null;
    }

    /** List stored snapshots newest-first, without their height payloads. */
    public List<SnapshotInfo> list() {
        List<SnapshotInfo> infos = new ArrayList<>();
        for (Snapshot s : store.values()) {
            infos.add(new SnapshotInfo(s.name(), s.size(), s.createdAt()));
        }
        infos.sort((a, b) -> Long.compare(b.createdAt(), a.createdAt()));
        return infos;
    }

    public record SnapshotInfo(String name, int size, long createdAt) {
    }
}
