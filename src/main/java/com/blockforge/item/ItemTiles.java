package com.blockforge.item;

import java.util.Random;

/**
 * Procedural 16x16 icon painters for items and HUD symbols. Like all art in
 * the game, generated from math — nothing is loaded from files.
 */
public final class ItemTiles {

    private static final int T = 16;

    private ItemTiles() {
    }

    private static int argb(int rgb) {
        return 0xFF000000 | (rgb & 0xFFFFFF);
    }

    private static int shade(int rgb, float m) {
        int r = Math.min(255, (int) (((rgb >> 16) & 0xFF) * m));
        int g = Math.min(255, (int) (((rgb >> 8) & 0xFF) * m));
        int b = Math.min(255, (int) ((rgb & 0xFF) * m));
        return argb((r << 16) | (g << 8) | b);
    }

    private static void px(int[] a, int x, int y, int c) {
        if (x >= 0 && x < T && y >= 0 && y < T) a[y * T + x] = c;
    }

    /** Metal ingot: slanted bar with highlight. */
    public static int[] ingot(int color, long seed) {
        int[] a = new int[T * T];
        for (int y = 6; y <= 11; y++) {
            for (int x = 2 + (11 - y); x <= 9 + (11 - y); x++) {
                float m = y <= 7 ? 1.15f : (y >= 10 ? 0.75f : 0.95f);
                px(a, x, y, shade(color, m));
            }
        }
        return a;
    }

    /** Raw ore chunk: irregular blob. */
    public static int[] rawOre(int color, long seed) {
        int[] a = new int[T * T];
        Random r = new Random(seed);
        for (int i = 0; i < 5; i++) {
            int cx = 5 + r.nextInt(6), cy = 5 + r.nextInt(6), s = 2 + r.nextInt(3);
            for (int y = cy - s; y <= cy + s; y++) {
                for (int x = cx - s; x <= cx + s; x++) {
                    if (Math.abs(x - cx) + Math.abs(y - cy) <= s) {
                        px(a, x, y, shade(color, 0.75f + r.nextFloat() * 0.45f));
                    }
                }
            }
        }
        return a;
    }

    /** Cut gem: rhombus with facets. */
    public static int[] gem(int color, long seed) {
        int[] a = new int[T * T];
        for (int y = 3; y <= 12; y++) {
            int half = y <= 7 ? (y - 3) + 1 : (12 - y) + 1;
            half = Math.min(half + 2, 6);
            for (int x = 8 - half; x <= 7 + half; x++) {
                float m = (x + y) % 4 == 0 ? 1.25f : (y <= 6 ? 1.05f : 0.8f);
                px(a, x, y, shade(color, m));
            }
        }
        return a;
    }

    /** Dust pile. */
    public static int[] dust(int color, long seed) {
        int[] a = new int[T * T];
        Random r = new Random(seed);
        for (int y = 8; y <= 13; y++) {
            int half = (y - 6);
            for (int x = 8 - half; x <= 7 + half; x++) {
                if (r.nextFloat() < 0.85f) px(a, x, y, shade(color, 0.8f + r.nextFloat() * 0.4f));
            }
        }
        return a;
    }

    public static int[] stick(long seed) {
        int[] a = new int[T * T];
        for (int i = 0; i < 10; i++) {
            px(a, 3 + i, 12 - i, argb(0x6B5233));
            px(a, 4 + i, 12 - i, argb(0x8A6B40));
        }
        return a;
    }

