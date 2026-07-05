package com.blockforge.entity;

import com.blockforge.item.ItemStack;
import com.blockforge.world.World;
import org.joml.Vector3f;

/**
 * A dropped item floating in the world: bobs and spins, merges with nearby
 * identical drops, drifts toward a close player, and despawns after 5 minutes.
 */
public final class ItemEntity extends Entity {

    public static final float PICKUP_RADIUS = 1.4f;
    public static final float MAGNET_RADIUS = 2.6f;
    private static final float DESPAWN_SECONDS = 300f;

    public ItemStack stack;
    public float pickupDelay = 0.6f;

    public ItemEntity(World world, ItemStack stack, float x, float y, float z,
                      float vx, float vy, float vz) {
        super(world, 0.3f, 0.3f);
        this.stack = stack;
        position.set(x, y, z);
        velocity.set(vx, vy, vz);
    }

    @Override
    public void tick(float dt) {
        age += dt;
        pickupDelay = Math.max(0, pickupDelay - dt);
        if (age > DESPAWN_SECONDS) {
            dead = true;
            return;
        }
        if (inLiquid()) {
            velocity.y = Math.min(velocity.y + 12f * dt, 1.5f); // float up
        } else {
            applyGravity(dt);
        }
        // ground friction
        if (onGround) {
            velocity.x *= Math.max(0, 1 - 8f * dt);
            velocity.z *= Math.max(0, 1 - 8f * dt);
        }
        move(velocity.x * dt, velocity.y * dt, velocity.z * dt);
        if (position.y < -8) dead = true;
    }

    /** Pulls the drop toward the player when close (magnet effect). */
    public void attractTo(Vector3f target, float dt) {
        if (pickupDelay > 0) return;
        float d = distanceTo(target);
        if (d < MAGNET_RADIUS && d > 0.05f) {
            float pull = 9f * dt / d;
            velocity.x += (target.x - position.x) * pull;
            velocity.y += (target.y + 0.9f - position.y) * pull * 0.6f;
            velocity.z += (target.z - position.z) * pull;
        }
    }

    public boolean canMerge(ItemEntity other) {
        return other != this && !other.dead && !dead
                && stack.canMergeWith(other.stack)
                && stack.count + other.stack.count <= stack.item.maxStack
                && distanceTo(other.position) < 1.2f;
    }
}
