package com.blockforge.entity;

import com.blockforge.render.TextureAtlas;

import java.util.EnumMap;
import java.util.Map;
import java.util.Random;

/**
 * Box-part models and procedurally painted textures for every mob type.
 * Sizes and offsets are in 1/16 block units; pivots are relative to the
 * entity origin (feet center). Like all game art, textures are generated
 * from code — no image files.
 */
public final class MobModels {

    public enum Anim {NONE, HEAD, LEG_A, LEG_B}

    /**
     * One textured box: pivot point, box corner offset from pivot, size.
     * {@code faceTile} (if >= 0) is drawn only on the front (-z) face so
     * eyes/snouts don't wrap around the whole box.
     */
    public record Part(float px, float py, float pz,
                       float ox, float oy, float oz,
                       float sx, float sy, float sz,
                       Anim anim, int tile, int faceTile) {

        public Part(float px, float py, float pz, float ox, float oy, float oz,
                    float sx, float sy, float sz, Anim anim, int tile) {
            this(px, py, pz, ox, oy, oz, sx, sy, sz, anim, tile, -1);
        }
    }

    private static final Map<MobType, Part[]> MODELS = new EnumMap<>(MobType.class);
    private static boolean registered;

    private MobModels() {
    }

    public static Part[] model(MobType type) {
        return MODELS.get(type);
    }

    // ------------------------------------------------------------- textures

    private static final int T = 16;

