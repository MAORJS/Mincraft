package com.blockforge.world;

import com.blockforge.save.WorldStorage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Infinite chunked world. Chunks are generated lazily around the player with
 * a small per-frame budget so the game never stalls. Player-modified chunks
 * are persisted through the optional {@link WorldStorage}.
 */
public final class World {

    public static final int SEA_LEVEL = 62;

    public final long seed;
    public final TerrainGenerator generator;
    private final WorldStorage storage; // may be null (unsaved world)

    private final Map<Long, Chunk> chunks = new HashMap<>();

    public World(long seed) {
        this(seed, null);
    }

    public World(long seed, WorldStorage storage) {
        this.seed = seed;
        this.storage = storage;
        this.generator = new TerrainGenerator(seed);
    }

    public Chunk getChunk(int cx, int cz) {
        return chunks.get(Chunk.key(cx, cz));
    }

    public Chunk getOrCreateChunk(int cx, int cz) {
        return chunks.computeIfAbsent(Chunk.key(cx, cz), k -> new Chunk(cx, cz));
    }

    public java.util.Collection<Chunk> loadedChunks() {
        return chunks.values();
    }

    public int getBlockId(int wx, int wy, int wz) {
        if (wy < 0 || wy >= Chunk.SY) return 0;
        Chunk c = getChunk(Math.floorDiv(wx, Chunk.SX), Math.floorDiv(wz, Chunk.SZ));
        if (c == null || !c.generated) return 0;
        return c.get(Math.floorMod(wx, Chunk.SX), wy, Math.floorMod(wz, Chunk.SZ));
    }

    public Block getBlock(int wx, int wy, int wz) {
        return Blocks.get(getBlockId(wx, wy, wz));
    }

    /** Sets a block and marks the containing (and adjacent, if on an edge) chunks dirty. */
    public void setBlock(int wx, int wy, int wz, int id) {
        if (wy < 0 || wy >= Chunk.SY) return;
        int cx = Math.floorDiv(wx, Chunk.SX);
        int cz = Math.floorDiv(wz, Chunk.SZ);
        Chunk c = getChunk(cx, cz);
        if (c == null) return;
        int lx = Math.floorMod(wx, Chunk.SX);
        int lz = Math.floorMod(wz, Chunk.SZ);
        c.set(lx, wy, lz, id);
        c.dirty = true;
        c.modified = true;
        if (lx == 0) markDirty(cx - 1, cz);
        if (lx == Chunk.SX - 1) markDirty(cx + 1, cz);
        if (lz == 0) markDirty(cx, cz - 1);
        if (lz == Chunk.SZ - 1) markDirty(cx, cz + 1);
    }

    private void markDirty(int cx, int cz) {
        Chunk c = getChunk(cx, cz);
        if (c != null) c.dirty = true;
    }

    /**
     * Ensures chunks around the given block position are generated, budgeted
     * per call. Returns the number of chunks generated.
     */
    public int ensureChunksAround(double px, double pz, int radius, int budget) {
        int pcx = Math.floorDiv((int) Math.floor(px), Chunk.SX);
        int pcz = Math.floorDiv((int) Math.floor(pz), Chunk.SZ);
        int generated = 0;

        // spiral out from the player so nearby chunks come in first
        for (int r = 0; r <= radius && generated < budget; r++) {
            for (int dx = -r; dx <= r && generated < budget; dx++) {
                for (int dz = -r; dz <= r && generated < budget; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    Chunk c = getOrCreateChunk(pcx + dx, pcz + dz);
                    if (!c.generated) {
                        if (storage != null && storage.loadChunk(c)) {
                            c.modified = true; // keep it on disk across sessions
                        } else {
                            generator.generate(c);
                        }
                        c.generated = true;
                        c.dirty = true;
                        markDirty(c.cx - 1, c.cz);
                        markDirty(c.cx + 1, c.cz);
                        markDirty(c.cx, c.cz - 1);
                        markDirty(c.cx, c.cz + 1);
                        generated++;
                    }
                }
            }
        }
        return generated;
    }

    /** Unloads chunks far outside the view radius, freeing their GPU meshes. */
    public void unloadFarChunks(double px, double pz, int keepRadius) {
        int pcx = Math.floorDiv((int) Math.floor(px), Chunk.SX);
        int pcz = Math.floorDiv((int) Math.floor(pz), Chunk.SZ);
        List<Chunk> doomed = new ArrayList<>();
        for (Iterator<Map.Entry<Long, Chunk>> it = chunks.entrySet().iterator(); it.hasNext(); ) {
            Chunk c = it.next().getValue();
            if (Math.max(Math.abs(c.cx - pcx), Math.abs(c.cz - pcz)) > keepRadius) {
                doomed.add(c);
                it.remove();
            }
        }
        for (Chunk c : doomed) {
            if (storage != null && c.modified) {
                storage.saveChunk(c);
            }
            c.deleteMeshes();
        }
    }

    /** Writes every player-modified chunk to disk (autosave / quit). */
    public int saveModifiedChunks() {
        if (storage == null) return 0;
        int saved = 0;
        for (Chunk c : chunks.values()) {
            if (c.modified) {
                storage.saveChunk(c);
                saved++;
            }
        }
        return saved;
    }

    /** Highest non-air block at (wx, wz), or SEA_LEVEL if the chunk is missing. */
    public int surfaceHeight(int wx, int wz) {
        for (int y = Chunk.SY - 1; y >= 0; y--) {
            int id = getBlockId(wx, y, wz);
            if (id != 0 && !Blocks.get(id).plant) return y;
        }
        return SEA_LEVEL;
    }
}
