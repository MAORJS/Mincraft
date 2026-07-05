package com.blockforge.item;

/** Mutable stack of one item type with count and (for tools) damage taken. */
public final class ItemStack {

    public final Item item;
    public int count;
    public int damage; // durability used so far

    public ItemStack(Item item, int count) {
        this.item = item;
        this.count = count;
    }

    public ItemStack(Item item, int count, int damage) {
        this.item = item;
        this.count = count;
        this.damage = damage;
    }

    public ItemStack copy() {
        return new ItemStack(item, count, damage);
    }

    public boolean canMergeWith(ItemStack other) {
        return other != null && other.item == item
                && item.durability == 0 && other.item.durability == 0
                && item.maxStack > 1;
    }

    /** Applies one point of tool wear; returns true if the tool broke. */
    public boolean damageTool() {
        if (item.durability <= 0) return false;
        damage++;
        return damage >= item.durability;
    }
}
