package com.blockforge.survival;

import com.blockforge.item.Item;
import com.blockforge.item.ItemStack;
import com.blockforge.item.Items;
import com.blockforge.world.Block;
import com.blockforge.world.Blocks;

import java.util.Random;

/**
 * Survival properties per block id: hardness, the tool class that digs it
 * fastest, the tool tier required for drops, and what it drops. Assigned by
 * name heuristics with explicit overrides, so the 200+ block registrations
 * stay untouched.
 */
public final class BlockProps {

    private static float[] hardness;          // -1 = unbreakable
    private static Item.ToolClass[] tool;
    private static int[] requiredTier;        // 0 = hand is fine
    private static int[] dropItem;            // -1 = self, -2 = nothing
    private static int[] dropCount;
    private static boolean initialized;

    private BlockProps() {
    }

    public static void init() {
        if (initialized) return;
        initialized = true;
        int n = Blocks.count();
        hardness = new float[n];
        tool = new Item.ToolClass[n];
        requiredTier = new int[n];
        dropItem = new int[n];
        dropCount = new int[n];

        for (Block b : Blocks.all()) {
            int id = b.id;
            String name = b.name;
            // defaults
            hardness[id] = 1f;
            tool[id] = Item.ToolClass.NONE;
            requiredTier[id] = 0;
            dropItem[id] = -1;
            dropCount[id] = 1;

            if (b.isAir() || b.liquid) {
                hardness[id] = -1;
                continue;
            }
            if (b.plant) {
                hardness[id] = 0f;
                if (name.equals("Tall Grass") || name.equals("Fern")) dropItem[id] = -2;
                continue;
            }
            if (contains(name, "Leaves", "Needles")) {
                hardness[id] = 0.3f;
                dropItem[id] = -2; // saplings/apples handled in bonusDrop
                continue;
            }
            if (contains(name, "Glass", "Ice") && !name.contains("Packed")) {
                hardness[id] = 0.4f;
                tool[id] = Item.ToolClass.PICK;
                dropItem[id] = -2;
                continue;
            }
            if (contains(name, "Log", "Planks", "Workbench", "Bookcase", "Crate",
                    "Music Box", "Chest", "Cactus", "Pumpkin", "Melon", "Bale",
                    "Mushroom Cap", "Mushroom Stem", "Blast Box")) {
                hardness[id] = 2f;
                tool[id] = Item.ToolClass.AXE;
                continue;
            }
            if (contains(name, "Dirt", "Grass Block", "Snowy Grass", "Sand", "Gravel",
                    "Clay", "Mud", "Snow Block", "Permafrost", "Mycelium")) {
                hardness[id] = 0.6f;
                tool[id] = Item.ToolClass.SHOVEL;
                continue;
            }
            if (contains(name, "Cloth", "Plaster", "Ceramic", "Sponge", "Packed Ice")) {
                hardness[id] = 0.9f;
                continue;
            }
            if (name.equals("Bedrock")) {
                hardness[id] = -1;
                continue;
            }
            if (name.equals("Obsidian") || name.equals("Obsidian Bricks")) {
                hardness[id] = 30f;
                tool[id] = Item.ToolClass.PICK;
                requiredTier[id] = 4;
                continue;
            }
            if (name.equals("Mob Spawner")) {
                hardness[id] = 5f;
                tool[id] = Item.ToolClass.PICK;
                dropItem[id] = -2; // never drops
                continue;
            }
            if (name.endsWith(" Ore")) {
                boolean deep = name.startsWith("Deep ");
                String ore = name.replace("Deep ", "").replace(" Ore", "");
                hardness[id] = deep ? 4.5f : 3f;
                tool[id] = Item.ToolClass.PICK;
                requiredTier[id] = switch (ore) {
                    case "Coal", "Copper", "Tin" -> 1;
                    case "Iron", "Silver", "Azurite" -> 2;
                    default -> 3; // gold, gems, diamond, redglow
                };
                Item drop = switch (ore) {
                    case "Coal" -> Items.COAL;
                    case "Iron" -> Items.RAW_IRON;
                    case "Copper" -> Items.RAW_COPPER;
                    case "Tin" -> Items.RAW_TIN;
                    case "Silver" -> Items.RAW_SILVER;
                    case "Gold" -> Items.RAW_GOLD;
                    case "Diamond" -> Items.DIAMOND;
                    case "Emerald" -> Items.EMERALD;
                    case "Ruby" -> Items.RUBY;
                    case "Sapphire" -> Items.SAPPHIRE;
                    case "Topaz" -> Items.TOPAZ;
                    case "Amethyst" -> Items.AMETHYST;
                    case "Azurite" -> Items.AZURITE;
                    case "Redglow" -> Items.REDGLOW_DUST;
                    default -> null;
                };
                if (drop != null) dropItem[id] = drop.id;
                continue;
            }
            if (contains(name, "Stone", "Cobble", "Brick", "Granite", "Diorite",
                    "Andesite", "Basalt", "Marble", "Slate", "Limestone", "Sandstone",
                    "Block of", "Tile", "Stove", "Glowlamp", "Lamp", "Lava Rock",
                    "Charcoal", "Deepstone", "Polished", "Chiseled")) {
                hardness[id] = 1.8f;
                tool[id] = Item.ToolClass.PICK;
                requiredTier[id] = 1;
                if (name.equals("Stone")) {
                    dropItem[id] = Items.forBlock(Blocks.COBBLESTONE).id;
                }
                if (name.startsWith("Block of ")) requiredTier[id] = 2;
            }
        }
        // grass drops dirt
        dropItem[Blocks.GRASS.id] = Items.forBlock(Blocks.DIRT).id;
        dropItem[Blocks.SNOWY_GRASS.id] = Items.forBlock(Blocks.DIRT).id;
        // lit stove drops stove
        dropItem[Blocks.STOVE_LIT.id] = Items.forBlock(Blocks.STOVE).id;
    }

