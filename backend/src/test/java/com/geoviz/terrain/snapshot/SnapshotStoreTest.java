package com.geoviz.terrain.snapshot;

import com.geoviz.terrain.erosion.ErosionParams;
import com.geoviz.terrain.noise.NoiseParams;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 快照存取测试：按键存取、同名覆盖、列表元信息。
 */
class SnapshotStoreTest {

    private final SnapshotStore store = new SnapshotStore();

    private static double[] heightmap(int res, double fill) {
        double[] h = new double[res * res];
        java.util.Arrays.fill(h, fill);
        return h;
    }

    @Test
    void saveAndFindRoundTrip() {
        NoiseParams noise = new NoiseParams(32, 4, 2.0, 0.5, 3.0, 1L);
        ErosionParams erosion = new ErosionParams(100, 0.3, 0.3, 0.02, 4.0, 4.0, 0.01, 0.05, 64, 0.01, 2L);
        double[] h = heightmap(32, 0.5);

        store.save("demo", 32, h, noise, erosion);

        Snapshot found = store.find("demo").orElseThrow();
        assertEquals("demo", found.name());
        assertEquals(32, found.resolution());
        assertEquals(h, found.heightmap());
        assertEquals(noise, found.noise());
        assertEquals(erosion, found.erosion());
        assertTrue(store.find("missing").isEmpty());
    }

    @Test
    void sameNameOverwrites() {
        store.save("a", 32, heightmap(32, 1.0), null, null);
        store.save("a", 32, heightmap(32, 2.0), null, null);

        Snapshot found = store.find("a").orElseThrow();
        assertEquals(2.0, found.heightmap()[0]);
        assertEquals(1, store.list().size());
    }

    @Test
    void listReturnsMetaWithoutHeightmap() {
        store.save("one", 32, heightmap(32, 0.1), null, null);
        store.save("two", 64, heightmap(64, 0.2), null, null);

        List<SnapshotMeta> list = store.list();
        assertEquals(2, list.size());
        assertTrue(list.stream().anyMatch(m -> m.name().equals("one") && m.resolution() == 32));
        assertTrue(list.stream().anyMatch(m -> m.name().equals("two") && m.resolution() == 64));
    }
}
