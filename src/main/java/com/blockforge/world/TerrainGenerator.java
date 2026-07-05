package com.blockforge.world;

import java.util.Random;

/**
 * Original terrain generator: biomes from temperature/moisture noise fields,
 * fractal heightmaps, 3D-noise caves, depth-tiered ore veins, trees, plants
 * and surface decoration.
 */
public final class TerrainGenerator {

    /** Receives block-entity setup requests from structure generation. */
    public interface StructureSink {
        void chestLoot(int wx, int wy, int wz, String lootTable);

        void spawner(int wx, int wy, int wz, com.blockforge.entity.MobType mobType);
    }

    private static final StructureSink NULL_SINK = new StructureSink() {
        public void chestLoot(int wx, int wy, int wz, String lootTable) {
        }

        public void spawner(int wx, int wy, int wz, com.blockforge.entity.MobType mobType) {
        }
    };

    private final long seed;
    private final Noise heightNoise;
    private final Noise detailNoise;
    private final Noise mountainNoise;
    private final Noise tempNoise;
    private final Noise moistNoise;
    private final Noise caveNoise;
    private final Noise tunnelNoiseA;
    private final Noise tunnelNoiseB;
    private final Noise cavernNoise;

    public TerrainGenerator(long seed) {
        this.seed = seed;
        heightNoise = new Noise(seed);
        detailNoise = new Noise(seed + 101);
        mountainNoise = new Noise(seed + 202);
        tempNoise = new Noise(seed + 303);
        moistNoise = new Noise(seed + 404);
        caveNoise = new Noise(seed + 505);
        tunnelNoiseA = new Noise(seed + 606);
        tunnelNoiseB = new Noise(seed + 707);
        cavernNoise = new Noise(seed + 808);
    }

    private enum Biome {PLAINS, FOREST, DESERT, TUNDRA, MOUNTAINS}

    private Biome biomeAt(int wx, int wz) {
        float t = tempNoise.fractal2(wx * 0.0025f, wz * 0.0025f, 3, 2f, 0.5f);
        float m = moistNoise.fractal2(wx * 0.0025f, wz * 0.0025f, 3, 2f, 0.5f);
        float mountain = mountainNoise.ridged2(wx * 0.002f, wz * 0.002f, 4);
        if (mountain > 1.28f) return Biome.MOUNTAINS;
        if (t < -0.25f) return Biome.TUNDRA;
        if (t > 0.3f && m < -0.05f) return Biome.DESERT;
        if (m > 0.05f) return Biome.FOREST;
        return Biome.PLAINS;
    }

    private int heightAt(int wx, int wz) {
        float base = heightNoise.fractal2(wx * 0.006f, wz * 0.006f, 4, 2f, 0.5f);
        float detail = detailNoise.fractal2(wx * 0.03f, wz * 0.03f, 3, 2f, 0.5f);
        float mountain = mountainNoise.ridged2(wx * 0.002f, wz * 0.002f, 4);

        float h = 64 + base * 14 + detail * 4;
        if (mountain > 1.05f) {
            h += (mountain - 1.05f) * 110f; // mountain uplift
        }
        return Math.max(4, Math.min(Chunk.SY - 4, Math.round(h)));
    }

    /** Fills the chunk with terrain. Deterministic per chunk. */
    public void generate(Chunk chunk) {
        generate(chunk, NULL_SINK);
    }

