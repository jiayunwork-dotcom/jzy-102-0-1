package com.geoviz.terrain.snapshot;

import java.time.Instant;

/**
 * 快照列表项（不含高度场数据本体，避免列表接口传输过大）。
 */
public record SnapshotMeta(String name, Instant savedAt, int resolution) {
}
