package com.blockforge.render;

import com.blockforge.player.Player;
import com.blockforge.world.Chunk;
import com.blockforge.world.Raycast;
import com.blockforge.world.World;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import static org.lwjgl.opengl.GL33C.*;

/**
 * World renderer: chunk meshes (opaque cut-out pass + translucent pass),
 * distance fog, day/night sky tint and the selection outline.
 */
public final class Renderer {

    private static final String WORLD_VS = """
            #version 330 core
            layout(location = 0) in vec3 aPos;
            layout(location = 1) in vec2 aUV;
            layout(location = 2) in float aShade;
            uniform mat4 uProj;
            uniform mat4 uView;
            uniform vec3 uOffset;
            out vec2 vUV;
            out float vShade;
            out float vDist;
            void main() {
                vec4 viewPos = uView * vec4(aPos + uOffset, 1.0);
                gl_Position = uProj * viewPos;
                vUV = aUV;
                vShade = aShade;
                vDist = length(viewPos.xyz);
            }
            """;

    private static final String WORLD_FS = """
            #version 330 core
            in vec2 vUV;
            in float vShade;
            in float vDist;
            uniform sampler2D uTex;
            uniform vec3 uFogColor;
            uniform float uFogStart;
            uniform float uFogEnd;
            uniform float uDayLight;
            uniform int uCutout;
            out vec4 fragColor;
            void main() {
                vec4 tex = texture(uTex, vUV);
                if (uCutout == 1 && tex.a < 0.5) discard;
                // negative shade marks emissive faces that ignore daylight
                float light = vShade < 0.0 ? -vShade : vShade * uDayLight;
                vec3 col = tex.rgb * min(light, 1.25);
                float fog = clamp((vDist - uFogStart) / (uFogEnd - uFogStart), 0.0, 1.0);
                fragColor = vec4(mix(col, uFogColor, fog), tex.a);
            }
            """;

    private static final String LINE_VS = """
            #version 330 core
            layout(location = 0) in vec3 aPos;
            uniform mat4 uProj;
            uniform mat4 uView;
            void main() {
                gl_Position = uProj * uView * vec4(aPos, 1.0);
            }
            """;

    private static final String LINE_FS = """
            #version 330 core
            uniform vec4 uColor;
            out vec4 fragColor;
            void main() {
                fragColor = uColor;
            }
            """;

    public int viewRadiusChunks;
    public float fovDegrees = 72f;
    public boolean fogEnabled = true;

    private final Shader worldShader;
    private final Shader lineShader;
    public final int atlasTexture;
    private final int lineVao;
    private final int lineVbo;

    private final Matrix4f proj = new Matrix4f();
    private final Matrix4f view = new Matrix4f();

    public Renderer(int viewRadiusChunks, int atlasTexture) {
        this.viewRadiusChunks = viewRadiusChunks;
        this.atlasTexture = atlasTexture;
        worldShader = new Shader(WORLD_VS, WORLD_FS);
        lineShader = new Shader(LINE_VS, LINE_FS);

        lineVao = glGenVertexArrays();
        lineVbo = glGenBuffers();
        glBindVertexArray(lineVao);
        glBindBuffer(GL_ARRAY_BUFFER, lineVbo);
        glBufferData(GL_ARRAY_BUFFER, 24L * 3 * Float.BYTES, GL_DYNAMIC_DRAW);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 3 * Float.BYTES, 0);
        glEnableVertexAttribArray(0);
        glBindVertexArray(0);

