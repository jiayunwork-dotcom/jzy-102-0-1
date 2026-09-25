package com.geoviz.terrain.noise;

import com.geoviz.terrain.validation.ParameterValidator;

import java.util.Random;

/**
 * 多层（分形）Perlin 噪声高度场生成器。
 *
 * 把若干层 2D Perlin 噪声叠加：每叠加一层，频率按 lacunarity 倍升高、
 * 振幅按 persistence 比例衰减，形成"有主体起伏又带细节"的地表。
 *
 * 归一化只做整体缩放（除以振幅和）与整体平移（最小值抬升到 0），
 * 不改变各层之间的相对结构，因此层数越多，高频细节越多这一性质
 * 可以被梯度统计稳定地观测到。
 *
 * 全部随机性来自参数里的种子（用于打乱 Perlin 排列表），
 * 同参数同种子的输出逐比特一致。
 */
public class NoiseGenerator {

    /**
     * 生成高度场。
     *
     * @return 长度为 resolution^2 的高度数组，按行优先存储（index = y * resolution + x），
     *         数值非负，量级约 [0, 1]
     */
    public double[] generate(NoiseParams params) {
        ParameterValidator.validate(params);

        int res = params.resolution();
        int[] perm = buildPermutation(new Random(params.seed()));
        double[] height = new double[res * res];

        double amplitudeSum = 0;
        for (int octave = 0; octave < params.octaves(); octave++) {
            double frequency = params.baseFrequency() * Math.pow(params.lacunarity(), octave);
            double amplitude = Math.pow(params.persistence(), octave);
            amplitudeSum += amplitude;
            for (int y = 0; y < res; y++) {
                double ny = (double) y / res * frequency;
                for (int x = 0; x < res; x++) {
                    double nx = (double) x / res * frequency;
                    height[y * res + x] += perlin(nx, ny, perm) * amplitude;
                }
            }
        }

        // 整体缩放 + 平移到非负区间；不做 min-max 拉伸，避免不同层数之间引入不可比的额外缩放
        double min = Double.POSITIVE_INFINITY;
        for (int i = 0; i < height.length; i++) {
            height[i] /= amplitudeSum;
            if (height[i] < min) {
                min = height[i];
            }
        }
        for (int i = 0; i < height.length; i++) {
            height[i] -= min;
        }
        return height;
    }

    /** 用种子打乱 0..255 排列表并复制成 512 长度，避免采样时越界取模。 */
    private static int[] buildPermutation(Random rng) {
        int[] p = new int[256];
        for (int i = 0; i < 256; i++) {
            p[i] = i;
        }
        for (int i = 255; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            int tmp = p[i];
            p[i] = p[j];
            p[j] = tmp;
        }
        int[] perm = new int[512];
        for (int i = 0; i < 512; i++) {
            perm[i] = p[i & 255];
        }
        return perm;
    }

    private static double fade(double t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    private static double lerp(double a, double b, double t) {
        return a + t * (b - a);
    }

    private static double grad(int hash, double x, double y) {
        return switch (hash & 7) {
            case 0 -> x + y;
            case 1 -> x - y;
            case 2 -> -x + y;
            case 3 -> -x - y;
            case 4 -> x;
            case 5 -> -x;
            case 6 -> y;
            default -> -y;
        };
    }

    /** 经典 2D Perlin 噪声（改进版缓和曲线），输出约 [-1, 1]。 */
    private static double perlin(double x, double y, int[] perm) {
        int xi = (int) Math.floor(x) & 255;
        int yi = (int) Math.floor(y) & 255;
        double xf = x - Math.floor(x);
        double yf = y - Math.floor(y);
        double u = fade(xf);
        double v = fade(yf);

        int aa = perm[perm[xi] + yi];
        int ab = perm[perm[xi] + yi + 1];
        int ba = perm[perm[xi + 1] + yi];
        int bb = perm[perm[xi + 1] + yi + 1];

        double x1 = lerp(grad(aa, xf, yf), grad(ba, xf - 1, yf), u);
        double x2 = lerp(grad(ab, xf, yf - 1), grad(bb, xf - 1, yf - 1), u);
        return lerp(x1, x2, v);
    }
}