    private static int shade(int rgb, float m) {
        int r = Math.min(255, (int) (((rgb >> 16) & 0xFF) * m));
        int g = Math.min(255, (int) (((rgb >> 8) & 0xFF) * m));
        int b = Math.min(255, (int) ((rgb & 0xFF) * m));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** Soft-noise hide texture, optionally with blotches of a second color. */
    private static int hide(int base, int blotch, float blotchChance, long seed) {
        Random r = new Random(seed);
        int[] px = new int[T * T];
        for (int i = 0; i < px.length; i++) {
            px[i] = shade(base, 0.85f + r.nextFloat() * 0.3f);
        }
        if (blotchChance > 0) {
            int blobs = 2 + r.nextInt(3);
            for (int b = 0; b < blobs; b++) {
                int cx = r.nextInt(T), cy = r.nextInt(T), s = 2 + r.nextInt(4);
                for (int y = cy - s; y <= cy + s; y++) {
                    for (int x = cx - s; x <= cx + s; x++) {
                        if (x < 0 || x >= T || y < 0 || y >= T) continue;
                        if (Math.abs(x - cx) + Math.abs(y - cy) <= s && r.nextFloat() < 0.8f) {
                            px[y * T + x] = shade(blotch, 0.85f + r.nextFloat() * 0.3f);
                        }
                    }
                }
            }
        }
        return TextureAtlas.register(px);
    }

    /** Face texture: hide base plus simple eyes (and optional snout strip). */
    private static int face(int base, int eye, int snout, long seed) {
        Random r = new Random(seed);
        int[] px = new int[T * T];
        for (int i = 0; i < px.length; i++) {
            px[i] = shade(base, 0.85f + r.nextFloat() * 0.3f);
        }
        // eyes
        for (int dy = 0; dy < 2; dy++) {
            for (int dx = 0; dx < 2; dx++) {
                px[(5 + dy) * T + 3 + dx] = shade(eye, 1f);
                px[(5 + dy) * T + 11 + dx] = shade(eye, 1f);
            }
        }
        if (snout >= 0) {
            for (int y = 9; y <= 12; y++) {
                for (int x = 5; x <= 10; x++) {
                    px[y * T + x] = shade(snout, 0.9f + r.nextFloat() * 0.2f);
                }
            }
        }
        return TextureAtlas.register(px);
    }

    // --------------------------------------------------------------- models

    /** Must run after Blocks registration, before atlas upload. */
    public static void registerAll() {
        if (registered) return;
        registered = true;
        long s = 7000;

        // COW: brown with white blotches
        int cowBody = hide(0x5E4230, 0xE8E4DA, 0.5f, s++);
        int cowHeadSide = hide(0x5E4230, -1, 0, s++);
        int cowFace = face(0x5E4230, 0x1A1A1A, 0xD8CDB5, s++);
        int cowLeg = hide(0x4A3524, -1, 0, s++);
        MODELS.put(MobType.COW, quadruped(12, 10, 18, 8, 8, 6, 10, cowBody, cowHeadSide, cowFace, cowLeg));

        // PIG
        int pigBody = hide(0xE8A0A8, -1, 0, s++);
        int pigFace = face(0xE8A0A8, 0x1A1A1A, 0xD87880, s++);
        int pigLeg = hide(0xD08890, -1, 0, s++);
        MODELS.put(MobType.PIG, quadruped(10, 8, 16, 6, 8, 8, 8, pigBody, pigBody, pigFace, pigLeg));

        // SHEEP: woolly
        int sheepBody = hide(0xE8E6E0, -1, 0, s++);
        int sheepHeadSide = hide(0xD8CDB5, -1, 0, s++);
        int sheepFace = face(0xD8CDB5, 0x1A1A1A, -1, s++);
        int sheepLeg = hide(0xC8BDA5, -1, 0, s++);
        MODELS.put(MobType.SHEEP, quadruped(11, 10, 16, 7, 7, 6, 10, sheepBody, sheepHeadSide, sheepFace, sheepLeg));

        // CHICKEN: small white bird
        int chBody = hide(0xEFEDE5, -1, 0, s++);
        int chFace = face(0xEFEDE5, 0x1A1A1A, 0xE8A83E, s++);
        int chLeg = hide(0xE8A83E, -1, 0, s++);
        MODELS.put(MobType.CHICKEN, new Part[]{
                new Part(0, 5 / 16f, 0, -3 / 16f, 0, -4 / 16f, 6, 6, 8, Anim.NONE, chBody),
                new Part(0, 11 / 16f, -3 / 16f, -2 / 16f, 0, -2 / 16f, 4, 4, 4, Anim.HEAD, chBody, chFace),
                new Part(-1.5f / 16f, 5 / 16f, 0, -0.5f / 16f, -5 / 16f, -0.5f / 16f, 1, 5, 1, Anim.LEG_A, chLeg),
                new Part(1.5f / 16f, 5 / 16f, 0, -0.5f / 16f, -5 / 16f, -0.5f / 16f, 1, 5, 1, Anim.LEG_B, chLeg),
        });

        // ZOMBIE: green humanoid
        int zBody = hide(0x3A6B4A, 0x2E5238, 0.3f, s++);
        int zHeadSide = hide(0x4A8A5A, -1, 0, s++);
        int zFace = face(0x4A8A5A, 0x1A1A1A, -1, s++);
        int zLimb = hide(0x35604A, -1, 0, s++);
        MODELS.put(MobType.ZOMBIE, humanoid(zBody, zHeadSide, zFace, zLimb));

        // SKELETON: pale gray humanoid, thin limbs
        int kBody = hide(0xC8C8C0, 0xA8A8A0, 0.25f, s++);
        int kHeadSide = hide(0xD8D8D0, -1, 0, s++);
        int kFace = face(0xD8D8D0, 0x101010, -1, s++);
        int kLimb = hide(0xC0C0B8, -1, 0, s++);
        Part[] sk = humanoid(kBody, kHeadSide, kFace, kLimb);
        // slim the limbs
        for (int i = 0; i < sk.length; i++) {
            Part p = sk[i];
            if (p.anim() == Anim.LEG_A || p.anim() == Anim.LEG_B) {
                sk[i] = new Part(p.px(), p.py(), p.pz(), -1 / 16f, p.oy(), -1 / 16f, 2, 12, 2, p.anim(), kLimb);
            }
        }
        MODELS.put(MobType.SKELETON, sk);

        // SPIDER: wide dark body + splayed legs
        int spBody = hide(0x24201E, 0x3A322E, 0.35f, s++);
        int spHeadSide = hide(0x2E2826, -1, 0, s++);
        int spFace = face(0x2E2826, 0xC42D2D, -1, s++);
        int spLeg = hide(0x1C1816, -1, 0, s++);
        MODELS.put(MobType.SPIDER, new Part[]{
                new Part(0, 5 / 16f, 2 / 16f, -5 / 16f, 0, -5 / 16f, 10, 8, 10, Anim.NONE, spBody),
                new Part(0, 6 / 16f, -5 / 16f, -3 / 16f, 0, -4 / 16f, 6, 6, 6, Anim.HEAD, spHeadSide, spFace),
                new Part(-5 / 16f, 8 / 16f, -2 / 16f, -8 / 16f, -8 / 16f, -1 / 16f, 8, 8, 2, Anim.LEG_A, spLeg),
                new Part(5 / 16f, 8 / 16f, -2 / 16f, 0, -8 / 16f, -1 / 16f, 8, 8, 2, Anim.LEG_B, spLeg),
                new Part(-5 / 16f, 8 / 16f, 3 / 16f, -8 / 16f, -8 / 16f, -1 / 16f, 8, 8, 2, Anim.LEG_B, spLeg),
                new Part(5 / 16f, 8 / 16f, 3 / 16f, 0, -8 / 16f, -1 / 16f, 8, 8, 2, Anim.LEG_A, spLeg),
        });
    }

    /** Standard four-legged body plan. */
    private static Part[] quadruped(float bw, float bh, float bl, float head,
                                    float legW, float legOff, float legH,
                                    int bodyTile, int headSideTile, int faceTile, int legTile) {
        float legY = legH / 16f;
        float bodyY = legY + bh / 32f;
        return new Part[]{
                // body centered above the legs
                new Part(0, bodyY, 0, -bw / 32f, -bh / 32f, -bl / 32f, bw, bh, bl, Anim.NONE, bodyTile),
                // head at the front (-z); eyes only on the front face
                new Part(0, bodyY + bh / 40f, -bl / 32f - head / 40f,
                        -head / 32f, -head / 40f, -head / 16f, head, head, head, Anim.HEAD, headSideTile, faceTile),
                // legs at the four corners, pivot at the hip
                new Part(-legOff / 32f, legY, -bl / 32f + 2 / 16f, -legW / 64f, -legY, -legW / 64f,
                        legW / 2f, legH, legW / 2f, Anim.LEG_A, legTile),
                new Part(legOff / 32f, legY, -bl / 32f + 2 / 16f, -legW / 64f, -legY, -legW / 64f,
                        legW / 2f, legH, legW / 2f, Anim.LEG_B, legTile),
                new Part(-legOff / 32f, legY, bl / 32f - 2 / 16f, -legW / 64f, -legY, -legW / 64f,
                        legW / 2f, legH, legW / 2f, Anim.LEG_B, legTile),
                new Part(legOff / 32f, legY, bl / 32f - 2 / 16f, -legW / 64f, -legY, -legW / 64f,
                        legW / 2f, legH, legW / 2f, Anim.LEG_A, legTile),
        };
    }

    /** Two-legged body plan with arms. */
    private static Part[] humanoid(int bodyTile, int headSideTile, int faceTile, int limbTile) {
        return new Part[]{
                new Part(0, 18 / 16f, 0, -4 / 16f, -6 / 16f, -2 / 16f, 8, 12, 4, Anim.NONE, bodyTile),
                new Part(0, 24 / 16f, 0, -4 / 16f, 0, -4 / 16f, 8, 8, 8, Anim.HEAD, headSideTile, faceTile),
                new Part(-2 / 16f, 12 / 16f, 0, -2 / 16f, -12 / 16f, -2 / 16f, 4, 12, 4, Anim.LEG_A, limbTile),
                new Part(2 / 16f, 12 / 16f, 0, -2 / 16f, -12 / 16f, -2 / 16f, 4, 12, 4, Anim.LEG_B, limbTile),
                new Part(-6 / 16f, 24 / 16f, 0, -2 / 16f, -12 / 16f, -2 / 16f, 4, 12, 4, Anim.LEG_B, limbTile),
                new Part(6 / 16f, 24 / 16f, 0, -2 / 16f, -12 / 16f, -2 / 16f, 4, 12, 4, Anim.LEG_A, limbTile),
        };
    }
}
