package com.blockforge.entity;

import com.blockforge.world.Blocks;
import com.blockforge.world.World;
import org.joml.Vector3f;

/**
 * Arrow projectile, fired by skeletons or the player's bow. Flies with light
 * gravity, sticks into blocks, and damages whatever it hits first.
 */
public final class Arrow extends Entity {

    public final float damage;
    /** true = fired by the player (hurts mobs), false = fired at the player. */
    public final boolean fromPlayer;
    public boolean stuck;

    public Arrow(World world, Vector3f from, Vector3f velocity, float damage, boolean fromPlayer) {
        super(world, 0.12f, 0.12f);
        position.set(from);
        this.velocity.set(velocity);
        this.damage = damage;
        this.fromPlayer = fromPlayer;
        this.yaw = (float) Math.toDegrees(Math.atan2(velocity.x, -velocity.z));
    }

    @Override
    public void tick(float dt) {
        age += dt;
        if (stuck) {
            if (age > 12f) dead = true;
            return;
        }
        if (age > 12f) {
            dead = true;
            return;
        }
        velocity.y -= 12f * dt;

        float nx = position.x + velocity.x * dt;
        float ny = position.y + velocity.y * dt;
        float nz = position.z + velocity.z * dt;
        int bx = (int) Math.floor(nx), by = (int) Math.floor(ny), bz = (int) Math.floor(nz);
        if (Blocks.get(world.getBlockId(bx, by, bz)).solid) {
            stuck = true;
            age = 0;
            return;
        }
        position.set(nx, ny, nz);
        yaw = (float) Math.toDegrees(Math.atan2(velocity.x, -velocity.z));
        if (position.y < -8) dead = true;
    }
}
