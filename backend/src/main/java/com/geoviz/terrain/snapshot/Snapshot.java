package com.geoviz.terrain.snapshot;

import com.geoviz.terrain.erosion.ErosionParams;
import com.geoviz.terrain.noise.NoiseParams;

import java.time.Instant;

/**
 * 一份命名快照：高度场数据 + 产生它时使用的参数。
 */
public record Snapshot(
        String name,
        Instant savedAt,
        int resolution,
        double[] heightmap,
        NoiseParams noise,
        ErosionParams erosion
) {
}