    /**
     * Tool icon: diagonal handle plus a head shaped by tool class,
     * head colored by material tier.
     */
    public static int[] tool(Item.ToolClass cls, int headColor, long seed) {
        int[] a = new int[T * T];
        int handle = 0x6B5233, handleHi = 0x8A6B40;
        // handle from bottom-left to upper-right
        for (int i = 0; i < 9; i++) {
            px(a, 3 + i, 12 - i, argb(handle));
            px(a, 4 + i, 12 - i, argb(handleHi));
        }
        switch (cls) {
            case PICK -> {
                // curved crown across the top
                for (int i = 0; i < 9; i++) {
                    int x = 4 + i;
                    int y = i < 3 ? 5 - i : (i > 5 ? i - 3 : 2);
                    px(a, x, y, shade(headColor, 1.0f));
                    px(a, x, y + 1, shade(headColor, 0.8f));
                }
            }
            case AXE -> {
                for (int y = 2; y <= 7; y++) {
                    for (int x = 8; x <= 12; x++) {
                        if (x - 8 + Math.abs(y - 4) <= 4) px(a, x, y, shade(headColor, y < 5 ? 1.05f : 0.8f));
                    }
                }
            }
            case SHOVEL -> {
                for (int y = 1; y <= 5; y++) {
                    for (int x = 10; x <= 13; x++) {
                        px(a, x, y, shade(headColor, y < 3 ? 1.05f : 0.85f));
                    }
                }
            }
            case SWORD -> {
                for (int i = 0; i < 10; i++) {
                    px(a, 4 + i, 11 - i, shade(headColor, 1.05f));
                    px(a, 5 + i, 11 - i, shade(headColor, 0.85f));
                }
                // guard
                px(a, 4, 9, argb(handle));
                px(a, 6, 11, argb(handle));
            }
            case BOW -> {
                for (int i = 0; i < 11; i++) {
                    int bend = (int) (Math.sin(i / 10.0 * Math.PI) * 3);
                    px(a, 4 + bend, 2 + i, argb(handle));
                    px(a, 5 + bend, 2 + i, argb(handleHi));
                }
                for (int i = 0; i < 11; i++) px(a, 10, 2 + i, argb(0xD8D8D0)); // string
            }
            default -> {
            }
        }
        return a;
    }

    public static int[] arrow(long seed) {
        int[] a = new int[T * T];
        for (int i = 0; i < 10; i++) {
            px(a, 3 + i, 12 - i, argb(0x8A6B40));
        }
        px(a, 12, 3, argb(0xB8B8B8));
        px(a, 13, 2, argb(0xD8D8D8));
        px(a, 12, 2, argb(0xD8D8D8));
        px(a, 13, 3, argb(0xB8B8B8));
        px(a, 3, 12, argb(0xE8E8E0)); // fletching
        px(a, 4, 13, argb(0xE8E8E0));
        px(a, 2, 13, argb(0xE8E8E0));
        return a;
    }

    /** Meat slab; raw = pink tones, cooked = brown tones. */
    public static int[] meat(boolean cooked, int baseColor, long seed) {
        int[] a = new int[T * T];
        Random r = new Random(seed);
        for (int y = 4; y <= 11; y++) {
            for (int x = 3; x <= 12; x++) {
                if ((x == 3 || x == 12) && (y == 4 || y == 11)) continue;
                float m = 0.85f + r.nextFloat() * 0.3f;
                px(a, x, y, shade(baseColor, m));
            }
        }
        int fat = cooked ? 0x8A6B40 : 0xEFD8D0;
        for (int x = 4; x <= 11; x += 2) px(a, x, 6, argb(fat));
        return a;
    }

    public static int[] drumstick(boolean cooked, long seed) {
        int[] a = new int[T * T];
        int meat = cooked ? 0xB07840 : 0xE8B090;
        for (int y = 3; y <= 9; y++) {
            for (int x = 6; x <= 12; x++) {
                if (Math.abs(x - 9) + Math.abs(y - 6) <= 4) px(a, x, y, shade(meat, 0.85f + ((x + y) % 3) * 0.1f));
            }
        }
        px(a, 5, 10, argb(0xE8E8E0));
        px(a, 4, 11, argb(0xE8E8E0));
        px(a, 3, 12, argb(0xF5F5F0));
        px(a, 4, 12, argb(0xF5F5F0));
        px(a, 3, 11, argb(0xF5F5F0));
        return a;
    }

    public static int[] apple(long seed) {
        int[] a = new int[T * T];
        for (int y = 5; y <= 12; y++) {
            for (int x = 4; x <= 11; x++) {
                double d = Math.hypot(x - 7.5, y - 8.5);
                if (d <= 4.2) px(a, x, y, shade(0xC42D2D, x < 7 ? 1.05f : 0.85f));
            }
        }
        px(a, 8, 4, argb(0x6B5233));
        px(a, 8, 3, argb(0x6B5233));
        px(a, 9, 3, argb(0x4E8A3A)); // leaf
        px(a, 10, 3, argb(0x4E8A3A));
        return a;
    }

