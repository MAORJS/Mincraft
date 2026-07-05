package com.blockforge.entity;

import com.blockforge.item.ItemStack;
import com.blockforge.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Data-driven mob definitions: dimensions, stats, behavior class, and drops.
 * Body models and textures are defined in {@link MobModels}.
 */
public enum MobType {

    COW("Cow", 0.9f, 1.4f, 10, 1.6f, false, false, 0),
    PIG("Pig", 0.9f, 0.9f, 10, 1.7f, false, false, 0),
    SHEEP("Sheep", 0.9f, 1.2f, 8, 1.6f, false, false, 0),
    CHICKEN("Chicken", 0.5f, 0.7f, 4, 1.8f, false, false, 0),
    ZOMBIE("Zombie", 0.6f, 1.9f, 20, 2.4f, true, true, 3),
    SPIDER("Spider", 1.3f, 0.9f, 16, 3.0f, true, false, 2),
    SKELETON("Skeleton", 0.6f, 1.9f, 20, 2.2f, true, true, 2);

    public final String displayName;
    public final float width, height;
    public final int maxHealth;
    public final float speed;
    public final boolean hostile;
    public final boolean undead;   // burns in daylight
    public final int attackDamage;

    MobType(String displayName, float width, float height, int maxHealth,
            float speed, boolean hostile, boolean undead, int attackDamage) {
        this.displayName = displayName;
        this.width = width;
        this.height = height;
        this.maxHealth = maxHealth;
        this.speed = speed;
        this.hostile = hostile;
        this.undead = undead;
        this.attackDamage = attackDamage;
    }

    public List<ItemStack> drops(Random rng) {
        List<ItemStack> out = new ArrayList<>();
        switch (this) {
            case COW -> {
                out.add(new ItemStack(Items.RAW_BEEF, 1 + rng.nextInt(3)));
                if (rng.nextBoolean()) out.add(new ItemStack(Items.LEATHER, 1 + rng.nextInt(2)));
            }
            case PIG -> out.add(new ItemStack(Items.RAW_PORK, 1 + rng.nextInt(3)));
            case SHEEP -> {
                out.add(new ItemStack(Items.RAW_MUTTON, 1 + rng.nextInt(2)));
                out.add(new ItemStack(Items.forBlock(
                        com.blockforge.world.Blocks.all().stream()
                                .filter(b -> b.name.equals("White Cloth")).findFirst().orElseThrow()), 1));
            }
            case CHICKEN -> {
                out.add(new ItemStack(Items.RAW_CHICKEN, 1));
                if (rng.nextBoolean()) out.add(new ItemStack(Items.FEATHER, 1 + rng.nextInt(2)));
            }
            case ZOMBIE -> {
                if (rng.nextInt(3) > 0) out.add(new ItemStack(Items.ROTTEN_FLESH, 1 + rng.nextInt(2)));
            }
            case SPIDER -> {
                if (rng.nextInt(3) > 0) out.add(new ItemStack(Items.SPIDER_SILK, 1 + rng.nextInt(2)));
            }
            case SKELETON -> {
                out.add(new ItemStack(Items.BONE, rng.nextInt(3)));
                out.add(new ItemStack(Items.ARROW, rng.nextInt(3)));
            }
        }
        out.removeIf(s -> s.count <= 0);
        return out;
    }
}
