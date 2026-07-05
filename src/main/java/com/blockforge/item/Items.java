package com.blockforge.item;

import com.blockforge.item.Item.Kind;
import com.blockforge.item.Item.ToolClass;
import com.blockforge.render.TextureAtlas;
import com.blockforge.world.Block;
import com.blockforge.world.Blocks;

import java.util.ArrayList;
import java.util.List;

/**
 * Item registry. Must run after {@link Blocks#registerAll()} and before the
 * atlas GL texture is created (icons register tiles).
 */
public final class Items {

    private static final List<Item> REGISTRY = new ArrayList<>();
    private static int[] blockToItem;

    // materials
    public static Item STICK, COAL, RAW_IRON, RAW_COPPER, RAW_TIN, RAW_SILVER, RAW_GOLD;
    public static Item IRON_INGOT, COPPER_INGOT, TIN_INGOT, SILVER_INGOT, GOLD_INGOT;
    public static Item DIAMOND, EMERALD, RUBY, SAPPHIRE, TOPAZ, AMETHYST, AZURITE, REDGLOW_DUST;
    public static Item BONE, SPIDER_SILK, LEATHER, FEATHER, ARROW;
    // food
    public static Item APPLE, ROTTEN_FLESH;
    public static Item RAW_BEEF, COOKED_BEEF, RAW_PORK, COOKED_PORK;
    public static Item RAW_CHICKEN, COOKED_CHICKEN, RAW_MUTTON, COOKED_MUTTON;
    // tools [tier 1..5 = wood stone iron gold diamond] x [pick axe shovel sword]
    public static Item[] PICKS, AXES, SHOVELS, SWORDS;
    public static Item BOW;

    // HUD icon tiles (not items)
    public static int TILE_HEART_FULL, TILE_HEART_HALF, TILE_HEART_EMPTY;
    public static int TILE_HUNGER_FULL, TILE_HUNGER_EMPTY, TILE_BUBBLE;

    private Items() {
    }

    public static Item get(int id) {
        return REGISTRY.get(id);
    }

    public static int count() {
        return REGISTRY.size();
    }

    public static List<Item> all() {
        return REGISTRY;
    }

    public static Item forBlock(Block b) {
        return REGISTRY.get(blockToItem[b.id]);
    }

    private static Item add(String name, Kind kind, int icon, int maxStack, int blockId,
                            ToolClass cls, int tier, float speed, int dur, float dmg, int food) {
        Item it = new Item(REGISTRY.size(), name, kind, icon, maxStack, blockId,
                cls, tier, speed, dur, dmg, food);
        REGISTRY.add(it);
        return it;
    }

    private static Item material(String name, int[] tile) {
        return add(name, Kind.MATERIAL, TextureAtlas.register(tile), 64, -1,
                ToolClass.NONE, 0, 1f, 0, 1f, 0);
    }

    private static Item food(String name, int[] tile, int value) {
        return add(name, Kind.FOOD, TextureAtlas.register(tile), 64, -1,
                ToolClass.NONE, 0, 1f, 0, 1f, value);
    }

    private static Item tool(String name, ToolClass cls, int headColor,
                             int tier, float speed, int dur, float dmg, long seed) {
        return add(name, Kind.TOOL, TextureAtlas.register(ItemTiles.tool(cls, headColor, seed)),
                1, -1, cls, tier, speed, dur, dmg, 0);
    }

