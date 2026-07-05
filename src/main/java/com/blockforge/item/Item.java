package com.blockforge.item;

/**
 * Immutable item definition. Every block has a matching block-item; tools,
 * materials, and foods are standalone items.
 */
public final class Item {

    public enum Kind {BLOCK, MATERIAL, TOOL, FOOD}

    public enum ToolClass {NONE, PICK, AXE, SHOVEL, SWORD, BOW}

    public final int id;
    public final String name;
    public final Kind kind;
    public final int icon;          // atlas tile
    public final int maxStack;
    public final int blockId;       // for Kind.BLOCK
    public final ToolClass toolClass;
    public final int tier;          // 1 wood, 2 stone, 3 iron/gold, 4 diamond
    public final float toolSpeed;   // mining speed multiplier
    public final int durability;    // 0 = unbreakable
    public final float attackDamage;
    public final int foodValue;     // hunger points restored

    Item(int id, String name, Kind kind, int icon, int maxStack, int blockId,
         ToolClass toolClass, int tier, float toolSpeed, int durability,
         float attackDamage, int foodValue) {
        this.id = id;
        this.name = name;
        this.kind = kind;
        this.icon = icon;
        this.maxStack = maxStack;
        this.blockId = blockId;
        this.toolClass = toolClass;
        this.tier = tier;
        this.toolSpeed = toolSpeed;
        this.durability = durability;
        this.attackDamage = attackDamage;
        this.foodValue = foodValue;
    }

    public boolean isBlock() {
        return kind == Kind.BLOCK;
    }

    public boolean isFood() {
        return kind == Kind.FOOD;
    }
}
