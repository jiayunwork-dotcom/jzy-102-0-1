package com.geoviz.terrain.validation;

import com.geoviz.terrain.erosion.ErosionParams;
import com.geoviz.terrain.noise.NoiseParams;

/**
 * 参数校验模块。
 *
 * 所有校验在任何计算开始之前完成；发现非法参数立即抛出
 * {@link InvalidParameterException}（携带具体原因），
 * 避免后端跑到中途才因除零、数组越界等问题崩溃。
 */
public final class ParameterValidator {

    public static final int MIN_RESOLUTION = 16;
    public static final int MAX_RESOLUTION = 512;
    public static final int MIN_OCTAVES = 1;
    public static final int MAX_OCTAVES = 12;
    public static final int MAX_DROPLETS = 500_000;
    public static final int MAX_STEPS = 512;

    private ParameterValidator() {
    }

    public static void validate(NoiseParams p) {
        require(p != null, "缺少噪声参数");
        require(p.resolution() >= MIN_RESOLUTION && p.resolution() <= MAX_RESOLUTION,
                "resolution（网格分辨率）必须在 [" + MIN_RESOLUTION + ", " + MAX_RESOLUTION
                        + "] 内，当前值: " + p.resolution());
        require(p.octaves() >= MIN_OCTAVES && p.octaves() <= MAX_OCTAVES,
                "octaves（噪声层数）必须在 [" + MIN_OCTAVES + ", " + MAX_OCTAVES
                        + "] 内，当前值: " + p.octaves());
        require(p.lacunarity() >= 1.0 && p.lacunarity() <= 4.0,
                "lacunarity（频率倍增系数）必须在 [1, 4] 内，当前值: " + p.lacunarity());
        require(p.persistence() > 0.0 && p.persistence() < 1.0,
                "persistence（振幅衰减系数）必须在 (0, 1) 开区间内，当前值: " + p.persistence());
        require(p.baseFrequency() >= 0.5 && p.baseFrequency() <= 16.0,
                "baseFrequency（基础频率）必须在 [0.5, 16] 内，当前值: " + p.baseFrequency());
    }

    public static void validate(ErosionParams p) {
        require(p != null, "缺少侵蚀参数");
        require(p.dropletCount() >= 1 && p.dropletCount() <= MAX_DROPLETS,
                "dropletCount（雨滴数量）必须在 [1, " + MAX_DROPLETS + "] 内，当前值: " + p.dropletCount());
        require(p.erodeRate() >= 0.0 && p.erodeRate() <= 1.0,
                "erodeRate（侵蚀率）必须在 [0, 1] 内，不能为负，当前值: " + p.erodeRate());
        require(p.depositRate() >= 0.0 && p.depositRate() <= 1.0,
                "depositRate（沉积率）必须在 [0, 1] 内，不能为负，当前值: " + p.depositRate());
        require(p.evaporateRate() >= 0.0 && p.evaporateRate() < 1.0,
                "evaporateRate（蒸发率）必须在 [0, 1) 内，不能为负，当前值: " + p.evaporateRate());
        require(p.gravity() >= 0.0,
                "gravity（重力系数）不能为负，当前值: " + p.gravity());
        require(p.capacityFactor() > 0.0,
                "capacityFactor（携带能力系数）必须为正，当前值: " + p.capacityFactor());
        require(p.baseFlow() >= 0.0,
                "baseFlow（基础流量）不能为负，当前值: " + p.baseFlow());
        require(p.inertia() >= 0.0 && p.inertia() < 1.0,
                "inertia（方向惯性）必须在 [0, 1) 内，当前值: " + p.inertia());
        require(p.maxSteps() >= 1 && p.maxSteps() <= MAX_STEPS,
                "maxSteps（最大步数）必须在 [1, " + MAX_STEPS + "] 内，当前值: " + p.maxSteps());
        require(p.minWater() > 0.0 && p.minWater() < 1.0,
                "minWater（水量阈值）必须在 (0, 1) 开区间内，当前值: " + p.minWater());
    }

    /** 校验随侵蚀请求一起提交的高度场：分辨率合法、长度与分辨率一致、数值有限。 */
    public static void validateHeightmap(int resolution, double[] heightmap) {
        require(resolution >= MIN_RESOLUTION && resolution <= MAX_RESOLUTION,
                "resolution（网格分辨率）必须在 [" + MIN_RESOLUTION + ", " + MAX_RESOLUTION
                        + "] 内，当前值: " + resolution);
        require(heightmap != null, "缺少高度场数据 heightmap");
        require(heightmap.length == (long) resolution * resolution,
                "高度场长度 " + heightmap.length + " 与分辨率 " + resolution + "x" + resolution
                        + " = " + (resolution * resolution) + " 不一致");
        for (double v : heightmap) {
            require(Double.isFinite(v), "高度场包含非法数值（NaN 或无穷大）");
        }
    }

    public static void validateSnapshotName(String name) {
        require(name != null && !name.isBlank(), "快照名称不能为空");
        require(name.length() <= 100, "快照名称过长（最多 100 个字符），当前长度: " + name.length());
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new InvalidParameterException(message);
        }
    }
}
