package com.geoviz.terrain.erosion;

/**
 * 一轮侵蚀模拟的结果。
 *
 * @param heightmap 侵蚀后的高度场（新数组，不修改输入）
 */
public record ErosionResult(double[] heightmap, ErosionStats stats) {
}
