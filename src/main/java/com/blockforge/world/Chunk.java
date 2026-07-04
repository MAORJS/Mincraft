package com.blockforge.world;

import com.blockforge.render.Mesh;

/**
 * A 16 x 128 x 16 column of blocks. Stores block ids only; meshes are built
 * by the renderer and cached here.
 */
public final class Chunk {

    public static final int SX = 16;
    public static final int SY = 128;
    public static final int SZ = 16;

    /** Chunk coordinates (world block x = cx * 16 + local x). */
    public final int cx, cz;

    private final short[] blocks = new short[SX * SY * SZ];

    /** Mesh needs rebuilding. */
    public boolean dirty = true;
    /** Set once terrain generation has finished. */
    public boolean generated = false;

    public Mesh opaqueMesh;
    public Mesh translucentMesh;

    public Chunk(int cx, int cz) {
        this.cx = cx;
        this.cz = cz;
    }

    private static int index(int x, int y, int z) {
        return (x * SZ + z) * SY + y;
    }

    public static boolean inBounds(int x, int y, int z) {
        return x >= 0 && x < SX && y >= 0 && y < SY && z >= 0 && z < SZ;
    }

    public int get(int x, int y, int z) {
        if (!inBounds(x, y, z)) return 0;
        return blocks[index(x, y, z)];
    }

    public void set(int x, int y, int z, int id) {
        if (!inBounds(x, y, z)) return;
        blocks[index(x, y, z)] = (short) id;
    }

    public void deleteMeshes() {
        if (opaqueMesh != null) {
            opaqueMesh.delete();
            opaqueMesh = null;
        }
        if (translucentMesh != null) {
            translucentMesh.delete();
            translucentMesh = null;
        }
    }

    public static long key(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
    }
}
