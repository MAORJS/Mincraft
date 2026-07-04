package com.blockforge.render;

import com.blockforge.world.Block;
import com.blockforge.world.Blocks;
import com.blockforge.world.Chunk;
import com.blockforge.world.World;

/**
 * Turns chunk block data into two vertex arrays: an opaque/cut-out mesh and a
 * translucent mesh (water, glass, ice). Faces between two opaque blocks are
 * skipped; liquids and translucent blocks merge faces with themselves.
 *
 * Vertices are chunk-local; the renderer supplies the chunk offset.
 */
public final class ChunkMesher {

    public static final class Result {
        public final float[] opaque;
        public final float[] translucent;

        Result(float[] opaque, float[] translucent) {
            this.opaque = opaque;
            this.translucent = translucent;
        }
    }

    private static final float SHADE_TOP = 1.0f;
    private static final float SHADE_BOTTOM = 0.55f;
    private static final float SHADE_X = 0.65f;
    private static final float SHADE_Z = 0.82f;

    private ChunkMesher() {
    }

    /** Growable float buffer (avoids ArrayList<Float> boxing). */
    private static final class FloatList {
        float[] a = new float[4096];
        int n = 0;

        void add(float v) {
            if (n == a.length) {
                float[] b = new float[a.length * 2];
                System.arraycopy(a, 0, b, 0, n);
                a = b;
            }
            a[n++] = v;
        }

        float[] toArray() {
            float[] out = new float[n];
            System.arraycopy(a, 0, out, 0, n);
            return out;
        }
    }

    public static Result build(World world, Chunk chunk) {
        FloatList opaque = new FloatList();
        FloatList translucent = new FloatList();
        int ox = chunk.cx * Chunk.SX;
        int oz = chunk.cz * Chunk.SZ;

        for (int x = 0; x < Chunk.SX; x++) {
            for (int z = 0; z < Chunk.SZ; z++) {
                for (int y = 0; y < Chunk.SY; y++) {
                    int id = chunk.get(x, y, z);
                    if (id == 0) continue;
                    Block b = Blocks.get(id);

                    if (b.plant) {
                        addCross(opaque, x, y, z, b);
                        continue;
                    }

                    FloatList target = b.translucent ? translucent : opaque;
                    // face visibility per direction
                    if (faceVisible(world, b, ox + x, y + 1, oz + z))
                        addFace(target, b, x, y, z, Face.TOP);
                    if (faceVisible(world, b, ox + x, y - 1, oz + z))
                        addFace(target, b, x, y, z, Face.BOTTOM);
                    if (faceVisible(world, b, ox + x + 1, y, oz + z))
                        addFace(target, b, x, y, z, Face.EAST);
                    if (faceVisible(world, b, ox + x - 1, y, oz + z))
                        addFace(target, b, x, y, z, Face.WEST);
                    if (faceVisible(world, b, ox + x, y, oz + z + 1))
                        addFace(target, b, x, y, z, Face.SOUTH);
                    if (faceVisible(world, b, ox + x, y, oz + z - 1))
                        addFace(target, b, x, y, z, Face.NORTH);
                }
            }
        }
        return new Result(opaque.toArray(), translucent.toArray());
    }

    private static boolean faceVisible(World world, Block self, int nx, int ny, int nz) {
        if (ny < 0) return false;               // never draw the world's underside
        if (ny >= Chunk.SY) return true;
        int nid = world.getBlockId(nx, ny, nz);
        if (nid == 0) return true;
        Block n = Blocks.get(nid);
        if (n.opaque) return false;             // fully hidden
        if (nid == self.id) return false;       // merge with self (water, glass, leaves)
        if (self.opaque) return true;           // opaque against transparent neighbour
        return true;
    }

    private enum Face {TOP, BOTTOM, EAST, WEST, SOUTH, NORTH}