    private static boolean contains(String name, String... parts) {
        for (String p : parts) {
            if (name.contains(p)) return true;
        }
        return false;
    }

    /** -1 means unbreakable. */
    public static float hardness(int blockId) {
        init();
        return hardness[blockId];
    }

    public static Item.ToolClass toolClass(int blockId) {
        init();
        return tool[blockId];
    }

    /** Seconds to break with the given held item (creative ignores this). */
    public static float breakTime(int blockId, ItemStack held) {
        init();
        float h = hardness[blockId];
        if (h < 0) return Float.POSITIVE_INFINITY;
        if (h == 0) return 0.05f;
        float speed = 1f;
        if (held != null && held.item.kind == Item.Kind.TOOL
                && held.item.toolClass == tool[blockId]) {
            speed = held.item.toolSpeed;
        }
        return h * 1.5f / speed;
    }

    /** Whether breaking with the given item yields drops. */
    public static boolean canHarvest(int blockId, ItemStack held) {
        init();
        int req = requiredTier[blockId];
        if (req == 0) return true;
        return held != null && held.item.kind == Item.Kind.TOOL
                && held.item.toolClass == tool[blockId]
                && held.item.tier >= req;
    }

    /** Main drop for the block, or null. */
    public static ItemStack drop(int blockId, Random rng) {
        init();
        int d = dropItem[blockId];
        if (d == -2) return null;
        if (d == -1) return new ItemStack(Items.forBlock(Blocks.get(blockId)), dropCount[blockId]);
        return new ItemStack(Items.get(d), dropCount[blockId]);
    }

    /** Chance-based extra drops (saplings and apples from leaves). */
    public static ItemStack bonusDrop(int blockId, Random rng) {
        init();
        Block b = Blocks.get(blockId);
        if (b.name.endsWith("Leaves") || b.name.endsWith("Needles")) {
            float roll = rng.nextFloat();
            if (roll < 0.05f) {
                // matching sapling: saplings are registered in wood order
                String wood = b.name.replace(" Leaves", "").replace(" Needles", "");
                for (Block sap : Blocks.all()) {
                    if (sap.name.equals(wood + " Sapling")) {
                        return new ItemStack(Items.forBlock(sap), 1);
                    }
                }
                if (b.name.startsWith("Pine")) {
                    for (Block sap : Blocks.all()) {
                        if (sap.name.equals("Pine Sapling")) {
                            return new ItemStack(Items.forBlock(sap), 1);
                        }
                    }
                }
            } else if (roll < 0.08f && b == Blocks.OAK_LEAVES) {
                return new ItemStack(Items.APPLE, 1);
            }
        }
        return null;
    }
}
