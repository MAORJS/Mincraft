package com.blockforge.render;

import com.blockforge.world.Block;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;
import java.util.List;

import static org.lwjgl.opengl.GL33C.*;

/**
 * 2D overlay: crosshair and a nine-slot hotbar with textured block icons.
 * Built as a dynamic quad batch each frame (a handful of quads — cheap).
 */
public final class Hud {

    private static final String HUD_VS = """
            #version 330 core
            layout(location = 0) in vec2 aPos;
            layout(location = 1) in vec2 aUV;
            layout(location = 2) in vec4 aColor;
            uniform mat4 uOrtho;
            out vec2 vUV;
            out vec4 vColor;
            void main() {
                gl_Position = uOrtho * vec4(aPos, 0.0, 1.0);
                vUV = aUV;
                vColor = aColor;
            }
            """;

    private static final String HUD_FS = """
            #version 330 core
            in vec2 vUV;
            in vec4 vColor;
            uniform sampler2D uTex;
            out vec4 fragColor;
            void main() {
                // aColor.a < 0 marks an untextured (solid color) quad
                if (vColor.a < 0.0) {
                    fragColor = vec4(vColor.rgb, -vColor.a);
                } else {
                    fragColor = texture(uTex, vUV) * vColor;
                }
            }
            """;

    private static final int MAX_QUADS = 64;
    private static final int FLOATS_PER_VERTEX = 8; // pos2 uv2 color4

    private final Shader shader;
    private final int vao;
    private final int vbo;
    private final FloatBuffer buf = BufferUtils.createFloatBuffer(MAX_QUADS * 6 * FLOATS_PER_VERTEX);
    private final Matrix4f ortho = new Matrix4f();
    private int quadCount;

    public Hud() {
        shader = new Shader(HUD_VS, HUD_FS);
        vao = glGenVertexArrays();
        vbo = glGenBuffers();
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, (long) MAX_QUADS * 6 * FLOATS_PER_VERTEX * Float.BYTES, GL_DYNAMIC_DRAW);
        int stride = FLOATS_PER_VERTEX * Float.BYTES;
        glVertexAttribPointer(0, 2, GL_FLOAT, false, stride, 0);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, stride, 2L * Float.BYTES);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(2, 4, GL_FLOAT, false, stride, 4L * Float.BYTES);
        glEnableVertexAttribArray(2);
        glBindVertexArray(0);
    }

    private void quad(float x, float y, float w, float h,
                      float u0, float v0, float u1, float v1,
                      float r, float g, float b, float a) {
        if (quadCount >= MAX_QUADS) return;
        float[][] vs = {
                {x, y, u0, v1}, {x, y + h, u0, v0}, {x + w, y + h, u1, v0},
                {x, y, u0, v1}, {x + w, y + h, u1, v0}, {x + w, y, u1, v1},
        };
        for (float[] v : vs) {
            buf.put(v[0]).put(v[1]).put(v[2]).put(v[3]).put(r).put(g).put(b).put(a);
        }
        quadCount++;
    }

    private void solidQuad(float x, float y, float w, float h, float r, float g, float b, float a) {
        quad(x, y, w, h, 0, 0, 0, 0, r, g, b, -a);
    }

    private void tileQuad(float x, float y, float w, float h, int tile) {
        float u0 = TextureAtlas.u0(tile) + TextureAtlas.UV_INSET;
        float v0 = TextureAtlas.v0(tile) + TextureAtlas.UV_INSET;
        float u1 = TextureAtlas.u0(tile) + TextureAtlas.UV_SPAN - TextureAtlas.UV_INSET;
        float v1 = TextureAtlas.v0(tile) + TextureAtlas.UV_SPAN - TextureAtlas.UV_INSET;
        quad(x, y, w, h, u0, v0, u1, v1, 1, 1, 1, 1);
    }

    /**
     * @param hotbar    the nine blocks currently in the hotbar window
     * @param selected  selected slot index 0..8
     */
    public void render(int width, int height, List<Block> hotbar, int selected, int atlasTexture) {
        buf.clear();
        quadCount = 0;

        float cx = width / 2f, cy = height / 2f;
        // crosshair
        solidQuad(cx - 10, cy - 1.5f, 20, 3, 0.95f, 0.95f, 0.95f, 0.75f);
        solidQuad(cx - 1.5f, cy - 10, 3, 20, 0.95f, 0.95f, 0.95f, 0.75f);

        // hotbar
        float slot = 52, pad = 5;
        float barW = hotbar.size() * slot + (hotbar.size() + 1) * pad;
        float x0 = cx - barW / 2f;
        float y0 = height - slot - 18;
        solidQuad(x0 - 2, y0 - pad - 2, barW + 4, slot + pad * 2 + 4, 0.08f, 0.08f, 0.08f, 0.6f);
        for (int i = 0; i < hotbar.size(); i++) {
            float sx = x0 + pad + i * (slot + pad);
            if (i == selected) {
                solidQuad(sx - 4, y0 - 4, slot + 8, slot + 8, 0.98f, 0.98f, 0.98f, 0.85f);
            }
            solidQuad(sx, y0, slot, slot, 0.25f, 0.25f, 0.28f, 0.85f);
            Block b = hotbar.get(i);
            if (b != null && !b.isAir()) {
                tileQuad(sx + 6, y0 + 6, slot - 12, slot - 12, b.texSide);
            }
        }

        // draw
        buf.flip();
        glDisable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        shader.use();
        ortho.identity().ortho2D(0, width, height, 0);
        shader.setMat4("uOrtho", ortho);
        shader.setInt("uTex", 0);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, atlasTexture);
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferSubData(GL_ARRAY_BUFFER, 0, buf);
        glDrawArrays(GL_TRIANGLES, 0, quadCount * 6);
        glBindVertexArray(0);
        glDisable(GL_BLEND);
        glEnable(GL_DEPTH_TEST);
    }

    public void delete() {
        shader.delete();
        glDeleteBuffers(vbo);
        glDeleteVertexArrays(vao);
    }
}
