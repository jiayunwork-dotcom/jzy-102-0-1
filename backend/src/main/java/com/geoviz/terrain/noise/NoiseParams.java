package com.geoviz.terrain.noise;

/**
 * 噪声地形生成参数。
 *
 * @param resolution    高度场网格边长（总格点数 = resolution^2）
 * @param octaves       噪声叠加层数；每多一层，频率升高、振幅衰减
 * @param lacunarity    频率倍增系数：第 i 层频率 = baseFrequency * lacunarity^i
 * @param persistence   振幅衰减系数：第 i 层振幅 = persistence^i
 * @param baseFrequency 基础频率（第一层噪声在网格上铺几个周期）
 * @param seed          随机种子，决定噪声排列表，同种子结果完全一致
 */
public record NoiseParams(
        int resolution,
        int octaves,
        double lacunarity,
        double persistence,
        double baseFrequency,
        long seed
) {
}
