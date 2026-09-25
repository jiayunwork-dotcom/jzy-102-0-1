package com.geoviz.terrain.erosion;

import com.geoviz.terrain.validation.ParameterValidator;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 雨滴式水力侵蚀模拟器（单线程、种子可控、质量守恒）。
 *
 * 每颗雨滴的生命周期：
 * <ol>
 *   <li>落在地图随机位置（落点来自种子化的 {@link Random}）；</li>
 *   <li>每一步：用双线性插值取脚下高度与梯度，沿"惯性方向 + 最陡下降方向"
 *       的混合方向前进一格；</li>
 *   <li>携带能力 = (流速 + 基础流量) * 水量 * 携带系数。
 *       含沙量超过携带能力（或在爬坡）→ 把超出部分按沉积率沉回地表（双线性 4 节点）；
 *       否则按侵蚀率从地表带走泥沙（以雨滴所在格为中心的小刷子分摊，
 *       冲出的沟壑因此有宽度、能连成水系）；</li>
 *   <li>速度随下滑落差累积：speed = sqrt(speed^2 + 落差 * gravity)；
 *       水量随路程蒸发：water *= (1 - evaporateRate)；</li>
 *   <li>水量低于阈值、步数耗尽或滑出地图时消亡；
 *       消亡前把身上剩余泥沙用刷子摊回当前位置（冲积扇式的淤积）。</li>
 * </ol>
 *
 * 设计说明（这些选择都是为了同时满足演示效果与可测试的不变量）：
 * <ul>
 *   <li>携带能力只依赖流速与水量，不含落差项：落差 → 速度 → 携带能力的链条
 *       已被速度积分表达，再乘一次落差会形成"坑越深→侵蚀越狠"的正反馈，
 *       多轮侵蚀后地形发散；</li>
 *   <li>侵蚀用小刷子分摊、中途沉积用双线性集中：侵蚀集中下切而沉积分散
 *       会让隆起失稳爆炸，反过来则沟壑连贯且多轮模拟有界；</li>
 *   <li>确定性：全部随机性来自参数种子，单线程执行，同参同种子结果逐比特一致；</li>
 *   <li>水量单调：water 每步只乘 (1 - evaporateRate)，严格不升；
 *       循环入口先检查水量，水量低于阈值的雨滴不会再执行任何侵蚀/沉积；</li>
 *   <li>质量守恒：侵蚀/沉积都按权重和为 1 的方式分配到格点（刷子越界时
 *       按界内权重重新归一化），从地表拿走的泥沙恰好等于雨滴获得的泥沙，
 *       雨滴消亡时剩余泥沙全部归还，整图高度和的变化只有浮点舍入误差量级。</li>
 * </ul>
 */
public class ErosionSimulator {

    /** 侵蚀/死亡倾泻刷子的半径（格），让沟壑有宽度、淤积成扇。 */
    static final int BRUSH_RADIUS = 2;

    /**
     * 在输入高度场上跑一轮侵蚀。
     *
     * @param source     输入高度场（不会被修改）
     * @param resolution 网格边长
     * @param params     侵蚀参数
     * @param listener   雨滴生命周期监听器，可为 null（测试用来观测不变量）
     * @return 侵蚀后的新高度场与统计信息
     */
    public ErosionResult erode(double[] source, int resolution, ErosionParams params, DropletListener listener) {
        ParameterValidator.validate(params);
        ParameterValidator.validateHeightmap(resolution, source);

        double[] height = source.clone();
        double totalBefore = sum(height);
        Random rng = new Random(params.seed());
        DropletListener l = listener == null ? DropletListener.NONE : listener;
        Brush brush = Brush.of(BRUSH_RADIUS);

        long totalSteps = 0;
        for (int i = 0; i < params.dropletCount(); i++) {
            totalSteps += simulateDroplet(height, resolution, params, rng, i, l, brush);
        }

        double totalAfter = sum(height);
        return new ErosionResult(height,
                new ErosionStats(params.dropletCount(), totalSteps, totalBefore, totalAfter));
    }