    /**
     * Corner tables per face. Each face lists 4 corners (x,y,z offsets); the
     * two triangles are (0,1,2) and (0,2,3). UV corners follow the same order:
     * (u0,v1) (u0,v0) (u1,v0) (u1,v1) for side faces so textures stay upright.
     */
    private static final int[][][] CORNERS = {
            // TOP (+Y)
            {{0, 1, 0}, {0, 1, 1}, {1, 1, 1}, {1, 1, 0}},
            // BOTTOM (-Y)
            {{0, 0, 0}, {1, 0, 0}, {1, 0, 1}, {0, 0, 1}},
            // EAST (+X)
            {{1, 1, 0}, {1, 0, 0}, {1, 0, 1}, {1, 1, 1}},
            // WEST (-X)
            {{0, 1, 1}, {0, 0, 1}, {0, 0, 0}, {0, 1, 0}},
            // SOUTH (+Z)
            {{0, 1, 1}, {0, 0, 1}, {1, 0, 1}, {1, 1, 1}},
            // NORTH (-Z)
            {{1, 1, 0}, {1, 0, 0}, {0, 0, 0}, {0, 1, 0}},
    };

    private static void addFace(FloatList out, Block b, int x, int y, int z, Face face) {
        int tile;
        float shade;
        switch (face) {
            case TOP -> {
                tile = b.texTop;
                shade = SHADE_TOP;
            }
            case BOTTOM -> {
                tile = b.texBottom;
                shade = SHADE_BOTTOM;
            }
            case EAST, WEST -> {
                tile = b.texSide;
                shade = SHADE_X;
            }
            default -> {
                tile = b.texSide;
                shade = SHADE_Z;
            }
        }
        if (b.emissive > 0f) {
            shade = -Math.max(0.1f, shade * b.emissive + b.emissive);
        }

        float u0 = TextureAtlas.u0(tile) + TextureAtlas.UV_INSET;
        float v0 = TextureAtlas.v0(tile) + TextureAtlas.UV_INSET;
        float u1 = TextureAtlas.u0(tile) + TextureAtlas.UV_SPAN - TextureAtlas.UV_INSET;
        float v1 = TextureAtlas.v0(tile) + TextureAtlas.UV_SPAN - TextureAtlas.UV_INSET;

        int[][] c = CORNERS[face.ordinal()];
        // UVs matched to corner order: corner 0 and 3 are the "top" edge for
        // side faces (v1 = image top after the atlas row flip)
        float[][] uv = switch (face) {
            case TOP, BOTTOM -> new float[][]{{u0, v1}, {u0, v0}, {u1, v0}, {u1, v1}};
            default -> new float[][]{{u0, v1}, {u0, v0}, {u1, v0}, {u1, v1}};
        };

        int[] order = {0, 1, 2, 0, 2, 3};
        for (int i : order) {
            out.add(x + c[i][0]);
            out.add(y + c[i][1]);
            out.add(z + c[i][2]);
            out.add(uv[i][0]);
            out.add(uv[i][1]);
            out.add(shade);
        }
    }

    /** Two crossed quads for plants. Rendered double-sided (culling is off). */
    private static void addCross(FloatList out, int x, int y, int z, Block b) {
        int tile = b.texSide;
        float u0 = TextureAtlas.u0(tile) + TextureAtlas.UV_INSET;
        float v0 = TextureAtlas.v0(tile) + TextureAtlas.UV_INSET;
        float u1 = TextureAtlas.u0(tile) + TextureAtlas.UV_SPAN - TextureAtlas.UV_INSET;
        float v1 = TextureAtlas.v0(tile) + TextureAtlas.UV_SPAN - TextureAtlas.UV_INSET;
        float shade = 0.95f;

        float[][][] quads = {
                {{0, 1, 0}, {0, 0, 0}, {1, 0, 1}, {1, 1, 1}},
                {{1, 1, 0}, {1, 0, 0}, {0, 0, 1}, {0, 1, 1}},
        };
        float[][] uv = {{u0, v1}, {u0, v0}, {u1, v0}, {u1, v1}};
        int[] order = {0, 1, 2, 0, 2, 3};
        for (float[][] q : quads) {
            for (int i : order) {
                out.add(x + q[i][0]);
                out.add(y + q[i][1]);
                out.add(z + q[i][2]);
                out.add(uv[i][0]);
                out.add(uv[i][1]);
                out.add(shade);
            }
        }
    }
}