    public void generate(Chunk chunk, StructureSink sink) {
        int ox = chunk.cx * Chunk.SX;
        int oz = chunk.cz * Chunk.SZ;
        Random rng = new Random(seed ^ (chunk.cx * 341873128712L + chunk.cz * 132897987541L));

        int[][] height = new int[Chunk.SX][Chunk.SZ];
        Biome[][] biome = new Biome[Chunk.SX][Chunk.SZ];

        for (int x = 0; x < Chunk.SX; x++) {
            for (int z = 0; z < Chunk.SZ; z++) {
                int wx = ox + x, wz = oz + z;
                height[x][z] = heightAt(wx, wz);
                biome[x][z] = biomeAt(wx, wz);
            }
        }

        for (int x = 0; x < Chunk.SX; x++) {
            for (int z = 0; z < Chunk.SZ; z++) {
                int wx = ox + x, wz = oz + z;
                int h = height[x][z];
                Biome b = biome[x][z];

                for (int y = 0; y <= h; y++) {
                    int id;
                    if (y <= 1 + rng.nextInt(2)) {
                        id = Blocks.BEDROCK.id;
                    } else if (y < 28) {
                        id = Blocks.DEEPSTONE.id;
                    } else if (y < h - 3) {
                        id = Blocks.STONE.id;
                    } else if (y < h) {
                        id = subSurface(b);
                    } else {
                        id = surface(b, h);
                    }
                    chunk.set(x, y, z, id);
                }

                // water fill up to sea level
                for (int y = h + 1; y <= World.SEA_LEVEL; y++) {
                    chunk.set(x, y, z, Blocks.WATER.id);
                }
                // freeze water surface in tundra
                if (b == Biome.TUNDRA && h < World.SEA_LEVEL) {
                    chunk.set(x, World.SEA_LEVEL, z, Blocks.ICE.id);
                }
                // beaches
                if (h >= World.SEA_LEVEL - 2 && h <= World.SEA_LEVEL + 1 && b != Biome.DESERT && b != Biome.TUNDRA) {
                    chunk.set(x, h, z, Blocks.SAND.id);
                    if (h >= 1) chunk.set(x, h - 1, z, Blocks.SAND.id);
                }
            }
        }

        carveCaves(chunk, ox, oz, height);
        placeOres(chunk, rng);
        decorate(chunk, rng, height, biome);
        placeStructures(chunk, rng, height, biome, sink);
    }

    private int subSurface(Biome b) {
        return switch (b) {
            case DESERT -> Blocks.SANDSTONE.id;
            case MOUNTAINS -> Blocks.STONE.id;
            default -> Blocks.DIRT.id;
        };
    }

    private int surface(Biome b, int h) {
        return switch (b) {
            case DESERT -> Blocks.SAND.id;
            case TUNDRA -> Blocks.SNOWY_GRASS.id;
            case MOUNTAINS -> h > 96 ? Blocks.SNOW_BLOCK.id : Blocks.STONE.id;
            default -> h < World.SEA_LEVEL ? Blocks.DIRT.id : Blocks.GRASS.id;
        };
    }