    public static void registerAll() {
        long s = 9000;

        // one block-item per block (icon reuses the block's side tile)
        blockToItem = new int[Blocks.count()];
        for (Block b : Blocks.all()) {
            Item it = add(b.name, Kind.BLOCK, b.texSide, 64, b.id,
                    ToolClass.NONE, 0, 1f, 0, 1f, 0);
            blockToItem[b.id] = it.id;
        }

        STICK = material("Stick", ItemTiles.stick(s++));
        COAL = material("Coal", ItemTiles.rawOre(0x2B2B2B, s++));
        RAW_IRON = material("Raw Iron", ItemTiles.rawOre(0xC8A080, s++));
        RAW_COPPER = material("Raw Copper", ItemTiles.rawOre(0xC96B4A, s++));
        RAW_TIN = material("Raw Tin", ItemTiles.rawOre(0xB0B8B4, s++));
        RAW_SILVER = material("Raw Silver", ItemTiles.rawOre(0xD8DCE0, s++));
        RAW_GOLD = material("Raw Gold", ItemTiles.rawOre(0xE8C93E, s++));
        IRON_INGOT = material("Iron Ingot", ItemTiles.ingot(0xD8D8D8, s++));
        COPPER_INGOT = material("Copper Ingot", ItemTiles.ingot(0xC96B4A, s++));
        TIN_INGOT = material("Tin Ingot", ItemTiles.ingot(0xB0B8B4, s++));
        SILVER_INGOT = material("Silver Ingot", ItemTiles.ingot(0xE0E4E8, s++));
        GOLD_INGOT = material("Gold Ingot", ItemTiles.ingot(0xE8C93E, s++));
        DIAMOND = material("Diamond", ItemTiles.gem(0x62E0DC, s++));
        EMERALD = material("Emerald", ItemTiles.gem(0x35C65A, s++));
        RUBY = material("Ruby", ItemTiles.gem(0xD22C55, s++));
        SAPPHIRE = material("Sapphire", ItemTiles.gem(0x2C6BD2, s++));
        TOPAZ = material("Topaz", ItemTiles.gem(0xE8A83E, s++));
        AMETHYST = material("Amethyst", ItemTiles.gem(0x9A55D2, s++));
        AZURITE = material("Azurite", ItemTiles.gem(0x2C55B0, s++));
        REDGLOW_DUST = material("Redglow Dust", ItemTiles.dust(0xD03A2A, s++));
        BONE = material("Bone", ItemTiles.bone(s++));
        SPIDER_SILK = material("Spider Silk", ItemTiles.silk(s++));
        LEATHER = material("Leather", ItemTiles.leather(s++));
        FEATHER = material("Feather", ItemTiles.feather(s++));
        ARROW = material("Arrow", ItemTiles.arrow(s++));

        APPLE = food("Apple", ItemTiles.apple(s++), 4);
        ROTTEN_FLESH = food("Rotten Flesh", ItemTiles.flesh(s++), 2);
        RAW_BEEF = food("Raw Beef", ItemTiles.meat(false, 0xD87878, s++), 2);
        COOKED_BEEF = food("Steak", ItemTiles.meat(true, 0x9A6038, s++), 7);
        RAW_PORK = food("Raw Porkchop", ItemTiles.meat(false, 0xE89AA0, s++), 2);
        COOKED_PORK = food("Cooked Porkchop", ItemTiles.meat(true, 0xB07848, s++), 7);
        RAW_CHICKEN = food("Raw Chicken", ItemTiles.drumstick(false, s++), 2);
        COOKED_CHICKEN = food("Cooked Chicken", ItemTiles.drumstick(true, s++), 6);
        RAW_MUTTON = food("Raw Mutton", ItemTiles.meat(false, 0xD8848C, s++), 2);
        COOKED_MUTTON = food("Cooked Mutton", ItemTiles.meat(true, 0xA06A40, s++), 6);

        // tool tiers: name prefix, head color, speed, durability, bonus damage
        String[] tierName = {"Wooden", "Stone", "Iron", "Golden", "Diamond"};
        int[] tierColor = {0xB08A55, 0x8A8A8A, 0xD8D8D8, 0xE8C93E, 0x62E0DC};
        float[] tierSpeed = {2f, 4f, 6f, 10f, 8f};
        int[] tierDur = {60, 130, 250, 33, 1560};
        // effective mining tier: gold digs fast but only as strong as wood
        int[] tierRank = {1, 2, 3, 1, 4};
        PICKS = new Item[5];
        AXES = new Item[5];
        SHOVELS = new Item[5];
        SWORDS = new Item[5];
        for (int i = 0; i < 5; i++) {
            PICKS[i] = tool(tierName[i] + " Pickaxe", ToolClass.PICK, tierColor[i],
                    tierRank[i], tierSpeed[i], tierDur[i], 2 + i * 0.5f, s++);
            AXES[i] = tool(tierName[i] + " Axe", ToolClass.AXE, tierColor[i],
                    tierRank[i], tierSpeed[i], tierDur[i], 3 + i * 0.5f, s++);
            SHOVELS[i] = tool(tierName[i] + " Shovel", ToolClass.SHOVEL, tierColor[i],
                    tierRank[i], tierSpeed[i], tierDur[i], 1.5f + i * 0.5f, s++);
            SWORDS[i] = tool(tierName[i] + " Sword", ToolClass.SWORD, tierColor[i],
                    tierRank[i], 1f, tierDur[i], 4 + i * 1.0f, s++);
        }
        BOW = tool("Bow", ToolClass.BOW, 0x6B5233, 0, 1f, 240, 1f, s++);

        // HUD icons
        TILE_HEART_FULL = TextureAtlas.register(ItemTiles.heart(2, s++));
        TILE_HEART_HALF = TextureAtlas.register(ItemTiles.heart(1, s++));
        TILE_HEART_EMPTY = TextureAtlas.register(ItemTiles.heart(0, s++));
        TILE_HUNGER_FULL = TextureAtlas.register(ItemTiles.hunger(true, s++));
        TILE_HUNGER_EMPTY = TextureAtlas.register(ItemTiles.hunger(false, s++));
        TILE_BUBBLE = TextureAtlas.register(ItemTiles.bubble(s++));
    }
}
