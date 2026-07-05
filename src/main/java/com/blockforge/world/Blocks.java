package com.blockforge.world;

import com.blockforge.render.TextureAtlas;
import com.blockforge.render.Tiles;

import java.util.ArrayList;
import java.util.List;

/**
 * Registry of every block type in the game (~190 types) together with its
 * procedurally generated textures. All names, IDs and artwork are original.
 */
public final class Blocks {

    private static final List<Block> REGISTRY = new ArrayList<>();

    public static Block AIR;

    // Frequently referenced blocks (world generation).
    public static Block GRASS, DIRT, STONE, DEEPSTONE, COBBLESTONE, BEDROCK;
    public static Block SAND, RED_SAND, GRAVEL, CLAY, SANDSTONE, RED_SANDSTONE;
    public static Block WATER, LAVA, ICE, SNOW_BLOCK, SNOWY_GRASS;
    public static Block OAK_LOG, OAK_LEAVES, BIRCH_LOG, BIRCH_LEAVES;
    public static Block PINE_LOG, PINE_LEAVES, WALNUT_LOG, WALNUT_LEAVES;
    public static Block CACTUS, PUMPKIN, MELON, TALL_GRASS, FERN, DEAD_BUSH;
    public static Block SUNFLOWER, ROSEBUD, BLUEBELL, LILY_WHITE, VIOLET, MARIGOLD;
    public static Block RED_MUSHROOM, BROWN_MUSHROOM;
    public static Block COAL_ORE, IRON_ORE, COPPER_ORE, GOLD_ORE, DIAMOND_ORE;
    public static Block OBSIDIAN, MOSSY_COBBLESTONE, GLOWLAMP;
    public static Block WORKBENCH, STOVE, STOVE_LIT, CHEST, SPAWNER, GLASS;

    private static int nextId = 0;

    private Blocks() {
    }

    public static Block get(int id) {
        return REGISTRY.get(id);
    }

    public static int count() {
        return REGISTRY.size();
    }

    public static List<Block> all() {
        return REGISTRY;
    }

    // ---------------------------------------------------------------- helpers

    private static Block add(String name, boolean solid, boolean opaque, boolean translucent,
                             boolean liquid, boolean plant, float emissive,
                             int top, int side, int bottom) {
        Block b = new Block(nextId++, name, solid, opaque, translucent, liquid, plant,
                emissive, top, side, bottom);
        REGISTRY.add(b);
        return b;
    }

    private static Block cube(String name, int[] tex) {
        int t = TextureAtlas.register(tex);
        return add(name, true, true, false, false, false, 0f, t, t, t);
    }

    private static Block cube(String name, int[] top, int[] side, int[] bottom) {
        int t = TextureAtlas.register(top);
        int s = TextureAtlas.register(side);
        int b = TextureAtlas.register(bottom);
        return add(name, true, true, false, false, false, 0f, t, s, b);
    }

    private static Block glowing(String name, int[] tex, float emissive) {
        int t = TextureAtlas.register(tex);
        return add(name, true, true, false, false, false, emissive, t, t, t);
    }

    private static Block translucentCube(String name, int[] tex) {
        int t = TextureAtlas.register(tex);
        return add(name, true, false, true, false, false, 0f, t, t, t);
    }

    private static Block liquid(String name, int[] tex, float emissive) {
        int t = TextureAtlas.register(tex);
        return add(name, false, false, true, true, false, emissive, t, t, t);
    }

    private static Block plant(String name, int[] tex) {
        int t = TextureAtlas.register(tex);
        return add(name, false, false, false, false, true, 0f, t, t, t);
    }

    private static Block leaves(String name, int[] tex) {
        int t = TextureAtlas.register(tex);
        // cut-out cube: not opaque so neighbours behind holes still render
        return add(name, true, false, false, false, false, 0f, t, t, t);
    }

    // ------------------------------------------------------------ registration