    private int simulateDroplet(double[] h, int res, ErosionParams p, Random rng,
                                int index, DropletListener listener, Brush brush) {
        double posX = rng.nextDouble() * (res - 1);
        double posY = rng.nextDouble() * (res - 1);
        double dirX = 0;
        double dirY = 0;
        double speed = 0;
        double water = 1.0;
        double sediment = 0;
        int step = 0;
        String reason;

        while (true) {
            // 循环入口先检查终止条件：水量低于阈值的雨滴绝不会执行后续侵蚀/沉积
            if (water < p.minWater()) {
                reason = "evaporated";
                break;
            }
            if (step >= p.maxSteps()) {
                reason = "steps_exhausted";
                break;
            }

            listener.onStep(index, step, water, sediment);

            // ---- 当前位置的高度与梯度（双线性插值）----
            int x0 = (int) posX;
            int y0 = (int) posY;
            double fx = posX - x0;
            double fy = posY - y0;

            double h00 = h[y0 * res + x0];
            double h10 = h[y0 * res + x0 + 1];
            double h01 = h[(y0 + 1) * res + x0];
            double h11 = h[(y0 + 1) * res + x0 + 1];

            double gradX = (h10 - h00) * (1 - fy) + (h11 - h01) * fy;
            double gradY = (h01 - h00) * (1 - fx) + (h11 - h10) * fx;
            double heightOld = h00 * (1 - fx) * (1 - fy) + h10 * fx * (1 - fy)
                    + h01 * (1 - fx) * fy + h11 * fx * fy;

            // ---- 方向 = 惯性方向与最陡下降方向的混合 ----
            dirX = dirX * p.inertia() - gradX * (1 - p.inertia());
            dirY = dirY * p.inertia() - gradY * (1 - p.inertia());
            double len = Math.hypot(dirX, dirY);
            if (len < 1e-12) {
                // 完全平地：随机选个方向（同样来自种子化的 rng，保证可复现）
                double angle = rng.nextDouble() * Math.PI * 2;
                dirX = Math.cos(angle);
                dirY = Math.sin(angle);
            } else {
                dirX /= len;
                dirY /= len;
            }

            double newX = posX + dirX;
            double newY = posY + dirY;
            if (newX < 0 || newX >= res - 1 || newY < 0 || newY >= res - 1) {
                reason = "out_of_bounds";
                break;
            }

            // ---- 新位置高度（双线性插值）----
            int nx0 = (int) newX;
            int ny0 = (int) newY;
            double nfx = newX - nx0;
            double nfy = newY - ny0;
            double nh00 = h[ny0 * res + nx0];
            double nh10 = h[ny0 * res + nx0 + 1];
            double nh01 = h[(ny0 + 1) * res + nx0];
            double nh11 = h[(ny0 + 1) * res + nx0 + 1];
            double heightNew = nh00 * (1 - nfx) * (1 - nfy) + nh10 * nfx * (1 - nfy)
                    + nh01 * (1 - nfx) * nfy + nh11 * nfx * nfy;

            double descent = heightOld - heightNew; // > 0 表示在下滑
            double capacity = (speed + p.baseFlow()) * water * p.capacityFactor();

            if (descent < 0) {
                // 在爬坡：把泥沙垫回脚下（最多填平爬坡高差）
                double amount = Math.min(-descent, sediment);
                if (amount > 0) {
                    depositBilinear(h, res, x0, y0, fx, fy, amount);
                    sediment -= amount;
                }
            } else if (sediment > capacity) {
                // 携带不了那么多泥沙：按沉积率沉积超出部分
                double amount = (sediment - capacity) * p.depositRate();
                if (amount > 0) {
                    depositBilinear(h, res, x0, y0, fx, fy, amount);
                    sediment -= amount;
                }
            } else {
                // 还有携带余量：用刷子从周围地面侵蚀泥沙
                double amount = (capacity - sediment) * p.erodeRate();
                if (amount > 0) {
                    brush.apply(h, res, x0, y0, -amount);
                    sediment += amount;
                }
            }

            speed = Math.sqrt(Math.max(0, speed * speed + descent * p.gravity()));
            posX = newX;
            posY = newY;
            water *= 1 - p.evaporateRate();
            step++;
        }

        // 雨滴消亡：把剩余泥沙全部摊回当前位置，保证整图质量守恒
        if (sediment > 0) {
            int x0 = (int) posX;
            int y0 = (int) posY;
            brush.apply(h, res, x0, y0, sediment);
        }
        listener.onDropletEnd(index, step, water, reason);
        return step;
    }

    /** 双线性 4 节点沉积（权重和恒为 1，严格守恒）。 */
    private static void depositBilinear(double[] h, int res, int x0, int y0,
                                        double fx, double fy, double amount) {
        h[y0 * res + x0] += amount * (1 - fx) * (1 - fy);
        h[y0 * res + x0 + 1] += amount * fx * (1 - fy);
        h[(y0 + 1) * res + x0] += amount * (1 - fx) * fy;
        h[(y0 + 1) * res + x0 + 1] += amount * fx * fy;
    }

    private static double sum(double[] h) {
        double s = 0;
        for (double v : h) {
            s += v;
        }
        return s;
    }

    /**
     * 圆形衰减刷子：以某格为中心、按 (1 - 距离/半径) 加权的圆盘。
     * 权重预先归一化；施加时若圆盘越出地图边界，则按界内权重重新归一化，
     * 保证任意位置施加的总量恰好等于请求量（质量守恒不被边界破坏）。
     */
    record Brush(int[][] offsets, double[] weights) {

        static Brush of(int radius) {
            List<int[]> offsets = new ArrayList<>();
            List<Double> weights = new ArrayList<>();
            double sum = 0;
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    double dist = Math.hypot(dx, dy);
                    if (dist <= radius) {
                        double w = 1 - dist / radius;
                        offsets.add(new int[]{dx, dy});
                        weights.add(w);
                        sum += w;
                    }
                }
            }
            double[] normalized = new double[weights.size()];
            for (int i = 0; i < weights.size(); i++) {
                normalized[i] = weights.get(i) / sum;
            }
            return new Brush(offsets.toArray(new int[0][]), normalized);
        }

        /** 以 (cx, cy) 为中心施加总量 delta（正为沉积、负为侵蚀），严格守恒。 */
        void apply(double[] h, int res, int cx, int cy, double delta) {
            double weightSum = 0;
            for (int k = 0; k < offsets.length; k++) {
                int x = cx + offsets[k][0];
                int y = cy + offsets[k][1];
                if (x >= 0 && x < res && y >= 0 && y < res) {
                    weightSum += weights[k];
                }
            }
            if (weightSum <= 0) {
                return;
            }
            for (int k = 0; k < offsets.length; k++) {
                int x = cx + offsets[k][0];
                int y = cy + offsets[k][1];
                if (x >= 0 && x < res && y >= 0 && y < res) {
                    h[y * res + x] += delta * weights[k] / weightSum;
                }
            }
        }
    }
}
