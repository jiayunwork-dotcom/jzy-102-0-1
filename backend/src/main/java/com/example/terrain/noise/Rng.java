package com.example.terrain.noise;

/**
 * Small, fast, fully deterministic pseudo-random generator.
 *
 * <p>This is a bit-mixing generator (mulberry32-style arithmetic expressed with
 * 64-bit longs). The ONLY source of randomness in erosion and in noise
 * construction must go through this class so that a fixed seed fully
 * determines the result.
 */
public final class Rng {

    private long state;

    public Rng(long seed) {
        // Spread arbitrary seeds (including small ones) before iterating.
        this.state = mix(seed ^ 0x9E3779B97F4A7C15L);
    }

    /** Uniform double in [0, 1). */
    public double nextDouble() {
        // 64-bit variant of the classic mulberry mix.
        state += 0x9E3779B97F4A7C15L;
        long z = state;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z ^= z >>> 31;
        // Use the top 53 bits for a double in [0, 1).
        return (z >>> 11) * (1.0 / 9007199254740992.0);
    }

    /** Uniform integer in [0, bound). */
    public int nextInt(int bound) {
        return (int) (nextDouble() * bound);
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
