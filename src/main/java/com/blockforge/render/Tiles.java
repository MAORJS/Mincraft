package com.blockforge.render;

import java.util.Random;

/**
 * Procedural 16x16 tile painters. All artwork in the game is generated here
 * from math and seeded randomness — original by construction.
 *
 * Colors are ARGB ints. Alpha 0 pixels are cut out (plants, leaves, glass
 * borders) or blended (translucent pass).
 */
public final class Tiles {

    private static final int T = 16;

    private Tiles() {
    }

    // ------------------------------------------------------------------ util

    private static int argb(int a, int r, int g, int b) {
        return (clamp(a) << 24) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    private static int shade(int rgb, float mul) {
        int r = (int) (((rgb >> 16) & 0xFF) * mul);
        int g = (int) (((rgb >> 8) & 0xFF) * mul);
        int b = (int) ((rgb & 0xFF) * mul);
        return argb(255, r, g, b);
    }

    private static int opaque(int rgb) {
        return 0xFF000000 | (rgb & 0xFFFFFF);
    }

    private static int withAlpha(int rgb, int a) {
        return (clamp(a) << 24) | (rgb & 0xFFFFFF);
    }

    private static Random rng(long seed) {
        return new Random(seed * 0x9E3779B97F4A7C15L + 12345L);
    }

    private static int[] fill(int rgb) {
        int[] px = new int[T * T];
        java.util.Arrays.fill(px, opaque(rgb));
        return px;
    }

    // -------------------------------------------------------------- painters

    /** Rocky texture: base color with clustered luminance noise. */
    public static int[] stone(int base, long seed) {
        Random r = rng(seed);
        int[] px = new int[T * T];
        float[] lum = new float[T * T];
        for (int i = 0; i < lum.length; i++) lum[i] = 0.85f + r.nextFloat() * 0.3f;
        // small blur pass for clumpy look
        for (int y = 0; y < T; y++) {
            for (int x = 0; x < T; x++) {
                float sum = 0;
                int n = 0;
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = (x + dx + T) % T, ny = (y + dy + T) % T;
                        sum += lum[ny * T + nx];
                        n++;
                    }
                }
                px[y * T + x] = shade(base, sum / n);
            }
        }
        return px;
    }

    /** Grainy texture: base with scattered lighter/darker specks (dirt, sand, gravel). */
    public static int[] speckle(int base, int accent, float density, long seed) {
        Random r = rng(seed);
        int[] px = new int[T * T];
        for (int i = 0; i < px.length; i++) {
            float m = 0.9f + r.nextFloat() * 0.2f;
            px[i] = r.nextFloat() < density ? shade(accent, m) : shade(base, m);
        }
        return px;
    }

    /** Soft cloth texture with a subtle woven pattern. */
    public static int[] cloth(int base, long seed) {
        Random r = rng(seed);
        int[] px = new int[T * T];
        for (int y = 0; y < T; y++) {
            for (int x = 0; x < T; x++) {
                float weave = ((x + y) % 2 == 0) ? 1.0f : 0.94f;
                float m = weave * (0.95f + r.nextFloat() * 0.08f);
                px[y * T + x] = shade(base, m);
            }
        }
        // darker border stitching
        for (int i = 0; i < T; i++) {
            px[i] = shade(base, 0.85f);
            px[(T - 1) * T + i] = shade(base, 0.85f);
            px[i * T] = shade(base, 0.85f);
            px[i * T + T - 1] = shade(base, 0.85f);
        }
        return px;
    }

    /** Grass/foliage top: two-tone green noise. */
    public static int[] grassTop(int base, long seed) {
        Random r = rng(seed);
        int[] px = new int[T * T];
        for (int i = 0; i < px.length; i++) {
            px[i] = shade(base, 0.82f + r.nextFloat() * 0.36f);
        }
        return px;
    }

    /** Dirt side with an irregular grass strip on top. */
    public static int[] grassSide(int dirt, int grass, long seed) {
        int[] px = speckle(dirt, shade(dirt, 0.8f) & 0xFFFFFF, 0.25f, seed);
        Random r = rng(seed + 77);
        for (int x = 0; x < T; x++) {
            int depth = 2 + r.nextInt(3);
            for (int y = 0; y < depth; y++) {
                px[y * T + x] = shade(grass, 0.85f + r.nextFloat() * 0.3f);
            }
        }
        return px;
    }

    /** Bark: vertical grain stripes. */
    public static int[] logSide(int bark, long seed) {
        Random r = rng(seed);
        int[] px = new int[T * T];
        float[] col = new float[T];
        for (int x = 0; x < T; x++) col[x] = 0.75f + r.nextFloat() * 0.4f;
        for (int y = 0; y < T; y++) {
            for (int x = 0; x < T; x++) {
                float m = col[x] * (0.92f + r.nextFloat() * 0.16f);
                px[y * T + x] = shade(bark, m);
            }
        }
        return px;
    }

    /** Cut log end: concentric rings. */
    public static int[] logTop(int bark, int inner, long seed) {
        int[] px = new int[T * T];
        Random r = rng(seed);
        for (int y = 0; y < T; y++) {
            for (int x = 0; x < T; x++) {
                float dx = x - 7.5f, dy = y - 7.5f;
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                if (d > 7.2f) {
                    px[y * T + x] = shade(bark, 0.8f + r.nextFloat() * 0.2f);
                } else {
                    float ring = (float) Math.sin(d * 2.1f) * 0.08f;
                    px[y * T + x] = shade(inner, 0.92f + ring + r.nextFloat() * 0.06f);
                }
            }
        }
        return px;
    }

    /** Horizontal boards with seams and nail dots. */
    public static int[] planks(int base, long seed) {
        Random r = rng(seed);
        int[] px = new int[T * T];
        for (int y = 0; y < T; y++) {
            float board = 0.85f + (0.15f * (((y / 4) * 31 + (int) (seed % 7)) % 3) / 2f);
            for (int x = 0; x < T; x++) {
                float m = board * (0.94f + r.nextFloat() * 0.12f);
                if (y % 4 == 3) m *= 0.72f; // seam
                px[y * T + x] = shade(base, m);
            }
        }
        return px;
    }

    /** Classic brick pattern with mortar. */
    public static int[] bricks(int brick, int mortar, long seed) {
        Random r = rng(seed);
        int[] px = new int[T * T];
        for (int y = 0; y < T; y++) {
            int row = y / 4;
            boolean mortarRow = (y % 4 == 3);
            for (int x = 0; x < T; x++) {
                int xo = (row % 2 == 0) ? x : x + 4;
                boolean mortarCol = (xo % 8 == 7);
                if (mortarRow || mortarCol) {
                    px[y * T + x] = shade(mortar, 0.9f + r.nextFloat() * 0.2f);
                } else {
                    px[y * T + x] = shade(brick, 0.88f + r.nextFloat() * 0.24f);
                }
            }
        }
        return px;
    }

    /** Large 8x8 cut-stone tiles. */
    public static int[] stoneBricks(int base, long seed) {
        Random r = rng(seed);
        int[] px = new int[T * T];
        for (int y = 0; y < T; y++) {
            for (int x = 0; x < T; x++) {
                boolean seam = (x % 8 == 7) || (y % 8 == 7);
                float m = seam ? 0.6f : 0.88f + r.nextFloat() * 0.22f;
                px[y * T + x] = shade(base, m);
            }
        }
        return px;
    }

    /** Scattered patches of an accent color (moss, cracks). */
    public static int[] ore(int stoneBase, int mineral, long seed) {
        int[] px = stone(stoneBase, seed);
        Random r = rng(seed + 999);
        int blobs = 3 + r.nextInt(3);
        for (int b = 0; b < blobs; b++) {
            int cx = 2 + r.nextInt(12), cy = 2 + r.nextInt(12);
            int size = 2 + r.nextInt(3);
            for (int i = 0; i < size * 3; i++) {
                int x = clampT(cx + r.nextInt(size) - size / 2);
                int y = clampT(cy + r.nextInt(size) - size / 2);
                px[y * T + x] = shade(mineral, 0.85f + r.nextFloat() * 0.3f);
            }
        }
        return px;
    }

    /**
     * Ore block: bold diamond-shaped nuggets with a dark rim, bright body and
     * sparkle highlight — high contrast so each ore reads clearly at a
     * distance.
     */
    public static int[] oreNuggets(int stoneBase, int mineral, long seed) {
        int[] px = stone(stoneBase, seed);
        Random r = rng(seed + 999);
        int rim = shade(mineral, 0.35f);
        int nuggets = 4 + r.nextInt(2);
        for (int n = 0; n < nuggets; n++) {
            int cx = 3 + r.nextInt(10);
            int cy = 3 + r.nextInt(10);
            int rad = 2 + (n == 0 ? 1 : r.nextInt(2)); // one big nugget guaranteed
            for (int dy = -rad; dy <= rad; dy++) {
                for (int dx = -rad; dx <= rad; dx++) {
                    int d = Math.abs(dx) + Math.abs(dy);
                    if (d > rad) continue;
                    int x = clampT(cx + dx), y = clampT(cy + dy);
                    if (d == rad) {
                        px[y * T + x] = rim;                              // dark outline
                    } else if (d <= rad - 2 && dx <= 0 && dy <= 0) {
                        px[y * T + x] = shade(mineral, 1.35f);            // lit corner
                    } else {
                        px[y * T + x] = shade(mineral, 0.95f + r.nextFloat() * 0.2f);
                    }
                }
            }
            // sparkle
            px[clampT(cy - 1) * T + clampT(cx - 1)] = 0xFFFFFFFF;
        }
        return px;
    }

    private static int clampT(int v) {
        return Math.max(0, Math.min(T - 1, v));
    }

    /** Solid refined mineral block with beveled border. */
    public static int[] mineralBlock(int base, long seed) {
        Random r = rng(seed);
        int[] px = new int[T * T];
        for (int y = 0; y < T; y++) {
            for (int x = 0; x < T; x++) {
                float m = 0.95f + r.nextFloat() * 0.08f;
                if (x == 0 || y == 0) m = 1.15f;
                if (x == T - 1 || y == T - 1) m = 0.7f;
                if ((x + y) % 6 == 0) m *= 1.06f; // sheen
                px[y * T + x] = shade(base, m);
            }
        }
        return px;
    }

    /** Smooth polished surface with a soft vertical gradient and border. */
    public static int[] polished(int base, long seed) {
        Random r = rng(seed);
        int[] px = new int[T * T];
        for (int y = 0; y < T; y++) {
            float g = 1.02f - y * 0.012f;
            for (int x = 0; x < T; x++) {
                float m = g * (0.97f + r.nextFloat() * 0.05f);
                if (x == 0 || y == 0 || x == T - 1 || y == T - 1) m *= 0.85f;
                px[y * T + x] = shade(base, m);
            }
        }
        return px;
    }

    /** Foliage with cut-out holes. */
    public static int[] leaves(int base, long seed) {
        Random r = rng(seed);
        int[] px = new int[T * T];
        for (int i = 0; i < px.length; i++) {
            if (r.nextFloat() < 0.18f) {
                px[i] = 0; // hole
            } else {
                px[i] = shade(base, 0.7f + r.nextFloat() * 0.5f);
            }
        }
        return px;
    }

    /** Liquid surface: soft noise, partially transparent. */
    public static int[] liquid(int base, int alpha, long seed) {
        Random r = rng(seed);
        int[] px = new int[T * T];
        for (int y = 0; y < T; y++) {
            for (int x = 0; x < T; x++) {
                float wave = (float) Math.sin((x + y * 0.5f) * 0.8f) * 0.06f;
                float m = 0.9f + wave + r.nextFloat() * 0.12f;
                px[y * T + x] = withAlpha(shade(base, m), alpha);
            }
        }
        return px;
    }

    /** Glass: transparent center, solid frame, sparse highlights. */
    public static int[] glass(int tint, int centerAlpha, long seed) {
        Random r = rng(seed);
        int[] px = new int[T * T];
        for (int y = 0; y < T; y++) {
            for (int x = 0; x < T; x++) {
                boolean frame = x == 0 || y == 0 || x == T - 1 || y == T - 1;
                if (frame) {
                    px[y * T + x] = withAlpha(shade(tint, 0.95f), 230);
                } else if ((x + y) % 9 == 2 && r.nextFloat() < 0.5f) {
                    px[y * T + x] = withAlpha(0xFFFFFF, 90); // streak highlight
                } else {
                    px[y * T + x] = withAlpha(tint, centerAlpha);
                }
            }
        }
        return px;
    }

    /** Glowing mottled surface (lamps). */
    public static int[] glow(int base, long seed) {
        Random r = rng(seed);
        int[] px = new int[T * T];
        for (int i = 0; i < px.length; i++) {
            float m = 0.85f + r.nextFloat() * 0.35f;
            px[i] = shade(base, m);
        }
        // bright hot spots
        for (int i = 0; i < 10; i++) {
            px[r.nextInt(px.length)] = argb(255, 255, 255, 220);
        }
        return px;
    }

    /** Crossed-quad plant: grass tuft. */
    public static int[] tuft(int base, long seed) {
        Random r = rng(seed);
        int[] px = new int[T * T];
        for (int b = 0; b < 9; b++) {
            int x = 2 + r.nextInt(12);
            int h = 5 + r.nextInt(9);
            int lean = r.nextInt(3) - 1;
            for (int y = 0; y < h; y++) {
                int yy = T - 1 - y;
                int xx = clampT(x + (y > h / 2 ? lean : 0));
                px[yy * T + xx] = shade(base, 0.75f + r.nextFloat() * 0.45f);
            }
        }
        return px;
    }

    /** Crossed-quad plant: flower with colored head. */
    public static int[] flower(int stem, int petal, int center, long seed) {
        Random r = rng(seed);
        int[] px = new int[T * T];
        int sx = 7 + r.nextInt(2);
        for (int y = 6; y < T; y++) {
            px[y * T + sx] = shade(stem, 0.85f + r.nextFloat() * 0.2f);
        }
        // leaf
        px[11 * T + sx + 1] = shade(stem, 1.0f);
        px[12 * T + sx - 1] = shade(stem, 0.9f);
        // petals around (sx, 4)
        int cx = sx, cy = 4;
        int[][] offs = {{0, -2}, {0, 2}, {-2, 0}, {2, 0}, {-1, -1}, {1, -1}, {-1, 1}, {1, 1}};
        for (int[] o : offs) {
            px[clampT(cy + o[1]) * T + clampT(cx + o[0])] = shade(petal, 0.9f + r.nextFloat() * 0.2f);
        }
        px[cy * T + cx] = opaque(center);
        return px;
    }

    /** Crossed-quad plant: mushroom. */
    public static int[] mushroom(int cap, int stalk, long seed) {
        Random r = rng(seed);
        int[] px = new int[T * T];
        for (int y = 9; y < T; y++) {
            px[y * T + 7] = shade(stalk, 0.9f + r.nextFloat() * 0.15f);
            px[y * T + 8] = shade(stalk, 0.85f + r.nextFloat() * 0.15f);
        }
        for (int y = 5; y < 9; y++) {
            int w = (y == 5) ? 3 : 5;
            for (int x = 8 - w / 2 - 1; x <= 7 + w / 2 + 1; x++) {
                if (x < 0 || x >= T) continue;
                px[y * T + x] = shade(cap, 0.85f + r.nextFloat() * 0.25f);
            }
        }
        return px;
    }

    /** Crossed-quad plant: sapling. */
    public static int[] sapling(int leaf, int trunk, long seed) {
        Random r = rng(seed);
        int[] px = new int[T * T];
        for (int y = 8; y < T; y++) px[y * T + 7] = shade(trunk, 0.9f);
        for (int y = 3; y < 10; y++) {
            int w = (y < 6) ? 2 : 3;
            for (int x = 7 - w; x <= 7 + w; x++) {
                if (r.nextFloat() < 0.75f) {
                    px[y * T + clampT(x)] = shade(leaf, 0.75f + r.nextFloat() * 0.4f);
                }
            }
        }
        return px;
    }

    /** Crossed-quad plant: dead shrub. */
    public static int[] deadBush(int wood, long seed) {
        Random r = rng(seed);
        int[] px = new int[T * T];
        for (int b = 0; b < 6; b++) {
            int x = 7, y = T - 1;
            int dx = r.nextInt(3) - 1;
            int len = 6 + r.nextInt(7);
            for (int i = 0; i < len && y >= 0; i++) {
                px[y * T + clampT(x)] = shade(wood, 0.8f + r.nextFloat() * 0.3f);
                y--;
                if (r.nextFloat() < 0.4f) x += dx;
            }
        }
        return px;
    }

    /** Cactus side: green with vertical ribs and spines. */
    public static int[] cactusSide(int base, long seed) {
        Random r = rng(seed);
        int[] px = new int[T * T];
        for (int y = 0; y < T; y++) {
            for (int x = 0; x < T; x++) {
                float m = (x % 4 == 0) ? 0.78f : 0.95f + r.nextFloat() * 0.1f;
                px[y * T + x] = shade(base, m);
            }
        }
        for (int i = 0; i < 8; i++) {
            px[r.nextInt(T) * T + r.nextInt(T)] = argb(255, 230, 230, 200);
        }
        return px;
    }

    /** Bookcase side: planks with rows of colored book spines. */
    public static int[] bookshelf(int plank, long seed) {
        int[] px = planks(plank, seed);
        Random r = rng(seed + 5);
        int[] spineColors = {0xA03030, 0x306040, 0x304880, 0x907030, 0x604080, 0x806040};
        for (int shelfRow = 0; shelfRow < 2; shelfRow++) {
            int y0 = 2 + shelfRow * 7;
            int x = 2;
            while (x < 14) {
                int w = 1 + r.nextInt(2);
                int c = spineColors[r.nextInt(spineColors.length)];
                for (int y = y0; y < y0 + 5; y++) {
                    for (int xx = x; xx < Math.min(x + w, 14); xx++) {
                        px[y * T + xx] = shade(c, 0.85f + r.nextFloat() * 0.3f);
                    }
                }
                x += w;
            }
        }
        return px;
    }

    /** Workbench top: planks with a tool-grid engraving. */
    public static int[] workbenchTop(int plank, long seed) {
        int[] px = planks(plank, seed);
        for (int i = 3; i <= 12; i++) {
            px[3 * T + i] = shade(plank, 0.55f);
            px[12 * T + i] = shade(plank, 0.55f);
            px[i * T + 3] = shade(plank, 0.55f);
            px[i * T + 12] = shade(plank, 0.55f);
            px[8 * T + i] = shade(plank, 0.6f);
            px[i * T + 8] = shade(plank, 0.6f);
        }
        return px;
    }

    /** Furnace front: stone with a dark opening and ember glow. */
    public static int[] furnaceFront(int base, boolean lit, long seed) {
        int[] px = stone(base, seed);
        Random r = rng(seed + 9);
        for (int y = 8; y < 14; y++) {
            for (int x = 4; x < 12; x++) {
                if (lit && y > 10 && r.nextFloat() < 0.5f) {
                    px[y * T + x] = argb(255, 255, 120 + r.nextInt(100), 20);
                } else {
                    px[y * T + x] = argb(255, 20, 18, 16);
                }
            }
        }
        return px;
    }

    /** Explosive crate: red body with a warning band. */
    public static int[] blastBoxSide(long seed) {
        Random r = rng(seed);
        int[] px = new int[T * T];
        for (int y = 0; y < T; y++) {
            for (int x = 0; x < T; x++) {
                int c = (y >= 6 && y <= 9) ? 0xEDE6D5 : 0xB3312C;
                px[y * T + x] = shade(c, 0.9f + r.nextFloat() * 0.2f);
            }
        }
        // fuse markings on the band
        for (int x = 2; x < 14; x += 3) {
            px[7 * T + x] = opaque(0x222222);
            px[8 * T + x] = opaque(0x222222);
        }
        return px;
    }

    /** Carved glowing face (lantern pumpkin front). */
    public static int[] carvedPumpkin(int base, boolean lit, long seed) {
        int[] px = stone(base, seed);
        int glowc = lit ? argb(255, 255, 220, 90) : argb(255, 25, 20, 12);
        // eyes
        for (int[] e : new int[][]{{4, 5}, {5, 5}, {10, 5}, {11, 5}, {4, 6}, {5, 6}, {10, 6}, {11, 6}}) {
            px[e[1] * T + e[0]] = glowc;
        }
        // jagged mouth
        for (int x = 3; x <= 12; x++) {
            px[10 * T + x] = glowc;
            if (x % 3 == 0) px[9 * T + x] = glowc;
            if (x % 3 == 1) px[11 * T + x] = glowc;
        }
        return px;
    }

    /** Checkered floor tile. */
    public static int[] checker(int a, int b, long seed) {
        Random r = rng(seed);
        int[] px = new int[T * T];
        for (int y = 0; y < T; y++) {
            for (int x = 0; x < T; x++) {
                int c = ((x / 4 + y / 4) % 2 == 0) ? a : b;
                px[y * T + x] = shade(c, 0.94f + r.nextFloat() * 0.1f);
            }
        }
        return px;
    }

    /** Melon/pumpkin style rind: striped. */
    public static int[] rind(int dark, int light, long seed) {
        Random r = rng(seed);
        int[] px = new int[T * T];
        for (int y = 0; y < T; y++) {
            for (int x = 0; x < T; x++) {
                int c = (x % 4 < 2) ? dark : light;
                px[y * T + x] = shade(c, 0.9f + r.nextFloat() * 0.18f);
            }
        }
        return px;
    }

    /** Plain solid color with light noise (concrete-style). */
    public static int[] plaster(int base, long seed) {
        Random r = rng(seed);
        int[] px = new int[T * T];
        for (int i = 0; i < px.length; i++) {
            px[i] = shade(base, 0.96f + r.nextFloat() * 0.06f);
        }
        return px;
    }

    /** Fired ceramic: base with darker swirls. */
    public static int[] ceramic(int base, long seed) {
        Random r = rng(seed);
        int[] px = new int[T * T];
        for (int y = 0; y < T; y++) {
            for (int x = 0; x < T; x++) {
                float sw = (float) Math.sin(x * 0.7f + y * 0.4f + (seed % 10)) * 0.07f;
                px[y * T + x] = shade(base, 0.9f + sw + r.nextFloat() * 0.08f);
            }
        }
        return px;
    }

    /** Snow: near-white with faint sparkle. */
    public static int[] snow(long seed) {
        Random r = rng(seed);
        int[] px = new int[T * T];
        for (int i = 0; i < px.length; i++) {
            int v = 238 + r.nextInt(18);
            px[i] = argb(255, v, v, Math.min(255, v + 4));
        }
        return px;
    }

    /** Ice: pale blue, translucent, with cracks. */
    public static int[] ice(int alpha, long seed) {
        Random r = rng(seed);
        int[] px = new int[T * T];
        for (int i = 0; i < px.length; i++) {
            int v = 200 + r.nextInt(30);
            px[i] = withAlpha(argb(255, v - 60, v - 20, 255) & 0xFFFFFF, alpha);
        }
        int x = r.nextInt(T), y = 0;
        while (y < T) { // one crack
            px[y * T + clampT(x)] = withAlpha(0xFFFFFF, Math.min(255, alpha + 40));
            y += 1 + r.nextInt(2);
            x += r.nextInt(3) - 1;
        }
        return px;
    }

    /** Chest side: planks with a metal band and latch. */
    public static int[] chestSide(int plank, long seed) {
        int[] px = planks(plank, seed);
        for (int x = 0; x < T; x++) {
            px[7 * T + x] = shade(0x4A4A4A, 0.9f);
            px[8 * T + x] = shade(0x5A5A5A, 0.95f);
        }
        // latch
        for (int y = 6; y <= 9; y++) {
            px[y * T + 7] = shade(0x8A8A8A, 1.0f);
            px[y * T + 8] = shade(0x707070, 0.95f);
        }
        return px;
    }

    /** Spawner cage: dark base with a bar lattice. */
    public static int[] cage(int dark, int bar, long seed) {
        Random r = rng(seed);
        int[] px = new int[T * T];
        for (int i = 0; i < px.length; i++) {
            px[i] = shade(dark, 0.85f + r.nextFloat() * 0.25f);
        }
        for (int i = 0; i < T; i++) {
            for (int k = 1; k < T; k += 4) {
                px[i * T + k] = shade(bar, 0.85f + r.nextFloat() * 0.2f);
                px[k * T + i] = shade(bar, 0.85f + r.nextFloat() * 0.2f);
            }
        }
        return px;
    }

    /** Sponge: yellow with dark pores. */
    public static int[] sponge(long seed) {
        Random r = rng(seed);
        int[] px = new int[T * T];
        for (int i = 0; i < px.length; i++) {
            px[i] = shade(0xC9B93B, 0.9f + r.nextFloat() * 0.2f);
        }
        for (int i = 0; i < 22; i++) {
            px[r.nextInt(px.length)] = shade(0x8A7D1E, 0.8f);
        }
        return px;
    }
}
