package com.blockforge.entity;

import com.blockforge.world.World;
import org.joml.Vector3f;

import java.util.Random;

/**
 * A creature. Passive mobs wander; hostile mobs chase the player and attack
 * on contact; skeletons keep range and shoot arrows. AI is deliberately
 * simple — distance-based, no pathfinding.
 */
public final class Mob extends Entity {

    public final MobType type;
    public float health;
    public float animTime;          // drives leg swing
    public float hurtTime;          // red flash timer

    private final Random rng = new Random();
    private float wanderTimer;
    private float wanderDX, wanderDZ;
    private float attackCooldown;
    private float shootCooldown;
    public float burnTimer;

    /** Set by Game each frame so AI can see the player. */
    public Vector3f playerPos;
    public boolean playerDead;

    /** Game collects arrows the mob wants to fire. */
    public Arrow pendingArrow;

    public Mob(World world, MobType type, float x, float y, float z) {
        super(world, type.width, type.height);
        this.type = type;
        this.health = type.maxHealth;
        position.set(x, y, z);
        yaw = rng.nextFloat() * 360f;
    }

    @Override
    public void tick(float dt) {
        age += dt;
        hurtTime = Math.max(0, hurtTime - dt);
        attackCooldown = Math.max(0, attackCooldown - dt);
        shootCooldown = Math.max(0, shootCooldown - dt);

        float moveX = 0, moveZ = 0;
        float dist = playerPos == null ? Float.MAX_VALUE : distanceTo(playerPos);
        boolean chasing = type.hostile && !playerDead && dist < 20f;

        if (chasing) {
            float dx = playerPos.x - position.x;
            float dz = playerPos.z - position.z;
            float len = (float) Math.sqrt(dx * dx + dz * dz);
            if (len > 0.01f) {
                dx /= len;
                dz /= len;
            }
            if (type == MobType.SKELETON) {
                // keep distance and shoot
                if (dist > 11f) {
                    moveX = dx;
                    moveZ = dz;
                } else if (dist < 6f) {
                    moveX = -dx;
                    moveZ = -dz;
                }
                if (dist < 14f && shootCooldown <= 0) {
                    shootCooldown = 2.4f;
                    Vector3f from = new Vector3f(position.x, position.y + height * 0.85f, position.z);
                    Vector3f target = new Vector3f(playerPos.x, playerPos.y + 1.4f, playerPos.z);
                    Vector3f dir = target.sub(from, new Vector3f()).normalize();
                    pendingArrow = new Arrow(world, from, dir.mul(18f), 3, false);
                }
            } else {
                moveX = dx;
                moveZ = dz;
            }
            yaw = (float) Math.toDegrees(Math.atan2(moveX == 0 ? dx : moveX, -(moveZ == 0 ? dz : moveZ)));
        } else {
            // wander: pick a direction every few seconds, sometimes stand still
            wanderTimer -= dt;
            if (wanderTimer <= 0) {
                wanderTimer = 2f + rng.nextFloat() * 4f;
                if (rng.nextInt(3) == 0) {
                    wanderDX = 0;
                    wanderDZ = 0;
                } else {
                    float a = rng.nextFloat() * (float) Math.PI * 2;
                    wanderDX = (float) Math.sin(a);
                    wanderDZ = (float) Math.cos(a);
                }
            }
            moveX = wanderDX * 0.45f;
            moveZ = wanderDZ * 0.45f;
            if (moveX != 0 || moveZ != 0) {
                yaw = (float) Math.toDegrees(Math.atan2(moveX, -moveZ));
            }
        }

        boolean water = inLiquid();
        velocity.x = moveX * type.speed;
        velocity.z = moveZ * type.speed;
        if (water) {
            velocity.y = Math.max(velocity.y - 9f * dt, -2f);
            if (rng.nextInt(4) == 0) velocity.y = 3f; // paddle up
        } else {
            applyGravity(dt);
        }

        // hop over obstacles: blocked horizontally while on the ground
        float beforeX = position.x, beforeZ = position.z;
        move(velocity.x * dt, velocity.y * dt, velocity.z * dt);
        boolean movingIntent = Math.abs(moveX) + Math.abs(moveZ) > 0.05f;
        boolean blocked = movingIntent
                && Math.abs(position.x - beforeX) + Math.abs(position.z - beforeZ) < 0.25f * type.speed * dt;
        if (blocked && onGround) {
            velocity.y = 8.2f;
            onGround = false;
        }

        if (movingIntent) animTime += dt * type.speed * 3f;

        // fall out of the world
        if (position.y < -8) dead = true;
    }

    /** True if the player is within melee reach and the attack is off cooldown. */
    public boolean tryMeleeAttack() {
        if (!type.hostile || type == MobType.SKELETON || playerPos == null) return false;
        if (attackCooldown > 0) return false;
        if (!intersects(playerPos, 0.6f, 1.8f, 0.35f)) return false;
        attackCooldown = 1.1f;
        return true;
    }

    /** @return true if this hit killed the mob. */
    public boolean damage(float amount, float knockX, float knockZ) {
        health -= amount;
        hurtTime = 0.4f;
        velocity.x += knockX;
        velocity.z += knockZ;
        velocity.y = Math.max(velocity.y, 5.5f);
        onGround = false;
        if (health <= 0) {
            dead = true;
            return true;
        }
        return false;
    }
}
