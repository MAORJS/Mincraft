package com.blockforge.world;

import org.joml.Vector3f;

/**
 * Voxel ray traversal (grid DDA). Steps through the block grid along the view
 * ray and reports the first selectable block plus the face that was hit.
 */
public final class Raycast {

    public static final class Hit {
        public int x, y, z;          // block hit
        public int px, py, pz;       // adjacent block (placement position)
    }

    private Raycast() {
    }

    public static Hit cast(World world, Vector3f origin, Vector3f dir, float maxDist) {
        int x = (int) Math.floor(origin.x);
        int y = (int) Math.floor(origin.y);
        int z = (int) Math.floor(origin.z);

        float dx = dir.x, dy = dir.y, dz = dir.z;
        int stepX = dx > 0 ? 1 : -1;
        int stepY = dy > 0 ? 1 : -1;
        int stepZ = dz > 0 ? 1 : -1;

        float tDeltaX = dx == 0 ? Float.POSITIVE_INFINITY : Math.abs(1f / dx);
        float tDeltaY = dy == 0 ? Float.POSITIVE_INFINITY : Math.abs(1f / dy);
        float tDeltaZ = dz == 0 ? Float.POSITIVE_INFINITY : Math.abs(1f / dz);

        float tMaxX = dx == 0 ? Float.POSITIVE_INFINITY
                : (dx > 0 ? (x + 1 - origin.x) : (origin.x - x)) * tDeltaX;
        float tMaxY = dy == 0 ? Float.POSITIVE_INFINITY
                : (dy > 0 ? (y + 1 - origin.y) : (origin.y - y)) * tDeltaY;
        float tMaxZ = dz == 0 ? Float.POSITIVE_INFINITY
                : (dz > 0 ? (z + 1 - origin.z) : (origin.z - z)) * tDeltaZ;

        int lastX = x, lastY = y, lastZ = z;
        float t = 0;
        while (t <= maxDist) {
            int id = world.getBlockId(x, y, z);
            if (id != 0 && !Blocks.get(id).liquid) {
                Hit hit = new Hit();
                hit.x = x;
                hit.y = y;
                hit.z = z;
                hit.px = lastX;
                hit.py = lastY;
                hit.pz = lastZ;
                return hit;
            }
            lastX = x;
            lastY = y;
            lastZ = z;
            if (tMaxX < tMaxY && tMaxX < tMaxZ) {
                x += stepX;
                t = tMaxX;
                tMaxX += tDeltaX;
            } else if (tMaxY < tMaxZ) {
                y += stepY;
                t = tMaxY;
                tMaxY += tDeltaY;
            } else {
                z += stepZ;
                t = tMaxZ;
                tMaxZ += tDeltaZ;
            }
        }
        return null;
    }
}
