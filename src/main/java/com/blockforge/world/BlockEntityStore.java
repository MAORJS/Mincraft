package com.blockforge.world;

import com.blockforge.entity.MobType;
import com.blockforge.item.Item;
import com.blockforge.item.ItemStack;
import com.blockforge.item.Items;
import com.blockforge.item.Recipes;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Per-position data for blocks that need state: furnaces (smelting progress),
 * chests (contents), and mob spawners. Furnaces keep smelting while their
 * screen is closed.
 */
public final class BlockEntityStore {

    public static final float SMELT_SECONDS = 10f;

    public static final class Furnace {
        public ItemStack input, fuel, output;
        public float progress;      // 0..SMELT_SECONDS
        public float burnLeft;      // seconds of fuel remaining
        public float burnTotal = 1; // for the flame gauge
    }

    public static final class Chest {
        public final ItemStack[] slots = new ItemStack[27];
    }

    public static final class Spawner {
        public MobType mobType = MobType.ZOMBIE;
        public float cooldown;
    }

    public final Map<Long, Furnace> furnaces = new HashMap<>();
    public final Map<Long, Chest> chests = new HashMap<>();
    public final Map<Long, Spawner> spawners = new HashMap<>();

    public static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    public Furnace furnaceAt(int x, int y, int z) {
        return furnaces.computeIfAbsent(key(x, y, z), k -> new Furnace());
    }

    public Chest chestAt(int x, int y, int z) {
        return chests.computeIfAbsent(key(x, y, z), k -> new Chest());
    }

    public Spawner spawnerAt(int x, int y, int z) {
        return spawners.computeIfAbsent(key(x, y, z), k -> new Spawner());
    }

    public void removeAt(int x, int y, int z) {
        long k = key(x, y, z);
        furnaces.remove(k);
        chests.remove(k);
        spawners.remove(k);
    }

    /**
     * Advances all furnaces. Returns a map of positions whose lit state
     * changed (true = now burning) so the world can swap block visuals.
     */
    public Map<Long, Boolean> tickFurnaces(float dt) {
        Map<Long, Boolean> changed = new HashMap<>();
        for (Map.Entry<Long, Furnace> e : furnaces.entrySet()) {
            Furnace f = e.getValue();
            boolean wasLit = f.burnLeft > 0;

            boolean canSmelt = false;
            ItemStack result = f.input == null ? null : Recipes.smeltResult(f.input.item);
            if (result != null) {
                canSmelt = f.output == null
                        || (f.output.item == result.item
                        && f.output.count + result.count <= f.output.item.maxStack);
            }

            // consume fuel when needed
            if (f.burnLeft <= 0 && canSmelt && f.fuel != null) {
                float secs = Recipes.fuelSeconds(f.fuel.item);
                if (secs > 0) {
                    f.burnLeft = secs;
                    f.burnTotal = secs;
                    f.fuel.count--;
                    if (f.fuel.count <= 0) f.fuel = null;
                }
            }

            if (f.burnLeft > 0) {
                f.burnLeft -= dt;
                if (canSmelt) {
                    f.progress += dt;
                    if (f.progress >= SMELT_SECONDS) {
                        f.progress = 0;
                        f.input.count--;
                        if (f.input.count <= 0) f.input = null;
                        if (f.output == null) {
                            f.output = result.copy();
                        } else {
                            f.output.count += result.count;
                        }
                    }
                } else {
                    f.progress = 0;
                }
            } else {
                f.progress = Math.max(0, f.progress - dt * 2);
            }

            boolean lit = f.burnLeft > 0;
            if (lit != wasLit) changed.put(e.getKey(), lit);
        }
        return changed;
    }

    // ------------------------------------------------------------------ loot

    /** Fills a chest with structure loot, deterministic per position. */
    public void fillChestLoot(int x, int y, int z, long seed, String table) {
        Chest c = chestAt(x, y, z);
        Random r = new Random(seed ^ key(x, y, z));
        Object[][] pool = switch (table) {
            case "shipwreck" -> new Object[][]{
                    {Items.GOLD_INGOT, 1, 4, 0.5f},
                    {Items.SILVER_INGOT, 1, 5, 0.5f},
                    {Items.IRON_INGOT, 1, 6, 0.7f},
                    {Items.APPLE, 1, 3, 0.6f},
                    {Items.SPIDER_SILK, 1, 4, 0.4f},
                    {Items.EMERALD, 1, 2, 0.25f},
                    {Items.forBlock(Blocks.get(Blocks.OAK_LOG.id + 4)), 4, 12, 0.8f},
            };
            case "dungeon" -> new Object[][]{
                    {Items.IRON_INGOT, 1, 4, 0.6f},
                    {Items.REDGLOW_DUST, 2, 6, 0.5f},
                    {Items.BONE, 2, 6, 0.7f},
                    {Items.ROTTEN_FLESH, 2, 5, 0.7f},
                    {Items.ARROW, 4, 10, 0.5f},
                    {Items.DIAMOND, 1, 2, 0.15f},
                    {Items.APPLE, 1, 2, 0.4f},
            };
            default -> new Object[][]{
                    {Items.COAL, 2, 6, 0.7f},
                    {Items.RAW_IRON, 1, 4, 0.6f},
                    {Items.APPLE, 1, 2, 0.5f},
                    {Items.STICK, 2, 8, 0.6f},
            };
        };
        for (Object[] entry : pool) {
            if (r.nextFloat() > (Float) entry[3]) continue;
            Item item = (Item) entry[0];
            int min = (Integer) entry[1], max = (Integer) entry[2];
            int count = min + r.nextInt(max - min + 1);
            int slot = r.nextInt(27);
            for (int tries = 0; tries < 27 && c.slots[slot] != null; tries++) {
                slot = (slot + 1) % 27;
            }
            if (c.slots[slot] == null) c.slots[slot] = new ItemStack(item, count);
        }
    }
}
