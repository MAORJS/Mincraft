package com.blockforge.item;

import com.blockforge.world.Blocks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Shaped crafting recipes, smelting recipes, and furnace fuels.
 * Patterns are up to 3x3 strings; matching normalizes both the recipe and the
 * grid to their bounding box, and tries the horizontal mirror too.
 */
public final class Recipes {

    public static final class Recipe {
        final Item[][] pattern; // normalized, rows x cols
        public final ItemStack result;

        Recipe(Item[][] pattern, ItemStack result) {
            this.pattern = pattern;
            this.result = result;
        }
    }

    private static final List<Recipe> RECIPES = new ArrayList<>();
    private static final Map<Item, ItemStack> SMELTING = new HashMap<>();
    private static final Map<Item, Float> FUEL = new HashMap<>();
    private static boolean initialized;

    private Recipes() {
    }

    // ---------------------------------------------------------------- define

    private static Item blockItem(com.blockforge.world.Block b) {
        return Items.forBlock(b);
    }

    private static void shaped(ItemStack result, String[] rows, Object... key) {
        Map<Character, Item> map = new HashMap<>();
        for (int i = 0; i < key.length; i += 2) {
            map.put((Character) key[i], (Item) key[i + 1]);
        }
        Item[][] grid = new Item[rows.length][];
        for (int r = 0; r < rows.length; r++) {
            grid[r] = new Item[rows[r].length()];
            for (int c = 0; c < rows[r].length(); c++) {
                char ch = rows[r].charAt(c);
                grid[r][c] = ch == ' ' ? null : map.get(ch);
            }
        }
        RECIPES.add(new Recipe(normalize(grid), result));
    }

    private static void smelt(Item in, ItemStack out) {
        SMELTING.put(in, out);
    }

    private static void fuel(Item item, float seconds) {
        FUEL.put(item, seconds);
    }

    public static void init() {
        if (initialized) return;
        initialized = true;

        Item oakLog = blockItem(Blocks.OAK_LOG);
        Item birchLog = blockItem(Blocks.BIRCH_LOG);
        Item pineLog = blockItem(Blocks.PINE_LOG);
        Item walnutLog = blockItem(Blocks.WALNUT_LOG);
        Item oakPlanks = blockItem(Blocks.get(Blocks.OAK_LOG.id + 4));
        Item birchPlanks = blockItem(Blocks.get(Blocks.OAK_LOG.id + 5));
        Item pinePlanks = blockItem(Blocks.get(Blocks.OAK_LOG.id + 6));
        Item walnutPlanks = blockItem(Blocks.get(Blocks.OAK_LOG.id + 7));
        Item cobble = blockItem(Blocks.COBBLESTONE);
        Item stone = blockItem(Blocks.STONE);
        Item sand = blockItem(Blocks.SAND);
        Item glass = blockItem(Blocks.GLASS);
        Item workbench = blockItem(Blocks.WORKBENCH);
        Item stove = blockItem(Blocks.STOVE);
        Item chest = blockItem(Blocks.CHEST);
        Item glowlamp = blockItem(Blocks.GLOWLAMP);
        Item bookcase = blockItem(blockNamed("Bookcase"));

        // planks & sticks
        shaped(new ItemStack(oakPlanks, 4), new String[]{"L"}, 'L', oakLog);
        shaped(new ItemStack(birchPlanks, 4), new String[]{"L"}, 'L', birchLog);
        shaped(new ItemStack(pinePlanks, 4), new String[]{"L"}, 'L', pineLog);
        shaped(new ItemStack(walnutPlanks, 4), new String[]{"L"}, 'L', walnutLog);
        shaped(new ItemStack(Items.STICK, 4), new String[]{"P", "P"}, 'P', oakPlanks);

        // stations & storage
        shaped(new ItemStack(workbench, 1), new String[]{"PP", "PP"}, 'P', oakPlanks);
        shaped(new ItemStack(stove, 1), new String[]{"CCC", "C C", "CCC"}, 'C', cobble);
        shaped(new ItemStack(chest, 1), new String[]{"PPP", "P P", "PPP"}, 'P', oakPlanks);
        shaped(new ItemStack(glowlamp, 1), new String[]{"RRR", "RGR", "RRR"},
                'R', Items.REDGLOW_DUST, 'G', glass);
        shaped(new ItemStack(bookcase, 1), new String[]{"PPP", "SSS", "PPP"},
                'P', oakPlanks, 'S', Items.STICK);

        // tools: [pick, axe, shovel, sword] x [wood, stone, iron, gold, diamond]
        Item[] heads = {oakPlanks, cobble, Items.IRON_INGOT, Items.GOLD_INGOT, Items.DIAMOND};
        for (int t = 0; t < 5; t++) {
            Item h = heads[t];
            shaped(new ItemStack(Items.PICKS[t], 1), new String[]{"HHH", " S ", " S "},
                    'H', h, 'S', Items.STICK);
            shaped(new ItemStack(Items.AXES[t], 1), new String[]{"HH", "HS", " S"},
                    'H', h, 'S', Items.STICK);
            shaped(new ItemStack(Items.SHOVELS[t], 1), new String[]{"H", "S", "S"},
                    'H', h, 'S', Items.STICK);
            shaped(new ItemStack(Items.SWORDS[t], 1), new String[]{"H", "H", "S"},
                    'H', h, 'S', Items.STICK);
        }
        shaped(new ItemStack(Items.BOW, 1), new String[]{" PT", "S T", " PT"},
                'P', Items.STICK, 'S', Items.STICK, 'T', Items.SPIDER_SILK);
        shaped(new ItemStack(Items.ARROW, 4), new String[]{"F", "S", "E"},
                'F', cobble, 'S', Items.STICK, 'E', Items.FEATHER);

        // mineral blocks <-> items
        record BlockCraft(Item unit, com.blockforge.world.Block block) {
        }
        List<BlockCraft> bc = List.of(
                new BlockCraft(Items.IRON_INGOT, blockNamed("Block of Iron")),
                new BlockCraft(Items.GOLD_INGOT, blockNamed("Block of Gold")),
                new BlockCraft(Items.DIAMOND, blockNamed("Block of Diamond")),
                new BlockCraft(Items.COPPER_INGOT, blockNamed("Block of Copper")),
                new BlockCraft(Items.COAL, blockNamed("Block of Coal")));
        for (BlockCraft c : bc) {
            if (c.block == null) continue;
            shaped(new ItemStack(blockItem(c.block), 1),
                    new String[]{"III", "III", "III"}, 'I', c.unit);
            shaped(new ItemStack(c.unit, 9), new String[]{"B"}, 'B', blockItem(c.block));
        }

        // smelting
        smelt(Items.RAW_IRON, new ItemStack(Items.IRON_INGOT, 1));
        smelt(Items.RAW_COPPER, new ItemStack(Items.COPPER_INGOT, 1));
        smelt(Items.RAW_TIN, new ItemStack(Items.TIN_INGOT, 1));
        smelt(Items.RAW_SILVER, new ItemStack(Items.SILVER_INGOT, 1));
        smelt(Items.RAW_GOLD, new ItemStack(Items.GOLD_INGOT, 1));
        smelt(sand, new ItemStack(glass, 1));
        smelt(cobble, new ItemStack(stone, 1));
        smelt(Items.RAW_BEEF, new ItemStack(Items.COOKED_BEEF, 1));
        smelt(Items.RAW_PORK, new ItemStack(Items.COOKED_PORK, 1));
        smelt(Items.RAW_CHICKEN, new ItemStack(Items.COOKED_CHICKEN, 1));
        smelt(Items.RAW_MUTTON, new ItemStack(Items.COOKED_MUTTON, 1));

        // fuels (seconds of burn; one smelt = 10s)
        fuel(Items.COAL, 80f);
        fuel(blockItem(blockNamed("Charcoal Block")), 800f);
        fuel(blockItem(blockNamed("Block of Coal")), 800f);
        fuel(oakPlanks, 15f);
        fuel(birchPlanks, 15f);
        fuel(pinePlanks, 15f);
        fuel(walnutPlanks, 15f);
        fuel(oakLog, 15f);
        fuel(birchLog, 15f);
        fuel(pineLog, 15f);
        fuel(walnutLog, 15f);
        fuel(Items.STICK, 5f);
    }

