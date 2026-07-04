package com.blockforge.world;

/**
 * Immutable definition of a block type. Instances are created once by the
 * {@link Blocks} registry at startup.
 */
public final class Block {

    public final int id;
    public final String name;
    /** Player collides with this block. */
    public final boolean solid;
    /** Completely hides neighbouring faces (full opaque cube). */
    public final boolean opaque;
    /** Rendered in the blended pass (water, glass, ice...). */
    public final boolean translucent;
    /** Liquid: not solid, merges faces with itself. */
    public final boolean liquid;
    /** Rendered as a crossed pair of quads (flowers, tall grass...). */
    public final boolean plant;
    /** 0..1 — minimum brightness regardless of daylight (lamps, lava). */
    public final float emissive;

    public final int texTop;
    public final int texSide;
    public final int texBottom;

    Block(int id, String name, boolean solid, boolean opaque, boolean translucent,
          boolean liquid, boolean plant, float emissive,
          int texTop, int texSide, int texBottom) {
        this.id = id;
        this.name = name;
        this.solid = solid;
        this.opaque = opaque;
        this.translucent = translucent;
        this.liquid = liquid;
        this.plant = plant;
        this.emissive = emissive;
        this.texTop = texTop;
        this.texSide = texSide;
        this.texBottom = texBottom;
    }

    public boolean isAir() {
        return id == 0;
    }
}
