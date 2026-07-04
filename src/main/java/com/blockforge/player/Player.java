package com.blockforge.player;

import com.blockforge.world.Block;
import com.blockforge.world.Blocks;
import com.blockforge.world.Chunk;
import com.blockforge.world.World;
import org.joml.Vector3f;

/**
 * First-person player: AABB physics (gravity, jumping, swimming), axis-swept
 * collision against solid blocks, and a fly mode.
 */
public final class Player {

    public static final float WIDTH = 0.6f;
    public static final float HEIGHT = 1.8f;
    public static final float EYE_HEIGHT = 1.62f;

    private static final float GRAVITY = 30f;
    private static final float JUMP_SPEED = 8.6f;
    private static final float WALK_SPEED = 4.4f;
    private static final float SPRINT_SPEED = 6.4f;
    private static final float FLY_SPEED = 11f;
    private static final float SWIM_SPEED = 3f;

    /** Feet position (bottom center of the AABB). */
    public final Vector3f position = new Vector3f();
    public final Vector3f velocity = new Vector3f();

    /** Degrees. Yaw 0 looks toward -Z; pitch positive looks down. */
    public float yaw;
    public float pitch;

    public boolean onGround;
    public boolean flying;

    private final World world;

    public Player(World world) {
        this.world = world;
    }

    public void turn(float dyaw, float dpitch) {
        yaw = (yaw + dyaw) % 360f;
        pitch = Math.max(-89.9f, Math.min(89.9f, pitch + dpitch));
    }

    public Vector3f lookDir() {
        float cy = (float) Math.cos(Math.toRadians(yaw));
        float sy = (float) Math.sin(Math.toRadians(yaw));
        float cp = (float) Math.cos(Math.toRadians(pitch));
        float sp = (float) Math.sin(Math.toRadians(pitch));
        return new Vector3f(sy * cp, -sp, -cy * cp).normalize();
    }

    public Vector3f eyePosition() {
        return new Vector3f(position.x, position.y + EYE_HEIGHT, position.z);
    }

    public boolean inWater() {
        Block b = world.getBlock((int) Math.floor(position.x),
                (int) Math.floor(position.y + 0.4f), (int) Math.floor(position.z));
        return b.liquid;
    }

    /**
     * @param forward -1..1 movement along the look direction (ground plane)
     * @param strafe  -1..1 movement sideways
     * @param jump    jump / fly up held
     * @param sneak   fly down held
     * @param sprint  sprint held
     */
    public void tick(float dt, float forward, float strafe, boolean jump, boolean sneak, boolean sprint) {
        boolean water = inWater();

        float cy = (float) Math.cos(Math.toRadians(yaw));
        float sy = (float) Math.sin(Math.toRadians(yaw));
        float ax = (sy * forward + cy * strafe);
        float az = (-cy * forward + sy * strafe);
        float len = (float) Math.sqrt(ax * ax + az * az);
        if (len > 1e-4f) {
            ax /= len;
            az /= len;
        }

        float speed = flying ? FLY_SPEED : (water ? SWIM_SPEED : (sprint ? SPRINT_SPEED : WALK_SPEED));
        velocity.x = ax * speed;
        velocity.z = az * speed;

        if (flying) {
            velocity.y = (jump ? FLY_SPEED : 0) - (sneak ? FLY_SPEED : 0);
        } else if (water) {
            velocity.y -= GRAVITY * 0.3f * dt;
            velocity.y = Math.max(velocity.y, -3.5f);
            if (jump) velocity.y = 4f;
        } else {
            velocity.y -= GRAVITY * dt;
            velocity.y = Math.max(velocity.y, -60f);
            if (jump && onGround) {
                velocity.y = JUMP_SPEED;
                onGround = false;
            }
        }

        move(velocity.x * dt, velocity.y * dt, velocity.z * dt);
    }

    /** Axis-separated swept movement with collision response. */
    private void move(float dx, float dy, float dz) {
        // move in small substeps so fast falls can't tunnel through blocks
        int steps = 1 + (int) (Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz))) / 0.4f);
        float sx = dx / steps, sy = dy / steps, sz = dz / steps;
        for (int i = 0; i < steps; i++) {
            stepAxis(sx, 0);
            boolean hitY = stepAxis(sy, 1);
            stepAxis(sz, 2);
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

    /** Moves along one axis; returns true if a collision clipped the motion. */
    private boolean stepAxis(float amount, int axis) {
        if (amount == 0) return false;
        float nx = position.x + (axis == 0 ? amount : 0);
        float ny = position.y + (axis == 1 ? amount : 0);
        float nz = position.z + (axis == 2 ? amount : 0);
        if (!collides(nx, ny, nz)) {
            position.set(nx, ny, nz);
            return false;
        }
        // clipped: snap flush against the obstacle
        float half = WIDTH / 2f;
        if (axis == 0) {
            position.x = amount > 0
                    ? (float) Math.floor(nx + half) - half - 0.001f
                    : (float) Math.floor(nx - half) + 1 + half + 0.001f;
        } else if (axis == 1) {
            position.y = amount > 0
                    ? (float) Math.floor(ny + HEIGHT) - HEIGHT - 0.001f
                    : (float) Math.floor(ny) + 1 + 0.001f;
        } else {
            position.z = amount > 0
                    ? (float) Math.floor(nz + half) - half - 0.001f
                    : (float) Math.floor(nz - half) + 1 + half + 0.001f;
        }
        return true;
    }

    /** AABB vs solid blocks. */
    public boolean collides(float px, float py, float pz) {
        float half = WIDTH / 2f;
        int x0 = (int) Math.floor(px - half);
        int x1 = (int) Math.floor(px + half);
        int y0 = (int) Math.floor(py);
        int y1 = (int) Math.floor(py + HEIGHT);
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

    /** True if placing a block at the given cell would overlap the player. */
    public boolean intersectsBlock(int bx, int by, int bz) {
        float half = WIDTH / 2f;
        return bx + 1 > position.x - half && bx < position.x + half
                && by + 1 > position.y && by < position.y + HEIGHT
                && bz + 1 > position.z - half && bz < position.z + half;
    }

    /** Drops the player on the surface at (x, z). */
    public void spawnAt(int x, int z) {
        int h = world.surfaceHeight(x, z);
        position.set(x + 0.5f, h + 1.01f, z + 0.5f);
        velocity.zero();
        onGround = false;
    }
}