    /** Builds the whole registry. Must run before the atlas texture is uploaded. */
    public static void registerAll() {
        long s = 1000; // per-tile seed counter, deterministic

        AIR = add("Air", false, false, false, false, false, 0f, 0, 0, 0);

        // --- natural terrain -------------------------------------------------
        STONE = cube("Stone", Tiles.stone(0x7F7F7F, s++));
        DEEPSTONE = cube("Deepstone", Tiles.stone(0x4E4E52, s++));
        GRASS = cube("Grass Block",
                Tiles.grassTop(0x5FA542, s++),
                Tiles.grassSide(0x8A6244, 0x5FA542, s++),
                Tiles.speckle(0x8A6244, 0x6E4E36, 0.25f, s++));
        DIRT = cube("Dirt", Tiles.speckle(0x8A6244, 0x6E4E36, 0.25f, s++));
        COBBLESTONE = cube("Cobblestone", Tiles.stoneBricks(0x818181, s++));
        MOSSY_COBBLESTONE = cube("Mossy Cobblestone", Tiles.ore(0x818181, 0x4F7A3A, s++));
        BEDROCK = cube("Bedrock", Tiles.speckle(0x2E2E2E, 0x555555, 0.4f, s++));
        SAND = cube("Sand", Tiles.speckle(0xDACFA0, 0xC9BB88, 0.3f, s++));
        RED_SAND = cube("Red Sand", Tiles.speckle(0xB5652E, 0xA05426, 0.3f, s++));
        GRAVEL = cube("Gravel", Tiles.speckle(0x84807C, 0x5E5A56, 0.45f, s++));
        CLAY = cube("Clay", Tiles.speckle(0x9AA3B0, 0x8790A0, 0.3f, s++));
        SANDSTONE = cube("Sandstone",
                Tiles.polished(0xD9CFA0, s++),
                Tiles.stoneBricks(0xD9CFA0, s++),
                Tiles.polished(0xC9BF90, s++));
        RED_SANDSTONE = cube("Red Sandstone",
                Tiles.polished(0xB5652E, s++),
                Tiles.stoneBricks(0xB5652E, s++),
                Tiles.polished(0xA55A28, s++));
        SNOW_BLOCK = cube("Snow Block", Tiles.snow(s++));
        SNOWY_GRASS = cube("Snowy Grass",
                Tiles.snow(s++),
                Tiles.grassSide(0x8A6244, 0xEEF2F5, s++),
                Tiles.speckle(0x8A6244, 0x6E4E36, 0.25f, s++));
        ICE = translucentCube("Ice", Tiles.ice(190, s++));
        cube("Packed Ice", Tiles.ice(255, s++));
        OBSIDIAN = cube("Obsidian", Tiles.stone(0x1B1123, s++));
        cube("Sponge", Tiles.sponge(s++));
        cube("Mud", Tiles.speckle(0x4E3B2A, 0x3C2D20, 0.35f, s++));
        cube("Permafrost", Tiles.speckle(0x6E7B86, 0xACC2CE, 0.25f, s++));
        cube("Limestone", Tiles.stone(0xC8C2B0, s++));
        cube("Marble", Tiles.stone(0xE8E6E0, s++));
        cube("Slate", Tiles.stone(0x39404A, s++));
        cube("Basalt", Tiles.stone(0x2F2C33, s++));
        cube("Granite", Tiles.speckle(0xA46A55, 0x8C5847, 0.35f, s++));
        cube("Diorite", Tiles.speckle(0xC4C4C0, 0x8F8F8B, 0.3f, s++));
        cube("Andesite", Tiles.speckle(0x8A8A84, 0x6E6E68, 0.3f, s++));

        WATER = liquid("Water", Tiles.liquid(0x3355CC, 178, s++), 0f);
        LAVA = liquid("Lava", Tiles.liquid(0xE25822, 255, s++), 0.95f);

        // --- wood family (4 species) ----------------------------------------
        OAK_LOG = cube("Oak Log",
                Tiles.logTop(0x6B5233, 0xB08A55, s++), Tiles.logSide(0x6B5233, s++),
                Tiles.logTop(0x6B5233, 0xB08A55, s++));
        BIRCH_LOG = cube("Birch Log",
                Tiles.logTop(0xD7D3C5, 0xC9B77E, s++), Tiles.logSide(0xD7D3C5, s++),
                Tiles.logTop(0xD7D3C5, 0xC9B77E, s++));
        PINE_LOG = cube("Pine Log",
                Tiles.logTop(0x4A3822, 0x8A6B40, s++), Tiles.logSide(0x4A3822, s++),
                Tiles.logTop(0x4A3822, 0x8A6B40, s++));
        WALNUT_LOG = cube("Walnut Log",
                Tiles.logTop(0x3B2A1E, 0x6E5138, s++), Tiles.logSide(0x3B2A1E, s++),
                Tiles.logTop(0x3B2A1E, 0x6E5138, s++));
        cube("Oak Planks", Tiles.planks(0xB08A55, s++));
        cube("Birch Planks", Tiles.planks(0xC9B77E, s++));
        cube("Pine Planks", Tiles.planks(0x8A6B40, s++));
        cube("Walnut Planks", Tiles.planks(0x6E5138, s++));
        OAK_LEAVES = leaves("Oak Leaves", Tiles.leaves(0x3E7A2A, s++));
        BIRCH_LEAVES = leaves("Birch Leaves", Tiles.leaves(0x6FA84C, s++));
        PINE_LEAVES = leaves("Pine Needles", Tiles.leaves(0x2A5233, s++));
        WALNUT_LEAVES = leaves("Walnut Leaves", Tiles.leaves(0x4D6B2E, s++));

        // --- ores: surface stone + deepstone variants ------------------------
        int stoneC = 0x7F7F7F, deepC = 0x4E4E52;
        String[] oreNames = {"Coal", "Iron", "Copper", "Tin", "Silver", "Gold",
                "Azurite", "Redglow", "Emerald", "Ruby", "Sapphire", "Topaz",
                "Amethyst", "Diamond"};
        int[] oreColors = {0x2B2B2B, 0xC8A080, 0xC96B4A, 0xB0B8B4, 0xD8DCE0, 0xE8C93E,
                0x2C55B0, 0xD03A2A, 0x35C65A, 0xD22C55, 0x2C6BD2, 0xE8A83E,
                0x9A55D2, 0x62E0DC};
        Block[] surfaceOres = new Block[oreNames.length];
        for (int i = 0; i < oreNames.length; i++) {
            surfaceOres[i] = cube(oreNames[i] + " Ore", Tiles.oreNuggets(stoneC, oreColors[i], s++));
        }
        for (int i = 0; i < oreNames.length; i++) {
            cube("Deep " + oreNames[i] + " Ore", Tiles.oreNuggets(deepC, oreColors[i], s++));
        }
        COAL_ORE = surfaceOres[0];
        IRON_ORE = surfaceOres[1];
        COPPER_ORE = surfaceOres[2];
        GOLD_ORE = surfaceOres[5];
        DIAMOND_ORE = surfaceOres[13];

        // --- refined mineral blocks ------------------------------------------
        for (int i = 0; i < oreNames.length; i++) {
            cube("Block of " + oreNames[i], Tiles.mineralBlock(oreColors[i], s++));
        }

        // --- masonry ----------------------------------------------------------
        cube("Stone Bricks", Tiles.stoneBricks(0x8A8A8A, s++));
        cube("Mossy Stone Bricks", Tiles.ore(0x8A8A8A, 0x4F7A3A, s++));
        cube("Cracked Stone Bricks", Tiles.ore(0x8A8A8A, 0x5E5E5E, s++));
        cube("Chiseled Stone", Tiles.checker(0x9A9A9A, 0x808080, s++));
        cube("Smooth Stone", Tiles.polished(0x9E9E9E, s++));
        cube("Clay Bricks", Tiles.bricks(0x9C5443, 0xB8AFa2, s++));
        cube("Deepstone Bricks", Tiles.stoneBricks(0x55555A, s++));
        cube("Polished Granite", Tiles.polished(0xA46A55, s++));
        cube("Polished Diorite", Tiles.polished(0xC4C4C0, s++));
        cube("Polished Andesite", Tiles.polished(0x8A8A84, s++));
        cube("Polished Basalt", Tiles.polished(0x2F2C33, s++));
        cube("Polished Marble", Tiles.polished(0xE8E6E0, s++));
        cube("Polished Slate", Tiles.polished(0x39404A, s++));
        cube("Polished Limestone", Tiles.polished(0xC8C2B0, s++));
        cube("Marble Bricks", Tiles.stoneBricks(0xE8E6E0, s++));
        cube("Slate Bricks", Tiles.stoneBricks(0x39404A, s++));
        cube("Basalt Bricks", Tiles.stoneBricks(0x2F2C33, s++));
        cube("Sand Bricks", Tiles.bricks(0xD9CFA0, 0xB8AE88, s++));
        cube("Checker Tile", Tiles.checker(0xE8E6E0, 0x2B2B2B, s++));
        cube("Terracotta Tile", Tiles.checker(0x9C5443, 0xB5652E, s++));

        // --- colored families -------------------------------------------------
        String[] colorNames = {"White", "Light Gray", "Gray", "Black", "Red", "Orange",
                "Yellow", "Lime", "Green", "Cyan", "Light Blue", "Blue",
                "Purple", "Magenta", "Pink", "Brown"};
        int[] colors = {0xE9ECEC, 0x9D9D97, 0x474F52, 0x1D1D21, 0xB02E26, 0xF9801D,
                0xFED83D, 0x80C71F, 0x5E7C16, 0x169C9C, 0x3AB3DA, 0x3C44AA,
                0x8932B8, 0xC74EBD, 0xF38BAA, 0x835432};

        for (int i = 0; i < colorNames.length; i++) {
            cube(colorNames[i] + " Cloth", Tiles.cloth(colors[i], s++));
        }
        GLASS = translucentCube("Glass", Tiles.glass(0xE8F0F2, 40, s++));
        for (int i = 0; i < colorNames.length; i++) {
            int t = TextureAtlas.register(Tiles.glass(colors[i], 120, s++));
            add(colorNames[i] + " Glass", true, false, true, false, false, 0f, t, t, t);
        }
        for (int i = 0; i < colorNames.length; i++) {
            cube(colorNames[i] + " Plaster", Tiles.plaster(colors[i], s++));
        }
        for (int i = 0; i < colorNames.length; i++) {
            cube(colorNames[i] + " Ceramic", Tiles.ceramic(colors[i], s++));
        }

        // --- lamps -------------------------------------------------------------
        GLOWLAMP = glowing("Glowlamp", Tiles.glow(0xF2C14E, s++), 1.0f);
        String[] lampNames = {"Red", "Orange", "Lime", "Cyan", "Blue", "Purple", "Pink", "White"};
        int[] lampColors = {0xE04040, 0xF08030, 0x90E040, 0x40D0D0, 0x5060E0, 0xA050E0, 0xF080B0, 0xF0F0E8};
        for (int i = 0; i < lampNames.length; i++) {
            glowing(lampNames[i] + " Lamp", Tiles.glow(lampColors[i], s++), 1.0f);
        }
        glowing("Lava Rock", Tiles.ore(0x2F2C33, 0xE25822, s++), 0.7f);

        // --- plants ------------------------------------------------------------
        TALL_GRASS = plant("Tall Grass", Tiles.tuft(0x5FA542, s++));
        FERN = plant("Fern", Tiles.tuft(0x3E7A2A, s++));
        DEAD_BUSH = plant("Dead Bush", Tiles.deadBush(0x8A6B40, s++));
        MARIGOLD = plant("Marigold", Tiles.flower(0x4E8A3A, 0xF2C14E, 0xB8860B, s++));
        ROSEBUD = plant("Rosebud", Tiles.flower(0x4E8A3A, 0xC42D2D, 0x7A1A1A, s++));
        BLUEBELL = plant("Bluebell", Tiles.flower(0x4E8A3A, 0x4A6BD2, 0x2C3E8A, s++));
        LILY_WHITE = plant("White Lily", Tiles.flower(0x4E8A3A, 0xEFF2F5, 0xD8C84E, s++));
        VIOLET = plant("Violet", Tiles.flower(0x4E8A3A, 0x9A55D2, 0x5A2E86, s++));
        SUNFLOWER = plant("Sunflower", Tiles.flower(0x4E8A3A, 0xF2A83E, 0x6E4E1E, s++));
        RED_MUSHROOM = plant("Red Mushroom", Tiles.mushroom(0xC42D2D, 0xE8E2D5, s++));
        BROWN_MUSHROOM = plant("Brown Mushroom", Tiles.mushroom(0x9A7B55, 0xD8CDB5, s++));
        plant("Oak Sapling", Tiles.sapling(0x3E7A2A, 0x6B5233, s++));
        plant("Birch Sapling", Tiles.sapling(0x6FA84C, 0xD7D3C5, s++));
        plant("Pine Sapling", Tiles.sapling(0x2A5233, 0x4A3822, s++));
        plant("Walnut Sapling", Tiles.sapling(0x4D6B2E, 0x3B2A1E, s++));

        // --- crops & food blocks ------------------------------------------------
        CACTUS = cube("Cactus",
                Tiles.grassTop(0x5F8A3A, s++), Tiles.cactusSide(0x5F8A3A, s++),
                Tiles.grassTop(0x5F8A3A, s++));
        PUMPKIN = cube("Pumpkin",
                Tiles.rind(0xC46A1E, 0xD8801E, s++), Tiles.rind(0xC46A1E, 0xD8801E, s++),
                Tiles.rind(0xB05E1A, 0xC46A1E, s++));
        int pkTop = TextureAtlas.register(Tiles.rind(0xC46A1E, 0xD8801E, s++));
        int pkFace = TextureAtlas.register(Tiles.carvedPumpkin(0xC46A1E, true, s++));
        add("Lantern Pumpkin", true, true, false, false, false, 0.9f, pkTop, pkFace, pkTop);
        MELON = cube("Melon",
                Tiles.rind(0x4E7A1E, 0x86A83E, s++), Tiles.rind(0x4E7A1E, 0x86A83E, s++),
                Tiles.rind(0x466E1A, 0x7A9A38, s++));
        cube("Hay Bale",
                Tiles.rind(0xC9A83E, 0xD8B84E, s++), Tiles.rind(0xB8982E, 0xC9A83E, s++),
                Tiles.rind(0xC9A83E, 0xD8B84E, s++));
        cube("Mushroom Cap", Tiles.plaster(0xC42D2D, s++));
        cube("Mushroom Stem", Tiles.plaster(0xE8E2D5, s++));

        // --- utility / decorative ------------------------------------------------
        int wbTop = TextureAtlas.register(Tiles.workbenchTop(0xB08A55, s++));
        int wbSide = TextureAtlas.register(Tiles.planks(0x9A7645, s++));
        WORKBENCH = add("Workbench", true, true, false, false, false, 0f, wbTop, wbSide, wbSide);

        int fuTop = TextureAtlas.register(Tiles.stone(0x707070, s++));
        int fuSide = TextureAtlas.register(Tiles.furnaceFront(0x707070, false, s++));
        STOVE = add("Stove", true, true, false, false, false, 0f, fuTop, fuSide, fuTop);
        int fuLit = TextureAtlas.register(Tiles.furnaceFront(0x707070, true, s++));
        STOVE_LIT = add("Lit Stove", true, true, false, false, false, 0.6f, fuTop, fuLit, fuTop);

        int bsTop = TextureAtlas.register(Tiles.planks(0xB08A55, s++));
        int bsSide = TextureAtlas.register(Tiles.bookshelf(0xB08A55, s++));
        add("Bookcase", true, true, false, false, false, 0f, bsTop, bsSide, bsTop);

        int tntTop = TextureAtlas.register(Tiles.checker(0xB3312C, 0x8A2622, s++));
        int tntSide = TextureAtlas.register(Tiles.blastBoxSide(s++));
        add("Blast Box", true, true, false, false, false, 0f, tntTop, tntSide, tntTop);

        cube("Music Box",
                Tiles.checker(0x6E5138, 0x5A422E, s++), Tiles.planks(0x6E5138, s++),
                Tiles.planks(0x5A422E, s++));
        cube("Crate", Tiles.stoneBricks(0xB08A55, s++));
        cube("Iron Bars Block", Tiles.checker(0x8A8A8A, 0xB8B8B8, s++));
        cube("Golden Tile", Tiles.checker(0xE8C93E, 0xC9A82E, s++));
        cube("Obsidian Bricks", Tiles.stoneBricks(0x1B1123, s++));
        cube("Charcoal Block", Tiles.stone(0x1E1A16, s++));

        int chTop = TextureAtlas.register(Tiles.planks(0x9A7645, s++));
        int chSide = TextureAtlas.register(Tiles.chestSide(0xB08A55, s++));
        CHEST = add("Chest", true, true, false, false, false, 0f, chTop, chSide, chTop);
        SPAWNER = cube("Mob Spawner", Tiles.cage(0x11151A, 0x39434E, s++));

        if (count() < 190) {
            throw new IllegalStateException("expected at least 190 blocks, got " + count());
        }
    }
}