    public static int[] bone(long seed) {
        int[] a = new int[T * T];
        for (int i = 0; i < 8; i++) {
            px(a, 4 + i, 11 - i, argb(0xEDEDE2));
            px(a, 5 + i, 11 - i, argb(0xD8D8CD));
        }
        px(a, 3, 12, argb(0xF5F5EA));
        px(a, 4, 13, argb(0xF5F5EA));
        px(a, 3, 13, argb(0xF5F5EA));
        px(a, 12, 3, argb(0xF5F5EA));
        px(a, 13, 4, argb(0xF5F5EA));
        px(a, 13, 3, argb(0xF5F5EA));
        return a;
    }

    public static int[] silk(long seed) {
        int[] a = new int[T * T];
        for (int i = 0; i < 12; i++) {
            int y = 2 + i;
            int x = 7 + (int) (Math.sin(i * 0.9) * 2.2);
            px(a, x, y, argb(0xE8E8E0));
            px(a, x + 1, y, argb(0xC8C8C0));
        }
        return a;
    }

    public static int[] flesh(long seed) {
        int[] a = new int[T * T];
        Random r = new Random(seed);
        for (int y = 4; y <= 11; y++) {
            for (int x = 3; x <= 12; x++) {
                if (r.nextFloat() < 0.85f) px(a, x, y, shade(0x8A5A3A, 0.7f + r.nextFloat() * 0.4f));
            }
        }
        for (int i = 0; i < 5; i++) px(a, 4 + r.nextInt(8), 5 + r.nextInt(6), argb(0x5E7C16));
        return a;
    }

    public static int[] leather(long seed) {
        int[] a = new int[T * T];
        for (int y = 4; y <= 12; y++) {
            for (int x = 3; x <= 12; x++) {
                boolean edge = y == 4 || y == 12 || x == 3 || x == 12;
                px(a, x, y, shade(0x9A6A3A, edge ? 0.7f : 0.95f + ((x * y) % 3) * 0.05f));
            }
        }
        return a;
    }

    public static int[] feather(long seed) {
        int[] a = new int[T * T];
        for (int i = 0; i < 10; i++) {
            px(a, 4 + i, 12 - i, argb(0xF0F0EA));
            if (i > 1 && i < 9) {
                px(a, 3 + i, 12 - i, argb(0xDCDCD2));
                px(a, 5 + i, 11 - i, argb(0xDCDCD2));
            }
        }
        return a;
    }

    // ------------------------------------------------------------- HUD icons

    /** Heart: full, half, or empty outline. */
    public static int[] heart(int fill, long seed) { // 2 full, 1 half, 0 empty
        int[] a = new int[T * T];
        int red = 0xD82C2C;
        for (int y = 3; y <= 12; y++) {
            for (int x = 2; x <= 13; x++) {
                boolean inside = heartShape(x, y);
                if (!inside) continue;
                boolean border = !heartShape(x - 1, y) || !heartShape(x + 1, y)
                        || !heartShape(x, y - 1) || !heartShape(x, y + 1);
                if (border) {
                    px(a, x, y, argb(0x2A0808));
                } else if (fill == 2 || (fill == 1 && x <= 7)) {
                    px(a, x, y, shade(red, y < 6 ? 1.1f : 0.9f));
                } else {
                    px(a, x, y, argb(0x3A3A3A));
                }
            }
        }
        return a;
    }

    private static boolean heartShape(int x, int y) {
        double lx = x - 5.0, ly = y - 5.5;
        double rx = x - 10.0, ry = y - 5.5;
        boolean lobes = (lx * lx + ly * ly <= 7) || (rx * rx + ry * ry <= 7);
        boolean tip = y >= 6 && y <= 12 && Math.abs(x - 7.5) <= (12.5 - y);
        return lobes || tip;
    }

    /** Hunger icon (haunch), full or empty. */
    public static int[] hunger(boolean full, long seed) {
        int[] a = drumstick(true, seed);
        if (!full) {
            for (int i = 0; i < a.length; i++) {
                if (a[i] != 0) a[i] = argb(0x3A3A3A);
            }
        }
        return a;
    }

    /** Air bubble. */
    public static int[] bubble(long seed) {
        int[] a = new int[T * T];
        for (int y = 3; y <= 12; y++) {
            for (int x = 3; x <= 12; x++) {
                double d = Math.hypot(x - 7.5, y - 7.5);
                if (d <= 4.6 && d >= 3.4) px(a, x, y, argb(0x8AC8F0));
            }
        }
        px(a, 6, 5, argb(0xD8F0FF));
        px(a, 5, 6, argb(0xD8F0FF));
        return a;
    }
}
