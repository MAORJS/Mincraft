package com.blockforge.render;

import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL33C.*;

/**
 * Dynamic 2D quad batch: colored/textured quads accumulated per frame and
 * flushed with a single texture bind. Quads with negative alpha are drawn as
 * untextured solid color (see shader).
 */
public final class Batch2D {

    private static final String VS = """
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

    private static final String FS = """
            #version 330 core
            in vec2 vUV;
            in vec4 vColor;
            uniform sampler2D uTex;
            out vec4 fragColor;
            void main() {
                if (vColor.a < 0.0) {
                    fragColor = vec4(vColor.rgb, -vColor.a);
                } else {
                    fragColor = texture(uTex, vUV) * vColor;
                }
            }
            """;

    private static final int MAX_QUADS = 8192;
    private static final int FLOATS_PER_VERTEX = 8; // pos2 uv2 rgba4

    private static Shader shader; // shared between all batches

    private final int vao;
    private final int vbo;
    private final FloatBuffer buf = BufferUtils.createFloatBuffer(MAX_QUADS * 6 * FLOATS_PER_VERTEX);
    private final Matrix4f ortho = new Matrix4f();
    private int quadCount;

    public Batch2D() {
        if (shader == null) {
            shader = new Shader(VS, FS);
        }
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

    public void begin() {
        buf.clear();
        quadCount = 0;
    }

    /** Textured quad (color multiplies the texture). */
    public void quad(float x, float y, float w, float h,
                     float u0, float v0, float u1, float v1,
                     float r, float g, float b, float a) {
        if (quadCount >= MAX_QUADS) return;
        // v1 maps to the top edge (tiles are stored flipped, see TextureAtlas)
        float[][] vs = {
                {x, y, u0, v1}, {x, y + h, u0, v0}, {x + w, y + h, u1, v0},
                {x, y, u0, v1}, {x + w, y + h, u1, v0}, {x + w, y, u1, v1},
        };
        for (float[] v : vs) {
            buf.put(v[0]).put(v[1]).put(v[2]).put(v[3]).put(r).put(g).put(b).put(a);
        }
        quadCount++;
    }

    /** Untextured solid-color quad. */
    public void solid(float x, float y, float w, float h, float r, float g, float b, float a) {
        quad(x, y, w, h, 0, 0, 0, 0, r, g, b, -a);
    }

    /** Draws everything accumulated since begin() using the given texture. */
    public void flush(int texture, int screenW, int screenH) {
        if (quadCount == 0) return;
        buf.flip();
        glDisable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        shader.use();
        ortho.identity().ortho2D(0, screenW, screenH, 0);
        shader.setMat4("uOrtho", ortho);
        shader.setInt("uTex", 0);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, texture);
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferSubData(GL_ARRAY_BUFFER, 0, buf);
        glDrawArrays(GL_TRIANGLES, 0, quadCount * 6);
        glBindVertexArray(0);
        glDisable(GL_BLEND);
        glEnable(GL_DEPTH_TEST);
    }

    public void delete() {
        glDeleteBuffers(vbo);
        glDeleteVertexArrays(vao);
    }
}
