package com.blockforge.world;

import java.util.Random;

/**
 * Classic gradient noise (2D and 3D) with fractal helpers.
 * Original implementation of the well-known gradient-noise algorithm.
 */
public final class Noise {

    private final int[] perm = new int[512];

    public Noise(long seed) {
        int[] p = new int[256];
        for (int i = 0; i < 256; i++) p[i] = i;
        Random rng = new Random(seed);
        for (int i = 255; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            int tmp = p[i];
            p[i] = p[j];
            p[j] = tmp;
        }
        for (int i = 0; i < 512; i++) perm[i] = p[i & 255];
    }

    private static float fade(float t) {
        return t * t * t * (t * (t * 6f - 15f) + 10f);
    }

    private static float lerp(float a, float b, float t) {
        return a + t * (b - a);
    }

    private static float grad2(int hash, float x, float y) {
        switch (hash & 7) {
            case 0: return  x + y;
            case 1: return -x + y;
            case 2: return  x - y;
            case 3: return -x - y;
            case 4: return  x;
            case 5: return -x;
            case 6: return  y;
            default: return -y;
        }
    }

    private static float grad3(int hash, float x, float y, float z) {
        int h = hash & 15;
        float u = h < 8 ? x : y;
        float v = h < 4 ? y : (h == 12 || h == 14 ? x : z);
        return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
    }

    /** 2D gradient noise in roughly [-1, 1]. */
    public float noise2(float x, float y) {
        int xi = (int) Math.floor(x) & 255;
        int yi = (int) Math.floor(y) & 255;
        float xf = x - (float) Math.floor(x);
        float yf = y - (float) Math.floor(y);
        float u = fade(xf);
        float v = fade(yf);

        int aa = perm[perm[xi] + yi];
        int ab = perm[perm[xi] + yi + 1];
        int ba = perm[perm[xi + 1] + yi];
        int bb = perm[perm[xi + 1] + yi + 1];

        float x1 = lerp(grad2(aa, xf, yf), grad2(ba, xf - 1, yf), u);
        float x2 = lerp(grad2(ab, xf, yf - 1), grad2(bb, xf - 1, yf - 1), u);
        return lerp(x1, x2, v) * 1.41f;
    }

    /** 3D gradient noise in roughly [-1, 1]. */
    public float noise3(float x, float y, float z) {
        int xi = (int) Math.floor(x) & 255;
        int yi = (int) Math.floor(y) & 255;
        int zi = (int) Math.floor(z) & 255;
        float xf = x - (float) Math.floor(x);
        float yf = y - (float) Math.floor(y);
        float zf = z - (float) Math.floor(z);
        float u = fade(xf);
        float v = fade(yf);
        float w = fade(zf);

        int a  = perm[xi] + yi;
        int aa = perm[a] + zi;
        int ab = perm[a + 1] + zi;
        int b  = perm[xi + 1] + yi;
        int ba = perm[b] + zi;
        int bb = perm[b + 1] + zi;

        float x1 = lerp(grad3(perm[aa], xf, yf, zf), grad3(perm[ba], xf - 1, yf, zf), u);
        float x2 = lerp(grad3(perm[ab], xf, yf - 1, zf), grad3(perm[bb], xf - 1, yf - 1, zf), u);
        float y1 = lerp(x1, x2, v);

        float x3 = lerp(grad3(perm[aa + 1], xf, yf, zf - 1), grad3(perm[ba + 1], xf - 1, yf, zf - 1), u);
        float x4 = lerp(grad3(perm[ab + 1], xf, yf - 1, zf - 1), grad3(perm[bb + 1], xf - 1, yf - 1, zf - 1), u);
        float y2 = lerp(x3, x4, v);

        return lerp(y1, y2, w);
    }

    /** Fractal (octaved) 2D noise in roughly [-1, 1]. */
    public float fractal2(float x, float y, int octaves, float lacunarity, float gain) {
        float sum = 0f, amp = 1f, freq = 1f, norm = 0f;
        for (int i = 0; i < octaves; i++) {
            sum += noise2(x * freq, y * freq) * amp;
            norm += amp;
            amp *= gain;
            freq *= lacunarity;
        }
        return sum / norm;
    }

    /** Fractal (octaved) 3D noise in roughly [-1, 1]. */
    public float fractal3(float x, float y, float z, int octaves, float lacunarity, float gain) {
        float sum = 0f, amp = 1f, freq = 1f, norm = 0f;
        for (int i = 0; i < octaves; i++) {
            sum += noise3(x * freq, y * freq, z * freq) * amp;
            norm += amp;
            amp *= gain;
            freq *= lacunarity;
        }
        return sum / norm;
    }

    /** Ridged 2D noise in [0, 1], good for mountains. */
    public float ridged2(float x, float y, int octaves) {
        float sum = 0f, amp = 0.5f, freq = 1f;
        for (int i = 0; i < octaves; i++) {
            sum += (1f - Math.abs(noise2(x * freq, y * freq))) * amp;
            amp *= 0.5f;
            freq *= 2f;
        }
        return sum;
    }
}
