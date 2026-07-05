package com.blockforge.entity;

import com.blockforge.world.Blocks;
import com.blockforge.world.Chunk;
import com.blockforge.world.World;
import org.joml.Vector3f;

/**
 * Base entity: AABB centered on (position.x, position.z) with feet at
 * position.y, axis-swept collision against solid blocks.
 */
public abstract class Entity {

    public final World world;
    public final Vector3f position = new Vector3f();
    public final Vector3f velocity = new Vector3f();
    public float width, height;
    public float yaw;
    public boolean onGround;
    public boolean dead;
    public float age;

    protected Entity(World world, float width, float height) {
        this.world = world;
        this.width = width;
        this.height = height;
    }

    public abstract void tick(float dt);

    protected void applyGravity(float dt) {
        velocity.y -= 30f * dt;
        if (velocity.y < -60f) velocity.y = -60f;
    }

    public boolean inLiquid() {
        int id = world.getBlockId((int) Math.floor(position.x),
                (int) Math.floor(position.y + height * 0.4f), (int) Math.floor(position.z));
        return Blocks.get(id).liquid;
    }

    /** Axis-separated swept move with substeps; zeroes velocity on the hit axes. */
    protected void move(float dx, float dy, float dz) {
        int steps = 1 + (int) (Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz))) / 0.4f);
        float sx = dx / steps, sy = dy / steps, sz = dz / steps;
        for (int i = 0; i < steps; i++) {
            if (stepAxis(sx, 0)) velocity.x = 0;
            boolean hitY = stepAxis(sy, 1);
            if (stepAxis(sz, 2)) velocity.z = 0;
            if (hitY && sy < 0) {
                onGround = true;
                velocity.y = 0;
            } else if (hitY) {
                velocity.y = 0;
            } else if (sy != 0) {
                onGround = false;
            }
        }
    }

    private boolean stepAxis(float amount, int axis) {
        if (amount == 0) return false;
        float nx = position.x + (axis == 0 ? amount : 0);
        float ny = position.y + (axis == 1 ? amount : 0);
        float nz = position.z + (axis == 2 ? amount : 0);
        if (!collides(nx, ny, nz)) {
            position.set(nx, ny, nz);
            return false;
        }
        float half = width / 2f;
        if (axis == 0) {
            position.x = amount > 0
                    ? (float) Math.floor(nx + half) - half - 0.001f
                    : (float) Math.floor(nx - half) + 1 + half + 0.001f;
        } else if (axis == 1) {
            position.y = amount > 0
                    ? (float) Math.floor(ny + height) - height - 0.001f
                    : (float) Math.floor(ny) + 1 + 0.001f;
        } else {
            position.z = amount > 0
                    ? (float) Math.floor(nz + half) - half - 0.001f
                    : (float) Math.floor(nz - half) + 1 + half + 0.001f;
        }
        return true;
    }

    public boolean collides(float px, float py, float pz) {
        float half = width / 2f;
        int x0 = (int) Math.floor(px - half);
        int x1 = (int) Math.floor(px + half);
        int y0 = (int) Math.floor(py);
        int y1 = (int) Math.floor(py + height);
        int z0 = (int) Math.floor(pz - half);
        int z1 = (int) Math.floor(pz + half);
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    if (y < 0 || y >= Chunk.SY) continue;
                    if (Blocks.get(world.getBlockId(x, y, z)).solid) return true;
                }
            }
        }
        return false;
    }

    public float distanceTo(Vector3f p) {
        float dx = p.x - position.x, dy = p.y - position.y, dz = p.z - position.z;
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** AABB overlap with another entity-style box. */
    public boolean intersects(Vector3f otherPos, float otherW, float otherH, float grow) {
        float hw = width / 2f + otherW / 2f + grow;
        return Math.abs(otherPos.x - position.x) < hw
                && Math.abs(otherPos.z - position.z) < hw
                && otherPos.y < position.y + height + grow
                && otherPos.y + otherH > position.y - grow;
    }

    /** Ray vs this entity's AABB; returns distance or -1. */
    public float rayIntersect(Vector3f origin, Vector3f dir, float maxDist) {
        float half = width / 2f;
        float tMin = 0f, tMax = maxDist;
        float[] lo = {position.x - half, position.y, position.z - half};
        float[] hi = {position.x + half, position.y + height, position.z + half};
        float[] o = {origin.x, origin.y, origin.z};
        float[] d = {dir.x, dir.y, dir.z};
        for (int i = 0; i < 3; i++) {
            if (Math.abs(d[i]) < 1e-8f) {
                if (o[i] < lo[i] || o[i] > hi[i]) return -1;
            } else {
                float inv = 1f / d[i];
                float t1 = (lo[i] - o[i]) * inv;
                float t2 = (hi[i] - o[i]) * inv;
                if (t1 > t2) {
                    float t = t1;
                    t1 = t2;
                    t2 = t;
                }
                tMin = Math.max(tMin, t1);
                tMax = Math.min(tMax, t2);
                if (tMin > tMax) return -1;
            }
        }
        return tMin;
    }
}
