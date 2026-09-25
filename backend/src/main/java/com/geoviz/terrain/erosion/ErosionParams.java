package com.geoviz.terrain.erosion;

/**
 * 雨滴式水力侵蚀模拟参数。
 *
 * @param dropletCount   一轮模拟投放的雨滴数量
 * @param erodeRate      侵蚀率：含沙量低于携带能力时，从地表带走泥沙的比例
 * @param depositRate    沉积率：含沙量超出携带能力时，沉积超出部分的比例
 * @param evaporateRate  蒸发率：每前进一步，水量乘以 (1 - evaporateRate)
 * @param gravity        重力系数：速度随下滑落差累积，speed^2 += 落差 * gravity
 * @param capacityFactor 携带能力系数：capacity = (speed + baseFlow) * water * capacityFactor
 * @param baseFlow       基础流量：即使流速为 0（平地）也保留的携带能力当量
 * @param inertia        方向惯性：当前方向与最陡下降方向的混合比例
 * @param maxSteps       单颗雨滴最大存活步数
 * @param minWater       水量阈值：水量低于该值雨滴立即消亡
 * @param seed           随机种子，控制雨滴落点等全部随机性
 */
public record ErosionParams(
        int dropletCount,
        double erodeRate,
        double depositRate,
        double evaporateRate,
        double gravity,
        double capacityFactor,
        double baseFlow,
        double inertia,
        int maxSteps,
        double minWater,
        long seed
) {
}
