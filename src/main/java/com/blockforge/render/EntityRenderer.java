package com.blockforge.render;

import com.blockforge.entity.Arrow;
import com.blockforge.entity.Entity;
import com.blockforge.entity.ItemEntity;
import com.blockforge.entity.Mob;
import com.blockforge.entity.MobModels;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Draws entities as textured boxes through the world shader: mobs from their
 * box-part models, dropped items as small spinning cubes/cards, arrows as
 * thin sticks. Uses a shared unit-cube mesh transformed by uModel and
 * retargeted to atlas tiles via the uUVOffset/uUVScale uniforms.
 */
public final class EntityRenderer {

    private static Mesh unitCube;
    private static final Matrix4f model = new Matrix4f();

    private EntityRenderer() {
    }

    /** Unit cube [0,1]^3 with per-face shade, UV [0,1] per face. */
    private static Mesh cube() {
        if (unitCube != null) return unitCube;
        float[][] shades = {{1f}, {0.55f}, {0.65f}, {0.65f}, {0.82f}, {0.82f}};
        int[][][] corners = {
                {{0, 1, 0}, {0, 1, 1}, {1, 1, 1}, {1, 1, 0}}, // top
                {{0, 0, 0}, {1, 0, 0}, {1, 0, 1}, {0, 0, 1}}, // bottom
                {{1, 1, 0}, {1, 0, 0}, {1, 0, 1}, {1, 1, 1}}, // +x
                {{0, 1, 1}, {0, 0, 1}, {0, 0, 0}, {0, 1, 0}}, // -x
                {{0, 1, 1}, {0, 0, 1}, {1, 0, 1}, {1, 1, 1}}, // +z
                {{1, 1, 0}, {1, 0, 0}, {0, 0, 0}, {0, 1, 0}}, // -z
        };
        float[][] uv = {{0, 1}, {0, 0}, {1, 0}, {1, 1}};
        int[] order = {0, 1, 2, 0, 2, 3};
        float[] data = new float[6 * 6 * 6];
        int i = 0;
        for (int f = 0; f < 6; f++) {
            for (int o : order) {
                data[i++] = corners[f][o][0];
                data[i++] = corners[f][o][1];
                data[i++] = corners[f][o][2];
                data[i++] = uv[o][0];
                data[i++] = uv[o][1];
                data[i++] = shades[f][0];
            }
        }
        unitCube = new Mesh(data);
        return unitCube;
    }

    private static void tileUV(Shader shader, int tile) {
        shader.setVec2("uUVOffset",
                TextureAtlas.u0(tile) + TextureAtlas.UV_INSET,
                TextureAtlas.v0(tile) + TextureAtlas.UV_INSET);
        shader.setVec2("uUVScale",
                TextureAtlas.UV_SPAN - 2 * TextureAtlas.UV_INSET,
                TextureAtlas.UV_SPAN - 2 * TextureAtlas.UV_INSET);
    }

    public static void render(Shader shader, List<Entity> entities, double time) {
        Mesh mesh = cube();
        shader.setVec3("uOffset", 0, 0, 0);
        for (Entity e : entities) {
            if (e instanceof Mob mob) {
                renderMob(shader, mesh, mob);
            } else if (e instanceof ItemEntity item) {
                renderItem(shader, mesh, item, time);
            } else if (e instanceof Arrow arrow) {
                renderArrow(shader, mesh, arrow);
            }
        }
    }

    private static void renderMob(Shader shader, Mesh mesh, Mob mob) {
        MobModels.Part[] parts = MobModels.model(mob.type);
        if (parts == null) return;
        float swing = (float) Math.sin(mob.animTime) * 0.55f;
        float yawRad = (float) Math.toRadians(-mob.yaw);
        // hurt flash: brighten via daylight override is complex; nudge upward instead
        float hurtLift = mob.hurtTime > 0 ? (float) Math.sin(mob.hurtTime * 25) * 0.03f : 0f;

        for (MobModels.Part p : parts) {
            float rot = switch (p.anim()) {
                case LEG_A -> swing;
                case LEG_B -> -swing;
                default -> 0f;
            };
            model.identity()
                    .translate(mob.position.x, mob.position.y + hurtLift, mob.position.z)
                    .rotateY(yawRad)
                    .translate(p.px(), p.py(), p.pz())
                    .rotateX(rot)
                    .translate(p.ox(), p.oy(), p.oz())
                    .scale(p.sx() / 16f, p.sy() / 16f, p.sz() / 16f);
            shader.setMat4("uModel", model);
            tileUV(shader, p.tile());
            mesh.draw();
        }
    }

    private static void renderItem(Shader shader, Mesh mesh, ItemEntity item, double time) {
        float bob = (float) Math.sin(time * 2.2 + item.age) * 0.06f + 0.12f;
        float spin = (float) (time * 1.4 + item.age);
        boolean isBlock = item.stack.item.isBlock();
        float s = 0.28f;
        float depth = isBlock ? s : 0.06f;
        model.identity()
                .translate(item.position.x, item.position.y + bob, item.position.z)
                .rotateY(spin)
                .translate(-s / 2f, 0, -depth / 2f)
                .scale(s, s, depth);
        shader.setMat4("uModel", model);
        tileUV(shader, item.stack.item.icon);
        mesh.draw();
    }

    private static void renderArrow(Shader shader, Mesh mesh, Arrow arrow) {
        model.identity()
                .translate(arrow.position.x, arrow.position.y, arrow.position.z)
                .rotateY((float) Math.toRadians(-arrow.yaw))
                .translate(-0.03f, -0.03f, -0.35f)
                .scale(0.06f, 0.06f, 0.7f);
        shader.setMat4("uModel", model);
        tileUV(shader, com.blockforge.item.Items.ARROW.icon);
        mesh.draw();
    }
}