    private static com.blockforge.world.Block blockNamed(String name) {
        for (com.blockforge.world.Block b : Blocks.all()) {
            if (b.name.equals(name)) return b;
        }
        return null;
    }

    // ----------------------------------------------------------------- match

    /** Trims empty rows/columns off a grid. */
    private static Item[][] normalize(Item[][] g) {
        int rows = g.length, cols = 0;
        for (Item[] r : g) cols = Math.max(cols, r.length);
        int top = rows, bottom = -1, left = cols, right = -1;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < g[r].length; c++) {
                if (g[r][c] != null) {
                    top = Math.min(top, r);
                    bottom = Math.max(bottom, r);
                    left = Math.min(left, c);
                    right = Math.max(right, c);
                }
            }
        }
        if (bottom < 0) return new Item[0][0];
        Item[][] out = new Item[bottom - top + 1][right - left + 1];
        for (int r = 0; r < out.length; r++) {
            for (int c = 0; c < out[0].length; c++) {
                int sc = left + c;
                out[r][c] = sc < g[top + r].length ? g[top + r][sc] : null;
            }
        }
        return out;
    }

    private static boolean same(Item[][] a, Item[][] b) {
        if (a.length != b.length || a[0].length != b[0].length) return false;
        for (int r = 0; r < a.length; r++) {
            for (int c = 0; c < a[0].length; c++) {
                if (a[r][c] != b[r][c]) return false;
            }
        }
        return true;
    }

    private static Item[][] mirror(Item[][] g) {
        Item[][] out = new Item[g.length][g[0].length];
        for (int r = 0; r < g.length; r++) {
            for (int c = 0; c < g[0].length; c++) {
                out[r][c] = g[r][g[0].length - 1 - c];
            }
        }
        return out;
    }

    /**
     * @param grid crafting grid as stacks (row-major, size x size), null = empty
     * @return the crafted result (copy), or null if no recipe matches
     */
    public static ItemStack match(ItemStack[] grid, int size) {
        init();
        Item[][] g = new Item[size][size];
        boolean any = false;
        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                ItemStack s = grid[r * size + c];
                g[r][c] = s == null ? null : s.item;
                any |= s != null;
            }
        }
        if (!any) return null;
        Item[][] norm = normalize(g);
        if (norm.length == 0) return null;
        for (Recipe rec : RECIPES) {
            if (rec.pattern.length == 0) continue;
            if (same(norm, rec.pattern) || same(norm, mirror(rec.pattern))) {
                return rec.result.copy();
            }
        }
        return null;
    }

    public static ItemStack smeltResult(Item in) {
        init();
        ItemStack out = SMELTING.get(in);
        return out == null ? null : out.copy();
    }

    public static float fuelSeconds(Item item) {
        init();
        return FUEL.getOrDefault(item, 0f);
    }
}
