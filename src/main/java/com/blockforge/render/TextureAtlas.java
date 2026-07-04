package com.blockforge.render;

import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL33C.*;

/**
 * Runtime-built texture atlas. Every tile is generated procedurally by
 * {@link Tiles}; the game ships no image assets at all.
 *
 * Tiles are 16x16 ARGB pixel arrays registered during block registration,
 * then packed into a single 512x512 RGBA texture on the render thread.
 */
public final class TextureAtlas {

    public static final int TILE = 16;
    public static final int GRID = 32;              // 32x32 tiles
    public static final int SIZE = TILE * GRID;     // 512 px

    private static final List<int[]> TILES = new ArrayList<>();

    private TextureAtlas() {
    }

    /** Registers a 16x16 ARGB tile and returns its atlas index. */
    public static int register(int[] argbPixels) {
        if (argbPixels.length != TILE * TILE) {
            throw new IllegalArgumentException("tile must be 16x16");
        }
        if (TILES.size() >= GRID * GRID) {
            throw new IllegalStateException("texture atlas is full");
        }
        TILES.add(argbPixels);
        return TILES.size() - 1;
    }

    public static float u0(int tile) {
        return (tile % GRID) / (float) GRID;
    }

    public static float v0(int tile) {
        return (tile / GRID) / (float) GRID;
    }

    /** Width of one tile in UV space, inset slightly to avoid atlas bleeding. */
    public static final float UV_INSET = 0.0005f;
    public static final float UV_SPAN = 1f / GRID;

    /** Uploads all registered tiles into a GL texture and returns its id. */
    public static int createGLTexture() {
        ByteBuffer buf = BufferUtils.createByteBuffer(SIZE * SIZE * 4);
        for (int t = 0; t < TILES.size(); t++) {
            int[] px = TILES.get(t);
            int ox = (t % GRID) * TILE;
            int oy = (t / GRID) * TILE;
            for (int y = 0; y < TILE; y++) {
                for (int x = 0; x < TILE; x++) {
                    int argb = px[y * TILE + x];
                    // flip rows within the cell: GL's v axis points up, our
                    // tile arrays are stored top-down
                    int idx = ((oy + (TILE - 1 - y)) * SIZE + (ox + x)) * 4;
                    buf.put(idx,     (byte) ((argb >> 16) & 0xFF));
                    buf.put(idx + 1, (byte) ((argb >> 8) & 0xFF));
                    buf.put(idx + 2, (byte) (argb & 0xFF));
                    buf.put(idx + 3, (byte) ((argb >> 24) & 0xFF));
                }
            }
        }

        int tex = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, tex);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, SIZE, SIZE, 0, GL_RGBA, GL_UNSIGNED_BYTE, buf);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        return tex;
    }
}
