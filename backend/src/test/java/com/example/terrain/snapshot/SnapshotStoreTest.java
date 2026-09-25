package com.example.terrain.snapshot;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnapshotStoreTest {

    private Snapshot snapshot(String name) {
        return new Snapshot(name, 4, new double[16],
                Map.of("seed", 1), null, System.currentTimeMillis());
    }

    @Test
    void savesAndLoadsByName() {
        SnapshotStore store = new SnapshotStore();
        Snapshot s = snapshot("ridge-1");
        store.save(s);

        assertTrue(store.exists("ridge-1"));
        assertSame(s, store.get("ridge-1"));
    }

    @Test
    void listsSavedSnapshots() {
        SnapshotStore store = new SnapshotStore();
        store.save(snapshot("a"));
        store.save(snapshot("b"));
        assertEquals(2, store.list().size());
    }

    @Test
    void rejectsDuplicateAndEmptyNames() {
        SnapshotStore store = new SnapshotStore();
        store.save(snapshot("dup"));
        assertThrows(IllegalStateException.class, () -> store.save(snapshot("dup")));
        assertThrows(IllegalArgumentException.class,
                () -> store.save(snapshot("  ")));
    }

    @Test
    void loadingMissingSnapshotThrows() {
        assertThrows(NoSuchElementException.class,
                () -> new SnapshotStore().get("ghost"));
    }
}