        glEnable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE); // double-sided: plants + no winding pitfalls
    }

    /** Rebuilds meshes for dirty chunks near the player, budgeted per frame. */
    public void updateMeshes(World world, Player player, int budget) {
        int pcx = Math.floorDiv((int) Math.floor(player.position.x), Chunk.SX);
        int pcz = Math.floorDiv((int) Math.floor(player.position.z), Chunk.SZ);
        int built = 0;
        for (int r = 0; r <= viewRadiusChunks && built < budget; r++) {
            for (int dx = -r; dx <= r && built < budget; dx++) {
                for (int dz = -r; dz <= r && built < budget; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    Chunk c = world.getChunk(pcx + dx, pcz + dz);
                    if (c == null || !c.generated || !c.dirty) continue;
                    ChunkMesher.Result res = ChunkMesher.build(world, c);
                    c.deleteMeshes();
                    c.opaqueMesh = new Mesh(res.opaque);
                    c.translucentMesh = new Mesh(res.translucent);
                    c.dirty = false;
                    built++;
                }
            }
        }
    }

    public void render(World world, Player player, float aspect, float dayLight,
                       Vector3f skyColor, Raycast.Hit selection) {
        glClearColor(skyColor.x, skyColor.y, skyColor.z, 1f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        proj.identity().perspective((float) Math.toRadians(fovDegrees), aspect, 0.05f, 512f);
        view.identity()
                .rotateX((float) Math.toRadians(player.pitch))
                .rotateY((float) Math.toRadians(player.yaw))
                .translate(-player.position.x, -(player.position.y + Player.EYE_HEIGHT), -player.position.z);

        float fogEnd = fogEnabled ? viewRadiusChunks * Chunk.SX - 8 : 10000f;
        float fogStart = fogEnabled ? fogEnd * 0.6f : 9000f;

        worldShader.use();
        worldShader.setMat4("uProj", proj);
        worldShader.setMat4("uView", view);
        worldShader.setInt("uTex", 0);
        worldShader.setVec3("uFogColor", skyColor.x, skyColor.y, skyColor.z);
        worldShader.setFloat("uFogStart", fogStart);
        worldShader.setFloat("uFogEnd", fogEnd);
        worldShader.setFloat("uDayLight", dayLight);

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, atlasTexture);

        int pcx = Math.floorDiv((int) Math.floor(player.position.x), Chunk.SX);
        int pcz = Math.floorDiv((int) Math.floor(player.position.z), Chunk.SZ);

        // opaque + cut-out pass
        worldShader.setInt("uCutout", 1);
        glDisable(GL_BLEND);
        for (Chunk c : world.loadedChunks()) {
            if (tooFar(c, pcx, pcz) || c.opaqueMesh == null) continue;
            worldShader.setVec3("uOffset", c.cx * Chunk.SX, 0, c.cz * Chunk.SZ);
            c.opaqueMesh.draw();
        }

        // translucent pass (water, glass, ice)
        worldShader.setInt("uCutout", 0);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDepthMask(false);
        for (Chunk c : world.loadedChunks()) {
            if (tooFar(c, pcx, pcz) || c.translucentMesh == null) continue;
            worldShader.setVec3("uOffset", c.cx * Chunk.SX, 0, c.cz * Chunk.SZ);
            c.translucentMesh.draw();
        }
        glDepthMask(true);

        if (selection != null) {
            drawSelectionBox(selection);
        }
        glDisable(GL_BLEND);
    }

    private boolean tooFar(Chunk c, int pcx, int pcz) {
        return Math.max(Math.abs(c.cx - pcx), Math.abs(c.cz - pcz)) > viewRadiusChunks;
    }

    private void drawSelectionBox(Raycast.Hit hit) {
        float e = 0.003f;
        float x0 = hit.x - e, y0 = hit.y - e, z0 = hit.z - e;
        float x1 = hit.x + 1 + e, y1 = hit.y + 1 + e, z1 = hit.z + 1 + e;
        float[] lines = {
                x0, y0, z0, x1, y0, z0, x1, y0, z0, x1, y0, z1,
                x1, y0, z1, x0, y0, z1, x0, y0, z1, x0, y0, z0,
                x0, y1, z0, x1, y1, z0, x1, y1, z0, x1, y1, z1,
                x1, y1, z1, x0, y1, z1, x0, y1, z1, x0, y1, z0,
                x0, y0, z0, x0, y1, z0, x1, y0, z0, x1, y1, z0,
                x1, y0, z1, x1, y1, z1, x0, y0, z1, x0, y1, z1,
        };
        lineShader.use();
        lineShader.setMat4("uProj", proj);
        lineShader.setMat4("uView", view);
        lineShader.setVec4("uColor", 0.05f, 0.05f, 0.05f, 0.9f);
        glBindVertexArray(lineVao);
        glBindBuffer(GL_ARRAY_BUFFER, lineVbo);
        glBufferSubData(GL_ARRAY_BUFFER, 0, lines);
        glLineWidth(2f);
        glDrawArrays(GL_LINES, 0, 24);
        glBindVertexArray(0);
    }

    public void delete() {
        // the atlas texture is owned by Main (shared with the GUI)
        worldShader.delete();
        lineShader.delete();
        glDeleteBuffers(lineVbo);
        glDeleteVertexArrays(lineVao);
    }
}