    private void carveCaves(Chunk chunk, int ox, int oz, int[][] height) {
        for (int x = 0; x < Chunk.SX; x++) {
            for (int z = 0; z < Chunk.SZ; z++) {
                int wx = ox + x, wz = oz + z;
                int maxY = Math.min(height[x][z] - 4, 100);
                for (int y = 4; y <= maxY; y++) {
                    boolean carve = false;

                    // blob caves
                    float n = caveNoise.fractal3(wx * 0.045f, y * 0.06f, wz * 0.045f, 3, 2f, 0.5f);
                    float threshold = 0.42f - (y < 30 ? 0.05f : 0f);
                    if (n > threshold) carve = true;

                    // spaghetti tunnels: the intersection of two noise ridges
                    if (!carve) {
                        float a = tunnelNoiseA.fractal3(wx * 0.02f, y * 0.035f, wz * 0.02f, 2, 2f, 0.5f);
                        float b = tunnelNoiseB.fractal3(wx * 0.02f, y * 0.035f, wz * 0.02f, 2, 2f, 0.5f);
                        if (Math.abs(a) < 0.065f && Math.abs(b) < 0.065f) carve = true;
                    }

                    // large caverns deep down
                    if (!carve && y < 42) {
                        float c = cavernNoise.fractal3(wx * 0.014f, y * 0.028f, wz * 0.014f, 2, 2f, 0.5f);
                        float depthBoost = (42 - y) / 42f * 0.12f;
                        if (c > 0.52f - depthBoost) carve = true;
                    }

                    if (carve) {
                        chunk.set(x, y, z, y < 12 ? Blocks.LAVA.id : Blocks.AIR.id);
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------ structures

    private void placeStructures(Chunk chunk, Random rng, int[][] height, Biome[][] biome,
                                 StructureSink sink) {
        int ox = chunk.cx * Chunk.SX;
        int oz = chunk.cz * Chunk.SZ;

        // shipwreck: on beaches / shallow water
        if (rng.nextInt(48) == 0) {
            int x = 4 + rng.nextInt(6), z = 4 + rng.nextInt(6);
            int h = height[x][z];
            if (h >= World.SEA_LEVEL - 6 && h <= World.SEA_LEVEL + 1) {
                buildShipwreck(chunk, x, Math.max(4, h - 1), z, rng, sink, ox, oz);
            }
        }

        // spawner room deep underground
        if (rng.nextInt(22) == 0) {
            int x = 5 + rng.nextInt(5), z = 5 + rng.nextInt(5);
            int y = 14 + rng.nextInt(26);
            if (y < height[x][z] - 12) {
                buildSpawnerRoom(chunk, x, y, z, rng, sink, ox, oz);
            }
        }

        // ruined tower on open ground
        if (rng.nextInt(56) == 0) {
            int x = 5 + rng.nextInt(5), z = 5 + rng.nextInt(5);
            int h = height[x][z];
            Biome b = biome[x][z];
            if (h > World.SEA_LEVEL + 1 && (b == Biome.PLAINS || b == Biome.FOREST || b == Biome.TUNDRA)) {
                buildRuinTower(chunk, x, h + 1, z, rng, sink, ox, oz);
            }
        }
    }

    private void buildShipwreck(Chunk chunk, int cx, int y, int cz, Random rng,
                                StructureSink sink, int ox, int oz) {
        int planks = Blocks.OAK_LOG.id + 4; // oak planks
        int len = 9, wid = 4;
        boolean alongX = rng.nextBoolean();
        for (int i = -len / 2; i <= len / 2; i++) {
            int half = Math.max(1, wid / 2 - (Math.abs(i) >= len / 2 - 1 ? 1 : 0));
            for (int j = -half; j <= half; j++) {
                int x = cx + (alongX ? i : j);
                int z = cz + (alongX ? j : i);
                if (!Chunk.inBounds(x, y, z)) continue;
                chunk.set(x, y, z, planks);                         // hull floor
                if (Math.abs(j) == half && Chunk.inBounds(x, y + 1, z)) {
                    chunk.set(x, y + 1, z, planks);                 // hull sides
                    if (rng.nextInt(3) == 0 && Chunk.inBounds(x, y + 2, z)) {
                        chunk.set(x, y + 2, z, planks);
                    }
                }
            }
        }
        // mast (often broken)
        int mastH = 2 + rng.nextInt(4);
        for (int i = 1; i <= mastH; i++) {
            if (Chunk.inBounds(cx, y + i, cz)) chunk.set(cx, y + i, cz, Blocks.OAK_LOG.id);
        }
        // loot chest in the hold
        int chestX = cx + (alongX ? 2 : 0);
        int chestZ = cz + (alongX ? 0 : 2);
        if (Chunk.inBounds(chestX, y + 1, chestZ)) {
            chunk.set(chestX, y + 1, chestZ, Blocks.CHEST.id);
            sink.chestLoot(ox + chestX, y + 1, oz + chestZ, "shipwreck");
        }
    }

    private void buildSpawnerRoom(Chunk chunk, int cx, int cy, int cz, Random rng,
                                  StructureSink sink, int ox, int oz) {
        int half = 3;
        for (int x = cx - half; x <= cx + half; x++) {
            for (int z = cz - half; z <= cz + half; z++) {
                for (int y = cy; y <= cy + 4; y++) {
                    if (!Chunk.inBounds(x, y, z)) continue;
                    boolean wall = x == cx - half || x == cx + half
                            || z == cz - half || z == cz + half
                            || y == cy || y == cy + 4;
                    if (wall) {
                        chunk.set(x, y, z, rng.nextInt(3) == 0
                                ? Blocks.MOSSY_COBBLESTONE.id : Blocks.COBBLESTONE.id);
                    } else {
                        chunk.set(x, y, z, Blocks.AIR.id);
                    }
                }
            }
        }
        // doorway gap
        chunk.set(cx - half, cy + 1, cz, Blocks.AIR.id);
        chunk.set(cx - half, cy + 2, cz, Blocks.AIR.id);

        chunk.set(cx, cy + 1, cz, Blocks.SPAWNER.id);
        sink.spawner(ox + cx, cy + 1, oz + cz,
                rng.nextBoolean() ? com.blockforge.entity.MobType.ZOMBIE
                        : com.blockforge.entity.MobType.SPIDER);

        int chestX = cx + half - 1, chestZ = cz + half - 1;
        chunk.set(chestX, cy + 1, chestZ, Blocks.CHEST.id);
        sink.chestLoot(ox + chestX, cy + 1, oz + chestZ, "dungeon");
    }

    private void buildRuinTower(Chunk chunk, int cx, int y, int cz, Random rng,
                                StructureSink sink, int ox, int oz) {
        int h = 5 + rng.nextInt(5);
        for (int level = 0; level < h; level++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    boolean wall = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                    if (!wall) continue;
                    if (Math.abs(dx) == 2 && Math.abs(dz) == 2 && rng.nextInt(4) == 0) continue;
                    // crumbling top
                    if (level > h - 3 && rng.nextInt(3) == 0) continue;
                    int x = cx + dx, z = cz + dz;
                    if (Chunk.inBounds(x, y + level, z)) {
                        chunk.set(x, y + level, z, rng.nextInt(4) == 0
                                ? Blocks.MOSSY_COBBLESTONE.id : Blocks.COBBLESTONE.id);
                    }
                }
            }
        }
        // doorway
        chunk.set(cx + 2, y, cz, Blocks.AIR.id);
        chunk.set(cx + 2, y + 1, cz, Blocks.AIR.id);
        if (Chunk.inBounds(cx, y, cz)) {
            chunk.set(cx, y, cz, Blocks.CHEST.id);
            sink.chestLoot(ox + cx, y, oz + cz, "ruin");
        }
    }

    /** Ore vein descriptors: {surfaceOreOffset, minY, maxY, veinsPerChunk, veinSize}. */
    private static final int[][] ORE_TABLE = {
            // offsets into the ore id range are computed from Blocks.COAL_ORE.id
            {0, 20, 100, 14, 10}, // Coal
            {1, 10, 70, 10, 7},   // Iron
            {2, 30, 90, 9, 7},    // Copper
            {3, 20, 64, 7, 6},    // Tin
            {4, 8, 40, 5, 5},     // Silver
            {5, 4, 34, 4, 6},     // Gold
            {6, 8, 34, 4, 5},     // Azurite
            {7, 4, 18, 6, 6},     // Redglow
            {8, 6, 34, 2, 3},     // Emerald
            {9, 4, 20, 2, 4},     // Ruby
            {10, 4, 20, 2, 4},    // Sapphire
            {11, 8, 30, 3, 4},    // Topaz
            {12, 4, 24, 3, 4},    // Amethyst
            {13, 2, 16, 2, 5},    // Diamond
    };

    private void placeOres(Chunk chunk, Random rng) {
        int coal = Blocks.COAL_ORE.id;
        int oreCount = ORE_TABLE.length;
        for (int[] ore : ORE_TABLE) {
            for (int v = 0; v < ore[3]; v++) {
                int x = rng.nextInt(Chunk.SX);
                int z = rng.nextInt(Chunk.SZ);
                int y = ore[1] + rng.nextInt(Math.max(1, ore[2] - ore[1]));
                for (int i = 0; i < ore[4]; i++) {
                    int bx = x + rng.nextInt(3) - 1;
                    int by = y + rng.nextInt(3) - 1;
                    int bz = z + rng.nextInt(3) - 1;
                    if (!Chunk.inBounds(bx, by, bz)) continue;
                    int cur = chunk.get(bx, by, bz);
                    if (cur == Blocks.STONE.id) {
                        chunk.set(bx, by, bz, coal + ore[0]);
                    } else if (cur == Blocks.DEEPSTONE.id) {
                        chunk.set(bx, by, bz, coal + oreCount + ore[0]); // deep variant
                    }
                }
            }
        }
    }

    private void decorate(Chunk chunk, Random rng, int[][] height, Biome[][] biome) {
        // trees (kept fully inside the chunk to stay deterministic)
        int trees = switch (biome[8][8]) {
            case FOREST -> 5 + rng.nextInt(4);
            case PLAINS -> rng.nextInt(2);
            case TUNDRA -> 1 + rng.nextInt(2);
            case MOUNTAINS -> rng.nextInt(2);
            case DESERT -> 0;
        };
        for (int i = 0; i < trees; i++) {
            int x = 3 + rng.nextInt(Chunk.SX - 6);
            int z = 3 + rng.nextInt(Chunk.SZ - 6);
            int h = height[x][z];
            int ground = chunk.get(x, h, z);
            if (ground != Blocks.GRASS.id && ground != Blocks.SNOWY_GRASS.id && ground != Blocks.DIRT.id) continue;
            if (h + 9 >= Chunk.SY) continue;
            Biome b = biome[x][z];
            if (b == Biome.TUNDRA || (b == Biome.MOUNTAINS && rng.nextBoolean())) {
                placePineTree(chunk, x, h + 1, z, rng);
            } else if (b == Biome.FOREST && rng.nextInt(4) == 0) {
                placeTree(chunk, x, h + 1, z, rng, Blocks.BIRCH_LOG.id, Blocks.BIRCH_LEAVES.id);
            } else if (b == Biome.FOREST && rng.nextInt(6) == 0) {
                placeTree(chunk, x, h + 1, z, rng, Blocks.WALNUT_LOG.id, Blocks.WALNUT_LEAVES.id);
            } else {
                placeTree(chunk, x, h + 1, z, rng, Blocks.OAK_LOG.id, Blocks.OAK_LEAVES.id);
            }
        }

        // cacti in deserts
        if (biome[8][8] == Biome.DESERT) {
            for (int i = 0; i < 3; i++) {
                int x = rng.nextInt(Chunk.SX);
                int z = rng.nextInt(Chunk.SZ);
                int h = height[x][z];
                if (chunk.get(x, h, z) == Blocks.SAND.id && h > World.SEA_LEVEL && h + 4 < Chunk.SY) {
                    int ch = 1 + rng.nextInt(3);
                    for (int y = 1; y <= ch; y++) chunk.set(x, h + y, z, Blocks.CACTUS.id);
                }
            }
        }

        // plants, flowers, mushrooms, pumpkins
        int plantTries = biome[8][8] == Biome.DESERT ? 4 : 18;
        for (int i = 0; i < plantTries; i++) {
            int x = rng.nextInt(Chunk.SX);
            int z = rng.nextInt(Chunk.SZ);
            int h = height[x][z];
            if (h + 1 >= Chunk.SY || chunk.get(x, h + 1, z) != 0) continue;
            int ground = chunk.get(x, h, z);
            Biome b = biome[x][z];

            if (b == Biome.DESERT && ground == Blocks.SAND.id) {
                chunk.set(x, h + 1, z, Blocks.DEAD_BUSH.id);
            } else if (ground == Blocks.GRASS.id) {
                int roll = rng.nextInt(20);
                int id;
                if (roll < 10) id = Blocks.TALL_GRASS.id;
                else if (roll < 12) id = Blocks.FERN.id;
                else if (roll == 12) id = Blocks.MARIGOLD.id;
                else if (roll == 13) id = Blocks.ROSEBUD.id;
                else if (roll == 14) id = Blocks.BLUEBELL.id;
                else if (roll == 15) id = Blocks.LILY_WHITE.id;
                else if (roll == 16) id = Blocks.VIOLET.id;
                else if (roll == 17) id = Blocks.SUNFLOWER.id;
                else if (roll == 18) id = rng.nextBoolean() ? Blocks.RED_MUSHROOM.id : Blocks.BROWN_MUSHROOM.id;
                else id = rng.nextInt(24) == 0 ? Blocks.PUMPKIN.id : Blocks.TALL_GRASS.id;
                chunk.set(x, h + 1, z, id);
            }
        }
    }

    private void placeTree(Chunk chunk, int x, int y, int z, Random rng, int logId, int leafId) {
        int trunk = 4 + rng.nextInt(3);
        for (int i = 0; i < trunk; i++) {
            chunk.set(x, y + i, z, logId);
        }
        int top = y + trunk;
        for (int dy = -2; dy <= 1; dy++) {
            int r = dy <= -1 ? 2 : 1;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx == 0 && dz == 0 && dy < 0) continue; // trunk
                    if (Math.abs(dx) == r && Math.abs(dz) == r && rng.nextBoolean()) continue;
                    int bx = x + dx, by = top + dy, bz = z + dz;
                    if (Chunk.inBounds(bx, by, bz) && chunk.get(bx, by, bz) == 0) {
                        chunk.set(bx, by, bz, leafId);
                    }
                }
            }
        }
        chunk.set(x, top + 1, z, leafId);
    }

    private void placePineTree(Chunk chunk, int x, int y, int z, Random rng) {
        int trunk = 6 + rng.nextInt(3);
        for (int i = 0; i < trunk; i++) {
            chunk.set(x, y + i, z, Blocks.PINE_LOG.id);
        }
        for (int layer = 0; layer < trunk - 2; layer++) {
            int by = y + 2 + layer;
            int r = Math.max(1, (trunk - 2 - layer) / 2);
            if (layer % 2 == 1) r = Math.max(1, r - 1);
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    if (Math.abs(dx) + Math.abs(dz) > r + 1) continue;
                    int bx = x + dx, bz = z + dz;
                    if (Chunk.inBounds(bx, by, bz) && chunk.get(bx, by, bz) == 0) {
                        chunk.set(bx, by, bz, Blocks.PINE_LEAVES.id);
                    }
                }
            }
        }
        chunk.set(x, y + trunk, z, Blocks.PINE_LEAVES.id);
    }
}
