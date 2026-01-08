package com.hamon.yukknd.util;

/**
 * Utility class for mathematical operations used in wave simulation.
 */
public final class MathUtils {

    private MathUtils() {
        // Utility class - prevent instantiation
    }

    /**
     * Performs smooth Hermite interpolation between 0 and 1 when edge0 < x < edge1.
     *
     * @param edge0 Lower edge of the Hermite function
     * @param edge1 Upper edge of the Hermite function
     * @param x The value to interpolate
     * @return Smoothly interpolated value between 0 and 1
     */
    public static float smoothstep(float edge0, float edge1, float x) {
        float t = clamp01((x - edge0) / (edge1 - edge0));
        return t * t * (3f - 2f * t);
    }

    /**
     * Clamps a value between 0 and 1.
     *
     * @param v The value to clamp
     * @return Value clamped to [0, 1] range
     */
    public static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    /**
     * Calculates an envelope function for wave amplitude.
     * Uses exponential attack and release curves.
     *
     * @param age Time since wave creation
     * @param attack Attack time constant (lower = faster attack)
     * @param fade Fade/release constant (higher = faster fade)
     * @return Envelope value (amplitude multiplier)
     */
    public static float envelope(float age, float attack, float fade) {
        float att = 1f - (float) Math.exp(-age / attack);
        float rel = (float) Math.exp(-fade * age);
        return att * rel;
    }
}
