package com.geoviz.terrain.erosion;

/**
 * 一轮侵蚀模拟的统计信息。
 *
 * @param totalHeightBefore 模拟前整图高度和（用于质量守恒校验）
 * @param totalHeightAfter  模拟后整图高度和（与 before 之差应只有浮点误差量级）
 */
public record ErosionStats(
        int dropletsSimulated,
        long totalSteps,
        double totalHeightBefore,
        double totalHeightAfter
) {
}
